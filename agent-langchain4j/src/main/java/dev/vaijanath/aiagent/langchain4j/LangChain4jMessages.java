package dev.vaijanath.aiagent.langchain4j;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.vaijanath.aiagent.model.Message;
import dev.vaijanath.aiagent.model.ToolCall;
import java.util.ArrayList;
import java.util.List;

/** Shared conversion between the agent's neutral {@link Message}s and LangChain4j's. */
final class LangChain4jMessages {

    private LangChain4jMessages() {}

    static List<ChatMessage> toLangChain4j(List<Message> messages) {
        List<ChatMessage> out = new ArrayList<>();
        for (Message m : messages) {
            out.add(toLangChain4j(m));
        }
        return out;
    }

    static ChatMessage toLangChain4j(Message m) {
        return switch (m.role()) {
            case SYSTEM -> SystemMessage.from(m.content());
            case USER -> UserMessage.from(m.content());
            case ASSISTANT -> m.hasToolCalls()
                    ? AiMessage.from(toExecutionRequests(m.toolCalls()))
                    : AiMessage.from(m.content());
            case TOOL -> ToolExecutionResultMessage.from(m.toolCallId(), m.toolName(), m.content());
        };
    }

    static List<ToolExecutionRequest> toExecutionRequests(List<ToolCall> calls) {
        List<ToolExecutionRequest> out = new ArrayList<>();
        for (ToolCall c : calls) {
            out.add(ToolExecutionRequest.builder()
                    .id(c.id()).name(c.name()).arguments(c.argumentsJson()).build());
        }
        return out;
    }
}
