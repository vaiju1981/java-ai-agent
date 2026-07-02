package dev.vaijanath.aiagent.demos.analytics;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

final class CsvSource implements DataSource {

    private final Connection connection;
    private final List<String> tableNames;

    private CsvSource(Connection connection, List<String> tableNames) {
        this.connection = connection;
        this.tableNames = List.copyOf(tableNames);
    }

    static CsvSource of(Path dbFile, Map<String, Path> namedCsvs) {
        try {
            Connection c = DriverManager.getConnection("jdbc:duckdb:" + dbFile.toAbsolutePath());
            List<String> names = new ArrayList<>();
            try (Statement st = c.createStatement()) {
                for (Map.Entry<String, Path> e : namedCsvs.entrySet()) {
                    String name = identifier(e.getKey());
                    st.execute("create or replace table " + quote(name) + " as "
                            + "select * from read_csv_auto('" + e.getValue().toAbsolutePath() + "')");
                    names.add(name);
                }
            }
            return new CsvSource(c, names);
        } catch (SQLException ex) {
            throw new IllegalStateException("failed to import CSVs into DuckDB", ex);
        }
    }

    Connection connection() {
        return connection;
    }

    long rowCount(String table) {
        return nativeQuery("select count(*) n from " + quote(table)).stream()
                .findFirst()
                .map(r -> ((Number) r.get("n")).longValue())
                .orElse(0L);
    }

    @Override
    public List<TableSchema> tables() {
        List<TableSchema> schemas = new ArrayList<>();
        for (String name : tableNames) {
            List<ColumnSchema> columns = new ArrayList<>();
            for (Map<String, Object> row : nativeQuery(
                    "select column_name, data_type from information_schema.columns "
                            + "where table_name = '" + name + "' order by ordinal_position")) {
                columns.add(new ColumnSchema(
                        String.valueOf(row.get("column_name")),
                        String.valueOf(row.get("data_type")),
                        ColumnRole.UNKNOWN));
            }
            schemas.add(new TableSchema(name, rowCount(name), columns));
        }
        return schemas;
    }

    @Override
    public List<Relationship> relationships() {
        return List.of();
    }

    @Override
    public List<Map<String, Object>> sample(String table, int limit) {
        return nativeQuery("select * from " + quote(table) + " limit " + Math.max(1, limit));
    }

    @Override
    public List<Map<String, Object>> nativeQuery(String sql) {
        try (Statement st = connection.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            ResultSetMetaData meta = rs.getMetaData();
            List<Map<String, Object>> rows = new ArrayList<>();
            while (rs.next()) {
                Map<String, Object> row = new LinkedHashMap<>();
                for (int i = 1; i <= meta.getColumnCount(); i++) {
                    row.put(meta.getColumnLabel(i), rs.getObject(i));
                }
                rows.add(row);
            }
            return rows;
        } catch (SQLException ex) {
            throw new IllegalStateException("query failed: " + sql, ex);
        }
    }

    @Override
    public void close() {
        try {
            connection.close();
        } catch (SQLException ex) {
            throw new IllegalStateException("failed to close CsvSource", ex);
        }
    }

    static String identifier(String value) {
        String id = value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
        return id.isBlank() || Character.isDigit(id.charAt(0)) ? "t_" + id : id;
    }

    static String quote(String id) {
        return "\"" + id.replace("\"", "\"\"") + "\"";
    }
}
