package dev.vaijanath.aiagent.openai;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.openai.models.chat.completions.ChatCompletionContentPart;
import com.openai.models.chat.completions.ChatCompletionCreateParams;
import com.openai.models.chat.completions.ChatCompletionTool;
import dev.vaijanath.aiagent.model.Media;
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
    void textOnlyUserMessageStaysAPlainString() {
        ModelRequest request = new ModelRequest(List.of(Message.user("hi")), List.of());

        ChatCompletionCreateParams params = OpenAiModelPort.toParams(request, "gpt-4o");

        assertTrue(params.messages().get(0).user().orElseThrow().content().text().isPresent());
    }

    @Test
    void mapsInlineImageMediaToADataUrlContentPart() {
        Media img = Media.image("image/png", new byte[] {1, 2, 3});
        ModelRequest request =
                new ModelRequest(List.of(Message.user("what is this?", List.of(img))), List.of());

        ChatCompletionCreateParams params = OpenAiModelPort.toParams(request, "gpt-4o");

        List<ChatCompletionContentPart> parts =
                params.messages().get(0).user().orElseThrow().content().arrayOfContentParts().orElseThrow();
        assertEquals(2, parts.size()); // text + image
        assertTrue(parts.get(0).text().isPresent());
        String url = parts.get(1).imageUrl().orElseThrow().imageUrl().url();
        assertTrue(url.startsWith("data:image/png;base64,"), url);
    }

    @Test
    void mapsImageUrlMediaAsAPlainUrlAndOmitsBlankText() {
        Media img = Media.imageUrl("https://example.com/cat.png");
        ModelRequest request = new ModelRequest(List.of(Message.user("", List.of(img))), List.of());

        ChatCompletionCreateParams params = OpenAiModelPort.toParams(request, "gpt-4o");

        List<ChatCompletionContentPart> parts =
                params.messages().get(0).user().orElseThrow().content().arrayOfContentParts().orElseThrow();
        assertEquals(1, parts.size()); // blank text omitted
        assertEquals(
                "https://example.com/cat.png", parts.get(0).imageUrl().orElseThrow().imageUrl().url());
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

    @Test
    void requireApiKeyRejectsAMissingKeyWithAClearMessage() {
        assertThrows(
                IllegalStateException.class, () -> OpenAiModelPort.requireApiKey(null, "OPENAI_API_KEY"));
        assertThrows(
                IllegalStateException.class, () -> OpenAiModelPort.requireApiKey("  ", "OPENAI_API_KEY"));
        assertDoesNotThrow(() -> OpenAiModelPort.requireApiKey("sk-test", "OPENAI_API_KEY"));
    }

    @Test
    void fromEnvChecksTheKey() {
        String key = System.getenv("OPENAI_API_KEY");
        if (key == null || key.isBlank()) {
            assertThrows(IllegalStateException.class, OpenAiModelPort::fromEnv);
        } else {
            assertDoesNotThrow(OpenAiModelPort::fromEnv); // key present → builds a client (no network)
        }
    }
}
