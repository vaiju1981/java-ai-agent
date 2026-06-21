package dev.vaijanath.aiagent.tool;

import java.util.Optional;

/**
 * Validates a tool call's arguments against its {@link ToolSpec} schema <em>before</em> the tool
 * runs, so a malformed or incomplete call is rejected without side effects. Returns a short reason if
 * invalid, or {@link Optional#empty()} if the arguments are acceptable.
 *
 * <p>The default is {@link #none()} (accept all), which keeps {@code agent-core} dependency-free; plug
 * in a real JSON-schema validator for production — {@code agent-tools-jsonschema} provides one.
 */
@FunctionalInterface
public interface ToolArgumentValidator {

    Optional<String> validate(ToolSpec spec, String argumentsJson);

    /** Validate the schema itself at runtime construction; production runtimes fail startup on error. */
    default Optional<String> validateSchema(ToolSpec spec) {
        return Optional.empty();
    }

    /** Accepts every call — the default when no validator is configured. */
    static ToolArgumentValidator none() {
        return (spec, argumentsJson) -> Optional.empty();
    }
}
