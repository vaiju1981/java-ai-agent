package dev.vaijanath.aiagent.demos.analytics;

import java.util.Locale;

final class SemanticRoles {

    private SemanticRoles() {}

    static ColumnRole classify(ColumnProfile p) {
        if (p.signals().contains("bool-like")) {
            return ColumnRole.BOOLEAN;
        }
        if (p.signals().contains("date-like")) {
            return ColumnRole.DATE;
        }
        if (p.signals().contains("epoch-like")) {
            return ColumnRole.TIMESTAMP;
        }
        if (p.signals().contains("id-like") || p.signals().contains("leading-zeros")) {
            return ColumnRole.IDENTIFIER;
        }
        if (ColumnProfiler.isNumeric(p.physicalType())) {
            return isRatio(p) ? ColumnRole.MEASURE_RATIO : ColumnRole.MEASURE_ADDITIVE;
        }
        // Non-numeric, not id/date/bool: a small value set is a dimension; otherwise free text.
        if (p.distinct() <= 50 || (p.rows() > 0 && (double) p.distinct() / p.rows() < 0.5)) {
            return ColumnRole.DIMENSION;
        }
        return ColumnRole.TEXT;
    }

    private static boolean isRatio(ColumnProfile p) {
        if (p.min() == null || p.max() == null) {
            return false;
        }
        boolean boundedUnit = p.min() >= 0 && p.max() <= 1.0;
        String name = p.name().toLowerCase(Locale.ROOT);
        boolean namedRate = name.contains("percent") || name.contains("rate")
                || name.contains("ratio") || name.contains("pct") || name.contains("share");
        boolean boundedPct = p.min() >= 0 && p.max() <= 100.0;
        return boundedUnit || (namedRate && boundedPct);
    }
}
