package dev.vaijanath.aiagent.memory;

import dev.vaijanath.aiagent.model.Message;
import dev.vaijanath.aiagent.model.Role;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.List;

/**
 * Short-term memory that keeps all system messages plus only the most recent {@code maxRecent}
 * non-system messages — bounding context growth on long conversations.
 *
 * <p>Note: windowing can drop an older assistant tool-call together with, or apart from, its tool
 * result. For heavy tool conversations, size {@code maxRecent} generously or use summarization.
 */
public final class WindowedMemory implements Memory {

    private final int maxRecent;
    private final List<Message> systemMessages = Collections.synchronizedList(new ArrayList<>());
    private final Deque<Message> recent = new ArrayDeque<>();

    public WindowedMemory(int maxRecent) {
        this.maxRecent = Math.max(1, maxRecent);
    }

    @Override
    public void add(Message message) {
        if (message.role() == Role.SYSTEM) {
            systemMessages.add(message);
            return;
        }
        synchronized (recent) {
            recent.addLast(message);
            while (recent.size() > maxRecent) {
                recent.removeFirst();
            }
        }
    }

    @Override
    public List<Message> history() {
        List<Message> result = new ArrayList<>(systemMessages);
        synchronized (recent) {
            result.addAll(recent);
        }
        return List.copyOf(result);
    }
}
