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
        return of(dbFile, namedCsvs, Map.of());
    }

    // The table name and CSV path/type-override values can't be bound as PreparedStatement
    // parameters (they're identifiers and read_csv(...) function arguments, not literal values in a
    // fixed query shape); identifiers are restricted to [a-z0-9_] by identifier(), and every
    // interpolated literal is escaped by sqlLiteral() before being embedded.
    @SuppressWarnings("java:S2077")
    static CsvSource of(Path dbFile, Map<String, Path> namedCsvs,
            Map<String, Map<String, String>> typeOverrides) {
        try {
            Connection c = DriverManager.getConnection("jdbc:duckdb:" + dbFile.toAbsolutePath());
            List<String> names = new ArrayList<>();
            try (Statement st = c.createStatement()) {
                for (Map.Entry<String, Path> e : namedCsvs.entrySet()) {
                    String name = identifier(e.getKey());
                    st.execute("create or replace table " + quote(name) + " as select * from "
                            + readCsv(e.getValue(), typeOverrides.getOrDefault(name, Map.of())));
                    names.add(name);
                }
                // Import is done; lock the connection down so the run_sql tool (which hands the LLM raw
                // DuckDB SQL) can't read arbitrary files off disk via read_csv/read_parquet/ATTACH/COPY.
                st.execute("set enable_external_access = false");
            }
            return new CsvSource(c, names);
        } catch (SQLException ex) {
            throw new IllegalStateException("failed to import CSVs into DuckDB", ex);
        }
    }

    private static String readCsv(Path csv, Map<String, String> overrides) {
        String path = sqlLiteral(csv.toAbsolutePath().toString());
        // sample_size=-1: infer each column's type from the whole file so a stray value deep in a
        // large CSV widens the column to text instead of failing the import. No rows are dropped.
        if (overrides.isEmpty()) {
            return "read_csv_auto('" + path + "', sample_size=-1)";
        }
        StringBuilder types = new StringBuilder();
        overrides.forEach((col, type) -> {
            if (types.length() > 0) {
                types.append(", ");
            }
            types.append("'").append(sqlLiteral(col)).append("': '").append(sqlLiteral(type)).append("'");
        });
        return "read_csv('" + path + "', auto_detect=true, sample_size=-1, types={" + types + "})";
    }

    /** Escapes a value for use inside a single-quoted SQL string literal (doubles embedded quotes). */
    private static String sqlLiteral(String value) {
        return value.replace("'", "''");
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
        return nativeQuery(sql, Integer.MAX_VALUE);
    }

    @Override
    public List<Map<String, Object>> nativeQuery(String sql, int maxRows) {
        try (Statement st = connection.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            ResultSetMetaData meta = rs.getMetaData();
            List<Map<String, Object>> rows = new ArrayList<>();
            while (rs.next() && rows.size() < maxRows) {
                Map<String, Object> row = new LinkedHashMap<>();
                for (int i = 1; i <= meta.getColumnCount(); i++) {
                    row.put(meta.getColumnLabel(i), Sql.jsonSafe(rs.getObject(i)));
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
        String id = value.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("(?:^_+)|(?:_+$)", "");
        return id.isBlank() || Character.isDigit(id.charAt(0)) ? "t_" + id : id;
    }

    static String quote(String id) {
        return Sql.quote(id);
    }
}
