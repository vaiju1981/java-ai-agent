package dev.vaijanath.aiagent.memory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vaijanath.aiagent.model.Message;
import java.util.List;
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

    @Test
    void listsOnlyTheTenantsSessionsMostRecentFirst() {
        InMemoryConversationStore store = new InMemoryConversationStore();
        store.withMemory("t", "a", m -> {
            m.add(Message.user("hi"));
            return null;
        });
        store.withMemory("t", "b", m -> {
            m.add(Message.user("yo"));
            m.add(Message.assistant("hey"));
            return null;
        });
        store.withMemory("other", "c", m -> {
            m.add(Message.user("nope"));
            return null;
        });

        List<ConversationHistory.SessionSummary> sessions = store.listSessions("t");
        assertEquals(2, sessions.size(), "only this tenant's sessions are listed");
        assertEquals("b", sessions.get(0).sessionId(), "most-recently-active session is first");
        assertEquals(2, sessions.get(0).messageCount());
    }

    @Test
    void readsSessionMessagesAndIsolatesByTenant() {
        InMemoryConversationStore store = new InMemoryConversationStore();
        store.withMemory("t", "s", m -> {
            m.add(Message.user("hi"));
            m.add(Message.assistant("hello"));
            return null;
        });

        List<Message> messages = store.messages("t", "s");
        assertEquals(2, messages.size());
        assertEquals("hi", messages.get(0).content());
        assertTrue(store.messages("t", "missing").isEmpty(), "an unknown session reads empty");
        assertTrue(store.messages("other", "s").isEmpty(), "another tenant cannot read the session");
    }

    @Test
    void deleteRemovesASessionsHistory() {
        InMemoryConversationStore store = new InMemoryConversationStore();
        store.withMemory("t", "s", m -> {
            m.add(Message.user("hi"));
            return null;
        });

        store.delete("t", "s");

        assertTrue(store.messages("t", "s").isEmpty(), "the deleted session has no messages");
        assertTrue(store.listSessions("t").isEmpty(), "the deleted session is no longer listed");
    }
}
