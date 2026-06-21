package dev.vaijanath.aiagent.model;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.vaijanath.aiagent.observe.AgentObserver;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class ObservingModelPortTest {

    @Test
    void emitsModelCallAndResponseEvents() {
        AtomicInteger calls = new AtomicInteger();
        AtomicInteger responses = new AtomicInteger();
        AgentObserver observer = new AgentObserver() {
            @Override
            public void onModelCall(ModelRequest request) {
                calls.incrementAndGet();
            }

            @Override
            public void onModelResponse(ModelResponse response) {
                responses.incrementAndGet();
            }
        };
        ModelPort base = request -> ModelResponse.text("ok");

        new ObservingModelPort(base, List.of(observer))
                .chat(ModelRequest.of(List.of(Message.user("x"))));

        assertEquals(1, calls.get());
        assertEquals(1, responses.get());
    }
}
