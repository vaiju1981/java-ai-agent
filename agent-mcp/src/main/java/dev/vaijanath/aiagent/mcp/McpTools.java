package dev.vaijanath.aiagent.mcp;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.service.tool.ToolExecutionResult;
import dev.vaijanath.aiagent.tool.Tool;
import dev.vaijanath.aiagent.tool.ToolResult;
import dev.vaijanath.aiagent.tool.ToolSpec;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Exposes the tools advertised by a connected MCP (Model Context Protocol) server as java-ai-agent
 * {@link Tool}s — so an agent can use any MCP server's tools through the same seam as native tools.
 *
 * <p>Built on the LangChain4j MCP client. Connect a {@code McpClient} (stdio/HTTP transport) and
 * pass it here. The MCP server's declared input schema is propagated (see {@link McpSchemas}) so a
 * {@code ToolArgumentValidator} can reject malformed calls at our boundary, and a failing call's
 * remote error detail is logged but kept out of the model context. End-to-end use requires a running
 * MCP server; the wrapping/execution logic is unit tested against the real API.
 */
public final class McpTools {

    private static final Logger log = LoggerFactory.getLogger(McpTools.class);

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
            // Propagate the server's real input schema so arguments can be validated before the call;
            // McpSchemas falls back to a permissive object schema for anything it can't express.
            return new ToolSpec(mcpSpec.name(), description, McpSchemas.toJsonSchema(mcpSpec.parameters()));
        }

        @Override
        public ToolResult invoke(String argumentsJson) {
            String args = (argumentsJson == null || argumentsJson.isBlank()) ? "{}" : argumentsJson;
            try {
                // langchain4j-mcp now returns a ToolExecutionResult (was a String); resultText() is the
                // textual content. Transport/protocol failures still surface as exceptions below.
                ToolExecutionResult result = client.executeTool(
                        ToolExecutionRequest.builder().name(mcpSpec.name()).arguments(args).build());
                return ToolResult.ok(result.resultText());
            } catch (RuntimeException e) {
                // The remote error detail goes to the log, never into the model's context.
                log.warn("MCP tool '{}' failed", mcpSpec.name(), e);
                return ToolResult.error("MCP tool '" + mcpSpec.name() + "' failed");
            }
        }
    }
}
