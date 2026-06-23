package dev.vaijanath.aiagent.fincopilot.advisor;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class HashingEmbedderTest {

    private final HashingEmbedder embedder = new HashingEmbedder(256);

    @Test
    void isDeterministic() {
        assertArrayEquals(embedder.embed("emergency fund savings"), embedder.embed("emergency fund savings"));
    }

    @Test
    void sharedTermsScoreHigherThanUnrelatedText() {
        float[] query = embedder.embed("how much should my emergency fund be");
        float[] related = embedder.embed("an emergency fund covers unexpected expenses");
        float[] unrelated = embedder.embed("the avalanche method pays the highest interest debt first");
        assertTrue(dot(query, related) > dot(query, unrelated), "shared-term text should be nearer");
    }

    @Test
    void isL2Normalized() {
        float[] v = embedder.embed("budgeting and saving");
        double norm = 0;
        for (float x : v) {
            norm += x * x;
        }
        assertTrue(Math.abs(Math.sqrt(norm) - 1.0) < 1e-5, "unit length");
    }

    private static double dot(float[] a, float[] b) {
        double sum = 0;
        for (int i = 0; i < a.length; i++) {
            sum += a[i] * b[i];
        }
        return sum;
    }
}
