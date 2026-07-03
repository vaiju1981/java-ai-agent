package dev.vaijanath.aiagent.demos.analytics;

import java.nio.file.Files;
import java.nio.file.Path;

final class Fixtures {
    private Fixtures() {}

    static Path customers(Path dir) throws Exception {
        Path p = dir.resolve("customers.csv");
        Files.writeString(p, """
                customer_id,segment,region,zip
                1,enterprise,east,02139
                2,smb,west,94107
                3,enterprise,east,10001
                """);
        return p;
    }

    static Path orders(Path dir) throws Exception {
        Path p = dir.resolve("orders.csv");
        Files.writeString(p, """
                order_id,customer_id,revenue,conversion_rate,ordered_at
                100,1,250.0,0.12,2025/01/02
                101,1,90.5,0.04,2025/01/03
                102,2,45.0,0.02,2025/01/03
                103,3,610.0,0.20,2025/01/04
                """);
        return p;
    }
}
