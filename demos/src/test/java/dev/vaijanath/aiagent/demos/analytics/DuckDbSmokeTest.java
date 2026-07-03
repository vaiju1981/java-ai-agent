package dev.vaijanath.aiagent.demos.analytics;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DuckDbSmokeTest {

    @TempDir
    Path tempDir;

    @Test
    void duckDbReadsCsvAutoWithoutLoadingRowsInJava() throws Exception {
        Path csv = tempDir.resolve("t.csv");
        Files.writeString(csv, "a,b\n1,x\n2,y\n");
        try (Connection c = DriverManager.getConnection("jdbc:duckdb:");
                Statement st = c.createStatement();
                ResultSet rs = st.executeQuery(
                        "select count(*) from read_csv_auto('" + csv.toAbsolutePath() + "')")) {
            rs.next();
            assertEquals(2, rs.getInt(1));
        }
    }
}
