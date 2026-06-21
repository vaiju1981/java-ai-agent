package dev.vaijanath.aiagent.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.vaijanath.aiagent.model.ModelResponse;
import dev.vaijanath.aiagent.model.StreamingModelPort;
import dev.vaijanath.aiagent.observe.AgentObserver;
import org.junit.jupiter.api.Test;

class LoopStreamingTest {

    @Test
    void streamsTokensThroughTheLoop() {
        StreamingModelPort model = (request, onToken) -> {
            onToken.accept("Hel");
            onToken.accept("lo");
            return ModelResponse.text("Hello");
        };
        StringBuilder streamed = new StringBuilder();
        AgentObserver capture = new AgentObserver() {
            @Override
            public void onToken(String token) {
                streamed.append(token);
            }
        };

        AgentResponse r = DefaultAgent.builder().model(model).observer(capture).build()
                .run(new AgentRequest("hi"));

        assertEquals("Hello", r.output());
        assertEquals("Hello", streamed.toString());
    }
}
