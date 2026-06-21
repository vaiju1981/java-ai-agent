package dev.vaijanath.aiagent.langchain4j;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.vaijanath.aiagent.model.Message;
import dev.vaijanath.aiagent.model.ModelPort;
import dev.vaijanath.aiagent.model.ModelRequest;
import dev.vaijanath.aiagent.model.ModelResponse;
import dev.vaijanath.aiagent.model.ToolCall;
import dev.vaijanath.aiagent.tool.ToolSpec;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The first reference L0 adapter: a {@link ModelPort} backed by any LangChain4j {@link ChatModel}
 * (OpenAI, Ollama/local, etc.), with tool-calling support.
 *
 * <p>The agent's neutral types are translated to/from LangChain4j's: tool specs go out as
 * {@link ToolSpecification}s, the model's tool requests come back as {@link ToolCall}s, and tool
 * results are replayed as {@link ToolExecutionResultMessage}s.
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
        List<ChatMessage> messages = new ArrayList<>();
        for (Message m : request.messages()) {
            messages.add(toLangChain4j(m));
        }

        ChatRequest.Builder builder = ChatRequest.builder().messages(messages);
        if (!request.tools().isEmpty()) {
            builder.toolSpecifications(toToolSpecifications(request.tools()));
        }

        ChatResponse response = chatModel.chat(builder.build());
        AiMessage ai = response.aiMessage();

        if (ai.hasToolExecutionRequests()) {
            List<ToolCall> calls = new ArrayList<>();
            for (ToolExecutionRequest req : ai.toolExecutionRequests()) {
                calls.add(new ToolCall(req.id(), req.name(), req.arguments()));
            }
            return new ModelResponse(ai.text(), calls);
        }

        String text = ai.text();
        return ModelResponse.text(text == null ? "" : text);
    }

    private static List<ToolSpecification> toToolSpecifications(List<ToolSpec> specs) {
        List<ToolSpecification> out = new ArrayList<>();
        for (ToolSpec s : specs) {
            out.add(ToolSpecification.builder()
                    .name(s.name())
                    .description(s.description())
                    .parameters(SchemaConverter.toJsonObjectSchema(s.parametersJsonSchema()))
                    .build());
        }
        return out;
    }

    private static ChatMessage toLangChain4j(Message m) {
        return switch (m.role()) {
            case SYSTEM -> SystemMessage.from(m.content());
            case USER -> UserMessage.from(m.content());
            case ASSISTANT -> m.hasToolCalls()
                    ? AiMessage.from(toExecutionRequests(m.toolCalls()))
                    : AiMessage.from(m.content());
            case TOOL -> ToolExecutionResultMessage.from(m.toolCallId(), m.toolName(), m.content());
        };
    }

    private static List<ToolExecutionRequest> toExecutionRequests(List<ToolCall> calls) {
        List<ToolExecutionRequest> out = new ArrayList<>();
        for (ToolCall c : calls) {
            out.add(ToolExecutionRequest.builder()
                    .id(c.id())
                    .name(c.name())
                    .arguments(c.argumentsJson())
                    .build());
        }
        return out;
    }

    @Override
    public String name() {
        return name;
    }
}
