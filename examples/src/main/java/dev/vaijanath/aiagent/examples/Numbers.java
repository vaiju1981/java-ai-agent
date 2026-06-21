package dev.vaijanath.aiagent.examples;

/** Formats doubles without a trailing ".0" for whole numbers, and to 4 dp otherwise. */
final class Numbers {

    private Numbers() {}

    static String format(double d) {
        if (d == Math.rint(d) && !Double.isInfinite(d)) {
            return String.valueOf((long) d);
        }
        return String.valueOf(Math.round(d * 10000.0) / 10000.0);
    }
}
