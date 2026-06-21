package dev.vaijanath.aiagent.demos;

import dev.vaijanath.aiagent.agent.Agent;
import dev.vaijanath.aiagent.agent.AgentRequest;
import dev.vaijanath.aiagent.agent.DefaultAgent;
import dev.vaijanath.aiagent.model.ModelPort;

/**
 * A data analyst over a 5,000-row SQLite database. The agent answers natural-language questions by
 * writing SQL that a tool executes — proving it scales to large data: the rows never enter the
 * prompt, only each query's aggregated result does. Needs {@code AGENT_MODEL} (a SQL-capable model).
 */
public final class DataAnalystDemo {

    public static void main(String[] args) throws Exception {
        ModelPort model = Demos.modelFromEnv();
        String db = SyntheticData.createTransactionsDb(5_000);
        int rows = SyntheticData.count(db, "transactions");

        System.out.println("== DataAnalystDemo ==  model: " + model.name());
        System.out.println("synthetic SQLite DB: " + rows + " transactions in one table\n");
        if (Demos.isStub(model)) {
            System.out.println("(set AGENT_MODEL to a SQL-capable Ollama model — the stub can't write SQL)\n");
        }

        Agent agent = DefaultAgent.builder()
                .model(model)
                .systemPrompt("You are a data analyst. The SQLite table 'transactions' has columns: "
                        + "id (int), txn_date (TEXT 'YYYY-MM-DD'), merchant (TEXT), category (TEXT), "
                        + "amount (REAL). Answer every question by calling the 'sql' tool with ONE "
                        + "read-only SELECT, then summarize the result in plain language.")
                .tool(new SqlTool(db, 50))
                .maxSteps(6)
                .build();

        String[] questions = {
            "What is the total amount spent in each category? Sort highest to lowest.",
            "Which 5 merchants did I spend the most money at?",
            "How many transactions were over $200, and what is the overall average transaction amount?",
        };
        for (String q : questions) {
            System.out.println("> " + q);
            System.out.println(agent.run(new AgentRequest(q)).output() + "\n");
        }
    }
}
