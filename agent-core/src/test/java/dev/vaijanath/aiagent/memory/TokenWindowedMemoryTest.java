package dev.vaijanath.aiagent.memory;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.vaijanath.aiagent.model.Message;
import dev.vaijanath.aiagent.model.Role;
import dev.vaijanath.aiagent.model.Tokenizer;
import java.util.List;
import org.junit.jupiter.api.Test;

class TokenWindowedMemoryTest {

    // One token per character, for exact, readable budget assertions.
    private static final Tokenizer ONE_PER_CHAR = String::length;

    @Test
    void dropsOldestNonSystemOverBudgetButKeepsSystem() {
        TokenWindowedMemory m = new TokenWindowedMemory(ONE_PER_CHAR, 10);
        m.add(Message.system("sys")); // 3
        m.add(Message.user("aaaa")); // +4 = 7
        m.add(Message.user("bbbb")); // +4 = 11 > 10 -> evict "aaaa"

        List<Message> h = m.history();
        assertEquals(List.of(Role.SYSTEM, Role.USER), h.stream().map(Message::role).toList());
        assertEquals("sys", h.get(0).content());
        assertEquals("bbbb", h.get(1).content());
    }

    @Test
    void alwaysKeepsTheLatestEvenIfItAloneExceedsBudget() {
        TokenWindowedMemory m = new TokenWindowedMemory(ONE_PER_CHAR, 2);
        m.add(Message.user("a really long message"));
        assertEquals(1, m.history().size());
    }

    @Test
    void keepsEverythingUnderBudget() {
        TokenWindowedMemory m = new TokenWindowedMemory(ONE_PER_CHAR, 1000);
        m.add(Message.system("s"));
        m.add(Message.user("hi"));
        m.add(Message.assistant("hello"));
        assertEquals(3, m.history().size());
    }
}
