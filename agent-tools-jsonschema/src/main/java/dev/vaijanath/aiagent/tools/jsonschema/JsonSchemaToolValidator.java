package dev.vaijanath.aiagent.tools.jsonschema;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.vaijanath.aiagent.tool.ToolArgumentValidator;
import dev.vaijanath.aiagent.tool.ToolSpec;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;

/**
 * A {@link ToolArgumentValidator} that checks a tool call's arguments against the MCP-style JSON
 * Schema in its {@link ToolSpec}: the arguments must parse, an object schema's {@code required}
 * fields must be present, and any present property must match its declared {@code type} (string,
 * number, integer, boolean, object, array), recursing into nested objects.
 *
 * <p>It covers the shapes used to describe tool parameters; it is not a full JSON Schema engine
 * ({@code $ref}, {@code oneOf}, {@code enum}, array-item validation, etc. are not enforced). An
 * unparseable schema is treated as "no constraint" rather than blocking the call.
 */
public final class JsonSchemaToolValidator implements ToolArgumentValidator {

    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public Optional<String> validate(ToolSpec spec, String argumentsJson) {
        JsonNode schema;
        try {
            schema = mapper.readTree(spec.parametersJsonSchema());
        } catch (Exception e) {
            return Optional.empty(); // a schema we can't parse can't be enforced; don't block
        }
        JsonNode args;
        try {
            args = mapper.readTree(
                    (argumentsJson == null || argumentsJson.isBlank()) ? "{}" : argumentsJson);
        } catch (Exception e) {
            return Optional.of("arguments are not valid JSON");
        }
        return check(schema, args, "arguments");
    }

    private Optional<String> check(JsonNode schema, JsonNode value, String path) {
        String type = schema.path("type").asText("");
        if ("object".equals(type)) {
            if (!value.isObject()) {
                return Optional.of(path + " must be an object");
            }
            for (JsonNode required : schema.path("required")) {
                if (!value.has(required.asText())) {
                    return Optional.of("missing required field '" + required.asText() + "'");
                }
            }
            Iterator<Map.Entry<String, JsonNode>> properties = schema.path("properties").fields();
            while (properties.hasNext()) {
                Map.Entry<String, JsonNode> property = properties.next();
                if (value.has(property.getKey())) {
                    Optional<String> error =
                            check(property.getValue(), value.get(property.getKey()), property.getKey());
                    if (error.isPresent()) {
                        return error;
                    }
                }
            }
            return Optional.empty();
        }
        return matchesType(type, value)
                ? Optional.empty()
                : Optional.of("field '" + path + "' must be " + type);
    }

    private static boolean matchesType(String type, JsonNode value) {
        return switch (type) {
            case "string" -> value.isTextual();
            case "integer" -> value.isIntegralNumber();
            case "number" -> value.isNumber();
            case "boolean" -> value.isBoolean();
            case "array" -> value.isArray();
            case "object" -> value.isObject();
            default -> true; // unknown or absent type: do not constrain
        };
    }
}
