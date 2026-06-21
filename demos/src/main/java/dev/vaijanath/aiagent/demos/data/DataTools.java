package dev.vaijanath.aiagent.demos.data;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.vaijanath.aiagent.demos.Sql;
import dev.vaijanath.aiagent.demos.SqlTool;
import dev.vaijanath.aiagent.tool.Tool;
import dev.vaijanath.aiagent.tool.ToolEffect;
import dev.vaijanath.aiagent.tool.ToolResult;
import dev.vaijanath.aiagent.tool.ToolSpec;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** A data-exploration toolkit over a SQLite database: schema discovery, a group-by aggregator, and SQL. */
final class DataTools {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Set<String> AGGREGATES = Set.of("count", "sum", "avg", "min", "max");

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
                    String table = Sql.ident(a.path("table").asText());
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
                    String table = Sql.ident(a.path("table").asText());
                    try (var st = c.createStatement();
                            ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM " + table)) {
                        rs.next();
                        return String.valueOf(rs.getLong(1));
                    }
                }));

        tools.add(dbTool(jdbcUrl, "sample_rows", "Return a few sample rows from a table.",
                "{\"type\":\"object\",\"properties\":{\"table\":{\"type\":\"string\"},"
                        + "\"limit\":{\"type\":\"integer\"}},\"required\":[\"table\"]}", (c, a) -> {
                    String table = Sql.ident(a.path("table").asText());
                    int limit = Math.min(Math.max(1, a.path("limit").asInt(5)), 20);
                    try (var st = c.createStatement();
                            ResultSet rs = st.executeQuery("SELECT * FROM " + table + " LIMIT " + limit)) {
                        return Sql.table(rs, limit);
                    }
                }));

        tools.add(dbTool(jdbcUrl, "distinct_values", "List the distinct values of a column (up to 50).",
                "{\"type\":\"object\",\"properties\":{\"table\":{\"type\":\"string\"},"
                        + "\"column\":{\"type\":\"string\"}},\"required\":[\"table\",\"column\"]}", (c, a) -> {
                    String table = Sql.ident(a.path("table").asText());
                    String column = Sql.ident(a.path("column").asText());
                    return queryColumn(c, "SELECT DISTINCT " + column + " FROM " + table + " LIMIT 50");
                }));

        tools.add(dbTool(jdbcUrl, "aggregate",
                "Group a table by one column and aggregate another: op is count, sum, avg, min, or max. "
                        + "Returns up to 50 groups sorted by the result, highest first.",
                "{\"type\":\"object\",\"properties\":{\"table\":{\"type\":\"string\"},"
                        + "\"group_by\":{\"type\":\"string\"},\"op\":{\"type\":\"string\"},"
                        + "\"value\":{\"type\":\"string\"}},\"required\":[\"table\",\"group_by\",\"op\"]}",
                (c, a) -> {
                    String table = Sql.ident(a.path("table").asText());
                    String groupBy = Sql.ident(a.path("group_by").asText());
                    String op = a.path("op").asText("").toLowerCase(Locale.ROOT);
                    if (!AGGREGATES.contains(op)) {
                        return "unsupported op '" + op + "' (use count, sum, avg, min, or max)";
                    }
                    String expr = op.equals("count") ? "COUNT(*)"
                            : op.toUpperCase(Locale.ROOT) + "(" + Sql.ident(a.path("value").asText()) + ")";
                    String sql = "SELECT " + groupBy + " AS \"" + groupBy + "\", ROUND(" + expr + ", 2) AS result "
                            + "FROM " + table + " GROUP BY " + groupBy + " ORDER BY result DESC LIMIT 50";
                    try (var st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
                        return Sql.table(rs, 50);
                    }
                }));

        return tools;
    }

    private static Tool dbTool(String url, String name, String description, String schema, SqlFn fn) {
        return new Tool() {
            @Override
            public ToolSpec spec() {
                return new ToolSpec(name, description, schema, ToolEffect.READ_ONLY);
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
            return Sql.firstColumn(rs);
        }
    }

    private static String strSchema(String field) {
        return "{\"type\":\"object\",\"properties\":{\"" + field + "\":{\"type\":\"string\"}},"
                + "\"required\":[\"" + field + "\"]}";
    }
}
