package dev.vaijanath.aiagent.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class InMemoryIdempotencyStoreTest {

    @Test
    void savesAndLooksUpScopedByTenant() {
        InMemoryIdempotencyStore store = new InMemoryIdempotencyStore();
        store.save("acme", "k", AgentResponse.completed("x"));

        assertTrue(store.lookup("acme", "k").isPresent());
        assertTrue(store.lookup("other", "k").isEmpty(), "scoped by tenant");
        assertTrue(store.lookup("acme", "other").isEmpty());
    }

    @Test
    void firstWriteWins() {
        InMemoryIdempotencyStore store = new InMemoryIdempotencyStore();
        store.save("acme", "k", AgentResponse.completed("first"));
        store.save("acme", "k", AgentResponse.completed("second"));

        assertEquals("first", store.lookup("acme", "k").orElseThrow().output());
    }

    @Test
    void evictsTheOldestBeyondTheCap() {
        InMemoryIdempotencyStore store = new InMemoryIdempotencyStore(2);
        store.save("acme", "a", AgentResponse.completed("a"));
        store.save("acme", "b", AgentResponse.completed("b"));
        store.save("acme", "c", AgentResponse.completed("c")); // over the cap → evicts "a"

        assertTrue(store.lookup("acme", "a").isEmpty());
        assertTrue(store.lookup("acme", "b").isPresent());
        assertTrue(store.lookup("acme", "c").isPresent());
    }
}
