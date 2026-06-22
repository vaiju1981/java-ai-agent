package dev.vaijanath.aiagent.memory;

import dev.vaijanath.aiagent.model.Message;
import java.util.List;

/**
 * Condenses a span of conversation into a short summary — the seam {@link SummarizingMemory} uses to
 * fold older turns instead of dropping them. Implementations typically call a model; tests supply a
 * deterministic stub.
 */
@FunctionalInterface
public interface Summarizer {

    String summarize(List<Message> messages);
}
