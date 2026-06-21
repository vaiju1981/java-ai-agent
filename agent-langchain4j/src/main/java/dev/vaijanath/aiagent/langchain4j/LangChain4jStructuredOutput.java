package dev.vaijanath.aiagent.langchain4j;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.vaijanath.aiagent.model.ModelRequest;
import dev.vaijanath.aiagent.model.StructuredOutput;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * {@link StructuredOutput} via LangChain4j: requests JSON ({@code ResponseFormat.JSON}), hints the
 * record's field names, and binds the reply to the target type with one Jackson mapper — so callers
 * never write a parser. Works with providers that support JSON output (e.g. Ollama, OpenAI).
 */
public final class LangChain4jStructuredOutput implements StructuredOutput {

    private final ChatModel model;
    private final ObjectMapper mapper = new ObjectMapper();

    public LangChain4jStructuredOutput(ChatModel model) {
        this.model = Objects.requireNonNull(model, "model");
    }

    @Override
    public <T> T generate(ModelRequest request, Class<T> type) {
        List<ChatMessage> messages = new ArrayList<>(LangChain4jMessages.toLangChain4j(request.messages()));
        messages.add(UserMessage.from(
                "Respond with ONLY a JSON object" + fieldsHint(type) + ". No prose, no code fences."));

        ChatResponse response = model.chat(ChatRequest.builder()
                .messages(messages)
                .responseFormat(ResponseFormat.JSON)
                .build());

        String json = stripFences(response.aiMessage().text());
        try {
            return mapper.readValue(json, type);
        } catch (Exception e) {
            throw new RuntimeException(
                    "structured output did not parse as " + type.getSimpleName() + ": " + json, e);
        }
    }

    private static String fieldsHint(Class<?> type) {
        if (!type.isRecord()) {
            return "";
        }
        StringBuilder sb = new StringBuilder(" with fields ");
        RecordComponent[] components = type.getRecordComponents();
        for (int i = 0; i < components.length; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append('"').append(components[i].getName()).append("\" (")
                    .append(components[i].getType().getSimpleName()).append(')');
        }
        return sb.toString();
    }

    private static String stripFences(String s) {
        if (s == null) {
            return "";
        }
        String t = s.strip();
        if (t.startsWith("```")) {
            int nl = t.indexOf('\n');
            if (nl >= 0) {
                t = t.substring(nl + 1);
            }
            if (t.endsWith("```")) {
                t = t.substring(0, t.length() - 3);
            }
        }
        return t.strip();
    }

}
