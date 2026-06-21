package dev.vaijanath.aiagent.agent;

import dev.vaijanath.aiagent.audit.AuditEvent;
import dev.vaijanath.aiagent.audit.AuditSink;
import dev.vaijanath.aiagent.guardrail.Guardrail;
import dev.vaijanath.aiagent.guardrail.GuardrailDecision;
import dev.vaijanath.aiagent.guardrail.GuardrailStage;
import dev.vaijanath.aiagent.guardrail.Guardrails;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Governs <b>any</b> {@link Agent} at the universal seam: input guardrails before the delegate runs,
 * output guardrails on whatever it returns, and a <b>hard</b> request deadline. Because it wraps the
 * {@code Agent} interface itself, composed and black-box agents ({@code DeepAgent}, {@code AdkAgent},
 * …) are governed too — not just {@code DefaultAgent}. Build one with {@link Trust}.
 *
 * <p>The whole turn — input guardrails, the delegate, and output guardrails — runs under the
 * deadline, so even a hanging model-backed guardrail cannot exceed it. Output guardrails run on the
 * delegate's result <em>even if the delegate marked it blocked</em>, so a black-box agent cannot
 * escape the policy by self-labelling unsafe output. A {@code turn.start} is emitted up front and a
 * matching {@code turn.end} is emitted <b>exactly once</b> in a {@code finally} — on completion, a
 * block, the deadline, or an exception — and only by the caller, so a late worker cannot double-record.
 *
 * <p>Limitations: cancellation on deadline is cooperative (the JVM cannot force-stop a thread), and
 * this governs a delegate's inputs and outputs — it cannot intercept tool calls that happen
 * <em>inside</em> a black-box delegate (gate those with a {@code ToolApprover} where the tools run).
 */
public final class PolicyEnforcingAgent implements Agent {

    private static final Logger log = LoggerFactory.getLogger(PolicyEnforcingAgent.class);
    private static final String DEADLINE_MESSAGE = "I ran out of time on this request.";

    private final Agent delegate;
    private final List<Guardrail> guardrails;
    private final AuditSink auditSink;

    PolicyEnforcingAgent(Agent delegate, List<Guardrail> guardrails, AuditSink auditSink) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.guardrails = List.copyOf(guardrails);
        this.auditSink = auditSink != null ? auditSink : AuditSink.none();
    }

    @Override
    public AgentResponse run(AgentRequest request) {
        RequestContext ctx = request.context();
        audit("turn.start", ctx, "input.len=" + request.input().length());
        // turn.end is recorded exactly once here, covering completion, blocks, deadline, and
        // exceptions; governedTurn (which may run on a worker thread) never records it, so a late
        // worker cannot produce a second ending.
        String endReason = "error";
        try {
            if (deadlineExceeded(ctx)) {
                endReason = "deadline_exceeded";
                return AgentResponse.stopped(DEADLINE_MESSAGE, "deadline_exceeded");
            }
            // Bound the ENTIRE turn — input guardrails, delegate, output guardrails — under the
            // deadline. null means it ran out of time.
            AgentResponse response = runBounded(ctx, () -> governedTurn(ctx, request));
            if (response == null) {
                endReason = "deadline_exceeded";
                return AgentResponse.stopped(DEADLINE_MESSAGE, "deadline_exceeded");
            }
            endReason = response.blocked() ? "blocked" : "stopReason=" + response.stopReason();
            return response;
        } finally {
            audit("turn.end", ctx, endReason);
        }
    }

    private AgentResponse governedTurn(RequestContext ctx, AgentRequest request) {
        GuardrailDecision in = Guardrails.apply(guardrails, GuardrailStage.INPUT, request.input());
        if (in.blocked()) {
            audit("guardrail.block", ctx, "stage=INPUT reason=" + in.reason());
            return AgentResponse.blocked(in.content(), in.reason());
        }

        AgentResponse response = delegate.run(new AgentRequest(in.content(), ctx));

        // Output guardrails run regardless of the delegate's own blocked flag — a delegate cannot
        // bypass the policy by labelling unsafe output as "blocked".
        GuardrailDecision out = Guardrails.apply(guardrails, GuardrailStage.OUTPUT, response.output());
        if (out.blocked()) {
            audit("guardrail.block", ctx, "stage=OUTPUT reason=" + out.reason());
            return AgentResponse.blocked(out.content(), out.reason());
        }
        return new AgentResponse(out.content(), response.blocked(), response.stopReason());
    }

    /** Runs {@code work} on a virtual thread bounded by the remaining deadline; null = timed out. */
    private AgentResponse runBounded(RequestContext ctx, Supplier<AgentResponse> work) {
        Optional<Instant> deadline = ctx.deadlineAt();
        if (deadline.isEmpty()) {
            return work.get();
        }
        long remainingMillis = Duration.between(Instant.now(), deadline.get()).toMillis();
        if (remainingMillis <= 0) {
            return null;
        }
        FutureTask<AgentResponse> task = new FutureTask<>(work::get);
        Thread worker = Thread.ofVirtual().name("governed-turn").start(task);
        try {
            return task.get(remainingMillis, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            task.cancel(true);
            worker.interrupt();
            log.warn("governed turn exceeded the deadline for session {}", ctx.sessionId());
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            task.cancel(true);
            return null;
        } catch (ExecutionException e) {
            if (e.getCause() instanceof RuntimeException re) {
                throw re;
            }
            throw new RuntimeException(e.getCause());
        }
    }

    private void audit(String type, RequestContext ctx, String detail) {
        // Auditing must never break a run: a throwing sink is logged and the event dropped.
        try {
            auditSink.record(AuditEvent.now(
                    type, ctx.traceId(), ctx.sessionId(), ctx.principal(), ctx.tenant(), detail));
        } catch (RuntimeException e) {
            log.warn("audit sink threw; dropping '{}' event", type, e);
        }
    }

    private static boolean deadlineExceeded(RequestContext ctx) {
        return ctx.deadlineAt().map(d -> !Instant.now().isBefore(d)).orElse(false);
    }
}
