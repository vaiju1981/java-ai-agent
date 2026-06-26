package dev.vaijanath.aiagent.rag;

/** Small vector-math helpers shared by the embedding-backed stores. */
public final class Vectors {

    private Vectors() {}

    /**
     * Cosine similarity of two embedding vectors — {@code 1} is identical direction, {@code 0} is
     * orthogonal (also returned if either vector is all zeros). When the dimensions differ, only the
     * shared leading components are compared.
     */
    public static double cosine(float[] a, float[] b) {
        double dot = 0;
        double normA = 0;
        double normB = 0;
        int n = Math.min(a.length, b.length);
        for (int i = 0; i < n; i++) {
            dot += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }
        return (normA == 0 || normB == 0) ? 0 : dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }
}
