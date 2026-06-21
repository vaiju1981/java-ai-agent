package dev.vaijanath.aiagent.observe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import dev.vaijanath.aiagent.model.ModelResponse;
import dev.vaijanath.aiagent.model.Usage;
import dev.vaijanath.aiagent.tool.ToolResult;
import org.junit.jupiter.api.Test;

class RedactingObserverTest {

    @Test
    void redactsContentButKeepsMeteringMetadata() {
        ModelResponse[] seenResponse = new ModelResponse[1];
        ToolResult[] seenToolResult = new ToolResult[1];
        boolean[] sawToken = {false};

        AgentObserver capture = new AgentObserver() {
            @Override
            public void onModelResponse(ModelResponse response) {
                seenResponse[0] = response;
            }

            @Override
            public void onToolResult(String toolName, ToolResult result) {
                seenToolResult[0] = result;
            }

            @Override
            public void onToken(String token) {
                sawToken[0] = true;
            }
        };
        RedactingObserver redactor = new RedactingObserver(capture);

        redactor.onModelResponse(ModelResponse.text("the secret answer", new Usage(10, 20)));
        redactor.onToolResult("search", ToolResult.error("sensitive failure detail"));
        redactor.onToken("secret");

        assertEquals("[redacted]", seenResponse[0].text(), "model text must be redacted");
        assertEquals(new Usage(10, 20), seenResponse[0].usage(), "usage must survive for metering");
        assertEquals("[redacted]", seenToolResult[0].content(), "tool result content must be redacted");
        assertEquals(true, seenToolResult[0].error(), "the error flag must survive");
        assertFalse(sawToken[0], "raw tokens must be dropped");
    }

    @Test
    void errorDoesNotLeakMessageCauseOrStackTrace() {
        Throwable[] seen = new Throwable[1];
        AgentObserver capture = new AgentObserver() {
            @Override
            public void onError(String stage, Throwable error) {
                seen[0] = error;
            }
        };

        new RedactingObserver(capture)
                .onError("model", new IllegalStateException("password=very-secret"));

        assertEquals("redacted IllegalStateException", seen[0].getMessage());
        assertNull(seen[0].getCause());
        assertEquals(0, seen[0].getStackTrace().length);
    }
}
