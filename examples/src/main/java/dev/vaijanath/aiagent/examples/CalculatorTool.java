package dev.vaijanath.aiagent.examples;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.vaijanath.aiagent.tool.Tool;
import dev.vaijanath.aiagent.tool.ToolEffect;
import dev.vaijanath.aiagent.tool.ToolResult;
import dev.vaijanath.aiagent.tool.ToolSpec;

/** A tiny demo tool: add two numbers. Shows the MCP-aligned ToolSpec + JSON-argument parsing. */
public final class CalculatorTool implements Tool {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public ToolSpec spec() {
        return new ToolSpec(
                "add",
                "Add two numbers and return their sum.",
                """
                {"type":"object",
                 "properties":{"a":{"type":"number"},"b":{"type":"number"}},
                 "required":["a","b"]}""",
                ToolEffect.READ_ONLY);
    }

    @Override
    public ToolResult invoke(String argumentsJson) {
        try {
            JsonNode args = MAPPER.readTree(argumentsJson);
            double a = args.path("a").asDouble();
            double b = args.path("b").asDouble();
            double sum = a + b;
            // Render whole numbers without a trailing ".0".
            String rendered = (sum == Math.rint(sum)) ? String.valueOf((long) sum) : String.valueOf(sum);
            return ToolResult.ok(rendered);
        } catch (Exception e) {
            return ToolResult.error("could not parse arguments: " + argumentsJson);
        }
    }
}
