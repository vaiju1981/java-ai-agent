package dev.vaijanath.aiagent.anthropic;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.core.JsonValue;
import com.anthropic.models.messages.Base64ImageSource;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.ImageBlockParam;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.TextBlockParam;
import com.anthropic.models.messages.Tool;
import com.anthropic.models.messages.ToolResultBlockParam;
import com.anthropic.models.messages.ToolUseBlockParam;
import com.anthropic.models.messages.UrlImageSource;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.vaijanath.aiagent.model.Media;
import dev.vaijanath.aiagent.model.Message;
import dev.vaijanath.aiagent.model.ModelPort;
import dev.vaijanath.aiagent.model.ModelRequest;
import dev.vaijanath.aiagent.model.ModelResponse;
import dev.vaijanath.aiagent.model.Role;
import dev.vaijanath.aiagent.model.ToolCall;
import dev.vaijanath.aiagent.model.Usage;
import dev.vaijanath.aiagent.tool.ToolSpec;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A {@link ModelPort} backed directly by the official Anthropic Java SDK — a first-party L0 substrate
 * with no intermediary framework, alongside the LangChain4j and Spring AI adapters.
 *
 * <p>Our minimal message/tool model is translated to the Anthropic Messages API and back: system
 * turns are folded into the request's {@code system} prompt; assistant tool calls and their results
 * round-trip as {@code tool_use} / {@code tool_result} content blocks, so the agent runtime keeps full
 * control of the loop while Anthropic only proposes tool calls.
 *
 * <p>Defaults to the latest Claude model; pass a different model id to the constructor. The model is
 * sent as a string so newer ids work without waiting for an SDK enum constant.
 */
public final class AnthropicModelPort implements ModelPort {

    /** Anthropic's current flagship model; override via the constructor for a different one. */
    public static final String DEFAULT_MODEL = "claude-opus-4-8";

    private static final long DEFAULT_MAX_TOKENS = 4096;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final AnthropicClient client;
    private final String model;
    private final long maxTokens;

    public AnthropicModelPort(AnthropicClient client, String model, long maxTokens) {
        this.client = Objects.requireNonNull(client, "client");
        this.model = Objects.requireNonNull(model, "model");
        this.maxTokens = maxTokens;
    }

    /** Builds a port from {@code ANTHROPIC_API_KEY} in the environment, using the default model. */
    public static AnthropicModelPort fromEnv() {
        return fromEnv(DEFAULT_MODEL);
    }

    public static AnthropicModelPort fromEnv(String model) {
        requireApiKey(System.getenv("ANTHROPIC_API_KEY"), "ANTHROPIC_API_KEY");
        return new AnthropicModelPort(AnthropicOkHttpClient.fromEnv(), model, DEFAULT_MAX_TOKENS);
    }

