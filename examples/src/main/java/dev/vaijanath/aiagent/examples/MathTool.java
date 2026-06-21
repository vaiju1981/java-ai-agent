package dev.vaijanath.aiagent.examples;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.vaijanath.aiagent.tool.Tool;
import dev.vaijanath.aiagent.tool.ToolResult;
import dev.vaijanath.aiagent.tool.ToolSpec;

/** A real arithmetic tool: add / subtract / multiply / divide two numbers. */
public final class MathTool implements Tool {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public ToolSpec spec() {
        return new ToolSpec(
                "math",
                "Do arithmetic on two numbers. 'op' is one of: add, subtract, multiply, divide.",
                """
                {"type":"object",
                 "properties":{
                   "op":{"type":"string"},
                   "a":{"type":"number"},
                   "b":{"type":"number"}},
                 "required":["op","a","b"]}""");
    }

    @Override
    public ToolResult invoke(String argumentsJson) {
        try {
            JsonNode n = MAPPER.readTree(argumentsJson);
            String op = n.path("op").asText();
            double a = n.path("a").asDouble();
            double b = n.path("b").asDouble();
            double result = switch (op.toLowerCase()) {
                case "add" -> a + b;
                case "subtract" -> a - b;
                case "multiply" -> a * b;
                case "divide" -> {
                    if (b == 0) {
                        yield Double.NaN;
                    }
                    yield a / b;
                }
                default -> throw new IllegalArgumentException("unknown op: " + op);
            };
            if (Double.isNaN(result)) {
                return ToolResult.error("cannot divide by zero");
            }
            return ToolResult.ok(Numbers.format(result));
        } catch (Exception e) {
            return ToolResult.error("math failed for " + argumentsJson + ": " + e.getMessage());
        }
    }
}
