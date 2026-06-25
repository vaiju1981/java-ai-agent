package dev.vaijanath.aiagent.supervise;

import dev.vaijanath.aiagent.model.Message;
import dev.vaijanath.aiagent.model.ModelPort;
import dev.vaijanath.aiagent.model.ModelRequest;
import dev.vaijanath.aiagent.model.ModelResponse;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A {@link Handoff} that asks a model whether the active agent should transfer to a peer. It shows the
 * task, the active agent's answer, and the other specialists, then matches the reply back to a peer
 * name — exact first, then by containment. {@code STAY} (or anything that matches no peer) keeps
 * control with the current agent.
 */
public final class LlmHandoff implements Handoff {

    private final ModelPort model;

    public LlmHandoff(ModelPort model) {
        this.model = Objects.requireNonNull(model, "model");
    }

    @Override
    public String next(String task, String current, String lastOutput, Map<String, String> roster) {
        StringBuilder peers = new StringBuilder();
        roster.forEach((name, desc) -> {
            if (!name.equals(current)) {
                peers.append("- ").append(name).append(": ").append(desc).append('\n');
            }
        });
        if (peers.isEmpty()) {
            return current; // no peers to hand off to
        }
        String prompt = "User request: " + task + "\n\nThe '" + current + "' agent answered:\n"
                + lastOutput + "\n\nIf another specialist should take over to better handle this, reply "
                + "with ONLY that specialist's name. Otherwise reply STAY.\n\nSpecialists:\n" + peers;
        ModelResponse response = model.chat(ModelRequest.of(List.of(
                Message.system("You decide whether to hand off to another specialist. "
                        + "Answer with one specialist name or STAY, nothing else."),
                Message.user(prompt))));
        String answer = response.text() == null ? "" : response.text().trim();

        for (String name : roster.keySet()) {
            if (!name.equals(current) && name.equalsIgnoreCase(answer)) {
                return name;
            }
        }
        for (String name : roster.keySet()) {
            if (!name.equals(current) && !name.isEmpty() && answer.toLowerCase().contains(name.toLowerCase())) {
                return name;
            }
        }
        return current; // STAY / no match -> keep control
    }
}
