package dev.vaijanath.aiagent.supervise;

import dev.vaijanath.aiagent.model.Message;
import dev.vaijanath.aiagent.model.ModelPort;
import dev.vaijanath.aiagent.model.ModelRequest;
import dev.vaijanath.aiagent.model.ModelResponse;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A {@link SpeakerSelector} that asks a model who should speak next, given the discussion so far and
 * the roster. The model replies with a participant's name, or {@code DONE} to end the chat — so unlike
 * {@link RoundRobinSelector}, an LLM moderator can conclude the conversation once the task is resolved.
 */
public final class LlmSpeakerSelector implements SpeakerSelector {

    private final ModelPort model;

    public LlmSpeakerSelector(ModelPort model) {
        this.model = Objects.requireNonNull(model, "model");
    }

    @Override
    public String next(String task, List<Turn> transcript, Map<String, String> roster) {
        StringBuilder convo = new StringBuilder();
        for (Turn turn : transcript) {
            convo.append(turn.speaker()).append(": ").append(turn.message()).append('\n');
        }
        StringBuilder participants = new StringBuilder();
        roster.forEach((name, desc) -> participants.append("- ").append(name).append(": ").append(desc).append('\n'));

        String prompt = "Task: " + task + "\n\nDiscussion so far:\n" + convo
                + "\nWho should speak next to move the task forward? Reply with ONLY one participant's "
                + "name, or DONE if the discussion has resolved the task.\n\nParticipants:\n" + participants;
        ModelResponse response = model.chat(ModelRequest.of(List.of(
                Message.system("You moderate a group discussion. Answer with one participant name or DONE."),
                Message.user(prompt))));
        String answer = response.text() == null ? "" : response.text().trim();

        for (String name : roster.keySet()) {
            if (name.equalsIgnoreCase(answer)) {
                return name;
            }
        }
        for (String name : roster.keySet()) {
            if (!name.isEmpty() && answer.toLowerCase().contains(name.toLowerCase())) {
                return name;
            }
        }
        return null; // DONE / no match -> end the chat
    }
}
