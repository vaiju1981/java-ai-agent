package dev.vaijanath.aiagent.demos.analytics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Proves time windows are generic: an epoch-millis long column ("ticks"-style) filters correctly. */
class EpochTimeTest {

    @TempDir
    Path tempDir;

    @Test
    void lastDaysWorksOnEpochMillisColumn() throws Exception {
        // ts = epoch millis for 2025-01-01, 02-01, 03-01, 03-31 (max). Not a DATE — a BIGINT.
        Path f = tempDir.resolve("evt.csv");
        Files.writeString(f, """
                ts,amount
                1735689600000,10
                1738368000000,20
                1740787200000,30
                1743379200000,40
                """);
        Map<String, Path> csvs = new LinkedHashMap<>();
        csvs.put("evt", f);
        try (CsvSource src = CsvSource.of(tempDir.resolve("e.duckdb"), csvs)) {
            SemanticModel model = SemanticModel.fromProfile(DatasetProfiler.profile(src));

            assertTrue(model.timeColumns().stream().anyMatch(t ->
                    t.column().equals("ts") && t.encoding() == SemanticModel.TimeEncoding.EPOCH_MILLIS),
                    "epoch-millis long detected as an EPOCH_MILLIS time column");

            double all = total(src, QueryPlanner.plan(model,
                    new QueryPlanner.QuerySpec(List.of("evt.amount"), List.of())));
            double last1 = total(src, QueryPlanner.plan(model, new QueryPlanner.QuerySpec(
                    List.of("evt.amount"), List.of(), List.of(), 1, "evt.ts")));
            assertEquals(100.0, all, 0.001);
            assertEquals(40.0, last1, 0.001); // only the latest epoch row, via to_timestamp(ts/1000)
        }
    }

    private static double total(CsvSource src, String sql) {
        return ((Number) src.nativeQuery(sql).get(0).get("amount")).doubleValue();
    }
}
