package dev.vaijanath.aiagent.memory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vaijanath.aiagent.model.Message;
import org.junit.jupiter.api.Test;

class InMemoryConversationStoreTest {

    @Test
    void sameKeyReturnsTheSameMemory() {
        InMemoryConversationStore store = new InMemoryConversationStore();
        store.get("t", "s").add(Message.user("x"));
        assertEquals(1, store.get("t", "s").history().size(), "same (tenant, session) is one memory");
    }

    @Test
    void evictsLeastRecentlyUsedBeyondTheCap() {
        InMemoryConversationStore store = new InMemoryConversationStore(InMemoryMemory::new, 2);
        store.get("t", "a").add(Message.user("hi")); // touch a
        store.get("t", "b"); // [a, b]
        store.get("t", "c"); // exceeds cap of 2 -> evict LRU (a); now [b, c]

        assertTrue(store.get("t", "a").history().isEmpty(),
                "the evicted session must start fresh, proving the store is bounded");
    }
}
