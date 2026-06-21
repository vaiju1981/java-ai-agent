package dev.vaijanath.aiagent.demos;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.vaijanath.aiagent.tool.Tool;
import dev.vaijanath.aiagent.tool.ToolResult;
import dev.vaijanath.aiagent.tool.ToolSpec;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Looks up the typical reference range for a common lab test (curated, authoritative-style data —
 * never the model guessing safety-critical numbers). Returns ranges only; interpretation and the
 * "see your doctor" framing are the agent's job, and it must not diagnose.
 */
public final class LabReferenceTool implements Tool {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** keyword → reference range (matched by substring). */
    private static final Map<String, String> RANGES = new LinkedHashMap<>();

    static {
        RANGES.put("fasting glucose", "70-99 mg/dL (fasting)");
        RANGES.put("glucose", "70-99 mg/dL (fasting)");
        RANGES.put("a1c", "below 5.7%");
        RANGES.put("total cholesterol", "below 200 mg/dL");
        RANGES.put("ldl", "below 100 mg/dL (optimal)");
        RANGES.put("hdl", "above 40 mg/dL");
        RANGES.put("triglycerides", "below 150 mg/dL");
        RANGES.put("vitamin d", "20-50 ng/mL");
        RANGES.put("tsh", "0.4-4.0 mIU/L");
        RANGES.put("hemoglobin", "13.5-17.5 g/dL (adult men), 12.0-15.5 (women)");
    }

    @Override
    public ToolSpec spec() {
        return new ToolSpec(
                "lab_reference",
                "Look up the normal reference range for a common lab test (e.g. glucose, LDL, A1C, TSH).",
                "{\"type\":\"object\",\"properties\":{\"test\":{\"type\":\"string\"}},"
                        + "\"required\":[\"test\"]}");
    }

    @Override
    public ToolResult invoke(String argumentsJson) {
        String test;
        try {
            test = MAPPER.readTree(argumentsJson).path("test").asText("");
        } catch (Exception e) {
            return ToolResult.error("could not parse arguments: " + argumentsJson);
        }
        String key = test.toLowerCase(Locale.ROOT);
        for (Map.Entry<String, String> e : RANGES.entrySet()) {
            if (key.contains(e.getKey())) {
                return ToolResult.ok("Typical reference range for " + test + ": " + e.getValue());
            }
        }
        return ToolResult.ok("No reference range on file for '" + test + "'.");
    }
}
