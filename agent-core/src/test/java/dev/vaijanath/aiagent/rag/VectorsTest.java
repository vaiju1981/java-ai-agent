package dev.vaijanath.aiagent.rag;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class VectorsTest {

    private static final double EPS = 1e-9;

    @Test
    void identicalDirectionIsOne() {
        assertEquals(1.0, Vectors.cosine(new float[] {1, 2, 3}, new float[] {2, 4, 6}), EPS);
    }

    @Test
    void orthogonalIsZero() {
        assertEquals(0.0, Vectors.cosine(new float[] {1, 0}, new float[] {0, 1}), EPS);
    }

    @Test
    void aZeroVectorIsZeroNotNaN() {
        assertEquals(0.0, Vectors.cosine(new float[] {0, 0}, new float[] {1, 1}), EPS);
    }

    @Test
    void differingLengthsCompareTheSharedPrefix() {
        assertEquals(1.0, Vectors.cosine(new float[] {1, 1, 1}, new float[] {1, 1}), EPS);
    }
}
