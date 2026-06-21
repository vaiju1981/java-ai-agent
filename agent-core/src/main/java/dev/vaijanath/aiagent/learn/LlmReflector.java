package dev.vaijanath.aiagent.learn;

import dev.vaijanath.aiagent.model.Message;
import dev.vaijanath.aiagent.model.ModelPort;
import dev.vaijanath.aiagent.model.ModelRequest;
import dev.vaijanath.aiagent.model.ModelResponse;
import dev.vaijanath.aiagent.model.StructuredOutput;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * A {@link Reflector} backed by a model.
 *
 * <p>Prefer the {@link StructuredOutput} constructor: the verdict is bound from JSON to {@link Verdict}
 * (no parsing). The {@link ModelPort} constructor is a portable fallback that parses a tiny
 * {@code OK} / {@code ISSUE: <lesson>} grammar.
 */
public final class LlmReflector implements Reflector {

    /** Structured verdict bound from JSON. */
    public record Verdict(boolean satisfactory, String lesson) {
    }

    private final StructuredOutput structured;
    private final ModelPort model;

    public LlmReflector(StructuredOutput structured) {
        this.structured = Objects.requireNonNull(structured, "structured");
        this.model = null;
    }

    public LlmReflector(ModelPort model) {
        this.model = Objects.requireNonNull(model, "model");
        this.structured = null;
    }

    @Override
    public Reflection reflect(String task, String answer) {
        String prompt = "Decide whether the ANSWER correctly and completely addresses the TASK. "
                + "If it does not, give a one-sentence lesson to fix it.\n\nTASK: " + task
                + "\n\nANSWER: " + answer;

        if (structured != null) {
            Verdict v = structured.generate(ModelRequest.of(List.of(Message.user(prompt))), Verdict.class);
            if (v == null) {
                return Reflection.ok();
            }
            return v.satisfactory() ? Reflection.ok() : Reflection.issue(v.lesson() == null ? "" : v.lesson());
        }

        String instruction = prompt + "\n\nReply with exactly 'OK', or 'ISSUE: <one short lesson>'.";
        ModelResponse resp = model.chat(ModelRequest.of(List.of(Message.user(instruction))));
        String verdict = resp.text() == null ? "" : resp.text().strip();
        if (verdict.toLowerCase(Locale.ROOT).startsWith("issue")) {
            int colon = verdict.indexOf(':');
            return Reflection.issue(colon >= 0 ? verdict.substring(colon + 1).strip() : verdict);
        }
        return Reflection.ok();
    }
}
