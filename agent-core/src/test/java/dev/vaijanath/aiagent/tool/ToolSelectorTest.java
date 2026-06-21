package dev.vaijanath.aiagent.tool;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ToolSelectorTest {

    private static Tool tool(String name, String description) {
        return new Tool() {
            @Override
            public ToolSpec spec() {
                return new ToolSpec(name, description, "{\"type\":\"object\"}");
            }

            @Override
            public ToolResult invoke(String argumentsJson) {
                return ToolResult.ok(name);
            }
        };
    }

    @Test
    void keywordSelectorNarrowsManyToolsToTheRelevantFew() {
        List<Tool> tools = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            tools.add(tool("tool_" + i, "performs operation number " + i));
        }
        tools.add(tool("weather_forecast", "get the weather forecast for a city"));

        List<Tool> selected = ToolSelectors.keyword(5).select("what is the weather today?", tools);

        assertTrue(selected.size() <= 5, "should present at most 5 tools");
        assertTrue(selected.stream().anyMatch(t -> t.name().equals("weather_forecast")),
                "the weather tool should be selected");
    }

    @Test
    void allSelectorPresentsEverything() {
        List<Tool> tools = List.of(tool("a", "x"), tool("b", "y"));
        assertEquals(2, ToolSelectors.all().select("anything", tools).size());
    }
}
