package dev.vaijanath.aiagent.deep;

import dev.vaijanath.aiagent.model.Message;
import dev.vaijanath.aiagent.model.ModelPort;
import dev.vaijanath.aiagent.model.ModelRequest;
import dev.vaijanath.aiagent.model.ModelResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A {@link Planner} that asks a model to decompose a task into subtasks, one per line. Parsing is
 * line-based (numbers/bullets stripped) so it needs no JSON dependency and keeps agent-core clean.
 */
public final class LlmPlanner implements Planner {

    private final ModelPort model;
    private final int maxSteps;

    public LlmPlanner(ModelPort model) {
        this(model, 5);
    }

    public LlmPlanner(ModelPort model, int maxSteps) {
        this.model = Objects.requireNonNull(model, "model");
        this.maxSteps = maxSteps;
    }

    @Override
    public Plan plan(String task) {
        String prompt = "Break the task below into between 2 and " + maxSteps
                + " concrete, independent subtasks that can each be handled on their own.\n"
                + "Reply with ONLY the subtasks, one per line, each a short imperative phrase. "
                + "No preamble, no numbering commentary.\n\nTask: " + task;
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

    /** Removes a leading "1.", "1)", "-", "*", or "•" marker. */
    private static String stripBullet(String s) {
        return s.replaceFirst("^\\s*(?:[-*•]|\\d+[.)])\\s*", "").strip();
    }
}
