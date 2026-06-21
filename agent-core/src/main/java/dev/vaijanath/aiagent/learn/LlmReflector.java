package dev.vaijanath.aiagent.learn;

import dev.vaijanath.aiagent.model.Message;
import dev.vaijanath.aiagent.model.ModelPort;
import dev.vaijanath.aiagent.model.ModelRequest;
import dev.vaijanath.aiagent.model.ModelResponse;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * A {@link Reflector} backed by a model. It asks for a verdict in a tiny grammar: {@code OK}, or
 * {@code ISSUE: <one-sentence lesson>}. Unparseable replies default to satisfactory, so reflection
 * never blocks a usable answer.
 */
public final class LlmReflector implements Reflector {

    private final ModelPort model;

    public LlmReflector(ModelPort model) {
        this.model = Objects.requireNonNull(model, "model");
    }

    @Override
    public Reflection reflect(String task, String answer) {
        String prompt = "You are a strict reviewer. Does the ANSWER correctly and completely "
                + "address the TASK?\nReply with exactly 'OK' if it does, or 'ISSUE: <one short "
                + "lesson to fix it>' if it does not.\n\nTASK: " + task + "\n\nANSWER: " + answer;
        ModelResponse resp = model.chat(ModelRequest.of(List.of(Message.user(prompt))));
        String verdict = resp.text() == null ? "" : resp.text().strip();

        if (verdict.toLowerCase(Locale.ROOT).startsWith("issue")) {
            int colon = verdict.indexOf(':');
            String lesson = colon >= 0 ? verdict.substring(colon + 1).strip() : verdict;
            return Reflection.issue(lesson);
        }
        return Reflection.ok();
    }
}
