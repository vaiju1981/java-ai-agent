package dev.vaijanath.aiagent.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ResilientModelPortTest {

    private static ModelRequest req() {
        return ModelRequest.of(List.of(Message.user("hi")));
    }

    @Test
    void retriesThenSucceeds() {
        AtomicInteger calls = new AtomicInteger();
        ModelPort flaky = request -> {
            if (calls.incrementAndGet() < 2) {
                throw new RuntimeException("transient");
            }
            return ModelResponse.text("ok");
        };

        ModelPort resilient = new ResilientModelPort(flaky, 3, Duration.ofSeconds(5), 1);

        assertEquals("ok", resilient.chat(req()).text());
        assertEquals(2, calls.get());
    }

    @Test
    void throwsAfterExhaustingRetries() {
        ModelPort broken = request -> {
            throw new RuntimeException("down");
        };
        ModelPort resilient = new ResilientModelPort(broken, 2, Duration.ofSeconds(5), 1);
        assertThrows(RuntimeException.class, () -> resilient.chat(req()));
    }

    @Test
    void timesOutSlowCalls() {
        ModelPort slow = request -> {
            try {
                Thread.sleep(300);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return ModelResponse.text("late");
        };
        ModelPort resilient = new ResilientModelPort(slow, 1, Duration.ofMillis(50), 1);
        assertThrows(RuntimeException.class, () -> resilient.chat(req()));
    }
}
