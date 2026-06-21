package dev.vaijanath.aiagent.agent;

import dev.vaijanath.aiagent.guardrail.Guardrail;
import dev.vaijanath.aiagent.guardrail.GuardrailDecision;
import dev.vaijanath.aiagent.guardrail.GuardrailStage;
import dev.vaijanath.aiagent.guardrail.Guardrails;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Governs <b>any</b> {@link Agent} at the universal seam: input guardrails before the delegate runs,
 * output guardrails on whatever it returns, and the request deadline. Because it wraps the
 * {@code Agent} interface itself, composed and black-box agents ({@code DeepAgent}, {@code AdkAgent},
 * …) are governed too — not just {@code DefaultAgent}. Build one with {@link Trust}.
 *
 * <p>Scope: this governs a delegate's <em>inputs and outputs</em>. It cannot intercept tool calls
 * that happen <em>inside</em> a black-box delegate (e.g. ADK executing its own tools); gate those
 * with a {@code ToolApprover} where the tools actually run (as {@code DefaultAgent} does).
 */
public final class PolicyEnforcingAgent implements Agent {

    private final Agent delegate;
    private final List<Guardrail> guardrails;

    PolicyEnforcingAgent(Agent delegate, List<Guardrail> guardrails) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.guardrails = List.copyOf(guardrails);
    }

    @Override
    public AgentResponse run(AgentRequest request) {
        GuardrailDecision in = Guardrails.apply(guardrails, GuardrailStage.INPUT, request.input());
        if (in.blocked()) {
            return AgentResponse.blocked(in.content(), in.reason());
        }
        if (deadlineExceeded(request)) {
            return AgentResponse.stopped("I ran out of time on this request.", "deadline_exceeded");
        }

        AgentResponse response = delegate.run(new AgentRequest(in.content(), request.context()));
        if (response.blocked()) {
            return response;
        }

        GuardrailDecision out = Guardrails.apply(guardrails, GuardrailStage.OUTPUT, response.output());
        if (out.blocked()) {
            return AgentResponse.blocked(out.content(), out.reason());
        }
        // Carry any output transformation while preserving the delegate's stop reason.
        return new AgentResponse(out.content(), false, response.stopReason());
    }

    private static boolean deadlineExceeded(AgentRequest request) {
        return request.context().deadlineAt().map(d -> !Instant.now().isBefore(d)).orElse(false);
    }
}
