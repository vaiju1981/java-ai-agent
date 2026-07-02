package dev.vaijanath.aiagent.demos.analytics;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class DatasetProfiler {

    private DatasetProfiler() {}

    static DatasetProfile profile(CsvSource src) {
        List<TableSchema> tables = src.tables();
        List<ColumnProfile> columns = new ArrayList<>();
        Map<String, Map<String, ColumnRole>> rolesByTable = new LinkedHashMap<>();
        Map<String, Map<String, String>> importPlan = new LinkedHashMap<>();
        List<TableSchema> typedTables = new ArrayList<>();

        for (TableSchema table : tables) {
            List<ColumnSchema> typedCols = new ArrayList<>();
            Map<String, ColumnRole> roleMap = new LinkedHashMap<>();
            for (ColumnSchema col : table.columns()) {
                ColumnProfile cp = ColumnProfiler.profile(src, table.name(), col);
                columns.add(cp);
                ColumnRole role = SemanticRoles.classify(cp);
                typedCols.add(new ColumnSchema(col.name(), col.physicalType(), role));
                roleMap.put(col.name(), role);
                if (cp.signals().contains("leading-zeros")) {
                    importPlan.computeIfAbsent(table.name(), k -> new LinkedHashMap<>())
                            .put(col.name(), "VARCHAR");
                }
            }
            rolesByTable.put(table.name(), roleMap);
            typedTables.add(new TableSchema(table.name(), table.rows(), typedCols));
        }

        List<Relationship> relationships = RelationshipDiscovery.discover(src, rolesByTable);
        return new DatasetProfile(typedTables, columns, relationships, importPlan);
    }
}
