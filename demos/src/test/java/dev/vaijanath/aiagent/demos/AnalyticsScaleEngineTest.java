package dev.vaijanath.aiagent.demos;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AnalyticsScaleEngineTest {

    @TempDir
    Path tempDir;

    @Test
    void catalogIncludesOptionalExternalConnectors() {
        AnalyticsScaleEngine scale = new AnalyticsScaleEngine();

        String catalog = scale.connectorCatalogJson();

        assertTrue(catalog.contains("\"local_csv\""));
        assertTrue(catalog.contains("\"hugging_face\""));
        assertTrue(catalog.contains("\"kaggle\""));
        assertTrue(catalog.contains("\"bigquery_public\""));
        assertTrue(catalog.contains("\"networkRequiredForCi\" : false"));
    }

    @Test
    void estimatesWarehousePushdownAndModelContextPolicy() {
        AnalyticsScaleEngine scale = new AnalyticsScaleEngine();
        ExternalDatasetReference reference = new ExternalDatasetReference(
                "bigquery_public",
                "bigquery-public-data.samples.wikipedia",
                "https://cloud.google.com/bigquery/public-data",
                "Warehouse-scale public SQL demo.");

        String estimate = scale.queryCostEstimateJson(
                reference,
                1_000_000,
                6,
                0.01,
                "date range filter",
                true);

        assertTrue(estimate.contains("\"estimatedSourceRows\" : 1000000"));
        assertTrue(estimate.contains("\"estimatedRowsScanned\" : 10000"));
        assertTrue(estimate.contains("\"maxRowsSentToModel\" : 1000"));
        assertTrue(estimate.contains("\"sendsRawLargeDataset\" : false"));
    }

    @Test
    void samplingAndCacheStayBoundedAndDeterministic() {
        AnalyticsScaleEngine scale = new AnalyticsScaleEngine();
        Path database = tempDir.resolve("analytics.sqlite");
        try (AnalyticsDatabase analytics = AnalyticsDatabase.importCsv(database, AnalyticsInsightAgent.sampleCsv())) {
            String sample = scale.sampleRowsJson(analytics, 5, "deterministic_modulo");
            String first = scale.cachedSqlJson(
                    "channel-rollup",
                    () -> analytics.sqlJson("select channel, count(*) as rows from data group by channel"));
            String second = scale.cachedSqlJson(
                    "channel-rollup",
                    () -> analytics.sqlJson("select channel, count(*) as rows from data group by channel"));

            assertTrue(sample.contains("\"requestedRows\" : 5"));
            assertTrue(sample.contains("\"modelContextRowLimit\" : 1000"));
            assertTrue(sample.contains("\"limitedTo\" : 25"));
            assertTrue(first.contains("\"cacheHit\" : false"));
            assertTrue(second.contains("\"cacheHit\" : true"));
            assertFalse(second.contains("\"error\""));
        }
    }
}
