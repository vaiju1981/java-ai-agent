package dev.vaijanath.aiagent.demos.analytics;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * User-editable metadata layered on top of auto-discovered relationships: include/exclude overrides
 * and free-text table/column descriptions. Persisted as one JSON file so a UI can edit it live;
 * {@link DatasetProfiler} applies it right after auto-discovery, and {@link AnalyticsToolset} folds
 * descriptions into the schema the agent sees.
 */
final class Catalog {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    record RelationshipRef(String fromTable, List<String> fromColumns, String toTable,
            List<String> toColumns, String cardinality) {
        Relationship toRelationship() {
            return new Relationship(fromTable, fromColumns, toTable, toColumns,
                    cardinality == null ? "many_to_one" : cardinality, 1.0);
        }
    }

    record ExcludeRef(String fromTable, String toTable, List<String> columns) {}

    private final Path file;
    private List<RelationshipRef> include = new ArrayList<>();
    private List<ExcludeRef> exclude = new ArrayList<>();
    private Map<String, String> tableDescriptions = new LinkedHashMap<>();
    private Map<String, String> columnDescriptions = new LinkedHashMap<>();

    private Catalog(Path file) {
        this.file = file;
    }

    static Catalog load(Path file) {
        Catalog catalog = new Catalog(file);
        if (Files.exists(file)) {
            try {
                Map<String, Object> raw = MAPPER.readValue(Files.readString(file), new TypeReference<>() {});
                Map<String, Object> relationships = asMap(raw.get("relationships"));
                catalog.include = MAPPER.convertValue(relationships.getOrDefault("include", List.of()),
                        new TypeReference<List<RelationshipRef>>() {});
                catalog.exclude = MAPPER.convertValue(relationships.getOrDefault("exclude", List.of()),
                        new TypeReference<List<ExcludeRef>>() {});
                Map<String, Object> descriptions = asMap(raw.get("descriptions"));
                catalog.tableDescriptions = MAPPER.convertValue(descriptions.getOrDefault("tables", Map.of()),
                        new TypeReference<Map<String, String>>() {});
                catalog.columnDescriptions = MAPPER.convertValue(descriptions.getOrDefault("columns", Map.of()),
                        new TypeReference<Map<String, String>>() {});
            } catch (IOException e) {
                throw new IllegalStateException("failed to read catalog: " + file, e);
            }
        }
        return catalog;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object value) {
        return value instanceof Map ? (Map<String, Object>) value : Map.of();
    }

    List<Relationship> apply(List<Relationship> discovered) {
        List<Relationship> out = new ArrayList<>();
        for (Relationship r : discovered) {
            boolean excluded = exclude.stream().anyMatch(x -> x.fromTable().equals(r.fromTable())
                    && x.toTable().equals(r.toTable()) && x.columns().equals(r.fromColumns()));
            if (!excluded) {
                out.add(r);
            }
        }
        include.forEach(i -> out.add(i.toRelationship()));
        return out;
    }

    String descriptionForTable(String table) {
        return tableDescriptions.get(table);
    }

    String descriptionForColumn(String table, String column) {
        return columnDescriptions.get(table + "." + column);
    }

    synchronized void addInclude(RelationshipRef ref) {
        include.add(ref);
        save();
    }

    synchronized void addExclude(ExcludeRef ref) {
        exclude.add(ref);
        save();
    }

    synchronized void setTableDescription(String table, String description) {
        tableDescriptions.put(table, description);
        save();
    }

    synchronized void setColumnDescription(String table, String column, String description) {
        columnDescriptions.put(table + "." + column, description);
        save();
    }

    Map<String, Object> toMap() {
        return Map.of(
                "relationships", Map.of("include", include, "exclude", exclude),
                "descriptions", Map.of("tables", tableDescriptions, "columns", columnDescriptions));
    }

    private void save() {
        try {
            if (file.getParent() != null) {
                Files.createDirectories(file.getParent());
            }
            Files.writeString(file, MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(toMap()));
        } catch (IOException e) {
            throw new IllegalStateException("failed to write catalog: " + file, e);
        }
    }
}
