package dev.vaijanath.aiagent.agent;

import dev.vaijanath.aiagent.audit.AuditSink;
import dev.vaijanath.aiagent.guardrail.Guardrail;
import java.util.List;

/**
 * Entry point for governance: wrap any {@link Agent} so trust is enforced at the universal seam
 * rather than configured per implementation. The recommended way to expose an agent is to govern it:
 *
 * <pre>{@code
 * Agent agent = Trust.govern(
 *     DefaultAgent.builder()
 *         .model(model)
 *         .tool(tool)
 *         .toolApprover(ToolApprovers.denyEffectful())   // deny side-effecting tools by default
 *         .build(),
 *     Guardrails.kidguard(guardModel));                  // crisis -> PII -> Llama Guard
 * }</pre>
 *
 * <p>This composes with any {@code Agent} — including {@code DeepAgent} and {@code AdkAgent} — so the
 * same input/output guardrails and deadline apply no matter how the agent is built.
 */
public final class Trust {

    private Trust() {}

    /** Govern an agent with guardrails applied to its input and output (and the request deadline). */
    public static Agent govern(Agent delegate, Guardrail... guardrails) {
        return new PolicyEnforcingAgent(delegate, List.of(guardrails), null);
    }

    /** Govern an agent with a list of guardrails. */
    public static Agent govern(Agent delegate, List<Guardrail> guardrails) {
        return new PolicyEnforcingAgent(delegate, guardrails, null);
    }

    /** Govern an agent and record an audit trail of its seam-level decisions. */
    public static Agent govern(Agent delegate, AuditSink auditSink, List<Guardrail> guardrails) {
        return new PolicyEnforcingAgent(delegate, guardrails, auditSink);
    }
}
