package dev.vaijanath.aiagent.memory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vaijanath.aiagent.model.Message;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class InMemoryConversationStoreTest {

    @Test
    void sameKeyReturnsTheSameMemory() {
        InMemoryConversationStore store = new InMemoryConversationStore();
        store.withMemory("t", "s", m -> {
            m.add(Message.user("x"));
            return null;
        });
        int size = store.withMemory("t", "s", m -> m.history().size());
        assertEquals(1, size, "same (tenant, session) is one memory");
    }

    @Test
    void evictsLeastRecentlyUsedBeyondTheCap() {
        InMemoryConversationStore store = new InMemoryConversationStore(InMemoryMemory::new, 2);
        store.withMemory("t", "a", m -> {
            m.add(Message.user("hi"));
            return null;
        });
        store.withMemory("t", "b", m -> null);
        store.withMemory("t", "c", m -> null); // exceeds cap of 2 -> evict LRU (a)

        boolean empty = store.withMemory("t", "a", m -> m.history().isEmpty());
        assertTrue(empty, "the evicted session must start fresh, proving the store is bounded");
    }

    @Test
    void concurrentWritesToOneSessionDoNotSplit() {
        InMemoryConversationStore store = new InMemoryConversationStore();
        IntStream.range(0, 200).parallel().forEach(i ->
                store.withMemory("t", "s", m -> {
                    m.add(Message.user("m" + i));
                    return null;
                }));

        int size = store.withMemory("t", "s", m -> m.history().size());
        assertEquals(200, size, "concurrent same-session writes must serialize on one memory, not split");
    }
}
