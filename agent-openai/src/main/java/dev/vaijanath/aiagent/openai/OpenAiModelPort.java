package dev.vaijanath.aiagent.openai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openai.client.OpenAIClient;
import com.openai.client.okhttp.OpenAIOkHttpClient;
import com.openai.core.JsonValue;
import com.openai.models.FunctionDefinition;
import com.openai.models.FunctionParameters;
import com.openai.models.chat.completions.ChatCompletion;
import com.openai.models.chat.completions.ChatCompletionAssistantMessageParam;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import com.openai.models.chat.completions.ChatCompletionFunctionTool;
import com.openai.models.chat.completions.ChatCompletionMessageFunctionToolCall;
import com.openai.models.chat.completions.ChatCompletionMessageParam;
import com.openai.models.chat.completions.ChatCompletionMessageToolCall;
import com.openai.models.chat.completions.ChatCompletionSystemMessageParam;
import com.openai.models.chat.completions.ChatCompletionTool;
import com.openai.models.chat.completions.ChatCompletionToolMessageParam;
import com.openai.models.chat.completions.ChatCompletionUserMessageParam;
import dev.vaijanath.aiagent.model.Message;
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
 * A {@link ModelPort} backed directly by the official OpenAI Java SDK — a first-party L0 substrate
 * with no intermediary framework, alongside the LangChain4j, Spring AI, and Anthropic adapters.
 *
 * <p>Our minimal message/tool model is translated to the Chat Completions API and back: each role maps
 * to the matching message param, assistant tool calls and their results round-trip as {@code tool_calls}
 * / tool messages, so the agent runtime keeps full control of the loop while OpenAI only proposes tool
 * calls.
 *
 * <p>Defaults to {@value #DEFAULT_MODEL}; pass a different model id to the constructor. The model is
 * sent as a string so newer ids work without waiting for an SDK enum constant.
 */
public final class OpenAiModelPort implements ModelPort {

    /** A widely available default; override via the constructor for a different one. */
    public static final String DEFAULT_MODEL = "gpt-4o";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final OpenAIClient client;
    private final String model;

    public OpenAiModelPort(OpenAIClient client, String model) {
        this.client = Objects.requireNonNull(client, "client");
        this.model = Objects.requireNonNull(model, "model");
    }

    /** Builds a port from {@code OPENAI_API_KEY} in the environment, using the default model. */
    public static OpenAiModelPort fromEnv() {
        return fromEnv(DEFAULT_MODEL);
    }

    public static OpenAiModelPort fromEnv(String model) {
        return new OpenAiModelPort(OpenAIOkHttpClient.fromEnv(), model);
    }

    @Override
    public ModelResponse chat(ModelRequest request) {
        return toModelResponse(client.chat().completions().create(toParams(request, model)));
    }

    @Override
    public String name() {
        return "openai:" + model;
    }

    // ---- request mapping (package-private statics, unit-tested without a live client) ----

    static ChatCompletionCreateParams toParams(ModelRequest request, String model) {
        ChatCompletionCreateParams.Builder builder = ChatCompletionCreateParams.builder().model(model);
        for (Message m : request.messages()) {
            switch (m.role()) {
                case SYSTEM -> builder.addMessage(ChatCompletionMessageParam.ofSystem(
                        ChatCompletionSystemMessageParam.builder().content(m.content()).build()));
                case USER -> builder.addMessage(ChatCompletionMessageParam.ofUser(
                        ChatCompletionUserMessageParam.builder().content(m.content()).build()));
                case ASSISTANT -> builder.addMessage(ChatCompletionMessageParam.ofAssistant(assistant(m)));
                case TOOL -> builder.addMessage(ChatCompletionMessageParam.ofTool(
                        ChatCompletionToolMessageParam.builder()
                                .toolCallId(m.toolCallId() == null ? "" : m.toolCallId())
                                .content(m.content())
                                .build()));
            }
        }
        for (ToolSpec spec : request.tools()) {
            builder.addTool(toTool(spec));
        }
        return builder.build();
    }

    private static ChatCompletionAssistantMessageParam assistant(Message m) {
        ChatCompletionAssistantMessageParam.Builder b = ChatCompletionAssistantMessageParam.builder();
        if (!m.content().isBlank()) {
            b.content(m.content());
        }
        if (m.hasToolCalls()) {
            List<ChatCompletionMessageToolCall> calls = new ArrayList<>();
            for (ToolCall c : m.toolCalls()) {
                calls.add(ChatCompletionMessageToolCall.ofFunction(
                        ChatCompletionMessageFunctionToolCall.builder()
                                .id(c.id() == null ? "" : c.id())
                                .function(ChatCompletionMessageFunctionToolCall.Function.builder()
                                        .name(c.name())
                                        .arguments(c.argumentsJson())
                                        .build())
                                .build()));
            }
            b.toolCalls(calls);
        }
        return b.build();
    }

    static ChatCompletionTool toTool(ToolSpec spec) {
        JsonNode schema = readTree(spec.parametersJsonSchema());
        FunctionParameters.Builder params = FunctionParameters.builder();
        schema.fields().forEachRemaining(
                e -> params.putAdditionalProperty(e.getKey(), JsonValue.fromJsonNode(e.getValue())));
        return ChatCompletionTool.ofFunction(ChatCompletionFunctionTool.builder()
                .function(FunctionDefinition.builder()
                        .name(spec.name())
                        .description(spec.description())
                        .parameters(params.build())
                        .build())
                .build());
    }

    // ---- response mapping ----

    static ModelResponse toModelResponse(ChatCompletion completion) {
        StringBuilder text = new StringBuilder();
        List<ToolCall> calls = new ArrayList<>();
        if (!completion.choices().isEmpty()) {
            var message = completion.choices().get(0).message();
            message.content().ifPresent(text::append);
            message.toolCalls().ifPresent(list -> {
                for (ChatCompletionMessageToolCall tc : list) {
                    tc.function().ifPresent(f -> calls.add(
                            new ToolCall(f.id(), f.function().name(), f.function().arguments())));
                }
            });
        }
        Usage usage = completion.usage()
                .map(u -> new Usage(u.promptTokens(), u.completionTokens()))
                .orElse(Usage.UNKNOWN);
        return new ModelResponse(text.toString(), calls, usage);
    }

    private static JsonNode readTree(String json) {
        try {
            return MAPPER.readTree(json == null || json.isBlank() ? "{}" : json);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("invalid JSON arguments/schema");
        }
    }
}
