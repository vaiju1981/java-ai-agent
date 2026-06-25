package dev.vaijanath.aiagent.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class StopReasonTest {

    @Test
    void classifiesEachOutcome() {
        assertEquals(StopReason.COMPLETED, AgentResponse.completed("x").reason());
        assertEquals(StopReason.BLOCKED, AgentResponse.blocked("x", "guardrail").reason());
        assertEquals(StopReason.MAX_STEPS, AgentResponse.stopped("x", "max_steps").reason());
        assertEquals(StopReason.DEADLINE_EXCEEDED, AgentResponse.stopped("x", "deadline_exceeded").reason());
        assertEquals(StopReason.MODEL_ERROR, AgentResponse.stopped("x", "model_error").reason());
        assertEquals(StopReason.UNKNOWN, AgentResponse.stopped("x", "something_custom").reason());
    }

    @Test
    void exposesRetryabilityAndCategory() {
        assertTrue(AgentResponse.stopped("x", "model_error").retryable());
        assertTrue(AgentResponse.stopped("x", "deadline_exceeded").retryable());
        assertFalse(AgentResponse.completed("x").retryable());
        assertFalse(AgentResponse.blocked("x", "g").retryable());
        assertFalse(AgentResponse.stopped("x", "max_steps").retryable());

        assertEquals(StopReason.Category.SUCCESS, AgentResponse.completed("x").reason().category());
        assertEquals(StopReason.Category.ERROR, AgentResponse.stopped("x", "model_error").reason().category());
        assertEquals(
                StopReason.Category.TIMEOUT, AgentResponse.stopped("x", "deadline_exceeded").reason().category());
    }

    @Test
    void blockedTakesPrecedenceOverTheStopReasonString() {
        assertEquals(StopReason.BLOCKED, AgentResponse.blocked("x", "model_error").reason());
    }
}