    static void requireApiKey(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " is not set — export it, or build the port with the "
                    + "AnthropicModelPort(client, model) constructor and a pre-configured client.");
        }
    }

    @Override
    public ModelResponse chat(ModelRequest request) {
        return toModelResponse(client.messages().create(toParams(request, model, maxTokens)));
    }

    @Override
    public String name() {
        return "anthropic:" + model;
    }

    // ---- request mapping (package-private statics, unit-tested without a live client) ----

    static MessageCreateParams toParams(ModelRequest request, String model, long maxTokens) {
        MessageCreateParams.Builder builder =
                MessageCreateParams.builder().model(model).maxTokens(maxTokens);

        // Anthropic carries the system prompt as a top-level field, not a message role.
        String system = request.messages().stream()
                .filter(m -> m.role() == Role.SYSTEM)
                .map(Message::content)
                .filter(s -> !s.isBlank())
                .reduce((a, b) -> a + "\n\n" + b)
                .orElse("");
        if (!system.isBlank()) {
            builder.system(system);
        }

        for (Message m : request.messages()) {
            switch (m.role()) {
                case SYSTEM -> { /* folded into the system prompt above */ }
                case USER -> {
                    if (m.hasMedia()) {
                        builder.addUserMessageOfBlockParams(userBlocks(m));
                    } else {
                        builder.addUserMessage(m.content());
                    }
                }
                case ASSISTANT -> {
                    if (m.hasToolCalls()) {
                        builder.addAssistantMessageOfBlockParams(assistantBlocks(m));
                    } else {
                        builder.addAssistantMessage(m.content());
                    }
                }
                case TOOL -> builder.addUserMessageOfBlockParams(List.of(
                        ContentBlockParam.ofToolResult(ToolResultBlockParam.builder()
                                .toolUseId(m.toolCallId() == null ? "" : m.toolCallId())
                                .content(m.content())
                                .build())));
            }
        }

        for (ToolSpec spec : request.tools()) {
            builder.addTool(toTool(spec));
        }
        return builder.build();
    }

    private static List<ContentBlockParam> userBlocks(Message m) {
        List<ContentBlockParam> blocks = new ArrayList<>();
        if (!m.content().isBlank()) {
            blocks.add(ContentBlockParam.ofText(TextBlockParam.builder().text(m.content()).build()));
        }
        for (Media media : m.media()) {
            if (media.kind() != Media.Kind.IMAGE) {
                continue; // the Messages API takes images here; audio is carried but not sent
            }
            ImageBlockParam.Source source = media.isUrl()
                    ? ImageBlockParam.Source.ofUrl(UrlImageSource.builder().url(media.url()).build())
                    : ImageBlockParam.Source.ofBase64(Base64ImageSource.builder()
                            .mediaType(Base64ImageSource.MediaType.of(media.mimeType()))
                            .data(media.base64Data())
                            .build());
            blocks.add(ContentBlockParam.ofImage(ImageBlockParam.builder().source(source).build()));
        }
        return blocks;
    }

    private static List<ContentBlockParam> assistantBlocks(Message m) {
        List<ContentBlockParam> blocks = new ArrayList<>();
        if (!m.content().isBlank()) {
            blocks.add(ContentBlockParam.ofText(TextBlockParam.builder().text(m.content()).build()));
        }
        for (ToolCall call : m.toolCalls()) {
            ToolUseBlockParam.Input.Builder input = ToolUseBlockParam.Input.builder();
            readTree(call.argumentsJson())
                    .fields()
                    .forEachRemaining(e -> input.putAdditionalProperty(e.getKey(), JsonValue.fromJsonNode(e.getValue())));
            blocks.add(ContentBlockParam.ofToolUse(ToolUseBlockParam.builder()
                    .id(call.id() == null ? "" : call.id())
                    .name(call.name())
                    .input(input.build())
                    .build()));
        }
        return blocks;
    }

    static Tool toTool(ToolSpec spec) {
        JsonNode schema = readTree(spec.parametersJsonSchema());
        Tool.InputSchema.Builder inputSchema = Tool.InputSchema.builder();

        JsonNode properties = schema.get("properties");
        if (properties != null && properties.isObject()) {
            Tool.InputSchema.Properties.Builder props = Tool.InputSchema.Properties.builder();
            properties.fields()
                    .forEachRemaining(e -> props.putAdditionalProperty(e.getKey(), JsonValue.fromJsonNode(e.getValue())));
            inputSchema.properties(props.build());
        }
        JsonNode required = schema.get("required");
        if (required != null && required.isArray()) {
            List<String> names = new ArrayList<>();
            required.forEach(n -> names.add(n.asText()));
            inputSchema.required(names);
        }
        return Tool.builder()
                .name(spec.name())
                .description(spec.description())
                .inputSchema(inputSchema.build())
                .build();
    }

    // ---- response mapping ----

    static ModelResponse toModelResponse(com.anthropic.models.messages.Message message) {
        StringBuilder text = new StringBuilder();
        List<ToolCall> calls = new ArrayList<>();
        for (ContentBlock block : message.content()) {
            block.text().ifPresent(t -> text.append(t.text()));
            block.toolUse().ifPresent(tu -> {
                JsonNode input = tu._input().convert(JsonNode.class);
                calls.add(new ToolCall(tu.id(), tu.name(), input == null ? "{}" : input.toString()));
            });
        }
        Usage usage = new Usage(message.usage().inputTokens(), message.usage().outputTokens());
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
