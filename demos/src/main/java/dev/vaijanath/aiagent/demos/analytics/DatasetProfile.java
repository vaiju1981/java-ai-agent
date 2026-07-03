package dev.vaijanath.aiagent.demos.analytics;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;

record DatasetProfile(
        List<TableSchema> tables,
        List<ColumnProfile> columns,
        List<Relationship> relationships,
        Map<String, Map<String, String>> importPlan) {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    String toJson() {
        try {
            return MAPPER.writeValueAsString(Map.of(
                    "tables", tables,
                    "columns", columns,
                    "relationships", relationships,
                    "importPlan", importPlan));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("failed to serialize DatasetProfile", e);
        }
    }
}
