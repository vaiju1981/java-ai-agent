package dev.vaijanath.aiagent.deep;

import dev.vaijanath.aiagent.agent.Agent;
import dev.vaijanath.aiagent.agent.AgentRequest;
import dev.vaijanath.aiagent.agent.AgentResponse;
import dev.vaijanath.aiagent.agent.RequestContext;
import dev.vaijanath.aiagent.model.Message;
import dev.vaijanath.aiagent.model.ModelPort;
import dev.vaijanath.aiagent.model.ModelRequest;
import dev.vaijanath.aiagent.model.ModelResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A deep agent: <b>plan → run sub-agents (one per subtask) → synthesize</b>. Implements
 * {@link Agent}, so a deep agent can itself be used as a sub-agent of another.
 *
 * <p>The plan is a DAG. Each {@link PlanStep} may declare {@link PlanStep#dependsOn() dependencies};
 * steps run in <b>waves</b> — a step becomes eligible once all its dependencies are {@code DONE}, and
 * eligible steps run concurrently on a virtual-thread-per-task executor (Loom, GA in Java 21). A
 * dependent step receives its upstream steps' results injected into its instruction, so step B can
 * build on step A's output. A flat plan (no dependencies) is just the degenerate case: one wave with
 * everything in it, exactly the previous fan-out behavior.
 *
 * <p>Each worker is a fresh {@link Agent} from the supplied factory, so there is no shared mutable
 * state. A {@code StructuredTaskScope} variant can replace the executor once that API is stable
 * without requiring preview.
 */
public final class DeepAgent implements Agent {

    private static final Logger log = LoggerFactory.getLogger(DeepAgent.class);

    private final Planner planner;
    private final Supplier<Agent> workerFactory;
    private final ModelPort synthesizer;
    private final Workspace workspace;
    private final boolean parallel;
    private final Duration stepTimeout;

    private DeepAgent(Builder b) {
        this.planner = Objects.requireNonNull(b.planner, "planner");
        this.workerFactory = Objects.requireNonNull(b.workerFactory, "workerFactory");
        this.synthesizer = Objects.requireNonNull(b.synthesizer, "synthesizer");
        this.workspace = b.workspace != null ? b.workspace : new InMemoryWorkspace();
        this.parallel = b.parallel;
        this.stepTimeout = b.stepTimeout;
    }

    public Workspace workspace() {
        return workspace;
    }

    @Override
    public AgentResponse run(AgentRequest request) {
        String task = request.input();
        RequestContext ctx = request.context();
        Plan plan = planner.plan(task);
        if (plan.isEmpty()) {
            log.warn("planner produced no steps; synthesizing directly");
            return AgentResponse.completed(synthesize(task, List.of()));
        }
        Dag.validate(plan.steps());
        workspace.write("plan.md", plan.render());
        log.info("deep agent: {} subtask(s), parallel={}", plan.steps().size(), parallel);

        runDag(plan, ctx);

        workspace.write("plan.md", plan.render()); // statuses are now resolved
        return AgentResponse.completed(synthesize(task, plan.steps()));
    }

    /** Runs the plan wave by wave: each pass executes the steps whose dependencies are all done. */
    private void runDag(Plan plan, RequestContext ctx) {
        List<PlanStep> steps = plan.steps();
        Map<Integer, PlanStep> byIndex = new LinkedHashMap<>();
        for (PlanStep step : steps) {
            byIndex.put(step.index(), step);
        }
        List<PlanStep> wave;
        while (!(wave = Dag.ready(steps)).isEmpty()) {
            runWave(wave, byIndex, ctx);
        }
        // No more eligible steps: any still PENDING are blocked by a failed dependency.
        for (PlanStep step : steps) {
            if (step.status() == PlanStep.Status.PENDING) {
                skip(step);
            }
        }
    }

    private void runWave(List<PlanStep> wave, Map<Integer, PlanStep> byIndex, RequestContext ctx) {
        if (parallel) {
            try (ExecutorService pool = Executors.newVirtualThreadPerTaskExecutor()) {
                List<Future<AgentResponse>> futures = new ArrayList<>();
                for (PlanStep step : wave) {
                    step.status(PlanStep.Status.RUNNING);
                    String input = instruction(step, byIndex);
                    futures.add(pool.submit(() ->
                            workerFactory.get().run(new AgentRequest(input, ctx.childSession()))));
                }
                for (int i = 0; i < wave.size(); i++) {
                    PlanStep step = wave.get(i);
                    Future<AgentResponse> future = futures.get(i);
                    try {
                        complete(step, future.get(stepTimeout.toMillis(), TimeUnit.MILLISECONDS).output());
                    } catch (Exception e) {
                        future.cancel(true);
                        fail(step, e);
                    }
                }
            }
        } else {
            for (PlanStep step : wave) {
                step.status(PlanStep.Status.RUNNING);
                try {
                    complete(step, workerFactory.get()
                            .run(new AgentRequest(instruction(step, byIndex), ctx.childSession())).output());
                } catch (RuntimeException e) {
                    fail(step, e);
                }
            }
        }
    }

    /** A step's input: its description, plus the results of any steps it depends on. */
    private static String instruction(PlanStep step, Map<Integer, PlanStep> byIndex) {
        if (step.dependsOn().isEmpty()) {
            return step.description();
        }
        StringBuilder sb = new StringBuilder(step.description());
        sb.append("\n\nContext from prior steps:");
        for (int dep : step.dependsOn()) {
            PlanStep upstream = byIndex.get(dep);
            if (upstream != null) {
                sb.append("\n- ").append(upstream.description()).append(": ").append(upstream.result());
            }
        }
        return sb.toString();
    }

    private void complete(PlanStep step, String output) {
        step.result(output);
        step.status(PlanStep.Status.DONE);
        workspace.write("step-" + step.index() + ".txt", output);
    }

    private void fail(PlanStep step, Exception e) {
        log.warn("subtask {} failed: {}", step.index(), e.toString());
        step.result("(failed: " + e.getMessage() + ")");
        step.status(PlanStep.Status.FAILED);
        workspace.write("step-" + step.index() + ".txt", step.result());
    }

    private void skip(PlanStep step) {
        log.warn("subtask {} skipped: a dependency failed", step.index());
        step.result("(skipped: a dependency failed)");
        step.status(PlanStep.Status.FAILED);
        workspace.write("step-" + step.index() + ".txt", step.result());
    }

    private String synthesize(String task, List<PlanStep> steps) {
        StringBuilder sb = new StringBuilder();
        sb.append("Synthesize one cohesive answer to the task using the sub-results below.\n");
        sb.append("Task: ").append(task).append("\n\n");
        for (PlanStep s : steps) {
            sb.append("Subtask ").append(s.index()).append(": ").append(s.description()).append('\n');
            sb.append("Result: ").append(s.result()).append("\n\n");
        }
        sb.append("Final answer:");
        ModelResponse resp = synthesizer.chat(ModelRequest.of(List.of(Message.user(sb.toString()))));
        return resp.text() == null ? "" : resp.text();
    }

    public static Builder builder() {
        return new Builder();
    }

    /** Fluent builder. {@code planner}, {@code worker}, and {@code synthesizer} are required. */
    public static final class Builder {
        private Planner planner;
        private Supplier<Agent> workerFactory;
        private ModelPort synthesizer;
        private Workspace workspace;
        private boolean parallel = true;
        private Duration stepTimeout = Duration.ofMinutes(2);

        public Builder planner(Planner planner) {
            this.planner = planner;
            return this;
        }

        /** A factory producing a fresh worker agent per subtask (no shared mutable state). */
        public Builder worker(Supplier<Agent> workerFactory) {
            this.workerFactory = workerFactory;
            return this;
        }

        public Builder synthesizer(ModelPort synthesizer) {
            this.synthesizer = synthesizer;
            return this;
        }

        public Builder workspace(Workspace workspace) {
            this.workspace = workspace;
            return this;
        }

        public Builder parallel(boolean parallel) {
            this.parallel = parallel;
            return this;
        }

        public Builder stepTimeout(Duration stepTimeout) {
            this.stepTimeout = stepTimeout;
            return this;
        }

        public DeepAgent build() {
            return new DeepAgent(this);
        }
    }
}
