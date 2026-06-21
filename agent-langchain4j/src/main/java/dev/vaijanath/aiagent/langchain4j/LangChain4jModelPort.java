package dev.vaijanath.aiagent.langchain4j;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;
import dev.vaijanath.aiagent.model.ModelPort;
import dev.vaijanath.aiagent.model.ModelRequest;
import dev.vaijanath.aiagent.model.ModelResponse;
import dev.vaijanath.aiagent.model.ToolCall;
import dev.vaijanath.aiagent.model.Usage;
import dev.vaijanath.aiagent.tool.ToolSpec;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * The first reference L0 adapter: a {@link ModelPort} backed by any LangChain4j {@link ChatModel}
 * (OpenAI, Ollama/local, etc.), with tool-calling. Message conversion is shared via
 * {@link LangChain4jMessages}.
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
        ChatRequest.Builder builder = ChatRequest.builder()
                .messages(LangChain4jMessages.toLangChain4j(request.messages()));
        if (!request.tools().isEmpty()) {
            builder.toolSpecifications(toToolSpecifications(request.tools()));
        }

        ChatResponse response = chatModel.chat(builder.build());
        AiMessage ai = response.aiMessage();
        Usage usage = toUsage(response.tokenUsage());

        if (ai.hasToolExecutionRequests()) {
            List<ToolCall> calls = new ArrayList<>();
            for (ToolExecutionRequest req : ai.toolExecutionRequests()) {
                calls.add(new ToolCall(req.id(), req.name(), req.arguments()));
            }
            return new ModelResponse(ai.text(), calls, usage);
        }
        String text = ai.text();
        return new ModelResponse(text == null ? "" : text, List.of(), usage);
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

    private static Usage toUsage(TokenUsage tokenUsage) {
        if (tokenUsage == null) {
            return Usage.UNKNOWN;
        }
        Integer in = tokenUsage.inputTokenCount();
        Integer out = tokenUsage.outputTokenCount();
        return new Usage(in == null ? 0 : in, out == null ? 0 : out);
    }

    @Override
    public String name() {
        return name;
    }
}
