package dev.vaijanath.aiagent.langchain4j;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import java.util.ArrayList;
import java.util.List;

/**
 * Converts an MCP-style JSON Schema string (as carried by {@code ToolSpec}) into LangChain4j's
 * {@link JsonObjectSchema}. Phase 1 supports the common case: an object of primitive properties
 * (string / number / integer / boolean) with an optional {@code required} list.
 */
final class SchemaConverter {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private SchemaConverter() {}

    static JsonObjectSchema toJsonObjectSchema(String json) {
        JsonObjectSchema.Builder builder = JsonObjectSchema.builder();
        try {
            JsonNode root = MAPPER.readTree(json);

            JsonNode properties = root.get("properties");
            if (properties != null && properties.isObject()) {
                properties.fieldNames().forEachRemaining(name -> {
                    JsonNode prop = properties.get(name);
                    String type = prop.has("type") ? prop.get("type").asText() : "string";
                    switch (type) {
                        case "number" -> builder.addNumberProperty(name);
                        case "integer" -> builder.addIntegerProperty(name);
                        case "boolean" -> builder.addBooleanProperty(name);
                        default -> builder.addStringProperty(name);
                    }
                });
            }

            JsonNode required = root.get("required");
            if (required != null && required.isArray()) {
                List<String> names = new ArrayList<>();
                required.forEach(n -> names.add(n.asText()));
                if (!names.isEmpty()) {
                    builder.required(names.toArray(new String[0]));
                }
            }
        } catch (Exception e) {
            throw new IllegalArgumentException("invalid tool parameter schema: " + json, e);
        }
        return builder.build();
    }
}
