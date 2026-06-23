package dev.vaijanath.aiagent.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vaijanath.aiagent.model.Message;
import dev.vaijanath.aiagent.model.ModelPort;
import dev.vaijanath.aiagent.model.ModelRequest;
import dev.vaijanath.aiagent.model.ModelResponse;
import dev.vaijanath.aiagent.model.Role;
import dev.vaijanath.aiagent.model.ToolCall;
import dev.vaijanath.aiagent.observe.AgentObserver;
import dev.vaijanath.aiagent.tool.ApprovalRequest;
import dev.vaijanath.aiagent.tool.ContextualTool;
import dev.vaijanath.aiagent.tool.ToolEffect;
import dev.vaijanath.aiagent.tool.ToolInvocation;
import dev.vaijanath.aiagent.tool.ToolResult;
import dev.vaijanath.aiagent.tool.ToolSpec;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/** Human-in-the-loop approval of effectful tools via the {@code ApprovalHandler} seam. */
class HitlApprovalTest {

    /** An effectful tool that records whether it actually ran. */
    private static final class WriteTool implements ContextualTool {
        final AtomicBoolean ran = new AtomicBoolean(false);

        @Override
        public ToolSpec spec() {
            return new ToolSpec(
                    "set_goal", "sets a goal", "{\"type\":\"object\",\"properties\":{}}", ToolEffect.EFFECTFUL);
        }

        @Override
        public ToolResult invoke(ToolInvocation invocation) {
            ran.set(true);
            return ToolResult.ok("goal set");
        }
    }

    /** First reply calls the named tool; the second answers from the last TOOL message. */
    private static ModelPort callsThenAnswers(String toolName) {
        return new ModelPort() {
            private int calls = 0;

            @Override
            public ModelResponse chat(ModelRequest request) {
                if (++calls == 1) {
                    return new ModelResponse("", List.of(new ToolCall("c1", toolName, "{}")));
                }
                String last = request.messages().stream()
                        .filter(m -> m.role() == Role.TOOL)
                        .reduce((a, b) -> b)
                        .map(Message::content)
                        .orElse("(none)");
                return ModelResponse.text("done: " + last);
            }
        };
    }

    @Test
    void effectfulToolRunsWhenApproved() {
        WriteTool tool = new WriteTool();
        AtomicReference<String> requested = new AtomicReference<>();
        AgentObserver observer = new AgentObserver() {
            @Override
            public void onApprovalRequired(ApprovalRequest request) {
                requested.set(request.call().name());
            }
        };

        AgentResponse r = DefaultAgent.builder()
                .model(callsThenAnswers("set_goal"))
                .tool(tool)
                .approvalHandler(request -> true)
                .observer(observer)
                .build()
                .run(new AgentRequest("set my goal"));

        assertTrue(tool.ran.get(), "an approved effectful tool runs");
        assertEquals("set_goal", requested.get(), "the observer is notified of the approval request");
        assertTrue(r.output().contains("goal set"), "got: " + r.output());
    }

    @Test
    void effectfulToolDeclinedWhenRejected() {
        WriteTool tool = new WriteTool();

        AgentResponse r = DefaultAgent.builder()
                .model(callsThenAnswers("set_goal"))
                .tool(tool)
                .approvalHandler(request -> false)
                .build()
                .run(new AgentRequest("set my goal"));

        assertFalse(tool.ran.get(), "a rejected effectful tool must not run");
        assertEquals("completed", r.stopReason());
        assertTrue(r.output().contains("declined"), "the model is told the user declined: " + r.output());
    }

    @Test
    void withoutAHandlerEffectfulToolIsHardDenied() {
        WriteTool tool = new WriteTool();

        DefaultAgent.builder()
                .model(callsThenAnswers("set_goal"))
                .tool(tool)
                .build() // no approval handler -> denyEffectful hard-denies
                .run(new AgentRequest("set my goal"));

        assertFalse(tool.ran.get(), "without an approval handler an effectful tool is denied, not escalated");
    }
}
