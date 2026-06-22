package dev.vaijanath.aiagent.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.langchain4j.mcp.client.DefaultMcpClient;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.mcp.client.transport.McpTransport;
import dev.langchain4j.mcp.client.transport.stdio.StdioMcpTransport;
import dev.vaijanath.aiagent.tool.Tool;
import dev.vaijanath.aiagent.tool.ToolResult;
import java.io.File;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * A real end-to-end test of the MCP adapter: it launches {@link TestStdioMcpServer} as a subprocess
 * and drives it through the <em>real</em> langchain4j-mcp {@link StdioMcpTransport} +
 * {@link DefaultMcpClient} — the full {@code initialize → tools/list → tools/call} JSON-RPC handshake
 * over stdio — verifying what the rest of the suite covers only against a fake client. Self-contained:
 * it needs only a JVM (no network, no credentials), so it runs in CI.
 */
class McpStdioE2eTest {

    @Test
    void wrapsAndInvokesARealMcpServerToolOverStdio() throws Exception {
        McpTransport transport = new StdioMcpTransport.Builder()
                .command(List.of(
                        javaBinary(),
                        "-cp",
                        System.getProperty("java.class.path"),
                        TestStdioMcpServer.class.getName()))
                .logEvents(false)
                .build();
        McpClient client = new DefaultMcpClient.Builder()
                .transport(transport)
                .key("e2e")
                .initializationTimeout(Duration.ofSeconds(20))
                .toolExecutionTimeout(Duration.ofSeconds(20))
                .build();

        try (client) {
            List<Tool> tools = McpTools.from(client);

            assertEquals(1, tools.size(), "server advertises exactly one tool");
            Tool add = tools.get(0);
            assertEquals("add", add.name());
            // The server's real input schema is propagated through to our ToolSpec.
            String schema = add.spec().parametersJsonSchema();
            assertTrue(schema.contains("\"a\""), schema);
            assertTrue(schema.contains("\"b\""), schema);

            ToolResult result = add.invoke("{\"a\":2,\"b\":3}");

            assertFalse(result.error(), "live add call should succeed: " + result.content());
            assertEquals("5", result.content().trim());
        }
    }

    private static String javaBinary() {
        String exe = System.getProperty("os.name", "").toLowerCase().contains("win") ? "java.exe" : "java";
        return System.getProperty("java.home") + File.separator + "bin" + File.separator + exe;
    }
}
