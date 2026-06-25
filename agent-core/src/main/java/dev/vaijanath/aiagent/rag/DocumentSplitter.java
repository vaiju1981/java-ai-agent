package dev.vaijanath.aiagent.rag;

import java.util.ArrayList;
import java.util.List;

/**
 * Splits a long document into overlapping chunks for embedding — the ingestion-time companion to a
 * {@link Retriever}. Chunks are sized by character count and broken at the latest natural boundary
 * (paragraph, then sentence, then newline, then word) inside the window, so a chunk rarely ends
 * mid-sentence. Consecutive chunks share an {@code overlap} of trailing characters so context that
 * straddles a boundary is still retrievable.
 *
 * <pre>{@code
 * DocumentSplitter splitter = DocumentSplitter.ofChars(1000, 150);
 * List<String> chunks = splitter.split(longText);
 * }</pre>
 */
public final class DocumentSplitter {

    // Tried in order: prefer to break at a paragraph, else a sentence, else a line, else a word.
    private static final String[] SEPARATORS = {"\n\n", ". ", "\n", " "};

    private final int maxChars;
    private final int overlap;

    private DocumentSplitter(int maxChars, int overlap) {
        if (maxChars < 1) {
            throw new IllegalArgumentException("maxChars must be >= 1");
        }
        if (overlap < 0 || overlap >= maxChars) {
            throw new IllegalArgumentException("overlap must be >= 0 and < maxChars");
        }
        this.maxChars = maxChars;
        this.overlap = overlap;
    }

    /** A splitter targeting {@code maxChars}-sized chunks that overlap by {@code overlap} characters. */
    public static DocumentSplitter ofChars(int maxChars, int overlap) {
        return new DocumentSplitter(maxChars, overlap);
    }

    /** Splits {@code text} into chunks; blank input yields an empty list, short input a single chunk. */
    public List<String> split(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        String trimmed = text.strip();
        if (trimmed.length() <= maxChars) {
            return List.of(trimmed);
        }
        List<String> chunks = new ArrayList<>();
        int start = 0;
        while (start < trimmed.length()) {
            int end = Math.min(start + maxChars, trimmed.length());
            if (end < trimmed.length()) {
                end = breakBefore(trimmed, start, end);
            }
            String chunk = trimmed.substring(start, end).strip();
            if (!chunk.isEmpty()) {
                chunks.add(chunk);
            }
            if (end >= trimmed.length()) {
                break;
            }
            start = Math.max(end - overlap, start + 1); // always make progress
        }
        return chunks;
    }

    /**
     * Returns the best chunk end in {@code (start, end]}: the position just after the latest separator
     * found no earlier than the window's midpoint, or {@code end} itself for a hard split when none fits.
     */
    private int breakBefore(String text, int start, int end) {
        int earliest = start + Math.max(1, maxChars / 2); // avoid tiny chunks from an early boundary
        for (String sep : SEPARATORS) {
            int idx = text.lastIndexOf(sep, end - 1);
            if (idx >= earliest) {
                return idx + sep.length();
            }
        }
        return end;
    }
}
