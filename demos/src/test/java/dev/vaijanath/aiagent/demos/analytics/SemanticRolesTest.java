package dev.vaijanath.aiagent.demos.analytics;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SemanticRolesTest {

    private ColumnProfile p(
            String name, String type, long rows, long distinct, Double min, Double max, Set<String> signals) {
        return new ColumnProfile("tbl", name, type, rows, distinct, 0, min, max, null, null, List.of(), signals);
    }

    @Test
    void classifiesByDataNotName() {
        assertEquals(ColumnRole.IDENTIFIER,
                SemanticRoles.classify(p("playerId", "BIGINT", 100, 100, 1d, 100d, Set.of("id-like"))));
        assertEquals(ColumnRole.IDENTIFIER,
                SemanticRoles.classify(p("zip", "BIGINT", 100, 90, 0d, 99999d, Set.of("leading-zeros"))));
        assertEquals(ColumnRole.TIMESTAMP,
                SemanticRoles.classify(p("ticks", "BIGINT", 100, 100, 1.7e12, 1.7e12, Set.of("epoch-like", "id-like"))));
        assertEquals(ColumnRole.DATE,
                SemanticRoles.classify(p("day", "VARCHAR", 100, 30, null, null, Set.of("date-like"))));
        assertEquals(ColumnRole.BOOLEAN,
                SemanticRoles.classify(p("isActive", "VARCHAR", 100, 2, null, null, Set.of("bool-like"))));
        assertEquals(ColumnRole.MEASURE_RATIO,
                SemanticRoles.classify(p("paybackPercent", "DOUBLE", 100, 80, 85d, 96d, Set.of())));
        assertEquals(ColumnRole.MEASURE_ADDITIVE,
                SemanticRoles.classify(p("theoWin", "DOUBLE", 100, 95, -50d, 5000d, Set.of())));
        assertEquals(ColumnRole.DIMENSION,
                SemanticRoles.classify(p("clubLevel", "VARCHAR", 100, 5, null, null, Set.of())));
    }
}
