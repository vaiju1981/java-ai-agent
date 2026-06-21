package dev.vaijanath.aiagent.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vaijanath.aiagent.guardrail.KeywordBlocklistGuardrail;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

/** {@code Trust.govern} wraps the universal Agent seam, so any agent (including black-box) is governed. */
class PolicyEnforcingAgentTest {

    private static final Agent ECHO = request -> AgentResponse.completed("echo: " + request.input());

    @Test
    void inputGuardrailBlocksBeforeDelegateRuns() {
        AtomicBoolean ran = new AtomicBoolean(false);
        Agent delegate = request -> {
            ran.set(true);
            return AgentResponse.completed("should not happen");
        };
        Agent governed = Trust.govern(delegate,
                new KeywordBlocklistGuardrail(List.of("forbidden"), "blocked at input"));

        AgentResponse r = governed.run(new AgentRequest("this is forbidden"));

        assertTrue(r.blocked());
        assertEquals("blocked at input", r.output());
        assertFalse(ran.get(), "the delegate must not run when input is blocked");
    }

    @Test
    void outputGuardrailBlocksTheDelegateResult() {
        Agent leaks = request -> AgentResponse.completed("here is the secret password");
        Agent governed = Trust.govern(leaks,
                new KeywordBlocklistGuardrail(List.of("secret"), "blocked at output"));

        AgentResponse r = governed.run(new AgentRequest("hello"));

        assertTrue(r.blocked());
        assertEquals("blocked at output", r.output());
    }

    @Test
    void cleanTurnsPassThrough() {
        Agent governed = Trust.govern(ECHO, new KeywordBlocklistGuardrail(List.of("nope"), "x"));

        AgentResponse r = governed.run(new AgentRequest("hello"));

        assertFalse(r.blocked());
        assertEquals("echo: hello", r.output());
    }

    @Test
    void deadlineStopsBeforeDelegateRuns() {
        AtomicBoolean ran = new AtomicBoolean(false);
        Agent delegate = request -> {
            ran.set(true);
            return AgentResponse.completed("x");
        };
        Agent governed = Trust.govern(delegate);
        RequestContext past = new RequestContext(
                "s", null, null, null, Instant.now().minusSeconds(1), null);

        AgentResponse r = governed.run(new AgentRequest("hi", past));

        assertEquals("deadline_exceeded", r.stopReason());
        assertFalse(ran.get());
    }

    @Test
    void outputGuardrailsApplyEvenWhenTheDelegateClaimsBlocked() {
        // A black-box agent emits unsafe content but self-labels it "blocked" to dodge the policy.
        Agent sneaky = request -> AgentResponse.blocked("here is the secret", "self-blocked");
        Agent governed = Trust.govern(sneaky,
                new KeywordBlocklistGuardrail(List.of("secret"), "stopped by policy"));

        AgentResponse r = governed.run(new AgentRequest("hi"));

        assertTrue(r.blocked());
        assertEquals("stopped by policy", r.output(),
                "the output policy must still apply to a self-blocked result");
    }

    @Test
    void deadlineIsHardEvenWhenTheDelegateBlocks() {
        Agent slow = request -> {
            try {
                Thread.sleep(60_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return AgentResponse.completed("late answer");
        };
        Agent governed = Trust.govern(slow);
        RequestContext soon = new RequestContext(
                "s", null, null, null, Instant.now().plusMillis(100), null);

        AgentResponse r = governed.run(new AgentRequest("hi", soon));

        assertEquals("deadline_exceeded", r.stopReason());
        assertFalse(r.output().contains("late answer"),
                "a result produced after the deadline must not be delivered");
    }
}
