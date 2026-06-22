package dev.vaijanath.aiagent.tools.annotations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.vaijanath.aiagent.tool.Tool;
import dev.vaijanath.aiagent.tool.ToolEffect;
import dev.vaijanath.aiagent.tool.ToolResult;
import dev.vaijanath.aiagent.tools.jsonschema.JsonSchemaToolValidator;
import java.util.List;
import org.junit.jupiter.api.Test;

class ReflectiveToolsTest {

    enum Unit {
        CELSIUS,
        FAHRENHEIT
    }

    record Point(int x, int y) {}

    /** A holder of annotated tool methods exercising the full range of generated behavior. */
    static final class SampleTools {

        @AgentTool(name = "get_weather", description = "Current weather for a city", effect = ToolEffect.READ_ONLY)
        public String getWeather(
                @ToolParam(description = "city name") String city,
                @ToolParam(description = "temperature units", required = false) Unit units) {
            return "weather in " + city + " (" + (units == null ? "CELSIUS" : units) + "): sunny";
        }

        @AgentTool(description = "add two integers", effect = ToolEffect.READ_ONLY)
        public int add(int a, int b) {
            return a + b;
        }

        @AgentTool(effect = ToolEffect.READ_ONLY)
        public String plot(@ToolParam List<String> labels, Point origin, boolean grid, String[] notes) {
            return labels.size() + "@" + origin + " grid=" + grid + " notes=" + notes.length;
        }

        @AgentTool
        public void record(String message) {
            // void return -> empty result
        }

        @AgentTool
        public ToolResult raw() {
            return ToolResult.error("custom failure");
        }

        @AgentTool(description = "always throws")
        public String boom() {
            throw new IllegalStateException("secret internal detail");
        }
    }

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static Tool tool(List<Tool> tools, String name) {
        return tools.stream().filter(t -> t.name().equals(name)).findFirst().orElseThrow();
    }

    @Test
    void discoversAnnotatedMethodsOnly() {
        List<Tool> tools = ReflectiveTools.from(new SampleTools());
        assertEquals(
                List.of("add", "boom", "get_weather", "plot", "raw", "record"),
                tools.stream().map(Tool::name).sorted().toList());
    }

    @Test
    void generatesSchemaWithTypesEnumsAndRequired() throws Exception {
        Tool weather = tool(ReflectiveTools.from(new SampleTools()), "get_weather");
        JsonNode schema = MAPPER.readTree(weather.spec().parametersJsonSchema());

        assertEquals("object", schema.get("type").asText());
        assertEquals("string", schema.at("/properties/city/type").asText());
        assertEquals("city name", schema.at("/properties/city/description").asText());
        assertEquals("string", schema.at("/properties/units/type").asText());
        assertEquals("CELSIUS", schema.at("/properties/units/enum/0").asText());
        assertEquals("FAHRENHEIT", schema.at("/properties/units/enum/1").asText());
        // city is required, the optional units is not
        List<String> required = MAPPER.convertValue(schema.get("required"), List.class);
        assertEquals(List.of("city"), required);
        assertEquals(ToolEffect.READ_ONLY, weather.spec().effect());
    }

    @Test
    void generatesSchemaForCollectionsRecordsAndArrays() throws Exception {
        Tool plot = tool(ReflectiveTools.from(new SampleTools()), "plot");
        JsonNode schema = MAPPER.readTree(plot.spec().parametersJsonSchema());

        assertEquals("array", schema.at("/properties/labels/type").asText());
        assertEquals("string", schema.at("/properties/labels/items/type").asText());
        assertEquals("object", schema.at("/properties/origin/type").asText());
        assertEquals("integer", schema.at("/properties/origin/properties/x/type").asText());
        assertEquals("boolean", schema.at("/properties/grid/type").asText());
        assertEquals("array", schema.at("/properties/notes/type").asText());
        assertEquals("string", schema.at("/properties/notes/items/type").asText());
    }

    @Test
    void bindsArgumentsAndInvokes() {
        Tool weather = tool(ReflectiveTools.from(new SampleTools()), "get_weather");

        ToolResult onlyCity = weather.invoke("{\"city\":\"NYC\"}");
        assertFalse(onlyCity.error());
        assertTrue(onlyCity.content().contains("weather in NYC"));
        assertTrue(onlyCity.content().contains("CELSIUS"), onlyCity.content());

        ToolResult withUnits = weather.invoke("{\"city\":\"NYC\",\"units\":\"FAHRENHEIT\"}");
        assertTrue(withUnits.content().contains("FAHRENHEIT"), withUnits.content());
    }

    @Test
    void bindsComplexArgumentsAndSerializesNonStringReturns() {
        List<Tool> tools = ReflectiveTools.from(new SampleTools());

        assertEquals("5", tool(tools, "add").invoke("{\"a\":2,\"b\":3}").content());

        ToolResult plotted = tool(tools, "plot")
                .invoke("{\"labels\":[\"a\",\"b\"],\"origin\":{\"x\":1,\"y\":2},\"grid\":true,\"notes\":[\"n\"]}");
        assertFalse(plotted.error());
        assertTrue(plotted.content().contains("2@"), plotted.content());
        assertTrue(plotted.content().contains("grid=true"), plotted.content());
    }

    @Test
    void voidReturnIsEmptyAndToolResultPassesThrough() {
        List<Tool> tools = ReflectiveTools.from(new SampleTools());

        ToolResult logged = tool(tools, "record").invoke("{\"message\":\"hi\"}");
        assertFalse(logged.error());
        assertEquals("", logged.content());

        ToolResult raw = tool(tools, "raw").invoke("{}");
        assertTrue(raw.error());
        assertEquals("custom failure", raw.content());
    }

    @Test
    void missingRequiredParamAndBadJsonYieldSafeErrors() {
        Tool weather = tool(ReflectiveTools.from(new SampleTools()), "get_weather");

        ToolResult missing = weather.invoke("{}");
        assertTrue(missing.error());
        assertTrue(missing.content().contains("city"), missing.content());

        ToolResult badJson = weather.invoke("not json");
        assertTrue(badJson.error());

        ToolResult wrongType = tool(ReflectiveTools.from(new SampleTools()), "add").invoke("{\"a\":\"nope\",\"b\":3}");
        assertTrue(wrongType.error());
    }

    @Test
    void toolExceptionIsContainedWithoutLeakingDetail() {
        ToolResult result = tool(ReflectiveTools.from(new SampleTools()), "boom").invoke("{}");
        assertTrue(result.error());
        assertFalse(result.content().contains("secret"), result.content());
    }

    @Test
    void generatedSchemaIsEnforceableByTheValidator() {
        Tool weather = tool(ReflectiveTools.from(new SampleTools()), "get_weather");
        JsonSchemaToolValidator validator = new JsonSchemaToolValidator();

        assertTrue(validator.validateSchema(weather.spec()).isEmpty(), "schema must be enforceable");
        assertTrue(validator.validate(weather.spec(), "{}").isPresent(), "missing required city");
        assertTrue(validator.validate(weather.spec(), "{\"city\":\"NYC\"}").isEmpty(), "valid call");
        assertTrue(
                validator.validate(weather.spec(), "{\"city\":\"NYC\",\"units\":\"kelvin\"}").isPresent(),
                "bad enum value");
    }
}
