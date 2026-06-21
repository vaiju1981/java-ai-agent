package dev.vaijanath.aiagent.springai;

import static org.junit.jupiter.api.Assertions.assertEquals;

import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vaijanath.aiagent.model.Message;
import dev.vaijanath.aiagent.model.ModelRequest;
import dev.vaijanath.aiagent.model.ModelResponse;
import dev.vaijanath.aiagent.tool.ToolSpec;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

class SpringAiModelPortTest {

    @Test
    void mapsChatModelTextThroughThePort() {
        ChatModel fake = new ChatModel() {
            @Override
            public ChatResponse call(Prompt prompt) {
                return new ChatResponse(List.of(new Generation(new AssistantMessage("hello from spring ai"))));
            }

            @Override
            public Flux<ChatResponse> stream(Prompt prompt) {
                return Flux.just(call(prompt));
            }
        };

        ModelResponse r =
                new SpringAiModelPort(fake).chat(ModelRequest.of(List.of(Message.user("hi"))));

        assertEquals("hello from spring ai", r.text());
    }

    @Test
    void parsesToolCallsBackFromSpringAi() {
        AssistantMessage withToolCall = new AssistantMessage("", Map.of(),
                List.of(new AssistantMessage.ToolCall("c1", "function", "calc", "{\"a\":1}")));
        ChatModel fake = new ChatModel() {
            @Override
            public ChatResponse call(Prompt prompt) {
                return new ChatResponse(List.of(new Generation(withToolCall)));
            }

            @Override
            public Flux<ChatResponse> stream(Prompt prompt) {
                return Flux.just(call(prompt));
            }
        };

        ModelResponse r = new SpringAiModelPort(fake).chat(new ModelRequest(
                List.of(Message.user("calc")),
                List.of(new ToolSpec("calc", "calculator", "{\"type\":\"object\"}"))));

        assertTrue(r.hasToolCalls());
        assertEquals("calc", r.toolCalls().get(0).name());
        assertEquals("{\"a\":1}", r.toolCalls().get(0).argumentsJson());
    }
}
