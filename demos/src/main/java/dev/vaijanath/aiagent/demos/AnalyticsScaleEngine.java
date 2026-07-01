package dev.vaijanath.aiagent.demos;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;

final class AnalyticsScaleEngine {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int MODEL_CONTEXT_ROW_LIMIT = 1_000;
    private static final int BYTES_PER_CELL_ESTIMATE = 16;

    private final Map<String, String> queryCache = new LinkedHashMap<>();

    String connectorCatalogJson() {
        List<Map<String, Object>> connectors = new ArrayList<>();
        connectors.add(connector(
                "local_csv",
                "implemented",
                "Local CSV upload/import",
                List.of("schema_inference", "sqlite_import", "tablesaw_profile")));
        connectors.add(connector(
                "hugging_face",
                "optional_metadata_only",
                "Hugging Face dataset reference",
                List.of("dataset_card", "file_split_selection", "sample_then_import")));
        connectors.add(connector(
                "kaggle",
                "optional_metadata_only",
                "Kaggle dataset reference",
                List.of("competition_or_dataset_slug", "manual_download_path", "multi_csv_import")));
        connectors.add(connector(
                "bigquery_public",
                "optional_metadata_only",
                "BigQuery public dataset reference",
                List.of("warehouse_pushdown_sql", "dry_run_cost_estimate", "bounded_result_fetch")));
        return json(Map.of(
                "connectors", connectors,
                "defaultMode", "deterministic_local_or_metadata_only",
                "networkRequiredForCi", false));
    }

    String queryCostEstimateJson(
            ExternalDatasetReference reference,
            long sourceRows,
            int selectedColumns,
            double sampleRate,
            String predicate,
            boolean pushdownSql) {
        long rows = Math.max(0, sourceRows);
        int columns = Math.max(1, selectedColumns);
        double boundedSampleRate = Math.max(0.0, Math.min(1.0, sampleRate));
        long sampledRows = Math.max(1, Math.round(rows * boundedSampleRate));
        long bytesScanned = rows * columns * BYTES_PER_CELL_ESTIMATE;
        Map<String, Object> estimate = new LinkedHashMap<>();
        estimate.put("dataset", Map.of(
                "provider", reference.provider(),
                "id", reference.id(),
                "url", reference.url()));
        estimate.put("predicate", predicate == null || predicate.isBlank() ? "none" : predicate);
        estimate.put("pushdownSql", pushdownSql);
        estimate.put("estimatedSourceRows", rows);
        estimate.put("selectedColumns", columns);
        estimate.put("estimatedRowsScanned", pushdownSql ? sampledRows : rows);
        estimate.put("estimatedBytesScanned", pushdownSql ? sampledRows * columns * BYTES_PER_CELL_ESTIMATE : bytesScanned);
        estimate.put("sampleRate", round(boundedSampleRate));
        estimate.put("sampledRows", sampledRows);
        estimate.put("modelContextPolicy", Map.of(
                "maxRowsSentToModel", MODEL_CONTEXT_ROW_LIMIT,
                "sendsRawLargeDataset", false,
                "largeResultHandling", "persist_artifact_and_summarize_shape"));
        return json(estimate);
    }

    String sampleRowsJson(AnalyticsDatabase analytics, int requestedRows, String strategy) {
        int limit = Math.max(1, Math.min(requestedRows, MODEL_CONTEXT_ROW_LIMIT));
        int rowCount = analytics.rowCount();
        int stride = Math.max(1, rowCount / limit);
        String selectedStrategy = strategy == null || strategy.isBlank() ? "deterministic_modulo" : strategy;
        String sql = switch (selectedStrategy.toLowerCase(Locale.ROOT)) {
            case "head" -> "select * from data order by _row_id limit " + limit;
            default -> "select * from data where (_row_id % " + stride + ") = 0 order by _row_id limit " + limit;
        };
        return json(Map.of(
                "strategy", selectedStrategy,
                "sourceRows", rowCount,
                "requestedRows", requestedRows,
                "modelContextRowLimit", MODEL_CONTEXT_ROW_LIMIT,
                "sql", sql,
                "result", parse(analytics.sqlJson(sql))));
    }

    String cachedSqlJson(String cacheKey, Supplier<String> supplier) {
        String key = cacheKey == null || cacheKey.isBlank() ? "default" : cacheKey;
        if (queryCache.containsKey(key)) {
            return json(Map.of(
                    "cacheKey", key,
                    "cacheHit", true,
                    "result", parse(queryCache.get(key))));
        }
        String result = supplier.get();
        queryCache.put(key, result);
        return json(Map.of(
                "cacheKey", key,
                "cacheHit", false,
                "result", parse(result)));
    }

    String millionRowDemoPlanJson() {
        ExternalDatasetReference reference = new ExternalDatasetReference(
                "bigquery_public",
                "bigquery-public-data.samples.wikipedia",
                "https://cloud.google.com/bigquery/public-data",
                "Warehouse-scale public SQL demo for pushdown aggregation and bounded result fetch.");
        return queryCostEstimateJson(
                reference,
                1_000_000L,
                6,
                0.01,
                "date range and project filters pushed to warehouse",
                true);
    }

    private static Map<String, Object> connector(
            String name,
            String status,
            String description,
            List<String> capabilities) {
        Map<String, Object> connector = new LinkedHashMap<>();
        connector.put("name", name);
        connector.put("status", status);
        connector.put("description", description);
        connector.put("capabilities", capabilities);
        return connector;
    }

    private static JsonNode parse(String json) {
        try {
            return MAPPER.readTree(json);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("failed to parse analytics JSON", e);
        }
    }

    private static String json(Object value) {
        try {
            return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("failed to render analytics scale JSON", e);
        }
    }

    private static double round(double value) {
        return Math.round(value * 1_000.0) / 1_000.0;
    }
}
