package dev.vaijanath.aiagent.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import org.junit.jupiter.api.Test;

class StreamingTest {

    @Test
    void streamingPortEmitsTokens() {
        StreamingModelPort port = (request, onToken) -> {
            onToken.accept("hel");
            onToken.accept("lo");
            return ModelResponse.text("hello");
        };

        StringBuilder seen = new StringBuilder();
        ModelResponse r = ModelPorts.stream(port, ModelRequest.of(List.of(Message.user("x"))), seen::append);

        assertEquals("hello", r.text());
        assertEquals("hello", seen.toString());
    }

    @Test
    void nonStreamingPortFallsBackToWholeReply() {
        ModelPort plain = request -> ModelResponse.text("whole reply");

        StringBuilder seen = new StringBuilder();
        ModelPorts.stream(plain, ModelRequest.of(List.of(Message.user("x"))), seen::append);

        assertEquals("whole reply", seen.toString());
    }
}
