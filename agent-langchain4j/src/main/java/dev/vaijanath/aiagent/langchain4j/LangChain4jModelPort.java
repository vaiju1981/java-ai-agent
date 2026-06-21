package dev.vaijanath.aiagent.langchain4j;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.vaijanath.aiagent.model.Message;
import dev.vaijanath.aiagent.model.ModelPort;
import dev.vaijanath.aiagent.model.ModelRequest;
import dev.vaijanath.aiagent.model.ModelResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The first reference L0 adapter: a {@link ModelPort} backed by any LangChain4j {@link ChatModel}
 * (OpenAI, Ollama/local, etc.).
 *
 * <p>Phase 0 is text-only — the conversation is forwarded and the model's text reply is returned.
 * Tool-calling through this port lands in Phase 1.
 */
public final class LangChain4jModelPort implements ModelPort {

    private final ChatModel chatModel;
    private final String name;

    public LangChain4jModelPort(ChatModel chatModel) {
        this(chatModel, "langchain4j");
    }

    public LangChain4jModelPort(ChatModel chatModel, String name) {
        this.chatModel = Objects.requireNonNull(chatModel, "chatModel");
        this.name = name;
    }

    @Override
    public ModelResponse chat(ModelRequest request) {
        List<ChatMessage> lcMessages = new ArrayList<>();
        for (Message m : request.messages()) {
            lcMessages.add(toLangChain4j(m));
        }
        ChatResponse response = chatModel.chat(lcMessages);
        String text = response.aiMessage().text();
        return ModelResponse.text(text == null ? "" : text);
    }

    private static ChatMessage toLangChain4j(Message m) {
        return switch (m.role()) {
            case SYSTEM -> SystemMessage.from(m.content());
            case USER -> UserMessage.from(m.content());
            case ASSISTANT -> AiMessage.from(m.content());
            // No tool calls in Phase 0; surface tool text as a user-visible observation.
            case TOOL -> UserMessage.from("[tool result] " + m.content());
        };
    }

    @Override
    public String name() {
        return name;
    }
}
