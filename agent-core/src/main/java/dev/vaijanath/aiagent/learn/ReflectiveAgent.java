package dev.vaijanath.aiagent.learn;

import dev.vaijanath.aiagent.agent.Agent;
import dev.vaijanath.aiagent.agent.AgentRequest;
import dev.vaijanath.aiagent.agent.AgentResponse;
import dev.vaijanath.aiagent.memory.Episode;
import dev.vaijanath.aiagent.memory.EpisodicStore;
import dev.vaijanath.aiagent.memory.InMemoryEpisodicStore;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An agent that learns from its mistakes. Before answering, it recalls lessons from similar past
 * episodes and injects them. After answering, it self-critiques; on a poor answer it records the
 * lesson and retries (up to a budget) with that lesson applied.
 *
 * <p>Learning is explicit and inspectable — every lesson is a recorded {@link Episode}, so behavior
 * change is auditable, never silent drift. Lessons recalled at the start make learning persist
 * <em>across</em> runs, not just within one.
 */
public final class ReflectiveAgent implements Agent {

    private static final Logger log = LoggerFactory.getLogger(ReflectiveAgent.class);

    private final Supplier<Agent> workerFactory;
    private final Reflector reflector;
    private final EpisodicStore memory;
    private final int maxAttempts;
    private final int recallLimit;

    private ReflectiveAgent(Builder b) {
        this.workerFactory = Objects.requireNonNull(b.workerFactory, "workerFactory");
        this.reflector = Objects.requireNonNull(b.reflector, "reflector");
        this.memory = b.memory != null ? b.memory : new InMemoryEpisodicStore();
        this.maxAttempts = Math.max(1, b.maxAttempts);
        this.recallLimit = b.recallLimit;
    }

    public EpisodicStore memory() {
        return memory;
    }

    @Override
    public AgentResponse run(AgentRequest request) {
        String task = request.input();
        StringBuilder lessons = new StringBuilder(formatPastLessons(memory.recall(task, recallLimit)));

        AgentResponse last = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            String input = lessons.isEmpty()
                    ? task
                    : task + "\n\nLessons to apply (from earlier attempts):\n" + lessons;
            // Carry the caller's identity/tenant/trace/deadline into each retry (fresh session).
            last = workerFactory.get().run(new AgentRequest(input, request.context().childSession()));

            Reflection reflection = reflector.reflect(task, last.output());
            if (reflection.satisfactory()) {
                if (attempt > 1) {
                    log.info("succeeded on attempt {} after applying a lesson", attempt);
                }
                return last;
            }

            // Learn from the mistake: record it and carry the lesson into the next attempt.
            log.info("attempt {} unsatisfactory; lesson: {}", attempt, reflection.lesson());
            memory.record(new Episode(task, last.output(), false, reflection.lesson()));
            lessons.append("- ").append(reflection.lesson()).append('\n');
        }
        return last;
    }

    private static String formatPastLessons(List<Episode> episodes) {
        if (episodes.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (Episode e : episodes) {
            if (!e.lesson().isBlank()) {
                sb.append("- ").append(e.lesson()).append('\n');
            }
        }
        return sb.toString();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private Supplier<Agent> workerFactory;
        private Reflector reflector;
        private EpisodicStore memory;
        private int maxAttempts = 2;
        private int recallLimit = 3;

        /** A factory producing a fresh worker per attempt (so retries don't share dirty state). */
        public Builder worker(Supplier<Agent> workerFactory) {
            this.workerFactory = workerFactory;
            return this;
        }

        public Builder reflector(Reflector reflector) {
            this.reflector = reflector;
            return this;
        }

        public Builder memory(EpisodicStore memory) {
            this.memory = memory;
            return this;
        }

        public Builder maxAttempts(int maxAttempts) {
            this.maxAttempts = maxAttempts;
            return this;
        }

        public Builder recallLimit(int recallLimit) {
            this.recallLimit = recallLimit;
            return this;
        }

        public ReflectiveAgent build() {
            return new ReflectiveAgent(this);
        }
    }
}
