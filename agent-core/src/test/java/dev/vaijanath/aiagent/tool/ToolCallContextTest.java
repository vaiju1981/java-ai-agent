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

    @Test
    void clientKeyIsStableAcrossTraceAndSessionRetries() {
        ToolSpec spec = new ToolSpec("pay", "pay", "{\"type\":\"object\"}", ToolEffect.EFFECTFUL);
        ToolCallContext first = new ToolCallContext(
                spec, "{\"amount\":5}", "alice", "acme", "trace-1", "session-1", null, "request-42");
        ToolCallContext retry = new ToolCallContext(
                spec, "{\"amount\":5}", "alice", "acme", "trace-2", "session-2", null, "request-42");

        assertEquals(first.idempotencyKey(), retry.idempotencyKey());
    }

    @Test
    void clientKeyStillDistinguishesDifferentArguments() {
        // Regression: with a client idempotency key, two distinct same-tool calls in one request must
        // not collapse — otherwise an effectful tool deduping by this key could skip a real operation.
        ToolSpec spec = new ToolSpec("pay", "pay", "{\"type\":\"object\"}", ToolEffect.EFFECTFUL);
        ToolCallContext five = new ToolCallContext(
                spec, "{\"amount\":5}", "alice", "acme", "trace-1", "session-1", null, "request-42");
        ToolCallContext six = new ToolCallContext(
                spec, "{\"amount\":6}", "alice", "acme", "trace-1", "session-1", null, "request-42");

        assertNotEquals(five.idempotencyKey(), six.idempotencyKey());
    }
}
