package dev.vaijanath.aiagent.springai;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.vaijanath.aiagent.model.Message;
import dev.vaijanath.aiagent.model.ModelRequest;
import dev.vaijanath.aiagent.model.ModelResponse;
import java.util.List;
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
}
