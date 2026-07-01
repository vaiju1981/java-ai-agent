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
import java.util.Locale;
import java.util.Map;

final class AnalyticsDecisionLog {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    private final Path logFile;
    private final AnalyticsEvidenceLog evidence;
    private final List<Map<String, Object>> decisions = new ArrayList<>();

    AnalyticsDecisionLog(Path logFile, AnalyticsEvidenceLog evidence) {
        this.logFile = logFile;
        this.evidence = evidence;
        persist();
    }

    Path logFile() {
        return logFile;
    }

    String write(String owner, String conclusion, String status, String rationale, List<String> evidenceIds) {
        if (owner == null || owner.isBlank()) {
            throw new IllegalArgumentException("decision owner is required");
        }
        if (conclusion == null || conclusion.isBlank()) {
            throw new IllegalArgumentException("decision conclusion is required");
        }
        if (evidenceIds == null || evidenceIds.isEmpty()) {
            throw new IllegalArgumentException("at least one evidence id is required");
        }

        Map<String, Object> decision = new LinkedHashMap<>();
        decision.put("id", "decision-" + (decisions.size() + 1));
        decision.put("recordedAt", Instant.now().toString());
        decision.put("owner", owner);
        decision.put("status", normalizeStatus(status));
        decision.put("conclusion", conclusion);
        decision.put("rationale", rationale == null ? "" : rationale);
        decision.put("evidenceManifest", evidence.manifest().toAbsolutePath().toString());
        decision.put("evidenceIds", List.copyOf(evidenceIds));
        decisions.add(decision);
        persist();
        return toJson(decision);
    }

    String decisionsJson() {
        return toJson(snapshot());
    }

    private Map<String, Object> snapshot() {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("decisionLog", logFile.toAbsolutePath().toString());
        value.put("evidenceManifest", evidence.manifest().toAbsolutePath().toString());
        value.put("decisionCount", decisions.size());
        value.put("decisions", decisions);
        return value;
    }

    private void persist() {
        try {
            Files.writeString(logFile, decisionsJson(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("failed to write analytics decision log", e);
        }
    }

    private static String normalizeStatus(String status) {
        String normalized = status == null || status.isBlank()
                ? "proposed"
                : status.toLowerCase(Locale.ROOT).replace(' ', '_');
        return switch (normalized) {
            case "proposed", "approved", "rejected", "needs_review" -> normalized;
            default -> "needs_review";
        };
    }

    private static String toJson(Object value) {
        try {
            return MAPPER.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("failed to serialize analytics decision log", e);
        }
    }
}
