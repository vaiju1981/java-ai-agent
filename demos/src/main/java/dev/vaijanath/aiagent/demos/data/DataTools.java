package dev.vaijanath.aiagent.demos.data;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.vaijanath.aiagent.demos.SqlTool;
import dev.vaijanath.aiagent.tool.Tool;
import dev.vaijanath.aiagent.tool.ToolResult;
import dev.vaijanath.aiagent.tool.ToolSpec;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.util.ArrayList;
import java.util.List;

/** A data-exploration toolkit over a SQLite database: schema discovery tools plus the SQL tool. */
final class DataTools {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private DataTools() {}

    @FunctionalInterface
    private interface SqlFn {
        String apply(Connection c, JsonNode args) throws Exception;
    }

    static List<Tool> toolkit(String jdbcUrl) {
        List<Tool> tools = new ArrayList<>();
        tools.add(new SqlTool(jdbcUrl, 50));

        tools.add(dbTool(jdbcUrl, "list_tables", "List the tables in the database.",
                "{\"type\":\"object\",\"properties\":{}}",
                (c, a) -> queryColumn(c, "SELECT name FROM sqlite_master WHERE type='table' "
                        + "AND name NOT LIKE 'sqlite_%'")));

        tools.add(dbTool(jdbcUrl, "describe_table", "List a table's columns and types.",
                strSchema("table"), (c, a) -> {
                    String table = ident(a.path("table").asText());
                    try (var st = c.createStatement();
                            ResultSet rs = st.executeQuery("PRAGMA table_info(" + table + ")")) {
                        StringBuilder sb = new StringBuilder();
                        while (rs.next()) {
                            sb.append(rs.getString("name")).append(" ").append(rs.getString("type")).append('\n');
                        }
                        return sb.toString().strip();
                    }
                }));

        tools.add(dbTool(jdbcUrl, "row_count", "Count the rows in a table.",
                strSchema("table"), (c, a) -> {
                    String table = ident(a.path("table").asText());
                    try (var st = c.createStatement();
                            ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM " + table)) {
                        rs.next();
                        return String.valueOf(rs.getLong(1));
                    }
                }));

        tools.add(dbTool(jdbcUrl, "sample_rows", "Return a few sample rows from a table.",
                "{\"type\":\"object\",\"properties\":{\"table\":{\"type\":\"string\"},"
                        + "\"limit\":{\"type\":\"integer\"}},\"required\":[\"table\"]}", (c, a) -> {
                    String table = ident(a.path("table").asText());
                    int limit = Math.min(Math.max(1, a.path("limit").asInt(5)), 20);
                    try (var st = c.createStatement();
                            ResultSet rs = st.executeQuery("SELECT * FROM " + table + " LIMIT " + limit)) {
                        return rows(rs, limit);
                    }
                }));

        tools.add(dbTool(jdbcUrl, "distinct_values", "List the distinct values of a column (up to 50).",
                "{\"type\":\"object\",\"properties\":{\"table\":{\"type\":\"string\"},"
                        + "\"column\":{\"type\":\"string\"}},\"required\":[\"table\",\"column\"]}", (c, a) -> {
                    String table = ident(a.path("table").asText());
                    String column = ident(a.path("column").asText());
                    return queryColumn(c, "SELECT DISTINCT " + column + " FROM " + table + " LIMIT 50");
                }));

        return tools;
    }

    private static Tool dbTool(String url, String name, String description, String schema, SqlFn fn) {
        return new Tool() {
            @Override
            public ToolSpec spec() {
                return new ToolSpec(name, description, schema);
            }

            @Override
            public ToolResult invoke(String argumentsJson) {
                try (Connection c = DriverManager.getConnection(url)) {
                    return ToolResult.ok(fn.apply(c, MAPPER.readTree(argumentsJson)));
                } catch (Exception e) {
                    return ToolResult.error(name + " failed: " + e.getMessage());
                }
            }
        };
    }

    private static String queryColumn(Connection c, String sql) throws Exception {
        try (var st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            List<String> out = new ArrayList<>();
            while (rs.next()) {
                out.add(rs.getString(1));
            }
            return String.join(", ", out);
        }
    }

    private static String rows(ResultSet rs, int max) throws Exception {
        ResultSetMetaData md = rs.getMetaData();
        int cols = md.getColumnCount();
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= cols; i++) {
            sb.append(i > 1 ? " | " : "").append(md.getColumnLabel(i));
        }
        sb.append('\n');
        int n = 0;
        while (rs.next() && n < max) {
            for (int i = 1; i <= cols; i++) {
                sb.append(i > 1 ? " | " : "").append(rs.getString(i));
            }
            sb.append('\n');
            n++;
        }
        return sb.toString().strip();
    }

    private static String strSchema(String field) {
        return "{\"type\":\"object\",\"properties\":{\"" + field + "\":{\"type\":\"string\"}},"
                + "\"required\":[\"" + field + "\"]}";
    }

    /** Allow only simple identifiers, so an injected table/column name can't smuggle SQL. */
    private static String ident(String s) {
        if (s == null || !s.matches("[A-Za-z0-9_]+")) {
            throw new IllegalArgumentException("invalid identifier: " + s);
        }
        return s;
    }
}
