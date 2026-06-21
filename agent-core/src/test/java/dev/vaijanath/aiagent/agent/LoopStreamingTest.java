package dev.vaijanath.aiagent.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vaijanath.aiagent.guardrail.KeywordBlocklistGuardrail;
import dev.vaijanath.aiagent.model.ModelResponse;
import dev.vaijanath.aiagent.model.StreamingModelPort;
import dev.vaijanath.aiagent.observe.AgentObserver;
import java.util.List;
import org.junit.jupiter.api.Test;

class LoopStreamingTest {

    private static final StreamingModelPort STREAMS = (request, onToken) -> {
        onToken.accept("Hel");
        onToken.accept("lo");
        return ModelResponse.text("Hello");
    };

    private static AgentObserver capturing(StringBuilder sink) {
        return new AgentObserver() {
            @Override
            public void onToken(String token) {
                sink.append(token);
            }
        };
    }

    @Test
    void rawTokensAreNotStreamedByDefault() {
        StringBuilder streamed = new StringBuilder();
        AgentResponse r = DefaultAgent.builder().model(STREAMS).observer(capturing(streamed)).build()
                .run(new AgentRequest("hi"));

        assertEquals("Hello", r.output());
        assertEquals("", streamed.toString(), "raw pre-guardrail tokens must not stream by default");
    }

    @Test
    void rawTokensStreamWhenExplicitlyEnabled() {
        StringBuilder streamed = new StringBuilder();
        AgentResponse r = DefaultAgent.builder().model(STREAMS).observer(capturing(streamed))
                .streamRawTokens(true).build()
                .run(new AgentRequest("hi"));

        assertEquals("Hello", r.output());
        assertEquals("Hello", streamed.toString());
    }

    @Test
    void blockedOutputDoesNotLeakThroughTheRawStream() {
        StringBuilder streamed = new StringBuilder();
        StreamingModelPort leaks = (request, onToken) -> {
            onToken.accept("secret data");
            return ModelResponse.text("secret data");
        };

        AgentResponse r = DefaultAgent.builder()
                .model(leaks)
                .guardrail(new KeywordBlocklistGuardrail(List.of("secret"), "blocked"))
                .observer(capturing(streamed))
                .build()
                .run(new AgentRequest("hi"));

        assertTrue(r.blocked());
        assertEquals("", streamed.toString(), "unsafe content must not escape via the raw token stream");
    }
}
