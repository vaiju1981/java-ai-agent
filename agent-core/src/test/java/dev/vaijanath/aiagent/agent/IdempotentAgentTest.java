package dev.vaijanath.aiagent.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import org.junit.jupiter.api.Test;

class IdempotentAgentTest {

    private static final class Counting implements Agent {
        int calls;
        AgentResponse next = AgentResponse.completed("ok");

        @Override
        public AgentResponse run(AgentRequest request) {
            calls++;
            return next;
        }
    }

    private static AgentRequest withKey(String key) {
        return new AgentRequest(
                "hi",
                new RequestContext(
                        "s1", "alice", "acme", "t1", null, Map.of(IdempotentAgent.KEY_ATTRIBUTE, key)));
    }

    @Test
    void replaysWithoutRerunningWhenTheSameKeyRepeats() {
        Counting delegate = new Counting();
        Agent agent = new IdempotentAgent(delegate, new InMemoryIdempotencyStore());

        AgentResponse first = agent.run(withKey("k1"));
        AgentResponse second = agent.run(withKey("k1"));

        assertEquals(1, delegate.calls, "the turn ran once; the retry was served from the store");
        assertEquals(first.output(), second.output());
    }

    @Test
    void runsEveryTimeWithoutAKey() {
        Counting delegate = new Counting();
        Agent agent = new IdempotentAgent(delegate, new InMemoryIdempotencyStore());

        agent.run(new AgentRequest("hi")); // ephemeral context, no idempotency key
        agent.run(new AgentRequest("hi"));

        assertEquals(2, delegate.calls);
    }

    @Test
    void doesNotCacheRetryableFailuresSoARetryReruns() {
        Counting delegate = new Counting();
        delegate.next = AgentResponse.stopped("oops", "model_error"); // retryable
        Agent agent = new IdempotentAgent(delegate, new InMemoryIdempotencyStore());

        agent.run(withKey("k1"));
        agent.run(withKey("k1"));

        assertEquals(2, delegate.calls, "a transient failure is not cached, so the retry actually re-runs");
    }

    @Test
    void differentKeysRunIndependently() {
        Counting delegate = new Counting();
        Agent agent = new IdempotentAgent(delegate, new InMemoryIdempotencyStore());

        agent.run(withKey("k1"));
        agent.run(withKey("k2"));

        assertEquals(2, delegate.calls);
    }
}
