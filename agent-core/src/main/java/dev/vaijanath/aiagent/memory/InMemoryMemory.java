package dev.vaijanath.aiagent.memory;

import dev.vaijanath.aiagent.model.Message;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** A simple in-process short-term memory. */
public final class InMemoryMemory implements Memory {

    private final List<Message> messages = Collections.synchronizedList(new ArrayList<>());

    @Override
    public void add(Message message) {
        messages.add(message);
    }

    @Override
    public List<Message> history() {
        synchronized (messages) {
            return List.copyOf(messages);
        }
    }
}
