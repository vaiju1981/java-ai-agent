package dev.vaijanath.aiagent.supervise;

import dev.vaijanath.aiagent.model.Message;
import dev.vaijanath.aiagent.model.ModelPort;
import dev.vaijanath.aiagent.model.ModelRequest;
import dev.vaijanath.aiagent.model.ModelResponse;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A {@link Router} that asks a model to pick the best specialist. It presents the roster
 * (name: description) and the request, then matches the model's reply back to a known name — exact
 * first, then by containment — leaving {@link SupervisorAgent} to fall back if nothing matches.
 */
public final class LlmRouter implements Router {

    private final ModelPort model;

    public LlmRouter(ModelPort model) {
        this.model = Objects.requireNonNull(model, "model");
    }

    @Override
    public String route(String input, Map<String, String> agents) {
        StringBuilder roster = new StringBuilder();
        agents.forEach((name, desc) -> roster.append("- ").append(name).append(": ").append(desc).append('\n'));
        String prompt = "Route the user request to the single best specialist.\n\nSpecialists:\n"
                + roster + "\nUser request: " + input + "\n\nReply with ONLY the specialist's name.";

        ModelResponse response = model.chat(ModelRequest.of(List.of(
                Message.system("You are a request router. Answer with one specialist name and nothing else."),
                Message.user(prompt))));
        String answer = response.text() == null ? "" : response.text().trim();

        for (String name : agents.keySet()) {
            if (name.equalsIgnoreCase(answer)) {
                return name;
            }
        }
        for (String name : agents.keySet()) {
            if (!name.isEmpty() && answer.toLowerCase().contains(name.toLowerCase())) {
                return name;
            }
        }
        return answer; // unknown -> the supervisor applies its fallback
    }
}
