package dev.vaijanath.aiagent.openai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.openai.models.chat.completions.ChatCompletionCreateParams;
import com.openai.models.chat.completions.ChatCompletionTool;
import dev.vaijanath.aiagent.model.Message;
import dev.vaijanath.aiagent.model.ModelRequest;
import dev.vaijanath.aiagent.model.ToolCall;
import dev.vaijanath.aiagent.tool.ToolEffect;
import dev.vaijanath.aiagent.tool.ToolSpec;
import java.util.List;
import org.junit.jupiter.api.Test;

class OpenAiModelPortTest {

    @Test
    void mapsConversationRolesIncludingToolCallsAndResults() {
        ModelRequest request = new ModelRequest(
                List.of(
                        Message.system("You are helpful."),
                        Message.user("weather in NYC?"),
                        Message.assistant(
                                "let me check",
                                List.of(new ToolCall("call_1", "get_weather", "{\"city\":\"NYC\"}"))),
                        Message.toolResult("call_1", "get_weather", "sunny")),
                List.of());

        ChatCompletionCreateParams params = OpenAiModelPort.toParams(request, "gpt-4o");

        // system + user + assistant(tool_calls) + tool result = 4 messages
        assertEquals(4, params.messages().size());
        assertTrue(params.tools().isEmpty());
    }

    @Test
    void mapsToolSpecsOntoTheRequest() {
        ToolSpec spec = new ToolSpec(
                "get_weather",
                "current weather",
                "{\"type\":\"object\",\"properties\":{\"city\":{\"type\":\"string\"}},\"required\":[\"city\"]}",
                ToolEffect.READ_ONLY);
        ModelRequest request = new ModelRequest(List.of(Message.user("hi")), List.of(spec));

        ChatCompletionCreateParams params = OpenAiModelPort.toParams(request, "gpt-4o");

        assertTrue(params.tools().isPresent());
        assertEquals(1, params.tools().get().size());
    }

    @Test
    void buildsAFunctionToolFromTheSchema() {
        ToolSpec spec = new ToolSpec(
                "get_weather",
                "current weather",
                "{\"type\":\"object\",\"properties\":{\"city\":{\"type\":\"string\"}},\"required\":[\"city\"]}",
                ToolEffect.READ_ONLY);

        ChatCompletionTool tool = OpenAiModelPort.toTool(spec);

        assertTrue(tool.function().isPresent());
        assertEquals("get_weather", tool.function().get().function().name());
    }
}
