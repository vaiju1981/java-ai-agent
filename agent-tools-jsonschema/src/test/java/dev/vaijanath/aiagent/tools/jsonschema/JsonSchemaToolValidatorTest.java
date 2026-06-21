package dev.vaijanath.aiagent.tools.jsonschema;

import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vaijanath.aiagent.tool.ToolArgumentValidator;
import dev.vaijanath.aiagent.tool.ToolSpec;
import org.junit.jupiter.api.Test;

class JsonSchemaToolValidatorTest {

    private static final ToolArgumentValidator VALIDATOR = new JsonSchemaToolValidator();

    private static ToolSpec paySpec() {
        return new ToolSpec("pay", "pay someone",
                "{\"type\":\"object\",\"properties\":{"
                        + "\"amount\":{\"type\":\"number\"},\"to\":{\"type\":\"string\"}},"
                        + "\"required\":[\"amount\",\"to\"]}");
    }

    @Test
    void acceptsValidArguments() {
        assertTrue(VALIDATOR.validate(paySpec(), "{\"amount\":5,\"to\":\"bob\"}").isEmpty());
    }

    @Test
    void rejectsMissingRequiredField() {
        assertTrue(VALIDATOR.validate(paySpec(), "{\"amount\":5}").orElseThrow().contains("to"));
    }

    @Test
    void rejectsWrongType() {
        assertTrue(VALIDATOR.validate(paySpec(), "{\"amount\":\"lots\",\"to\":\"bob\"}")
                .orElseThrow().contains("amount"));
    }

    @Test
    void rejectsMalformedJson() {
        assertTrue(VALIDATOR.validate(paySpec(), "not json").orElseThrow().contains("valid JSON"));
    }

    @Test
    void rejectsAnInvalidToolSchemaAtAssemblyTime() {
        ToolSpec spec = new ToolSpec("bad", "bad", "not json");

        assertTrue(VALIDATOR.validateSchema(spec).orElseThrow().contains("valid JSON"));
    }

    @Test
    void rejectsSchemaFeaturesItCannotEnforce() {
        ToolSpec spec = new ToolSpec("conditional", "", """
                {"type":"object","properties":{"value":{"oneOf":[
                  {"type":"string"},{"type":"number"}
                ]}}}
                """);

        assertTrue(VALIDATOR.validateSchema(spec).orElseThrow().contains("oneOf"));
        assertTrue(VALIDATOR.validate(spec, "{\"value\":false}").isPresent());
    }

    @Test
    void enforcesClosedObjectsEnumsAndStringBounds() {
        ToolSpec spec = new ToolSpec("search", "search", """
                {"type":"object","additionalProperties":false,"properties":{
                  "mode":{"type":"string","enum":["fast","deep"]},
                  "query":{"type":"string","minLength":3,"maxLength":10}
                },"required":["mode","query"]}
                """);

        assertTrue(VALIDATOR.validate(spec, "{\"mode\":\"slow\",\"query\":\"valid\"}").isPresent());
        assertTrue(VALIDATOR.validate(spec, "{\"mode\":\"fast\",\"query\":\"x\"}").isPresent());
        assertTrue(VALIDATOR.validate(
                        spec, "{\"mode\":\"fast\",\"query\":\"valid\",\"surprise\":true}")
                .isPresent());
        assertTrue(VALIDATOR.validate(spec, "{\"mode\":\"deep\",\"query\":\"valid\"}").isEmpty());
    }

    @Test
    void validatesArrayItemsAndNumericBounds() {
        ToolSpec spec = new ToolSpec("batch", "batch", """
                {"type":"object","properties":{"values":{"type":"array","items":{
                  "type":"number","minimum":1,"maximum":10
                }}},"required":["values"]}
                """);

        assertTrue(VALIDATOR.validate(spec, "{\"values\":[1,5,10]}").isEmpty());
        assertTrue(VALIDATOR.validate(spec, "{\"values\":[0]}").isPresent());
        assertTrue(VALIDATOR.validate(spec, "{\"values\":[11]}").isPresent());
    }
}
