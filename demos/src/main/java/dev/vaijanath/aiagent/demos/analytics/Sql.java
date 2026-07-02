package dev.vaijanath.aiagent.demos.analytics;

/** Shared SQL identifier quoting, used by every SQL-family {@link DataSource} and the profiler/planner. */
final class Sql {

    private Sql() {}

    static String quote(String id) {
        return "\"" + id.replace("\"", "\"\"") + "\"";
    }

    static String qcol(String table, String column) {
        return quote(table) + "." + quote(column);
    }
}
