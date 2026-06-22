package dev.vaijanath.aiagent.springboot.web;

import dev.vaijanath.aiagent.model.ToolCall;
import dev.vaijanath.aiagent.observe.AgentObserver;
import dev.vaijanath.aiagent.tool.ToolResult;
import java.io.IOException;
import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * An {@link AgentObserver} that forwards a turn's tool lifecycle to one SSE client — a {@code tool}
 * event per tool call and a {@code tool_result} event (name + ok flag only) per result, never raw
 * model content. Shared by agent web apps so the streaming contract is defined once.
 */
public final class SseAgentObserver implements AgentObserver {

    private final SseEmitter emitter;

    public SseAgentObserver(SseEmitter emitter) {
        this.emitter = emitter;
    }

    @Override
    public void onToolCall(ToolCall call) {
        send("tool", Map.of("name", call.name()));
    }

    @Override
    public void onToolResult(String toolName, ToolResult result) {
        send("tool_result", Map.of("name", toolName, "ok", !result.error()));
    }

    private void send(String event, Object data) {
        try {
            emitter.send(SseEmitter.event().name(event).data(data, MediaType.APPLICATION_JSON));
        } catch (IOException | RuntimeException e) {
            // the client disconnected or the stream is already complete — drop the event
        }
    }
}
