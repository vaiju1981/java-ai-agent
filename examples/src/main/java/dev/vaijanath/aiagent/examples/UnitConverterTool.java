package dev.vaijanath.aiagent.examples;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.vaijanath.aiagent.tool.Tool;
import dev.vaijanath.aiagent.tool.ToolEffect;
import dev.vaijanath.aiagent.tool.ToolResult;
import dev.vaijanath.aiagent.tool.ToolSpec;
import java.util.Locale;

/** Converts a value between common units: c↔f (temperature), km↔mi (distance), kg↔lb (weight). */
public final class UnitConverterTool implements Tool {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public ToolSpec spec() {
        return new ToolSpec(
                "convert",
                "Convert a value between units. Supported pairs: c/f, km/mi, kg/lb (either direction).",
                """
                {"type":"object",
                 "properties":{
                   "value":{"type":"number"},
                   "from":{"type":"string"},
                   "to":{"type":"string"}},
                 "required":["value","from","to"]}""",
                ToolEffect.READ_ONLY);
    }

    @Override
    public ToolResult invoke(String argumentsJson) {
        try {
            JsonNode n = MAPPER.readTree(argumentsJson);
            double v = n.path("value").asDouble();
            String from = n.path("from").asText().toLowerCase(Locale.ROOT);
            String to = n.path("to").asText().toLowerCase(Locale.ROOT);
            String pair = from + "->" + to;
            double out = switch (pair) {
                case "c->f" -> v * 9 / 5 + 32;
                case "f->c" -> (v - 32) * 5 / 9;
                case "km->mi" -> v / 1.609344;
                case "mi->km" -> v * 1.609344;
                case "kg->lb" -> v * 2.2046226218;
                case "lb->kg" -> v / 2.2046226218;
                default -> throw new IllegalArgumentException("unsupported conversion: " + pair);
            };
            return ToolResult.ok(Numbers.format(out) + " " + to);
        } catch (Exception e) {
            return ToolResult.error("convert failed for " + argumentsJson + ": " + e.getMessage());
        }
    }
}
