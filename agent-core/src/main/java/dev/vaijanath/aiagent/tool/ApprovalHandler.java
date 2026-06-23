package dev.vaijanath.aiagent.tool;

/**
 * Decides — possibly by consulting a human — whether an effectful tool call that the {@link ToolApprover}
 * did not auto-approve may run. The governed runtime calls this inline during a turn for an
 * {@link ToolEffect#EFFECTFUL} tool the approver denied, just after notifying observers via
 * {@code AgentObserver.onApprovalRequired}.
 *
 * <p>{@link #requestApproval} <b>may block</b> awaiting a human, so run turns that can hit it off the
 * request thread (the SSE / virtual-thread path, where blocking is cheap) and bound it with the request
 * deadline. Returning {@code false} declines the call: the model is told the user declined and the turn
 * continues. When no handler is configured, an unapproved effectful tool is hard-denied (the safe default).
 */
@FunctionalInterface
public interface ApprovalHandler {

    boolean requestApproval(ApprovalRequest request);
}
