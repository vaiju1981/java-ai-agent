package dev.vaijanath.aiagent.supervise;

import java.util.List;
import java.util.Map;

/**
 * Picks who speaks next in a {@link GroupChatAgent} — the moderator of a shared conversation. Given
 * the task, the transcript so far, and the roster, it returns the next speaker's name, or
 * {@code null}/blank/an unknown name to end the discussion.
 *
 * <p>Pluggable like {@link Router}: {@link RoundRobinSelector} cycles through the agents,
 * {@link LlmSpeakerSelector} lets a model choose (and end) the conversation, or a test can script it.
 */
@FunctionalInterface
public interface SpeakerSelector {

    /**
     * @param task       the original request that opened the discussion
     * @param transcript the shared conversation so far (the first turn is the {@code "user"} task)
     * @param roster     the available agents as {@code name -> description}, in registration order
     * @return the next speaker's name, or {@code null}/blank/unknown to end the chat
     */
    String next(String task, List<Turn> transcript, Map<String, String> roster);

    /** One contribution to the shared transcript. */
    record Turn(String speaker, String message) {
    }
}
