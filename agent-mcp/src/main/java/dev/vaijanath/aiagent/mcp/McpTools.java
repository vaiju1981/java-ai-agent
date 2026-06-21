package dev.vaijanath.aiagent.mcp;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.mcp.client.McpClient;
import dev.vaijanath.aiagent.tool.Tool;
import dev.vaijanath.aiagent.tool.ToolResult;
import dev.vaijanath.aiagent.tool.ToolSpec;
import java.util.List;

/**
 * Exposes the tools advertised by a connected MCP (Model Context Protocol) server as java-ai-agent
 * {@link Tool}s — so an agent can use any MCP server's tools through the same seam as native tools.
 *
 * <p>Built on the LangChain4j MCP client. Connect a {@code McpClient} (stdio/HTTP transport) and
 * pass it here. End-to-end use requires a running MCP server; the wrapping/execution logic is unit
 * tested against the real API.
 */
public final class McpTools {

    private McpTools() {}

    /** Wraps every tool the MCP server advertises. */
    public static List<Tool> from(McpClient client) {
        return client.listTools().stream()
                .map(spec -> (Tool) new McpTool(client, spec))
                .toList();
    }

    private record McpTool(McpClient client, ToolSpecification mcpSpec) implements Tool {

        @Override
        public ToolSpec spec() {
            String description = mcpSpec.description() == null ? "" : mcpSpec.description();
            // The MCP server validates arguments; a permissive schema is advertised to the model.
            return new ToolSpec(mcpSpec.name(), description, "{\"type\":\"object\"}");
        }

        @Override
        public ToolResult invoke(String argumentsJson) {
            String args = (argumentsJson == null || argumentsJson.isBlank()) ? "{}" : argumentsJson;
            try {
                String out = client.executeTool(
                        ToolExecutionRequest.builder().name(mcpSpec.name()).arguments(args).build());
                return ToolResult.ok(out);
            } catch (RuntimeException e) {
                return ToolResult.error("MCP tool '" + mcpSpec.name() + "' failed: " + e.getMessage());
            }
        }
    }
}
