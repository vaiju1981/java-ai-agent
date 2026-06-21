package dev.vaijanath.aiagent.springai;

import dev.vaijanath.aiagent.model.ModelPort;
import dev.vaijanath.aiagent.model.ModelRequest;
import dev.vaijanath.aiagent.model.ModelResponse;
import dev.vaijanath.aiagent.model.Usage;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;

/**
 * A {@link ModelPort} backed by any Spring AI {@link ChatModel} (OpenAI, Ollama, Azure, etc.) —
 * a second L0 substrate alongside LangChain4j, proving the runtime is vendor-neutral.
 *
 * <p>Phase 6 is text-only (plus token usage); tool-calling parity with the LangChain4j adapter is a
 * follow-up.
 */
public final class SpringAiModelPort implements ModelPort {

    private final ChatModel chatModel;
    private final String name;

    public SpringAiModelPort(ChatModel chatModel) {
        this(chatModel, "spring-ai");
    }

    public SpringAiModelPort(ChatModel chatModel, String name) {
        this.chatModel = Objects.requireNonNull(chatModel, "chatModel");
        this.name = name;
    }

    @Override
    public ModelResponse chat(ModelRequest request) {
        List<org.springframework.ai.chat.messages.Message> messages = new ArrayList<>();
        for (dev.vaijanath.aiagent.model.Message m : request.messages()) {
            messages.add(toSpring(m));
        }
        ChatResponse response = chatModel.call(new Prompt(messages));
        String text = (response.getResult() != null && response.getResult().getOutput() != null)
                ? response.getResult().getOutput().getText()
                : "";
        return new ModelResponse(text == null ? "" : text, List.of(), toUsage(response));
    }

    private static org.springframework.ai.chat.messages.Message toSpring(
            dev.vaijanath.aiagent.model.Message m) {
        return switch (m.role()) {
            case SYSTEM -> new SystemMessage(m.content());
            case USER -> new UserMessage(m.content());
            case ASSISTANT -> new AssistantMessage(m.content());
            case TOOL -> new UserMessage("[tool result] " + m.content());
        };
    }

    private static Usage toUsage(ChatResponse response) {
        try {
            if (response.getMetadata() == null || response.getMetadata().getUsage() == null) {
                return Usage.UNKNOWN;
            }
            org.springframework.ai.chat.metadata.Usage u = response.getMetadata().getUsage();
            Number in = u.getPromptTokens();
            Number out = u.getCompletionTokens();
            return new Usage(in == null ? 0 : in.longValue(), out == null ? 0 : out.longValue());
        } catch (RuntimeException e) {
            return Usage.UNKNOWN;
        }
    }

    @Override
    public String name() {
        return name;
    }
}
