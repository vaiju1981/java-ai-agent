package dev.vaijanath.aiagent.memory;

import dev.vaijanath.aiagent.model.Message;
import java.util.List;

/**
 * Conversation memory. Phase 0 provides short-term history; long-term and episodic stores will
 * implement this same seam (and may be backed by a substrate's vector store).
 */
public interface Memory {

    void add(Message message);

    List<Message> history();
}
