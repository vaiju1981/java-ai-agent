package dev.vaijanath.aiagent.supervise;

import dev.vaijanath.aiagent.agent.Agent;
import dev.vaijanath.aiagent.agent.AgentRequest;
import dev.vaijanath.aiagent.agent.AgentResponse;
import dev.vaijanath.aiagent.agent.RequestContext;
import dev.vaijanath.aiagent.checkpoint.Checkpoint;
import dev.vaijanath.aiagent.checkpoint.CheckpointStore;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Coordinates specialists in a loop: <b>decide &rarr; delegate &rarr; observe &rarr; decide again</b>,
 * until the {@link Manager} finishes or a round budget is spent. Where {@link SupervisorAgent} routes
 * once and returns, a manager can delegate, inspect the result, and re-delegate — to the same
 * specialist to refine, or to a different one to take the next step.
 *
 * <p>Like every other orchestrator here it is itself an {@link Agent}, so it nests: a specialist may
 * be a {@code DeepAgent}, a {@code ReflectiveAgent}, a {@link SupervisorAgent}, or another
 * {@code ManagerAgent}, and the whole tree can be wrapped once with {@code Trust.govern(...)} so the
 * same guardrails and deadline apply to every node.
 *
 * <p>Each delegation runs in a {@link RequestContext#childSession() child session}: the caller's
 * identity, tenant, trace, and deadline carry through, but specialists never share conversation memory
 * with each other or the parent.
 *
 * <p>With an optional {@link CheckpointStore} the loop becomes <b>crash-resumable</b>: the round
 * history is saved after each delegation (keyed by the request's {@code traceId}), and a retry with
 * the same {@code traceId} reloads it and continues from where it stopped — re-deciding from the
 * restored history rather than starting over. A clean finish (or spending the budget) deletes the
 * checkpoint. Without a store, nothing is persisted and behavior is unchanged.
 */
public final class ManagerAgent implements Agent {

    private static final Logger log = LoggerFactory.getLogger(ManagerAgent.class);

    private final Map<String, Agent> agents;
    private final Map<String, String> descriptions;
    private final Manager manager;
    private final String fallback;
    private final int maxRounds;
    private final CheckpointStore checkpoints; // null = no durable resume

    private ManagerAgent(Builder b) {
        if (b.agents.isEmpty()) {
            throw new IllegalArgumentException("at least one specialist is required");
        }
        this.agents = Map.copyOf(b.agents);
        this.descriptions = Map.copyOf(b.descriptions);
        this.manager = Objects.requireNonNull(b.manager, "manager");
        this.fallback = b.fallback != null ? b.fallback : b.firstName;
        if (!agents.containsKey(fallback)) {
            throw new IllegalArgumentException("fallback '" + fallback + "' is not a registered specialist");
        }
        this.maxRounds = Math.max(1, b.maxRounds);
        this.checkpoints = b.checkpoints;
    }

    /** Keys the durable record of one run: its tenant and resume id. */
    private record RunKey(String tenant, String runId) {
    }

    @Override
    public AgentResponse run(AgentRequest request) {
        String task = request.input();
        RequestContext ctx = request.context();

        RunKey run = null;
        List<Manager.Round> history = new ArrayList<>();
        if (checkpoints != null) {
            run = new RunKey(ctx.tenant(), ctx.traceId());
            Optional<Checkpoint> saved = checkpoints.load(run.tenant(), run.runId());
            if (saved.isPresent()) {
                task = saved.get().task(); // resume the original task
                history = restore(saved.get());
                log.info("resuming manager run '{}' from checkpoint ({} round(s) done)",
                        run.runId(), history.size());
            }
        }
        String lastOutput = history.isEmpty() ? "" : history.get(history.size() - 1).output();

        for (int round = history.size() + 1; round <= maxRounds; round++) {
            Manager.Decision decision =
                    Objects.requireNonNull(manager.decide(task, List.copyOf(history), descriptions),
                            "manager returned a null decision");

            if (decision.done()) {
                log.info("manager finished after {} delegation(s)", history.size());
                deleteCheckpoint(run);
                return AgentResponse.completed(decision.answer() != null ? decision.answer() : lastOutput);
            }

            Agent specialist = agents.get(decision.specialist());
            if (specialist == null) {
                log.warn("manager chose unknown specialist '{}'; using fallback '{}'",
                        decision.specialist(), fallback);
                specialist = agents.get(fallback);
            }
            String instruction = decision.instruction() != null ? decision.instruction() : task;

            AgentResponse resp = specialist.run(new AgentRequest(instruction, ctx.childSession()));
            lastOutput = resp.output();
            history.add(new Manager.Round(decision.specialist(), instruction, resp.output()));
            persist(run, task, history);
            log.info("round {}/{}: delegated to '{}'", round, maxRounds, decision.specialist());
        }

        log.info("manager hit round budget ({}); returning the last result", maxRounds);
        deleteCheckpoint(run);
        return AgentResponse.stopped(lastOutput, "max_rounds");
    }

    /** Saves the round history after a delegation (no-op when not checkpointing). */
    private void persist(RunKey run, String task, List<Manager.Round> history) {
        if (run != null) {
            checkpoints.save(run.tenant(), run.runId(), snapshot(task, history));
        }
    }

    private void deleteCheckpoint(RunKey run) {
        if (run != null) {
            checkpoints.delete(run.tenant(), run.runId());
        }
    }

    private static Checkpoint snapshot(String task, List<Manager.Round> history) {
        List<Checkpoint.Step> steps = new ArrayList<>();
        int index = 1;
        for (Manager.Round round : history) {
            steps.add(new Checkpoint.Step(
                    index++, round.specialist(), List.of(), "DONE", pack(round.instruction(), round.output())));
        }
        return new Checkpoint(task, steps);
    }

    private static List<Manager.Round> restore(Checkpoint checkpoint) {
        List<Manager.Round> history = new ArrayList<>();
        for (Checkpoint.Step step : checkpoint.steps()) {
            String[] io = unpack(step.result());
            history.add(new Manager.Round(step.description(), io[0], io[1]));
        }
        return history;
    }

    // A round's instruction and output share one field, length-prefixed, so neither can corrupt the
    // other regardless of content (there is no delimiter to collide with).
    private static String pack(String instruction, String output) {
        return instruction.length() + ":" + instruction + output;
    }

    private static String[] unpack(String packed) {
        int colon = packed.indexOf(':');
        int length = Integer.parseInt(packed.substring(0, colon));
        return new String[] {
            packed.substring(colon + 1, colon + 1 + length), packed.substring(colon + 1 + length)
        };
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Fluent builder. At least one {@code specialist} and a {@code manager} are required. */
    public static final class Builder {

        private final Map<String, Agent> agents = new LinkedHashMap<>();
        private final Map<String, String> descriptions = new LinkedHashMap<>();
        private Manager manager;
        private String fallback;
        private String firstName;
        private int maxRounds = 5;
        private CheckpointStore checkpoints;

        /** Registers a specialist; its {@code description} guides the {@link Manager}'s choices. */
        public Builder specialist(String name, String description, Agent agent) {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(agent, "agent");
            if (firstName == null) {
                firstName = name;
            }
            agents.put(name, agent);
            descriptions.put(name, description == null ? "" : description);
            return this;
        }

        public Builder manager(Manager manager) {
            this.manager = manager;
            return this;
        }

        /** The specialist to use when the manager names an unknown one (default: the first added). */
        public Builder fallback(String name) {
            this.fallback = name;
            return this;
        }

        /** Maximum delegations before the loop stops and returns the last result (default 5). */
        public Builder maxRounds(int maxRounds) {
            this.maxRounds = maxRounds;
            return this;
        }

        /**
         * Makes the loop crash-resumable: the round history is saved after each delegation (keyed by
         * the request's {@code traceId}) and a retry with the same {@code traceId} resumes. Optional —
         * without it nothing is persisted.
         */
        public Builder checkpoints(CheckpointStore checkpoints) {
            this.checkpoints = checkpoints;
            return this;
        }

        public ManagerAgent build() {
            return new ManagerAgent(this);
        }
    }
}
