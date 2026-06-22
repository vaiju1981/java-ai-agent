package dev.vaijanath.aiagent.mcp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.langchain4j.model.chat.request.json.JsonArraySchema;
import dev.langchain4j.model.chat.request.json.JsonBooleanSchema;
import dev.langchain4j.model.chat.request.json.JsonEnumSchema;
import dev.langchain4j.model.chat.request.json.JsonIntegerSchema;
import dev.langchain4j.model.chat.request.json.JsonNumberSchema;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonSchemaElement;
import dev.langchain4j.model.chat.request.json.JsonStringSchema;
import java.util.List;
import java.util.Map;

/**
 * Converts an MCP tool's LangChain4j {@link JsonObjectSchema} parameter declaration into the
 * MCP-style JSON Schema string that a {@code ToolArgumentValidator} can enforce — so MCP tool calls
 * are validated at our boundary, not only by the remote server.
 *
 * <p>It emits only the subset the validator supports (object/array/string/number/integer/boolean,
 * plus {@code required} and {@code enum}). If the schema uses anything outside that subset (e.g.
 * {@code $ref} or {@code anyOf}), conversion falls back to the {@linkplain #PERMISSIVE permissive}
 * object schema rather than emitting something the validator would reject at startup — so the result
 * is always at least as safe as the previous wide-open schema, and stricter whenever it can be.
 */
final class McpSchemas {

    /** A wide-open object schema: the safe fallback when a schema can't be fully expressed. */
    static final String PERMISSIVE = "{\"type\":\"object\"}";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private McpSchemas() {}

    static String toJsonSchema(JsonObjectSchema parameters) {
        if (parameters == null) {
            return PERMISSIVE;
        }
        try {
            return MAPPER.writeValueAsString(object(parameters));
        } catch (UnsupportedSchemaException | JsonProcessingException e) {
            return PERMISSIVE;
        }
    }

    private static ObjectNode element(JsonSchemaElement element) {
        if (element instanceof JsonObjectSchema object) {
            return object(object);
        }
        if (element instanceof JsonStringSchema) {
            return typed("string");
        }
        if (element instanceof JsonIntegerSchema) {
            return typed("integer");
        }
        if (element instanceof JsonNumberSchema) {
            return typed("number");
        }
        if (element instanceof JsonBooleanSchema) {
            return typed("boolean");
        }
        if (element instanceof JsonEnumSchema enumSchema) {
            return enumNode(enumSchema);
        }
        if (element instanceof JsonArraySchema array) {
            return array(array);
        }
        // $ref, anyOf, null, or any future element the validator can't enforce.
        throw new UnsupportedSchemaException();
    }

    private static ObjectNode object(JsonObjectSchema schema) {
        ObjectNode node = typed("object");
        Map<String, JsonSchemaElement> properties = schema.properties();
        if (properties != null && !properties.isEmpty()) {
            ObjectNode props = node.putObject("properties");
            for (Map.Entry<String, JsonSchemaElement> entry : properties.entrySet()) {
                props.set(entry.getKey(), element(entry.getValue()));
            }
        }
        List<String> required = schema.required();
        if (required != null && !required.isEmpty()) {
            ArrayNode req = node.putArray("required");
            required.forEach(req::add);
        }
        // Respect an explicitly closed object; otherwise stay open so a server that accepts extra
        // arguments isn't rejected at our boundary.
        Boolean additionalProperties = schema.additionalProperties();
        if (additionalProperties != null) {
            node.put("additionalProperties", additionalProperties);
        }
        return node;
    }

    private static ObjectNode array(JsonArraySchema schema) {
        ObjectNode node = typed("array");
        node.set("items", schema.items() != null ? element(schema.items()) : MAPPER.createObjectNode());
        return node;
    }

    private static ObjectNode enumNode(JsonEnumSchema schema) {
        ObjectNode node = typed("string");
        if (schema.enumValues() != null && !schema.enumValues().isEmpty()) {
            ArrayNode values = node.putArray("enum");
            schema.enumValues().forEach(values::add);
        }
        return node;
    }

    private static ObjectNode typed(String type) {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("type", type);
        return node;
    }

    /** Signals that an element falls outside the enforceable subset, triggering the safe fallback. */
    private static final class UnsupportedSchemaException extends RuntimeException {
        UnsupportedSchemaException() {
            super(null, null, false, false);
        }
    }
}
