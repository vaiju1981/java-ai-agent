package dev.vaijanath.aiagent.demos.logs;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.Random;

/** Builds a synthetic SQLite table of application request logs for the log-analyst demo. */
final class SyntheticLogs {

    private SyntheticLogs() {}

    private static final String[] ENDPOINTS =
            {"/login", "/search", "/cart", "/checkout", "/api/orders", "/api/users", "/health"};

    /** Creates a temp SQLite DB with {@code rows} log lines and returns its JDBC URL. */
    static String createLogsDb(int rows) throws Exception {
        Path file = Files.createTempFile("jaa-logs-", ".sqlite");
        file.toFile().deleteOnExit();
        String url = "jdbc:sqlite:" + file;

        try (Connection c = DriverManager.getConnection(url); Statement s = c.createStatement()) {
            s.execute("CREATE TABLE logs ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT, ts TEXT, level TEXT, endpoint TEXT, "
                    + "status INTEGER, latency_ms INTEGER)");
            c.setAutoCommit(false);
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO logs(ts, level, endpoint, status, latency_ms) VALUES(?,?,?,?,?)")) {
                Random rnd = new Random(7); // deterministic
                LocalDateTime start = LocalDateTime.of(2025, 1, 1, 0, 0);
                for (int i = 0; i < rows; i++) {
                    String endpoint = ENDPOINTS[rnd.nextInt(ENDPOINTS.length)];
                    LocalDateTime ts = start.plusMinutes(rnd.nextInt(31 * 24 * 60)); // across January
                    int roll = rnd.nextInt(100);
                    int status = roll < 88 ? 200 : roll < 96 ? 404 : 500;
                    String level = status >= 500 ? "ERROR" : status >= 400 ? "WARN" : "INFO";
                    int base = endpoint.equals("/checkout") ? 400 : endpoint.equals("/search") ? 250 : 80;
                    int latency = base + rnd.nextInt(base) + (status >= 500 ? 1000 : 0);
                    ps.setString(1, ts.toString());
                    ps.setString(2, level);
                    ps.setString(3, endpoint);
                    ps.setInt(4, status);
                    ps.setInt(5, latency);
                    ps.addBatch();
                    if (i % 1000 == 0) {
                        ps.executeBatch();
                    }
                }
                ps.executeBatch();
            }
            c.commit();
        }
        return url;
    }

    static int count(String jdbcUrl, String table) throws Exception {
        try (Connection c = DriverManager.getConnection(jdbcUrl);
                Statement s = c.createStatement();
                ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM " + table)) {
            rs.next();
            return rs.getInt(1);
        }
    }
}
