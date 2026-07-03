package dev.vaijanath.aiagent.demos;

import java.util.Locale;

final class DemoEmbeddings {

    private DemoEmbeddings() {}

    static float[] keywordHash(String text) {
        float[] v = new float[96];
        for (String token : text.toLowerCase(Locale.ROOT).split("\\W+")) {
            if (token.length() >= 3) {
                v[Math.floorMod(token.hashCode(), v.length)] += 1f;
            }
        }
        return v;
    }
}
