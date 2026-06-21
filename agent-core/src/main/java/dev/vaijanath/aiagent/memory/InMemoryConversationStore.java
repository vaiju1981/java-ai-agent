package dev.vaijanath.aiagent.memory;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Keeps one {@link Memory} per {@code (tenant, sessionId)} pair, bounded by an LRU cap so a
 * long-running service cannot leak memory as ephemeral sessions accumulate: once the cap is reached,
 * the least-recently-used session is evicted (a later request for it simply starts fresh). For
 * durable multi-turn conversations, use stable session ids and a store sized for the working set, or
 * supply a persistent {@link ConversationStore}.
 */
public final class InMemoryConversationStore implements ConversationStore {

    /** A NUL separator cannot appear in ids, so distinct (tenant, session) pairs never collide. */
    private static final String SEP = String.valueOf((char) 0);

    private static final int DEFAULT_MAX_SESSIONS = 10_000;

    private final Map<String, Memory> byKey;
    private final Supplier<Memory> factory;

    public InMemoryConversationStore() {
        this(InMemoryMemory::new, DEFAULT_MAX_SESSIONS);
    }

    public InMemoryConversationStore(Supplier<Memory> factory) {
        this(factory, DEFAULT_MAX_SESSIONS);
    }

    public InMemoryConversationStore(Supplier<Memory> factory, int maxSessions) {
        this.factory = factory;
        int cap = Math.max(1, maxSessions);
        this.byKey = new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Memory> eldest) {
                return size() > cap;
            }
        };
    }

    @Override
    public synchronized Memory get(String tenant, String sessionId) {
        return byKey.computeIfAbsent(tenant + SEP + sessionId, k -> factory.get());
    }
}
