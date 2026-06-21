package dev.vaijanath.aiagent.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.McpGetPromptResult;
import dev.langchain4j.mcp.client.McpPrompt;
import dev.langchain4j.mcp.client.McpReadResourceResult;
import dev.langchain4j.mcp.client.McpResource;
import dev.langchain4j.mcp.client.McpResourceTemplate;
import dev.vaijanath.aiagent.tool.Tool;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class McpToolsTest {

    /** A minimal in-memory MCP client exposing one "echo" tool. */
    private static final class FakeMcpClient implements McpClient {
        @Override
        public String key() {
            return "fake";
        }

        @Override
        public List<ToolSpecification> listTools() {
            return List.of(ToolSpecification.builder().name("echo").description("echoes input").build());
        }

        @Override
        public String executeTool(ToolExecutionRequest request) {
            return "echoed:" + request.arguments();
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
        List<Tool> tools = McpTools.from(new FakeMcpClient());

        assertEquals(1, tools.size());
        assertEquals("echo", tools.get(0).name());
        assertEquals("echoed:{\"x\":1}", tools.get(0).invoke("{\"x\":1}").content());
    }
}
