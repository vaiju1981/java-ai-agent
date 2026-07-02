package dev.vaijanath.aiagent.demos.analytics;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A {@link DataSource} over any JDBC SQL database (tables read via JDBC metadata, queries pushed
 * down to the engine). Proves the port — and the whole profiler/planner/agent stack — is not tied
 * to CSV/DuckDB: point it at SQLite, Postgres, etc. Relationships are discovered by the profiler.
 */
final class SqlSource implements DataSource {

    private final Connection connection;
    private final List<String> tableNames;

    private SqlSource(Connection connection, List<String> tableNames) {
        this.connection = connection;
        this.tableNames = List.copyOf(tableNames);
    }

    static SqlSource of(String jdbcUrl) {
        try {
            return of(DriverManager.getConnection(jdbcUrl));
        } catch (SQLException e) {
            throw new IllegalStateException("failed to connect: " + jdbcUrl, e);
        }
    }

    static SqlSource of(Connection connection) {
        try {
            List<String> names = new ArrayList<>();
            try (ResultSet rs = connection.getMetaData().getTables(null, null, "%", new String[] {"TABLE"})) {
                while (rs.next()) {
                    String name = rs.getString("TABLE_NAME");
                    if (name != null && !name.startsWith("sqlite_")) {
                        names.add(name);
                    }
                }
            }
            return new SqlSource(connection, names);
        } catch (SQLException e) {
            throw new IllegalStateException("failed to read SQL metadata", e);
        }
    }

    @Override
    public List<TableSchema> tables() {
        List<TableSchema> out = new ArrayList<>();
        for (String name : tableNames) {
            List<ColumnSchema> columns = new ArrayList<>();
            try (ResultSet rs = connection.getMetaData().getColumns(null, null, name, "%")) {
                while (rs.next()) {
                    columns.add(new ColumnSchema(
                            rs.getString("COLUMN_NAME"), rs.getString("TYPE_NAME"), ColumnRole.UNKNOWN));
                }
            } catch (SQLException e) {
                throw new IllegalStateException("failed to read columns for " + name, e);
            }
            long rows = ((Number) nativeQuery("select count(*) n from " + Sql.quote(name)).get(0).get("n")).longValue();
            out.add(new TableSchema(name, rows, columns));
        }
        return out;
    }

    @Override
    public List<Relationship> relationships() {
        return List.of();
    }

    @Override
    public List<Map<String, Object>> sample(String table, int limit) {
        return nativeQuery("select * from " + Sql.quote(table) + " limit " + Math.max(1, limit));
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
        } catch (SQLException e) {
            throw new IllegalStateException("query failed: " + sql, e);
        }
    }

    @Override
    public void close() {
        try {
            connection.close();
        } catch (SQLException e) {
            throw new IllegalStateException("failed to close SqlSource", e);
        }
    }
}
