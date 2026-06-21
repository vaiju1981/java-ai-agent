package dev.vaijanath.aiagent.skill;

import dev.vaijanath.aiagent.model.Message;
import dev.vaijanath.aiagent.model.ModelPort;
import dev.vaijanath.aiagent.model.ModelRequest;
import dev.vaijanath.aiagent.model.ModelResponse;
import java.util.List;
import java.util.Objects;

/**
 * Asks a model to distill a reusable skill from a solved task, in a tiny labeled grammar
 * ({@code NAME:/DESCRIPTION:/INSTRUCTIONS:}) so no JSON dependency is needed. Returns {@code null}
 * if the reply has no usable name.
 */
public final class LlmSkillSynthesizer implements SkillSynthesizer {

    private final ModelPort model;

    public LlmSkillSynthesizer(ModelPort model) {
        this.model = Objects.requireNonNull(model, "model");
    }

    @Override
    public Skill synthesize(String task, String solution) {
        String prompt = "From the task and its good solution, distill a REUSABLE skill for similar "
                + "future tasks. Reply in exactly this form and nothing else:\n"
                + "NAME: <short-kebab-name>\nDESCRIPTION: <one line>\nINSTRUCTIONS: <how to handle "
                + "such tasks>\n\nTask: " + task + "\nSolution: " + solution;
        ModelResponse resp = model.chat(ModelRequest.of(List.of(Message.user(prompt))));
        String text = resp.text() == null ? "" : resp.text();

        String name = field(text, "NAME:");
        if (name.isBlank()) {
            return null;
        }
        String description = field(text, "DESCRIPTION:");
        String instructions = after(text, "INSTRUCTIONS:");
        return Skill.of(name, description, instructions.isBlank() ? description : instructions);
    }

    /** The single-line value following a label. */
    private static String field(String text, String label) {
        for (String line : text.split("\\R")) {
            String t = line.strip();
            if (t.regionMatches(true, 0, label, 0, label.length())) {
                return t.substring(label.length()).strip();
            }
        }
        return "";
    }

    /** Everything after a label (may span multiple lines). */
    private static String after(String text, String label) {
        int i = text.indexOf(label);
        return i < 0 ? "" : text.substring(i + label.length()).strip();
    }
}
