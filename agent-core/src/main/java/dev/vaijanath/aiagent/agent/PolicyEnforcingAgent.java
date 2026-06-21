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
 * <p>Output guardrails run on the delegate's result <em>even if the delegate marked it blocked</em>,
 * so a black-box agent cannot escape the policy by self-labelling unsafe output as "blocked". The
 * deadline is enforced by running the delegate with the remaining time and cancelling on expiry, so a
 * delegate that blocks or returns late cannot deliver a result past the deadline.
 *
 * <p>Scope: this governs a delegate's inputs and outputs. It cannot intercept tool calls that happen
 * <em>inside</em> a black-box delegate (e.g. ADK executing its own tools); gate those with a
 * {@code ToolApprover} where the tools actually run (as {@code DefaultAgent} does).
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
        GuardrailDecision in = Guardrails.apply(guardrails, GuardrailStage.INPUT, request.input());
        if (in.blocked()) {
            audit("guardrail.block", ctx, "stage=INPUT reason=" + in.reason());
            return deadlineOr(ctx, AgentResponse.blocked(in.content(), in.reason()));
        }
        if (deadlineExceeded(ctx)) {
            return deadlineExceededResponse(ctx);
        }

        // Run the delegate bounded by the remaining deadline; null means it ran out of time.
        AgentResponse response = runBounded(ctx, () -> delegate.run(new AgentRequest(in.content(), ctx)));
        if (response == null) {
            return deadlineExceededResponse(ctx);
        }

        // Output guardrails run regardless of the delegate's own blocked flag — a delegate cannot
        // bypass the policy by labelling unsafe output as "blocked".
        GuardrailDecision out = Guardrails.apply(guardrails, GuardrailStage.OUTPUT, response.output());
        if (out.blocked()) {
            audit("guardrail.block", ctx, "stage=OUTPUT reason=" + out.reason());
            return AgentResponse.blocked(out.content(), out.reason());
        }
        // Check once more before delivering: a result produced after the deadline is not delivered.
        if (deadlineExceeded(ctx)) {
            return deadlineExceededResponse(ctx);
        }
        audit("turn.end", ctx, "stopReason=" + response.stopReason());
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
        Thread worker = Thread.ofVirtual().name("governed-delegate").start(task);
        try {
            return task.get(remainingMillis, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            task.cancel(true);
            worker.interrupt();
            log.warn("governed delegate exceeded the deadline for session {}", ctx.sessionId());
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

    /** If the deadline has passed, return the timeout response; otherwise the given response. */
    private AgentResponse deadlineOr(RequestContext ctx, AgentResponse response) {
        return deadlineExceeded(ctx) ? deadlineExceededResponse(ctx) : response;
    }

    private AgentResponse deadlineExceededResponse(RequestContext ctx) {
        audit("turn.end", ctx, "deadline_exceeded");
        return AgentResponse.stopped(DEADLINE_MESSAGE, "deadline_exceeded");
    }

    private void audit(String type, RequestContext ctx, String detail) {
        auditSink.record(AuditEvent.now(
                type, ctx.traceId(), ctx.sessionId(), ctx.principal(), ctx.tenant(), detail));
    }

    private static boolean deadlineExceeded(RequestContext ctx) {
        return ctx.deadlineAt().map(d -> !Instant.now().isBefore(d)).orElse(false);
    }
}
