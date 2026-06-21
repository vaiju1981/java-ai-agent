package dev.vaijanath.aiagent.demos;

import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vaijanath.aiagent.tool.ToolResult;
import org.junit.jupiter.api.Test;

class LabReferenceToolTest {

    private final LabReferenceTool tool = new LabReferenceTool();

    @Test
    void returnsRangeForKnownTest() {
        ToolResult r = tool.invoke("{\"test\":\"fasting glucose\"}");
        assertTrue(r.content().contains("70-99"));
    }

    @Test
    void reportsUnknownTest() {
        ToolResult r = tool.invoke("{\"test\":\"unobtanium level\"}");
        assertTrue(r.content().toLowerCase().contains("no reference range"));
    }
}
