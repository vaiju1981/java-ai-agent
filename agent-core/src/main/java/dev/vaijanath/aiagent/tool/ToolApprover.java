package dev.vaijanath.aiagent.tool;

/**
 * Authorizes tool calls before the runtime executes them — the capability/permission gate.
 *
 * <p>This is <b>authorization</b> (which tools may run, and human approval for sensitive ones), not
 * OS-level sandboxing of tool code (not achievable in pure Java since the SecurityManager's removal).
 * A tool's own implementation is still responsible for what it does once permitted.
 */
public interface ToolApprover {

    ToolDecision authorize(ToolCallContext call);
}
