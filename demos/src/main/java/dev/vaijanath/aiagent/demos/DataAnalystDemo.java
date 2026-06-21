package dev.vaijanath.aiagent.demos;

import dev.vaijanath.aiagent.agent.Agent;
import dev.vaijanath.aiagent.agent.AgentRequest;
import dev.vaijanath.aiagent.agent.DefaultAgent;
import dev.vaijanath.aiagent.model.ModelPort;
import dev.vaijanath.aiagent.tool.Tool;
import java.util.List;

/**
 * A data analyst over a 5,000-row SQLite database. It has a toolkit — schema discovery
 * ({@code list_tables}, {@code describe_table}, {@code sample_rows}, {@code distinct_values},
 * {@code row_count}) plus read-only {@code sql} — so the agent can explore the data, then query it.
 * Scales because the rows stay in the DB; only results enter context. Needs {@code AGENT_MODEL}.
 */
public final class DataAnalystDemo {

    public static void main(String[] args) throws Exception {
        ModelPort model = Demos.modelFromEnv();
        String db = SyntheticData.createTransactionsDb(5_000);
        int rows = SyntheticData.count(db, "transactions");
        List<Tool> toolkit = DataTools.toolkit(db);

        System.out.println("== DataAnalystDemo ==  model: " + model.name());
        System.out.println(rows + " transactions, " + toolkit.size() + " tools (explore + query)\n");
        if (Demos.isStub(model)) {
            System.out.println("(set AGENT_MODEL to a SQL-capable Ollama model — the stub can't write SQL)\n");
        }

        DefaultAgent.Builder builder = DefaultAgent.builder()
                .model(model)
                .systemPrompt("You are a data analyst. Explore the database with list_tables, "
                        + "describe_table, sample_rows, distinct_values, and row_count, and run "
                        + "read-only queries with the sql tool. For any question, explore the schema "
                        + "if you need to, then answer in plain language.")
                .maxSteps(8);
        toolkit.forEach(builder::tool);
        Agent agent = builder.build();

        String[] questions = {
            "What tables and columns are available in this database?",
            "What distinct spending categories exist?",
            "What is the total amount spent in each category? Sort highest to lowest.",
            "Show me 3 sample transactions.",
        };
        for (String q : questions) {
            System.out.println("> " + q);
            System.out.println(agent.run(new AgentRequest(q)).output() + "\n");
        }
    }
}
