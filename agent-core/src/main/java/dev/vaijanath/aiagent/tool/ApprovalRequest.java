package dev.vaijanath.aiagent.tool;

import dev.vaijanath.aiagent.model.ToolCall;
import java.util.Objects;

/**
 * A request to approve an effectful tool call that policy did not auto-approve. The {@code approvalId} is
 * a unique token for this request — surfaced to a UI (e.g. via {@code AgentObserver.onApprovalRequired})
 * so the decision can be resolved out of band — and the {@code call}/{@code context} describe what would
 * run, so a human can decide with the tool name and arguments in hand.
 */
public record ApprovalRequest(String approvalId, ToolCall call, ToolCallContext context) {

    public ApprovalRequest {
        Objects.requireNonNull(approvalId, "approvalId");
        Objects.requireNonNull(call, "call");
        Objects.requireNonNull(context, "context");
    }
}
