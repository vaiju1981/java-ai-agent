package dev.vaijanath.aiagent.tool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import org.junit.jupiter.api.Test;

class ToolCallContextTest {

    private static ToolCallContext call(String argumentsJson) {
        return new ToolCallContext(
                new ToolSpec("pay", "pay", "{\"type\":\"object\"}", ToolEffect.EFFECTFUL),
                argumentsJson, "alice", "acme", "trace", "session", null);
    }

    @Test
    void idempotencyKeyIsStableForTheSameCall() {
        assertEquals(call("{\"amount\":5}").idempotencyKey(), call("{\"amount\":5}").idempotencyKey());
    }

    @Test
    void idempotencyKeyChangesWithArguments() {
        assertNotEquals(call("{\"amount\":5}").idempotencyKey(), call("{\"amount\":6}").idempotencyKey());
    }
}
