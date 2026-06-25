package dev.vaijanath.aiagent.rag;

import java.util.Map;

/**
 * The write seam an {@link Ingestor} pushes chunks into — anything that can persist an embeddable
 * passage under a tenant and id. {@link InMemoryVectorStore#add} matches this shape, so a store is
 * wired in with a method reference:
 *
 * <pre>{@code
 * InMemoryVectorStore store = new InMemoryVectorStore(embedder);
 * new Ingestor(DocumentSplitter.ofChars(1000, 150)).ingest("tenant", doc, store::add);
 * }</pre>
 */
@FunctionalInterface
public interface ChunkStore {

    void store(String tenant, String id, String text, Map<String, String> metadata);
}
