package dev.vaijanath.aiagent.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vaijanath.aiagent.audit.AuditEvent;
import dev.vaijanath.aiagent.audit.InMemoryAuditSink;
import dev.vaijanath.aiagent.guardrail.Guardrail;
import dev.vaijanath.aiagent.guardrail.GuardrailDecision;
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

    @Test
    void aHangingGuardrailCannotExceedTheDeadline() {
        Guardrail slow = (stage, content) -> {
            try {
                Thread.sleep(60_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return GuardrailDecision.allow(content);
        };
        Agent governed = Trust.govern(request -> AgentResponse.completed("x"), slow);
        RequestContext soon = new RequestContext(
                "s", null, null, null, Instant.now().plusMillis(100), null);

        AgentResponse r = governed.run(new AgentRequest("hi", soon));

        assertEquals("deadline_exceeded", r.stopReason(), "a hanging guardrail must not beat the deadline");
    }

    @Test
    void emitsTurnStartAndTurnEndAtTheSeam() {
        InMemoryAuditSink audit = new InMemoryAuditSink();
        Agent governed = Trust.govern(request -> AgentResponse.completed("ok"), audit, List.of());

        governed.run(new AgentRequest("hi"));

        List<String> types = audit.events().stream().map(AuditEvent::type).toList();
        assertTrue(types.contains("turn.start"), types.toString());
        assertEquals(1, types.stream().filter("turn.end"::equals).count(), types.toString());
    }

    @Test
    void recordsExactlyOneTurnEndEvenWhenTheDelegateThrows() {
        InMemoryAuditSink audit = new InMemoryAuditSink();
        Agent boom = request -> {
            throw new RuntimeException("delegate boom");
        };
        Agent governed = Trust.govern(boom, audit, List.of());

        assertThrows(RuntimeException.class, () -> governed.run(new AgentRequest("hi")));

        long ends = audit.events().stream().filter(e -> e.type().equals("turn.end")).count();
        assertEquals(1, ends, "a throwing delegate must still close the lifecycle exactly once");
    }
}
