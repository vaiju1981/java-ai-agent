package dev.vaijanath.aiagent.tool;

import java.util.Objects;

/**
 * An MCP-aligned tool description: a name, a description for the model, a JSON Schema for the
 * parameters, and a capability {@link ToolEffect}. Keeping this MCP-shaped means tools can later be
 * exposed to, or consumed from, any MCP-speaking substrate without translation.
 */
public record ToolSpec(String name, String description, String parametersJsonSchema, ToolEffect effect) {

    public ToolSpec {
        Objects.requireNonNull(name, "name");
        description = description == null ? "" : description;
        parametersJsonSchema = (parametersJsonSchema == null || parametersJsonSchema.isBlank())
                ? "{\"type\":\"object\",\"properties\":{}}"
                : parametersJsonSchema;
        effect = effect == null ? ToolEffect.EFFECTFUL : effect;
    }

    /** A tool of unspecified effect is treated as {@link ToolEffect#EFFECTFUL} — the safe default. */
    public ToolSpec(String name, String description, String parametersJsonSchema) {
        this(name, description, parametersJsonSchema, ToolEffect.EFFECTFUL);
    }
}
