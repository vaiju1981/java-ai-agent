package dev.vaijanath.aiagent.memory;

import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/** Keeps one {@link Memory} per {@code (tenant, sessionId)} pair in a concurrent map, created on first use. */
public final class InMemoryConversationStore implements ConversationStore {

    /** A NUL separator cannot appear in ids, so distinct (tenant, session) pairs never collide. */
    private static final String SEP = String.valueOf((char) 0);

    private final ConcurrentHashMap<String, Memory> byKey = new ConcurrentHashMap<>();
    private final Supplier<Memory> factory;

    public InMemoryConversationStore() {
        this(InMemoryMemory::new);
    }

    public InMemoryConversationStore(Supplier<Memory> factory) {
        this.factory = factory;
    }

    @Override
    public Memory get(String tenant, String sessionId) {
        return byKey.computeIfAbsent(tenant + SEP + sessionId, k -> factory.get());
    }
}
