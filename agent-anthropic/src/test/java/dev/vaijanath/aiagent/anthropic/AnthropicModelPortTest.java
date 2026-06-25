package dev.vaijanath.aiagent.anthropic;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.anthropic.models.messages.Base64ImageSource;
import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.Tool;
import dev.vaijanath.aiagent.model.Media;
import dev.vaijanath.aiagent.model.Message;
import dev.vaijanath.aiagent.model.ModelRequest;
import dev.vaijanath.aiagent.model.ToolCall;
import dev.vaijanath.aiagent.tool.ToolEffect;
import dev.vaijanath.aiagent.tool.ToolSpec;
import java.util.List;
import org.junit.jupiter.api.Test;

class AnthropicModelPortTest {

    @Test
    void foldsSystemAndMapsConversationRoles() {
        ModelRequest request = new ModelRequest(
                List.of(
                        Message.system("You are helpful."),
                        Message.user("weather in NYC?"),
                        Message.assistant(
                                "let me check",
                                List.of(new ToolCall("tu_1", "get_weather", "{\"city\":\"NYC\"}"))),
                        Message.toolResult("tu_1", "get_weather", "sunny")),
                List.of());

        MessageCreateParams params = AnthropicModelPort.toParams(request, "claude-opus-4-8", 1024);

        assertEquals(1024, params.maxTokens());
        assertTrue(params.system().isPresent(), "the system turn must fold into the top-level system prompt");
        // system folded out -> user + assistant(tool_use) + tool_result(as a user turn) = 3 messages
        assertEquals(3, params.messages().size());
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

        MessageCreateParams params = AnthropicModelPort.toParams(request, "claude-opus-4-8", 1024);

        assertTrue(params.tools().isPresent());
        assertEquals(1, params.tools().get().size());
    }

    @Test
    void mapsInlineImageMediaToABase64ImageBlock() {
        Media img = Media.image("image/png", new byte[] {1, 2, 3});
        ModelRequest request =
                new ModelRequest(List.of(Message.user("what is this?", List.of(img))), List.of());

        MessageCreateParams params = AnthropicModelPort.toParams(request, "claude-opus-4-8", 1024);

        List<ContentBlockParam> blocks = params.messages().get(0).content().blockParams().orElseThrow();
        assertEquals(2, blocks.size()); // text + image
        assertTrue(blocks.get(0).text().isPresent());
        Base64ImageSource source = blocks.get(1).image().orElseThrow().source().base64().orElseThrow();
        assertEquals(Base64ImageSource.MediaType.IMAGE_PNG, source.mediaType());
        assertTrue(source.data().length() > 0);
    }

    @Test
    void mapsImageUrlMediaToAUrlImageBlock() {
        Media img = Media.imageUrl("https://example.com/cat.png");
        ModelRequest request = new ModelRequest(List.of(Message.user("", List.of(img))), List.of());

        MessageCreateParams params = AnthropicModelPort.toParams(request, "claude-opus-4-8", 1024);

        List<ContentBlockParam> blocks = params.messages().get(0).content().blockParams().orElseThrow();
        assertEquals(1, blocks.size()); // blank text omitted
        assertEquals(
                "https://example.com/cat.png",
                blocks.get(0).image().orElseThrow().source().url().orElseThrow().url());
    }

    @Test
    void textOnlyUserMessageStaysAPlainString() {
        ModelRequest request = new ModelRequest(List.of(Message.user("hi")), List.of());

        MessageCreateParams params = AnthropicModelPort.toParams(request, "claude-opus-4-8", 1024);

        assertTrue(params.messages().get(0).content().string().isPresent());
    }

    @Test
    void buildsToolWithSchemaPropertiesAndRequired() {
        ToolSpec spec = new ToolSpec(
                "get_weather",
                "current weather",
                "{\"type\":\"object\",\"properties\":{\"city\":{\"type\":\"string\"},"
                        + "\"units\":{\"type\":\"string\"}},\"required\":[\"city\"]}",
                ToolEffect.READ_ONLY);

        Tool tool = AnthropicModelPort.toTool(spec);

        assertEquals("get_weather", tool.name());
        assertEquals("current weather", tool.description().orElse(""));
        assertEquals(List.of("city"), tool.inputSchema().required().orElse(List.of()));
        assertTrue(tool.inputSchema().properties().isPresent());
    }

    @Test
    void toolWithoutRequiredOrPropertiesStillBuilds() {
        ToolSpec spec = new ToolSpec(
                "ping", "no args", "{\"type\":\"object\",\"properties\":{}}", ToolEffect.READ_ONLY);

        Tool tool = AnthropicModelPort.toTool(spec);

        assertEquals("ping", tool.name());
        assertTrue(
                tool.inputSchema().required().isEmpty()
                        || tool.inputSchema().required().get().isEmpty());
    }

    @Test
    void requireApiKeyRejectsAMissingKeyWithAClearMessage() {
        assertThrows(
                IllegalStateException.class, () -> AnthropicModelPort.requireApiKey(null, "ANTHROPIC_API_KEY"));
        assertThrows(
                IllegalStateException.class, () -> AnthropicModelPort.requireApiKey(" ", "ANTHROPIC_API_KEY"));
        assertDoesNotThrow(() -> AnthropicModelPort.requireApiKey("sk-ant-x", "ANTHROPIC_API_KEY"));
    }

    @Test
    void fromEnvChecksTheKey() {
        String key = System.getenv("ANTHROPIC_API_KEY");
        if (key == null || key.isBlank()) {
            assertThrows(IllegalStateException.class, AnthropicModelPort::fromEnv);
        } else {
            assertDoesNotThrow(() -> {
                AnthropicModelPort.fromEnv(); // key present → builds a client (no network)
            });
        }
    }
}
