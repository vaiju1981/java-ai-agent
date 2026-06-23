package dev.vaijanath.aiagent.memory;

import dev.vaijanath.aiagent.model.Message;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Keeps one {@link Memory} per {@code (tenant, sessionId)} pair, bounded by an LRU cap so a
 * long-running service cannot leak memory as ephemeral sessions accumulate. An entry is <b>pinned</b>
 * while {@link #withMemory} runs, so an in-flight session is never evicted out from under a caller;
 * once the cap is exceeded, the least-recently-used <em>unpinned</em> entry is evicted (a later
 * request for it simply starts fresh). The key is a record, so ids may contain any characters.
 *
 * <p>Also implements {@link ConversationHistory}, so live (non-evicted) sessions can be listed and
 * replayed. Note this reflects only what is still in memory — evicted sessions are gone; use a durable
 * store for history that must survive eviction or restarts.
 */
public final class InMemoryConversationStore implements ConversationStore, ConversationHistory {

    private static final int DEFAULT_MAX_SESSIONS = 10_000;

    private record Key(String tenant, String sessionId) {}

    private static final class Entry {
        final Memory memory;
        int pins;
        long lastAccess;
        Instant lastActivity = Instant.EPOCH;

        Entry(Memory memory) {
            this.memory = memory;
        }
    }

    private final Map<Key, Entry> entries = new ConcurrentHashMap<>();
    private final AtomicLong clock = new AtomicLong();
    private final Supplier<Memory> factory;
    private final int maxSessions;

    public InMemoryConversationStore() {
        this(InMemoryMemory::new, DEFAULT_MAX_SESSIONS);
    }

    public InMemoryConversationStore(Supplier<Memory> factory) {
        this(factory, DEFAULT_MAX_SESSIONS);
    }

    public InMemoryConversationStore(Supplier<Memory> factory, int maxSessions) {
        this.factory = factory;
        this.maxSessions = Math.max(1, maxSessions);
    }

    @Override
    public <R> R withMemory(String tenant, String sessionId, Function<Memory, R> action) {
        Key key = new Key(tenant, sessionId);
        // Atomically get-or-create and pin, so concurrent calls for the same key share one Entry.
        Entry entry = entries.compute(key, (k, cur) -> {
            Entry e = (cur != null) ? cur : new Entry(factory.get());
            e.pins++;
            e.lastAccess = clock.incrementAndGet();
            e.lastActivity = Instant.now();
            return e;
        });
        try {
            synchronized (entry.memory) {
                return action.apply(entry.memory);
            }
        } finally {
            entries.compute(key, (k, cur) -> {
                if (cur != null) {
                    cur.pins--;
                }
                return cur;
            });
            evictIfNeeded();
        }
    }

    @Override
    public List<SessionSummary> listSessions(String tenant) {
        List<SessionSummary> sessions = new ArrayList<>();
        entries.forEach((key, entry) -> {
            if (key.tenant().equals(tenant)) {
                long count;
                synchronized (entry.memory) {
                    count = entry.memory.history().size();
                }
                sessions.add(new SessionSummary(key.sessionId(), count, entry.lastActivity));
            }
        });
        sessions.sort(Comparator.comparing(SessionSummary::lastActivity).reversed());
        return sessions;
    }

    @Override
    public List<Message> messages(String tenant, String sessionId) {
        Entry entry = entries.get(new Key(tenant, sessionId));
        if (entry == null) {
            return List.of();
        }
        synchronized (entry.memory) {
            return List.copyOf(entry.memory.history());
        }
    }

    /** Evicts the least-recently-used unpinned entry if over the cap; pinned (in-flight) entries stay. */
    private void evictIfNeeded() {
        if (entries.size() <= maxSessions) {
            return;
        }
        entries.entrySet().stream()
                .filter(e -> e.getValue().pins == 0)
                .min(Comparator.comparingLong(e -> e.getValue().lastAccess))
                .ifPresent(victim ->
                        // Re-check pins atomically: a concurrent withMemory may have re-pinned it.
                        entries.computeIfPresent(victim.getKey(), (k, cur) -> cur.pins == 0 ? null : cur));
    }
}
