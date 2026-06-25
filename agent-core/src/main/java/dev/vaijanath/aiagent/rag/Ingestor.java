package dev.vaijanath.aiagent.rag;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * Drives RAG ingestion: splits a {@link Document} with a {@link DocumentSplitter} and writes each chunk
 * to a {@link ChunkStore}. Chunk ids are {@code "{documentId}#{n}"}; the document's metadata rides along
 * on every chunk. The store is anything with a matching {@code add} (e.g. {@code store::add}).
 */
public final class Ingestor {

    private final DocumentSplitter splitter;

    public Ingestor(DocumentSplitter splitter) {
        this.splitter = Objects.requireNonNull(splitter, "splitter");
    }

    /** Splits one document and writes its chunks to {@code store}; returns the number of chunks written. */
    public int ingest(String tenant, Document document, ChunkStore store) {
        Objects.requireNonNull(document, "document");
        Objects.requireNonNull(store, "store");
        List<String> chunks = splitter.split(document.text());
        for (int i = 0; i < chunks.size(); i++) {
            store.store(tenant, document.id() + "#" + i, chunks.get(i), document.metadata());
        }
        return chunks.size();
    }

    /** Ingests every document; returns the total number of chunks written. */
    public int ingestAll(String tenant, Collection<Document> documents, ChunkStore store) {
        Objects.requireNonNull(documents, "documents");
        int total = 0;
        for (Document document : documents) {
            total += ingest(tenant, document, store);
        }
        return total;
    }
}
