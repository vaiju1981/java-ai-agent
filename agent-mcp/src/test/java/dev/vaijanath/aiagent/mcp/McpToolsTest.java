package dev.vaijanath.aiagent.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.McpGetPromptResult;
import dev.langchain4j.mcp.client.McpPrompt;
import dev.langchain4j.mcp.client.McpReadResourceResult;
import dev.langchain4j.mcp.client.McpResource;
import dev.langchain4j.mcp.client.McpResourceTemplate;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.vaijanath.aiagent.tool.Tool;
import dev.vaijanath.aiagent.tool.ToolResult;
import dev.vaijanath.aiagent.tools.jsonschema.JsonSchemaToolValidator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

class McpToolsTest {

    /** A minimal in-memory MCP client over a fixed tool list and a pluggable execute behaviour. */
    private record FakeMcpClient(
            List<ToolSpecification> tools, Function<ToolExecutionRequest, String> executor)
            implements McpClient {

        @Override
        public String key() {
            return "fake";
        }

        @Override
        public List<ToolSpecification> listTools() {
            return tools;
        }

        @Override
        public String executeTool(ToolExecutionRequest request) {
            return executor.apply(request);
        }

        @Override
        public List<McpResource> listResources() {
            return List.of();
        }

        @Override
        public List<McpResourceTemplate> listResourceTemplates() {
            return List.of();
        }

        @Override
        public McpReadResourceResult readResource(String uri) {
            return null;
        }

        @Override
        public List<McpPrompt> listPrompts() {
            return List.of();
        }

        @Override
        public McpGetPromptResult getPrompt(String name, Map<String, Object> arguments) {
            return null;
        }

        @Override
        public void checkHealth() {
        }

        @Override
        public void close() {
        }
    }

    @Test
    void wrapsMcpServerToolsAsTools() {
        McpClient client = new FakeMcpClient(
                List.of(ToolSpecification.builder().name("echo").description("echoes input").build()),
                request -> "echoed:" + request.arguments());

        List<Tool> tools = McpTools.from(client);

        assertEquals(1, tools.size());
        assertEquals("echo", tools.get(0).name());
        assertEquals("echoed:{\"x\":1}", tools.get(0).invoke("{\"x\":1}").content());
    }

    @Test
    void propagatesAnEnforceableArgumentSchema() {
        ToolSpecification weather = ToolSpecification.builder()
                .name("weather")
                .description("current weather")
                .parameters(JsonObjectSchema.builder()
                        .addStringProperty("city")
                        .addEnumProperty("units", List.of("celsius", "fahrenheit"))
                        .required("city")
                        .build())
                .build();
        Tool tool = McpTools.from(new FakeMcpClient(List.of(weather), request -> "ok")).get(0);

        JsonSchemaToolValidator validator = new JsonSchemaToolValidator();
        // The propagated schema is one the validator accepts (so it won't throw at startup) ...
        assertTrue(validator.validateSchema(tool.spec()).isEmpty(), "schema must be enforceable");
        // ... and it actually rejects malformed calls at our boundary before reaching the server.
        assertTrue(validator.validate(tool.spec(), "{\"units\":\"celsius\"}").isPresent(), "missing required city");
        assertTrue(validator.validate(tool.spec(), "{\"city\":\"NYC\",\"units\":\"kelvin\"}").isPresent(), "bad enum");
        assertTrue(validator.validate(tool.spec(), "{\"city\":\"NYC\",\"units\":\"celsius\"}").isEmpty(), "valid call");
    }

    @Test
    void keepsRemoteFailureDetailOutOfTheResult() {
        McpClient failing = new FakeMcpClient(
                List.of(ToolSpecification.builder().name("flaky").build()),
                request -> {
                    throw new RuntimeException("secret internal stacktrace detail");
                });

        ToolResult result = McpTools.from(failing).get(0).invoke("{}");

        assertTrue(result.error());
        assertFalse(result.content().contains("secret"),
                "remote error detail must not leak into the model context: " + result.content());
    }
}
