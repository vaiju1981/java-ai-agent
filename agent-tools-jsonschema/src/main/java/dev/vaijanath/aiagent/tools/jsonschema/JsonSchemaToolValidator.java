package dev.vaijanath.aiagent.tools.jsonschema;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.vaijanath.aiagent.tool.ToolArgumentValidator;
import dev.vaijanath.aiagent.tool.ToolSpec;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * A {@link ToolArgumentValidator} that checks a tool call's arguments against the MCP-style JSON
 * Schema in its {@link ToolSpec}: the arguments must parse, an object schema's {@code required}
 * fields must be present, and any present property must match its declared {@code type} (string,
 * number, integer, boolean, object, array), recursing into nested objects and array items. It also
 * enforces {@code enum}, numeric bounds, string lengths, and {@code additionalProperties:false}.
 *
 * <p>It covers the shapes used to describe tool parameters; it is not a full JSON Schema engine
 * ({@code $ref}, {@code oneOf}, and conditional schemas are not enforced). An unparseable schema is
 * rejected fail-closed rather than silently disabling validation.
 */
public final class JsonSchemaToolValidator implements ToolArgumentValidator {

    private static final Set<String> TYPES = Set.of("object", "array", "string", "number", "integer", "boolean");
    private static final Set<String> UNSUPPORTED = Set.of(
            "$ref", "oneOf", "anyOf", "allOf", "not", "if", "then", "else", "pattern",
            "patternProperties", "dependentRequired", "contains", "prefixItems");

    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public Optional<String> validateSchema(ToolSpec spec) {
        try {
            JsonNode schema = mapper.readTree(spec.parametersJsonSchema());
            if (schema == null || !schema.isObject()) {
                return Optional.of("schema must be a JSON object");
            }
            if (!"object".equals(schema.path("type").asText())) {
                return Optional.of("tool argument schema type must be object");
            }
            return checkSchema(schema, "schema");
        } catch (Exception e) {
            return Optional.of("schema is not valid JSON");
        }
    }

    @Override
    public Optional<String> validate(ToolSpec spec, String argumentsJson) {
        Optional<String> invalidSchema = validateSchema(spec);
        if (invalidSchema.isPresent()) {
            return Optional.of("tool " + invalidSchema.get());
        }
        JsonNode schema;
        try {
            schema = mapper.readTree(spec.parametersJsonSchema());
        } catch (Exception e) {
            return Optional.of("tool schema is not valid JSON");
        }
        if (schema == null || !schema.isObject()) {
            return Optional.of("tool schema must be a JSON object");
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

    private Optional<String> checkSchema(JsonNode schema, String path) {
        for (String keyword : UNSUPPORTED) {
            if (schema.has(keyword)) {
                return Optional.of(path + " uses unsupported keyword '" + keyword + "'");
            }
        }
        String type = schema.path("type").asText("");
        if (!type.isEmpty() && !TYPES.contains(type)) {
            return Optional.of(path + " has unsupported type '" + type + "'");
        }
        if ("object".equals(type)) {
            if (schema.has("properties") && !schema.path("properties").isObject()) {
                return Optional.of(path + ".properties must be an object");
            }
            if (schema.has("required") && !schema.path("required").isArray()) {
                return Optional.of(path + ".required must be an array");
            }
            if (schema.has("additionalProperties")
                    && !schema.path("additionalProperties").isBoolean()) {
                return Optional.of(path + ".additionalProperties must be boolean");
            }
            Iterator<Map.Entry<String, JsonNode>> properties = schema.path("properties").fields();
            while (properties.hasNext()) {
                Map.Entry<String, JsonNode> property = properties.next();
                if (!property.getValue().isObject()) {
                    return Optional.of(path + ".properties." + property.getKey() + " must be an object");
                }
                Optional<String> error =
                        checkSchema(property.getValue(), path + ".properties." + property.getKey());
                if (error.isPresent()) {
                    return error;
                }
            }
        }
        if ("array".equals(type)) {
            if (!schema.path("items").isObject()) {
                return Optional.of(path + ".items must be an object schema");
            }
            return checkSchema(schema.path("items"), path + ".items");
        }
        if (schema.has("enum") && !schema.path("enum").isArray()) {
            return Optional.of(path + ".enum must be an array");
        }
        return Optional.empty();
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
            java.util.Set<String> declared = new java.util.HashSet<>();
            while (properties.hasNext()) {
                Map.Entry<String, JsonNode> property = properties.next();
                declared.add(property.getKey());
                if (value.has(property.getKey())) {
                    Optional<String> error =
                            check(property.getValue(), value.get(property.getKey()), property.getKey());
                    if (error.isPresent()) {
                        return error;
                    }
                }
            }
            if (schema.path("additionalProperties").isBoolean()
                    && !schema.path("additionalProperties").asBoolean()) {
                Iterator<String> names = value.fieldNames();
                while (names.hasNext()) {
                    String name = names.next();
                    if (!declared.contains(name)) {
                        return Optional.of("unexpected field '" + name + "'");
                    }
                }
            }
            return Optional.empty();
        }
        if (!matchesType(type, value)) {
            return Optional.of("field '" + path + "' must be " + type);
        }
        if (schema.path("enum").isArray()) {
            boolean found = false;
            for (JsonNode allowed : schema.path("enum")) {
                found |= allowed.equals(value);
            }
            if (!found) {
                return Optional.of("field '" + path + "' is not an allowed value");
            }
        }
        if (value.isArray() && schema.path("items").isObject()) {
            for (int i = 0; i < value.size(); i++) {
                Optional<String> error = check(schema.path("items"), value.get(i), path + "[" + i + "]");
                if (error.isPresent()) {
                    return error;
                }
            }
        }
        if (value.isTextual()) {
            int length = value.textValue().length();
            if (schema.has("minLength") && length < schema.path("minLength").asInt()) {
                return Optional.of("field '" + path + "' is shorter than minLength");
            }
            if (schema.has("maxLength") && length > schema.path("maxLength").asInt()) {
                return Optional.of("field '" + path + "' is longer than maxLength");
            }
        }
        if (value.isNumber()) {
            if (schema.has("minimum") && value.asDouble() < schema.path("minimum").asDouble()) {
                return Optional.of("field '" + path + "' is below minimum");
            }
            if (schema.has("maximum") && value.asDouble() > schema.path("maximum").asDouble()) {
                return Optional.of("field '" + path + "' is above maximum");
            }
        }
        return Optional.empty();
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
