package dev.vaijanath.aiagent.springai;

import dev.vaijanath.aiagent.model.ModelPort;
import dev.vaijanath.aiagent.model.ModelRequest;
import dev.vaijanath.aiagent.model.ModelResponse;
import dev.vaijanath.aiagent.model.ToolCall;
import dev.vaijanath.aiagent.model.Usage;
import dev.vaijanath.aiagent.tool.ToolSpec;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

/**
 * A {@link ModelPort} backed by any Spring AI {@link ChatModel} — a second L0 substrate alongside
 * LangChain4j, with tool-calling. Tools are passed as definitions with Spring AI's internal tool
 * execution <b>disabled</b>, so the model only requests tools and <i>our</i> runtime executes them
 * (honoring guardrails and the {@code ToolApprover}).
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

        Prompt prompt;
        if (request.tools().isEmpty()) {
            prompt = new Prompt(messages);
        } else {
            List<ToolCallback> callbacks = request.tools().stream()
                    .map(SpringAiModelPort::toCallback)
                    .toList();
            ToolCallingChatOptions options = ToolCallingChatOptions.builder()
                    .toolCallbacks(callbacks)
                    .internalToolExecutionEnabled(false) // the agent runtime executes tools, not Spring AI
                    .build();
            prompt = new Prompt(messages, options);
        }

        ChatResponse response = chatModel.call(prompt);
        AssistantMessage ai = response.getResult().getOutput();
        Usage usage = toUsage(response);

        if (ai.hasToolCalls()) {
            List<ToolCall> calls = new ArrayList<>();
            for (AssistantMessage.ToolCall tc : ai.getToolCalls()) {
                calls.add(new ToolCall(tc.id(), tc.name(), tc.arguments()));
            }
            return new ModelResponse(ai.getText(), calls, usage);
        }
        String text = ai.getText();
        return new ModelResponse(text == null ? "" : text, List.of(), usage);
    }

    private static org.springframework.ai.chat.messages.Message toSpring(
            dev.vaijanath.aiagent.model.Message m) {
        return switch (m.role()) {
            case SYSTEM -> new SystemMessage(m.content());
            case USER -> new UserMessage(m.content());
            case ASSISTANT -> m.hasToolCalls()
                    ? new AssistantMessage(m.content(), Map.of(), toSpringToolCalls(m.toolCalls()))
                    : new AssistantMessage(m.content());
            case TOOL -> new ToolResponseMessage(List.of(
                    new ToolResponseMessage.ToolResponse(m.toolCallId(), m.toolName(), m.content())));
        };
    }

    private static List<AssistantMessage.ToolCall> toSpringToolCalls(List<ToolCall> calls) {
        List<AssistantMessage.ToolCall> out = new ArrayList<>();
        for (ToolCall c : calls) {
            out.add(new AssistantMessage.ToolCall(c.id(), "function", c.name(), c.argumentsJson()));
        }
        return out;
    }

    /** A definition-only callback; execution is handled by the agent runtime, not Spring AI. */
    private static ToolCallback toCallback(ToolSpec spec) {
        ToolDefinition definition = ToolDefinition.builder()
                .name(spec.name())
                .description(spec.description())
                .inputSchema(spec.parametersJsonSchema())
                .build();
        return new ToolCallback() {
            @Override
            public ToolDefinition getToolDefinition() {
                return definition;
            }

            @Override
            public String call(String toolInput) {
                throw new UnsupportedOperationException("tool execution is handled by the agent runtime");
            }
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
