package dev.vaijanath.aiagent.springboot.web;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import dev.vaijanath.aiagent.model.ToolCall;
import dev.vaijanath.aiagent.tool.ToolResult;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class SseAgentObserverTest {

    @Test
    void forwardsToolLifecycleAndSwallowsAfterComplete() {
        SseEmitter emitter = new SseEmitter();
        SseAgentObserver observer = new SseAgentObserver(emitter);
        assertDoesNotThrow(() -> {
            // Before completion the emitter buffers events; nothing escapes.
            observer.onToolCall(new ToolCall("c1", "lookup", "{}"));
            observer.onToolResult("lookup", ToolResult.ok("raw"));
            observer.onToolData("lookup", "{\"rows\":3}");
            // After completion further sends are swallowed rather than thrown.
            emitter.complete();
            observer.onToolCall(new ToolCall("c2", "calc", "{}"));
            observer.onToolResult("calc", ToolResult.error("boom"));
            observer.onToolData("calc", "{\"x\":1}");
        });
    }
}
