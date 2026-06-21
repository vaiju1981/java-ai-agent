package dev.vaijanath.aiagent.demos.data;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vaijanath.aiagent.demos.SyntheticData;
import dev.vaijanath.aiagent.tool.Tool;
import java.util.List;
import org.junit.jupiter.api.Test;

class DataToolsTest {

    private static Tool byName(List<Tool> tools, String name) {
        return tools.stream().filter(t -> t.name().equals(name)).findFirst().orElseThrow();
    }

    @Test
    void exploresTheSchema() throws Exception {
        String db = SyntheticData.createTransactionsDb(50);
        List<Tool> tk = DataTools.toolkit(db);

        assertTrue(byName(tk, "list_tables").invoke("{}").content().contains("transactions"));
        assertTrue(byName(tk, "describe_table").invoke("{\"table\":\"transactions\"}").content().contains("amount"));
        assertEquals(String.valueOf(SyntheticData.count(db, "transactions")),
                byName(tk, "row_count").invoke("{\"table\":\"transactions\"}").content());
        assertTrue(byName(tk, "distinct_values")
                .invoke("{\"table\":\"transactions\",\"column\":\"category\"}").content().contains("Travel"));
    }

    @Test
    void aggregatesByGroup() throws Exception {
        String db = SyntheticData.createTransactionsDb(200);
        Tool aggregate = byName(DataTools.toolkit(db), "aggregate");

        String sum = aggregate.invoke(
                "{\"table\":\"transactions\",\"group_by\":\"category\",\"op\":\"sum\",\"value\":\"amount\"}").content();
        assertTrue(sum.contains("category"), sum);
        assertTrue(sum.contains("Travel"), sum);

        String bad = aggregate.invoke(
                "{\"table\":\"transactions\",\"group_by\":\"category\",\"op\":\"median\",\"value\":\"amount\"}")
                .content();
        assertTrue(bad.contains("unsupported"), bad);
    }

    @Test
    void rejectsInjectedIdentifiers() throws Exception {
        String db = SyntheticData.createTransactionsDb(10);
        Tool describe = byName(DataTools.toolkit(db), "describe_table");
        assertTrue(describe.invoke("{\"table\":\"x; DROP TABLE transactions\"}").error());
    }
}
