package dev.vaijanath.aiagent.demos;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class AnalyticsEvidenceLog {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);
    private static final int MAX_RESULT_CHARS = 4_000;

    private final Path manifest;
    private final List<Map<String, Object>> events = new ArrayList<>();

    AnalyticsEvidenceLog(Path manifest) {
        this.manifest = manifest;
        persist();
    }

    Path manifest() {
        return manifest;
    }

    void recordDataset(AnalyticsDatasetConnector.DatasetRef dataset, int rows, int columns) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("source", dataset.source());
        details.put("localFile", dataset.localFile().toAbsolutePath().toString());
        details.put("metadata", dataset.metadata());
        details.put("rows", rows);
        details.put("columns", columns);
        record("dataset", "connect_dataset", details);
    }

    void recordSemanticPlan(String question, String planJson) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("question", question);
        details.put("plan", parseJson(planJson));
        record("semantic_plan", "semantic_query_plan", details);
    }

    void recordQuery(String sql, String resultJson) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("sql", sql);
        details.put("result", parseJson(resultJson));
        record("query", "sql_query", details);
    }

    void recordDashboard(Path dashboard, Path spec) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("path", dashboard.toAbsolutePath().toString());
        details.put("spec", spec.toAbsolutePath().toString());
        details.put("format", "html");
        record("artifact", "dashboard_create", details);
    }

    void recordDashboardUpdate(Path dashboard, Path spec, String operation, String tileId, int revision) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("path", dashboard.toAbsolutePath().toString());
        details.put("spec", spec.toAbsolutePath().toString());
        details.put("operation", operation);
        details.put("tileId", tileId);
        details.put("revision", revision);
        record("artifact_update", "dashboard_update", details);
    }

    String manifestJson() {
        return toJson(snapshot());
    }

    private void record(String type, String name, Map<String, Object> details) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("id", "evidence-" + (events.size() + 1));
        event.put("type", type);
        event.put("name", name);
        event.put("recordedAt", Instant.now().toString());
        event.put("details", details);
        events.add(event);
        persist();
    }

    private Map<String, Object> snapshot() {
        Map<String, Object> manifestJson = new LinkedHashMap<>();
        manifestJson.put("manifest", manifest.toAbsolutePath().toString());
        manifestJson.put("eventCount", events.size());
        manifestJson.put("events", events);
        return manifestJson;
    }

    private void persist() {
        try {
            Files.writeString(manifest, manifestJson(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("failed to write analytics evidence manifest", e);
        }
    }

    private static Object parseJson(String json) {
        if (json.length() > MAX_RESULT_CHARS) {
            return Map.of("truncated", true, "preview", json.substring(0, MAX_RESULT_CHARS));
        }
        try {
            return MAPPER.readValue(json, Object.class);
        } catch (JsonProcessingException e) {
            return json;
        }
    }

    private static String toJson(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("failed to serialize analytics evidence manifest", e);
        }
    }
}
