package dev.vaijanath.aiagent.graph;

import dev.vaijanath.aiagent.agent.Agent;
import dev.vaijanath.aiagent.agent.AgentRequest;
import dev.vaijanath.aiagent.agent.AgentResponse;
import dev.vaijanath.aiagent.agent.RequestContext;
import dev.vaijanath.aiagent.checkpoint.Checkpoint;
import dev.vaijanath.aiagent.checkpoint.CheckpointStore;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A small <b>workflow graph</b>: named nodes joined by {@link Edge}s, walked one node at a time from a
 * start node until an edge leads to {@link #END} (the LangGraph shape). Each node is any {@link Agent}
 * — a {@code DefaultAgent}, a {@code DeepAgent}, a {@code ManagerAgent}, a {@code GroupChatAgent}, or
 * another {@code GraphAgent} — so the graph <b>generalizes</b> the other orchestrators: it adds
 * conditional routing and <b>cycles</b> (an edge may route back to an earlier node) on top of them.
 *
 * <p>The state passed between nodes is the text each node produces: a node runs on the current state
 * and its output becomes the next state. Itself an {@link Agent}, so it composes and can be wrapped
 * once with {@code Trust.govern(...)}; each node runs in a fresh {@link RequestContext#childSession()
 * child session}. With an optional {@link CheckpointStore} the walk is <b>crash-resumable</b>: the
 * current node and state are saved before each step (keyed by the request's {@code traceId}) and a
 * retry resumes there. A {@code blocked} node is surfaced as-is; the step budget bounds cycles.
 */
public final class GraphAgent implements Agent {

    /** Sentinel an {@link Edge} returns to end the walk; the current state is the answer. */
    public static final String END = "__end__";

    private static final Logger log = LoggerFactory.getLogger(GraphAgent.class);

    private final Map<String, Agent> nodes;
    private final Map<String, Edge> edges;
    private final String start;
    private final int maxSteps;
    private final CheckpointStore checkpoints;

    private GraphAgent(Builder b) {
        if (b.nodes.isEmpty()) {
            throw new IllegalArgumentException("at least one node is required");
        }
        this.nodes = Map.copyOf(b.nodes);
        this.edges = Map.copyOf(b.edges);
        this.start = b.start != null ? b.start : b.firstNode;
        if (!nodes.containsKey(start)) {
            throw new IllegalArgumentException("start '" + start + "' is not a registered node");
        }
        this.maxSteps = Math.max(1, b.maxSteps);
        this.checkpoints = b.checkpoints;
    }

    /** Keys the durable record of one walk: its tenant and resume id. */
    private record RunKey(String tenant, String runId) {
    }

    @Override
    public AgentResponse run(AgentRequest request) {
        RequestContext ctx = request.context();
        String current = start;
        String state = request.input();

        RunKey run = null;
        if (checkpoints != null) {
            run = new RunKey(ctx.tenant(), ctx.traceId());
            Optional<Checkpoint> saved = checkpoints.load(run.tenant(), run.runId());
            if (saved.isPresent() && !saved.get().steps().isEmpty()) {
                Checkpoint.Step at = saved.get().steps().get(0);
                current = nodes.containsKey(at.description()) ? at.description() : start;
                state = at.result();
                log.info("resuming graph run '{}' at node '{}'", run.runId(), current);
            }
        }

        for (int step = 1; step <= maxSteps; step++) {
            persist(run, current, state); // "about to run `current` with `state`" — so a crash resumes here
            AgentResponse response = nodes.get(current).run(new AgentRequest(state, ctx.childSession()));
            if (response.blocked()) {
                return response;
            }
            state = response.output();
            Edge edge = edges.get(current);
            String next = edge == null ? END : edge.next(state);
            if (next == null || next.equals(END) || !nodes.containsKey(next)) {
                log.info("graph reached an end after {} step(s) (last node '{}')", step, current);
                deleteCheckpoint(run);
                return AgentResponse.completed(state);
            }
            log.info("graph step {}/{}: '{}' -> '{}'", step, maxSteps, current, next);
            current = next;
        }

        log.info("graph hit the step budget ({})", maxSteps);
        deleteCheckpoint(run);
        return AgentResponse.stopped(state, "max_steps");
    }

    private void persist(RunKey run, String node, String state) {
        if (run != null) {
            checkpoints.save(run.tenant(), run.runId(),
                    new Checkpoint(node, List.of(new Checkpoint.Step(0, node, List.of(), "ACTIVE", state))));
        }
    }

    private void deleteCheckpoint(RunKey run) {
        if (run != null) {
            checkpoints.delete(run.tenant(), run.runId());
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Fluent builder. At least one {@code node} is required; edges default to {@link #END}. */
    public static final class Builder {

        private final Map<String, Agent> nodes = new LinkedHashMap<>();
        private final Map<String, Edge> edges = new LinkedHashMap<>();
        private String start;
        private String firstNode;
        private int maxSteps = 25;
        private CheckpointStore checkpoints;

        /** Registers a node; any {@link Agent} can be a node (so graphs nest). */
        public Builder node(String name, Agent agent) {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(agent, "agent");
            if (firstNode == null) {
                firstNode = name;
            }
            nodes.put(name, agent);
            return this;
        }

        /** The node to run first (default: the first registered). */
        public Builder start(String name) {
            this.start = name;
            return this;
        }

        /** An unconditional transition: after {@code from}, always go to {@code to}. */
        public Builder edge(String from, String to) {
            edges.put(from, state -> to);
            return this;
        }

        /** A conditional transition: after {@code from}, the {@link Edge} picks the next node from state. */
        public Builder edge(String from, Edge edge) {
            edges.put(from, edge);
            return this;
        }

        /** Maximum node visits before the walk stops (bounds cycles; default 25). */
        public Builder maxSteps(int maxSteps) {
            this.maxSteps = maxSteps;
            return this;
        }

        /** Makes the walk crash-resumable, keyed by the request's {@code traceId}. Optional. */
        public Builder checkpoints(CheckpointStore checkpoints) {
            this.checkpoints = checkpoints;
            return this;
        }

        public GraphAgent build() {
            return new GraphAgent(this);
        }
    }
}
