package dev.vaijanath.aiagent.demos;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.Random;

/** Builds a synthetic SQLite database of transactions, so demos can query a realistically large set. */
public final class SyntheticData {

    private SyntheticData() {}

    /** {merchant, category} pairs the generator draws from. */
    private static final String[][] MERCHANTS = {
        {"Whole Foods", "Groceries"}, {"Trader Joe's", "Groceries"}, {"Safeway", "Groceries"},
        {"Shell", "Transport"}, {"Chevron", "Transport"}, {"Uber", "Transport"},
        {"Starbucks", "Dining"}, {"Chipotle", "Dining"}, {"McDonald's", "Dining"},
        {"Amazon", "Shopping"}, {"Target", "Shopping"}, {"Best Buy", "Shopping"},
        {"Netflix", "Entertainment"}, {"Spotify", "Entertainment"}, {"Steam", "Entertainment"},
        {"PG&E", "Utilities"}, {"Comcast", "Utilities"}, {"AT&T", "Utilities"},
        {"Delta", "Travel"}, {"Marriott", "Travel"}, {"Airbnb", "Travel"},
    };

    /** Creates a temp SQLite DB with {@code rows} transactions and returns its JDBC URL. */
    public static String createTransactionsDb(int rows) throws Exception {
        Path file = Files.createTempFile("jaa-demo-", ".sqlite");
        file.toFile().deleteOnExit();
        String url = "jdbc:sqlite:" + file;

        try (Connection c = DriverManager.getConnection(url); Statement s = c.createStatement()) {
            s.execute("CREATE TABLE transactions ("
                    + "id INTEGER PRIMARY KEY AUTOINCREMENT, txn_date TEXT, merchant TEXT, "
                    + "category TEXT, amount REAL)");
            c.setAutoCommit(false);
            try (PreparedStatement ps = c.prepareStatement(
                    "INSERT INTO transactions(txn_date, merchant, category, amount) VALUES(?,?,?,?)")) {
                Random rnd = new Random(42); // deterministic
                LocalDate start = LocalDate.of(2025, 1, 1);
                for (int i = 0; i < rows; i++) {
                    String[] mc = MERCHANTS[rnd.nextInt(MERCHANTS.length)];
                    LocalDate date = start.plusDays(rnd.nextInt(365));
                    double amount = Math.round((2 + rnd.nextDouble() * baseFor(mc[1])) * 100.0) / 100.0;
                    ps.setString(1, date.toString());
                    ps.setString(2, mc[0]);
                    ps.setString(3, mc[1]);
                    ps.setDouble(4, amount);
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

    public static int count(String jdbcUrl, String table) throws Exception {
        try (Connection c = DriverManager.getConnection(jdbcUrl);
                Statement s = c.createStatement();
                ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM " + table)) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private static double baseFor(String category) {
        return switch (category) {
            case "Travel" -> 800;
            case "Shopping" -> 300;
            case "Utilities" -> 200;
            case "Groceries" -> 150;
            case "Transport" -> 80;
            case "Dining" -> 40;
            default -> 30;
        };
    }
}
