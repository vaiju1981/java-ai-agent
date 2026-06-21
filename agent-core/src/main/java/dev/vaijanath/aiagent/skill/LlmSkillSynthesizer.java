package dev.vaijanath.aiagent.skill;

import dev.vaijanath.aiagent.model.Message;
import dev.vaijanath.aiagent.model.ModelPort;
import dev.vaijanath.aiagent.model.ModelRequest;
import dev.vaijanath.aiagent.model.ModelResponse;
import dev.vaijanath.aiagent.model.StructuredOutput;
import java.util.List;
import java.util.Objects;

/**
 * Distills a reusable {@link Skill} from a solved task.
 *
 * <p>Prefer the {@link StructuredOutput} constructor: the result is bound from JSON to {@link SkillDto}
 * (no parsing). The {@link ModelPort} constructor is a portable fallback parsing a labeled grammar.
 */
public final class LlmSkillSynthesizer implements SkillSynthesizer {

    /** Structured skill bound from JSON. */
    public record SkillDto(String name, String description, String instructions) {
    }

    private final StructuredOutput structured;
    private final ModelPort model;

    public LlmSkillSynthesizer(StructuredOutput structured) {
        this.structured = Objects.requireNonNull(structured, "structured");
        this.model = null;
    }

    public LlmSkillSynthesizer(ModelPort model) {
        this.model = Objects.requireNonNull(model, "model");
        this.structured = null;
    }

    @Override
    public Skill synthesize(String task, String solution) {
        String prompt = "From the task and its good solution, distill a REUSABLE skill for similar "
                + "future tasks: a short kebab-case name, a one-line description, and instructions "
                + "on how to handle such tasks.\n\nTask: " + task + "\nSolution: " + solution;

        if (structured != null) {
            SkillDto dto = structured.generate(ModelRequest.of(List.of(Message.user(prompt))), SkillDto.class);
            if (dto == null || dto.name() == null || dto.name().isBlank()) {
                return null;
            }
            String instructions = (dto.instructions() == null || dto.instructions().isBlank())
                    ? nullToEmpty(dto.description())
                    : dto.instructions();
            return Skill.of(dto.name().strip(), nullToEmpty(dto.description()), instructions);
        }

        ModelResponse resp = model.chat(ModelRequest.of(List.of(Message.user(prompt
                + "\n\nReply exactly as:\nNAME: <name>\nDESCRIPTION: <one line>\nINSTRUCTIONS: <text>"))));
        String text = resp.text() == null ? "" : resp.text();
        String name = field(text, "NAME:");
        if (name.isBlank()) {
            return null;
        }
        String description = field(text, "DESCRIPTION:");
        String instructions = after(text, "INSTRUCTIONS:");
        return Skill.of(name, description, instructions.isBlank() ? description : instructions);
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private static String field(String text, String label) {
        for (String line : text.split("\\R")) {
            String t = line.strip();
            if (t.regionMatches(true, 0, label, 0, label.length())) {
                return t.substring(label.length()).strip();
            }
        }
        return "";
    }

    private static String after(String text, String label) {
        int i = text.indexOf(label);
        return i < 0 ? "" : text.substring(i + label.length()).strip();
    }
}
