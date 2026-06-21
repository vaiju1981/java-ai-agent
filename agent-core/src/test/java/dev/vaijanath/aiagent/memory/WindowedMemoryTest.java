package dev.vaijanath.aiagent.memory;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.vaijanath.aiagent.model.Message;
import java.util.List;
import org.junit.jupiter.api.Test;

class WindowedMemoryTest {

    @Test
    void keepsSystemAndMostRecent() {
        WindowedMemory memory = new WindowedMemory(2);
        memory.add(Message.system("sys"));
        for (int i = 1; i <= 5; i++) {
            memory.add(Message.user("u" + i));
        }

        List<Message> history = memory.history();

        assertEquals(3, history.size());
        assertEquals("sys", history.get(0).content());
        assertEquals("u4", history.get(1).content());
        assertEquals("u5", history.get(2).content());
    }
}
