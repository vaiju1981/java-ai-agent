package dev.vaijanath.aiagent.supervise;

import dev.vaijanath.aiagent.agent.Agent;
import dev.vaijanath.aiagent.agent.AgentRequest;
import dev.vaijanath.aiagent.agent.AgentResponse;
import dev.vaijanath.aiagent.agent.RequestContext;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Runs a <b>group chat</b>: several agents share one transcript and a {@link SpeakerSelector} picks
 * who speaks next each round — the AutoGen pattern. Every speaker sees the full shared conversation
 * and adds to it, so the agents build on each other's contributions (unlike {@link ManagerAgent},
 * where workers are delegated isolated subtasks, or {@link HandoffAgent}, where control transfers
 * between peers one at a time).
 *
 * <p>Itself an {@link Agent}, so it composes and can be wrapped once with {@code Trust.govern(...)}.
 * Each speaker runs in a fresh {@link RequestContext#childSession() child session} and receives the
 * rendered transcript as its input, so the shared conversation is explicit (no shared store needed).
 * The chat ends when the selector returns no next speaker (a natural conclusion) or the round budget
 * is spent; the final speaker's message is the answer. A {@code blocked} turn is returned as-is.
 */
public final class GroupChatAgent implements Agent {

    private static final Logger log = LoggerFactory.getLogger(GroupChatAgent.class);

    private final Map<String, Agent> agents;
    private final Map<String, String> descriptions;
    private final SpeakerSelector selector;
    private final int maxRounds;

    private GroupChatAgent(Builder b) {
        if (b.agents.isEmpty()) {
            throw new IllegalArgumentException("at least one agent is required");
        }
        // Preserve registration order so a round-robin selector cycles predictably.
        this.agents = Collections.unmodifiableMap(new LinkedHashMap<>(b.agents));
        this.descriptions = Collections.unmodifiableMap(new LinkedHashMap<>(b.descriptions));
        this.selector = Objects.requireNonNull(b.selector, "selector");
        this.maxRounds = Math.max(1, b.maxRounds);
    }

    @Override
    public AgentResponse run(AgentRequest request) {
        String task = request.input();
        RequestContext ctx = request.context();
        List<SpeakerSelector.Turn> transcript = new ArrayList<>();
        transcript.add(new SpeakerSelector.Turn("user", task));

        AgentResponse last = null;
        for (int round = 1; round <= maxRounds; round++) {
            String speaker = selector.next(task, List.copyOf(transcript), descriptions);
            if (speaker == null || speaker.isBlank() || !agents.containsKey(speaker)) {
                log.info("group chat concluded after {} turn(s)", transcript.size() - 1);
                return AgentResponse.completed(last == null ? "" : last.output());
            }
            last = agents.get(speaker).run(new AgentRequest(prompt(transcript, speaker), ctx.childSession()));
            if (last.blocked()) {
                return last; // a guardrail stopped this turn; surface it
            }
            transcript.add(new SpeakerSelector.Turn(speaker, last.output()));
            log.info("group chat round {}/{}: {} spoke", round, maxRounds, speaker);
        }

        log.info("group chat hit the round budget ({})", maxRounds);
        return AgentResponse.stopped(last == null ? "" : last.output(), "max_rounds");
    }

    /** The speaker's input: the shared transcript plus a cue to contribute next. */
    private static String prompt(List<SpeakerSelector.Turn> transcript, String speaker) {
        StringBuilder sb = new StringBuilder("You are '").append(speaker)
                .append("' in a group discussion. The conversation so far:\n\n");
        for (SpeakerSelector.Turn turn : transcript) {
            sb.append(turn.speaker()).append(": ").append(turn.message()).append('\n');
        }
        sb.append("\nContribute the next message as '").append(speaker).append("'.");
        return sb.toString();
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Fluent builder. At least one {@code agent} and a {@code selector} are required. */
    public static final class Builder {

        private final Map<String, Agent> agents = new LinkedHashMap<>();
        private final Map<String, String> descriptions = new LinkedHashMap<>();
        private SpeakerSelector selector;
        private int maxRounds = 6;

        /** Registers a participant; its {@code description} guides the {@link SpeakerSelector}. */
        public Builder agent(String name, String description, Agent agent) {
            Objects.requireNonNull(name, "name");
            Objects.requireNonNull(agent, "agent");
            agents.put(name, agent);
            descriptions.put(name, description == null ? "" : description);
            return this;
        }

        public Builder selector(SpeakerSelector selector) {
            this.selector = selector;
            return this;
        }

        /** Maximum speaking turns before the chat stops and returns the last message (default 6). */
        public Builder maxRounds(int maxRounds) {
            this.maxRounds = maxRounds;
            return this;
        }

        public GroupChatAgent build() {
            return new GroupChatAgent(this);
        }
    }
}
