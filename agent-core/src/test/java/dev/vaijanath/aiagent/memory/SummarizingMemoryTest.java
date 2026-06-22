package dev.vaijanath.aiagent.memory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vaijanath.aiagent.model.Message;
import dev.vaijanath.aiagent.model.Role;
import dev.vaijanath.aiagent.model.Tokenizer;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class SummarizingMemoryTest {

    private static final Tokenizer ONE_PER_CHAR = String::length;

    @Test
    void keepsEverythingVerbatimUnderBudget() {
        AtomicInteger calls = new AtomicInteger();
        Summarizer summarizer = messages -> {
            calls.incrementAndGet();
            return "gist";
        };
        SummarizingMemory m = new SummarizingMemory(ONE_PER_CHAR, summarizer, 1000, 1);
        m.add(Message.system("system"));
        m.add(Message.user("hi"));
        m.add(Message.assistant("hello"));

        assertEquals(0, calls.get(), "no folding under budget");
        assertEquals(3, m.history().size());
    }

    @Test
    void foldsOldestIntoARollingSummaryOverBudget() {
        AtomicInteger calls = new AtomicInteger();
        Summarizer summarizer = messages -> {
            calls.incrementAndGet();
            return "earlier stuff";
        };
        SummarizingMemory m = new SummarizingMemory(ONE_PER_CHAR, summarizer, 50, 1);
        m.add(Message.system("system prompt"));
        for (int i = 0; i < 8; i++) {
            m.add(Message.user("message-" + i));
        }

        List<Message> h = m.history();
        // The real system prompt is preserved as the first message.
        assertEquals(Role.SYSTEM, h.get(0).role());
        assertEquals("system prompt", h.get(0).content());
        // A summary system message carries the folded older turns.
        assertTrue(
                h.stream().anyMatch(x -> x.role() == Role.SYSTEM && x.content().contains("earlier stuff")),
                "a rolling summary message must be present");
        assertTrue(calls.get() > 0, "summarizer must have been invoked");
        // The most recent turn survives verbatim.
        assertEquals("message-7", h.get(h.size() - 1).content());
    }
}
