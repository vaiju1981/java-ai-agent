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
}
