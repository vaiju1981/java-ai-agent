package dev.vaijanath.aiagent.observe;

import dev.vaijanath.aiagent.agent.AgentResponse;
import dev.vaijanath.aiagent.guardrail.GuardrailDecision;
import dev.vaijanath.aiagent.guardrail.GuardrailStage;
import dev.vaijanath.aiagent.model.ModelRequest;
import dev.vaijanath.aiagent.model.ModelResponse;
import dev.vaijanath.aiagent.model.ToolCall;
import dev.vaijanath.aiagent.tool.ToolResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Logs the agent's lifecycle to SLF4J (turn boundaries at INFO, steps at DEBUG). */
public final class LoggingObserver implements AgentObserver {

    private static final Logger log = LoggerFactory.getLogger("dev.vaijanath.aiagent.trace");

    @Override
    public void onTurnStart(String input) {
        log.info("turn start: {}", truncate(input));
    }

    @Override
    public void onGuardrail(GuardrailStage stage, String guardrailName, GuardrailDecision decision) {
        log.debug("guardrail {} [{}] -> {}", guardrailName, stage, decision.allowed() ? "allow" : "BLOCK");
    }

    @Override
    public void onModelCall(ModelRequest request) {
        log.debug("model call: {} messages, {} tools", request.messages().size(), request.tools().size());
    }

    @Override
    public void onModelResponse(ModelResponse response) {
        log.debug("model response: {} tool-call(s), tokens in={} out={}",
                response.toolCalls().size(), response.usage().inputTokens(), response.usage().outputTokens());
    }

    @Override
    public void onToolCall(ToolCall call) {
        log.debug("tool call: {} {}", call.name(), call.argumentsJson());
    }

    @Override
    public void onToolResult(String toolName, ToolResult result) {
        log.debug("tool result [{}]: {}", toolName, result.error() ? "error" : "ok");
    }

    @Override
    public void onTurnEnd(AgentResponse response) {
        log.info("turn end: {} ({})", response.blocked() ? "BLOCKED" : "ok", response.stopReason());
    }

    private static String truncate(String s) {
        if (s == null) {
            return "";
        }
        return s.length() <= 80 ? s : s.substring(0, 80) + "…";
    }
}
