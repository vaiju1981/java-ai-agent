package dev.vaijanath.aiagent.rag;

import java.util.Map;
import java.util.Objects;

/**
 * A retrieved piece of context: its source {@code id}, the {@code text}, a relevance {@code score}
 * (higher is more relevant), and optional {@code metadata} (source, title, ...).
 */
public record RetrievedChunk(String id, String text, double score, Map<String, String> metadata) {

    public RetrievedChunk {
        Objects.requireNonNull(id, "id");
        text = text == null ? "" : text;
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    public RetrievedChunk(String id, String text, double score) {
        this(id, text, score, Map.of());
    }
}
