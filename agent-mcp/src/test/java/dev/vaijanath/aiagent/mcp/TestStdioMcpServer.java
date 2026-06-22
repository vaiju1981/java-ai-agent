package dev.vaijanath.aiagent.mcp;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

/**
 * A minimal, self-contained MCP server speaking newline-delimited JSON-RPC 2.0 over stdio — a test
 * fixture that lets {@link McpStdioE2eTest} drive the <em>real</em> langchain4j-mcp stdio transport
 * and client against a real process, with no network and no credentials. It advertises one tool,
 * {@code add}, which sums two numbers. Launched as a subprocess by the e2e test.
 *
 * <p>stdout is the JSON-RPC channel and carries nothing else; anything diagnostic would go to stderr.
 */
public final class TestStdioMcpServer {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private TestStdioMcpServer() {}

    public static void main(String[] args) throws Exception {
        PrintStream out = new PrintStream(System.out, true, StandardCharsets.UTF_8);
        try (BufferedReader in =
                new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = in.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                JsonNode request = MAPPER.readTree(line);
                if (!request.has("id") || request.get("id").isNull()) {
                    // A notification (e.g. notifications/initialized): acknowledged by doing nothing.
                    continue;
                }
                out.println(MAPPER.writeValueAsString(handle(request)));
            }
        }
    }

    private static ObjectNode handle(JsonNode request) {
        String method = request.path("method").asText("");
        ObjectNode response = MAPPER.createObjectNode();
        response.put("jsonrpc", "2.0");
        response.set("id", request.get("id"));
        switch (method) {
            case "initialize" -> response.set("result", initializeResult(request));
            case "tools/list" -> response.set("result", toolsListResult());
            case "tools/call" -> response.set("result", toolsCallResult(request));
            case "ping" -> response.set("result", MAPPER.createObjectNode());
            default -> {
                ObjectNode error = MAPPER.createObjectNode();
                error.put("code", -32601);
                error.put("message", "method not found: " + method);
                response.set("error", error);
            }
        }
        return response;
    }

    private static ObjectNode initializeResult(JsonNode request) {
        ObjectNode result = MAPPER.createObjectNode();
        // Echo the client's protocol version so the handshake is version-agnostic.
        result.put("protocolVersion", request.path("params").path("protocolVersion").asText("2024-11-05"));
        ObjectNode capabilities = MAPPER.createObjectNode();
        capabilities.set("tools", MAPPER.createObjectNode());
        result.set("capabilities", capabilities);
        ObjectNode serverInfo = MAPPER.createObjectNode();
        serverInfo.put("name", "test-stdio-mcp-server");
        serverInfo.put("version", "1.0.0");
        result.set("serverInfo", serverInfo);
        return result;
    }

    private static ObjectNode toolsListResult() {
        ObjectNode number = MAPPER.createObjectNode();
        number.put("type", "number");
        ObjectNode properties = MAPPER.createObjectNode();
        properties.set("a", number.deepCopy());
        properties.set("b", number.deepCopy());
        ObjectNode inputSchema = MAPPER.createObjectNode();
        inputSchema.put("type", "object");
        inputSchema.set("properties", properties);
        inputSchema.set("required", MAPPER.createArrayNode().add("a").add("b"));

        ObjectNode add = MAPPER.createObjectNode();
        add.put("name", "add");
        add.put("description", "Adds two numbers and returns the sum.");
        add.set("inputSchema", inputSchema);

        ObjectNode result = MAPPER.createObjectNode();
        result.set("tools", MAPPER.createArrayNode().add(add));
        return result;
    }

    private static ObjectNode toolsCallResult(JsonNode request) {
        JsonNode arguments = request.path("params").path("arguments");
        double sum = arguments.path("a").asDouble(0) + arguments.path("b").asDouble(0);
        // Render whole sums without a trailing ".0" so the result reads as a plain integer.
        String text = sum == Math.rint(sum) ? Long.toString((long) sum) : Double.toString(sum);

        ObjectNode content = MAPPER.createObjectNode();
        content.put("type", "text");
        content.put("text", text);
        ObjectNode result = MAPPER.createObjectNode();
        result.set("content", MAPPER.createArrayNode().add(content));
        result.put("isError", false);
        return result;
    }
}
