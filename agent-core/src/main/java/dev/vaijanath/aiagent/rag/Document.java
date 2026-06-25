package dev.vaijanath.aiagent.rag;

import java.util.Map;
import java.util.Objects;

/**
 * A source document to ingest: a stable {@code id}, its full {@code text}, and optional {@code metadata}
 * (e.g. title, URL, source). An {@link Ingestor} splits it into chunks whose ids derive from this id.
 */
public record Document(String id, String text, Map<String, String> metadata) {

    public Document {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(text, "text");
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    public Document(String id, String text) {
        this(id, text, Map.of());
    }
}
