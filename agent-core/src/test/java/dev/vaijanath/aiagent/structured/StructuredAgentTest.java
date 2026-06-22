package dev.vaijanath.aiagent.structured;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vaijanath.aiagent.agent.Agent;
import dev.vaijanath.aiagent.agent.AgentResponse;
import dev.vaijanath.aiagent.model.ModelRequest;
import dev.vaijanath.aiagent.model.StructuredOutput;
import org.junit.jupiter.api.Test;

class StructuredAgentTest {

    record Weather(String city, String summary) {}

    /** A StructuredOutput that records its request, fails the first {@code failTimes} calls, then binds. */
    static final class FakeStructuredOutput implements StructuredOutput {
        private final Object value;
        private final int failTimes;
        ModelRequest lastRequest;
        int calls;

        FakeStructuredOutput(Object value, int failTimes) {
            this.value = value;
            this.failTimes = failTimes;
        }

        @Override
        public <T> T generate(ModelRequest request, Class<T> type) {
            this.lastRequest = request;
            calls++;
            if (calls <= failTimes) {
                throw new IllegalStateException("binding failed");
            }
            return type.cast(value);
        }
    }

    @Test
    void coercesCompletedAnswerAndCarriesContextIntoTheCoercionCall() {
        Agent agent = req -> AgentResponse.completed("NYC is sunny, 72F.");
        FakeStructuredOutput so = new FakeStructuredOutput(new Weather("NYC", "sunny"), 0);

        StructuredResult<Weather> result = new StructuredAgent(agent, so).run("weather in NYC?", Weather.class);

        assertTrue(result.present());
        assertEquals(new Weather("NYC", "sunny"), result.value());
        assertEquals("completed", result.raw().stopReason());
        assertEquals(1, so.calls);
        // The coercion call sees both the user request and the assistant's answer.
        String coercionUserMsg = so.lastRequest.messages().get(1).content();
        assertTrue(coercionUserMsg.contains("weather in NYC?"), coercionUserMsg);
        assertTrue(coercionUserMsg.contains("NYC is sunny, 72F."), coercionUserMsg);
    }

    @Test
    void blockedTurnYieldsNoValueAndSkipsCoercion() {
        Agent agent = req -> AgentResponse.blocked("I can't help with that.", "guardrail.block");
        FakeStructuredOutput so = new FakeStructuredOutput(new Weather("x", "y"), 0);

        StructuredResult<Weather> result = new StructuredAgent(agent, so).run("...", Weather.class);

        assertFalse(result.present());
        assertNull(result.value());
        assertTrue(result.raw().blocked());
        assertEquals(0, so.calls, "a guardrail's safe replacement must not be coerced");
        assertThrows(IllegalStateException.class, result::orElseThrow);
    }

    @Test
    void stoppedTurnYieldsNoValueAndSkipsCoercion() {
        Agent agent = req -> AgentResponse.stopped("partial answer", "max_steps");
        FakeStructuredOutput so = new FakeStructuredOutput(new Weather("x", "y"), 0);

        StructuredResult<Weather> result = new StructuredAgent(agent, so).run("...", Weather.class);

        assertFalse(result.present());
        assertEquals(0, so.calls);
    }

    @Test
    void repairsByRetryingThenSucceeds() {
        Agent agent = req -> AgentResponse.completed("ok");
        FakeStructuredOutput so = new FakeStructuredOutput(new Weather("NYC", "sunny"), 1); // fail once

        StructuredResult<Weather> result = new StructuredAgent(agent, so).run("...", Weather.class);

        assertTrue(result.present());
        assertEquals(2, so.calls);
        assertEquals(new Weather("NYC", "sunny"), result.orElseThrow());
    }

    @Test
    void throwsAfterExhaustingAttempts() {
        Agent agent = req -> AgentResponse.completed("ok");
        FakeStructuredOutput so = new FakeStructuredOutput(new Weather("NYC", "sunny"), 99); // always fail
        StructuredAgent agentUnderTest = new StructuredAgent(agent, so, "instruction", 2);

        assertThrows(StructuredCoercionException.class, () -> agentUnderTest.run("...", Weather.class));
        assertEquals(2, so.calls);
    }
}
