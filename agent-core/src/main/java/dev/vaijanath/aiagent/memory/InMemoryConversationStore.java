package dev.vaijanath.aiagent.memory;

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/** Keeps one {@link Memory} per session id in a concurrent map, created on first use. */
public final class InMemoryConversationStore implements ConversationStore {

    private final ConcurrentHashMap<String, Memory> bySession = new ConcurrentHashMap<>();
    private final Supplier<Memory> factory;

    public InMemoryConversationStore() {
        this(InMemoryMemory::new);
    }

    public InMemoryConversationStore(Supplier<Memory> factory) {
        this.factory = factory;
    }

    @Override
    public Memory get(String sessionId) {
        return bySession.computeIfAbsent(sessionId, k -> factory.get());
    }
}
