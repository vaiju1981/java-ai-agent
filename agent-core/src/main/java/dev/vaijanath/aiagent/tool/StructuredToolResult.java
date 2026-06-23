package dev.vaijanath.aiagent.tool;

import java.util.Objects;

/**
 * A {@link ToolResult} paired with an optional structured JSON payload. The {@code result} is what the
 * model sees (text + error flag); the {@code dataJson} is surfaced only to observers and UIs — to render
 * a tool's output inline, log it, etc. — and is <b>never</b> sent to the model. {@code dataJson} is null
 * when there is no structured payload, in which case this behaves like a plain {@link ToolResult}.
 */
public record StructuredToolResult(ToolResult result, String dataJson) {

    public StructuredToolResult {
        Objects.requireNonNull(result, "result");
        dataJson = (dataJson == null || dataJson.isBlank()) ? null : dataJson;
    }

    /** Wraps a plain result with no structured payload. */
    public static StructuredToolResult of(ToolResult result) {
        return new StructuredToolResult(result, null);
    }

    /** A successful result whose model-facing text is {@code content} and whose UI payload is {@code dataJson}. */
    public static StructuredToolResult of(String content, String dataJson) {
        return new StructuredToolResult(ToolResult.ok(content), dataJson);
    }

    /** True if a structured JSON payload is present. */
    public boolean hasData() {
        return dataJson != null;
    }
}
