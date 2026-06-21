package dev.vaijanath.aiagent.deep;

import dev.vaijanath.aiagent.model.Message;
import dev.vaijanath.aiagent.model.ModelPort;
import dev.vaijanath.aiagent.model.ModelRequest;
import dev.vaijanath.aiagent.model.ModelResponse;
import dev.vaijanath.aiagent.model.StructuredOutput;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A {@link Planner} that asks a model to decompose a task into subtasks.
 *
 * <p>Prefer the {@link StructuredOutput} constructor: the model returns a JSON object bound directly
 * to {@link Steps} — no parsing. The {@link ModelPort} constructor is a portable fallback that
 * parses a one-per-line reply for providers without structured output.
 */
public final class LlmPlanner implements Planner {

    /** Structured result the model fills in (bound from JSON, no manual parsing). */
    public record Steps(List<String> steps) {
    }

    private final StructuredOutput structured; // preferred; null when using free-text
    private final ModelPort model;             // free-text fallback; null when structured
    private final int maxSteps;

    public LlmPlanner(StructuredOutput structured) {
        this(structured, 5);
    }

    public LlmPlanner(StructuredOutput structured, int maxSteps) {
        this.structured = Objects.requireNonNull(structured, "structured");
        this.model = null;
        this.maxSteps = maxSteps;
    }

    public LlmPlanner(ModelPort model) {
        this(model, 5);
    }

    public LlmPlanner(ModelPort model, int maxSteps) {
        this.model = Objects.requireNonNull(model, "model");
        this.structured = null;
        this.maxSteps = maxSteps;
    }

    @Override
    public Plan plan(String task) {
        return structured != null ? structuredPlan(task) : freeTextPlan(task);
    }

    private Plan structuredPlan(String task) {
        String prompt = "Break the task into between 2 and " + maxSteps + " concrete, independent "
                + "subtasks, each a short imperative phrase.\nTask: " + task;
        Steps result = structured.generate(ModelRequest.of(List.of(Message.user(prompt))), Steps.class);
        List<PlanStep> steps = new ArrayList<>();
        if (result != null && result.steps() != null) {
            int index = 1;
            for (String s : result.steps()) {
                if (s != null && !s.isBlank()) {
                    steps.add(new PlanStep(index++, s.strip()));
                    if (steps.size() >= maxSteps) {
                        break;
                    }
                }
            }
        }
        return new Plan(steps);
    }

    private Plan freeTextPlan(String task) {
        String prompt = "Break the task below into between 2 and " + maxSteps
                + " concrete, independent subtasks that can each be handled on their own.\n"
                + "Reply with ONLY the subtasks, one per line, each a short imperative phrase.\n\nTask: " + task;
        ModelResponse resp = model.chat(ModelRequest.of(List.of(Message.user(prompt))));
        return new Plan(parse(resp.text()));
    }

    private List<PlanStep> parse(String text) {
        List<PlanStep> steps = new ArrayList<>();
        if (text == null) {
            return steps;
        }
        int index = 1;
        for (String raw : text.split("\\R")) {
            String line = stripBullet(raw.strip());
            if (line.isBlank()) {
                continue;
            }
            steps.add(new PlanStep(index++, line));
            if (steps.size() >= maxSteps) {
                break;
            }
        }
        return steps;
    }

    private static String stripBullet(String s) {
        return s.replaceFirst("^\\s*(?:[-*•]|\\d+[.)])\\s*", "").strip();
    }
}
