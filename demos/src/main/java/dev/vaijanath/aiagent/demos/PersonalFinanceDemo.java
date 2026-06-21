package dev.vaijanath.aiagent.demos;

import dev.vaijanath.aiagent.agent.Agent;
import dev.vaijanath.aiagent.agent.AgentRequest;
import dev.vaijanath.aiagent.agent.DefaultAgent;
import dev.vaijanath.aiagent.model.ModelPort;
import java.util.Map;

/**
 * A personal-finance assistant over the same synthetic transaction data, using several tools:
 * {@code categorize_merchant} (classify a merchant), {@code budget_check} (spend vs. budget), and
 * {@code sql} (any other question, at scale). The model picks the right tool per question.
 * Needs {@code AGENT_MODEL}.
 */
public final class PersonalFinanceDemo {

    private static final Map<String, Double> MONTHLY_BUDGETS = Map.of(
            "Dining", 1500.0,
            "Groceries", 5000.0,
            "Travel", 8000.0,
            "Shopping", 3000.0,
            "Transport", 1000.0,
            "Utilities", 2500.0,
            "Entertainment", 500.0);

    public static void main(String[] args) throws Exception {
        ModelPort model = Demos.modelFromEnv();
        String db = SyntheticData.createTransactionsDb(5_000);

        System.out.println("== PersonalFinanceDemo ==  model: " + model.name());
        System.out.println("tools: categorize_merchant, budget_check, sql\n");
        if (Demos.isStub(model)) {
            System.out.println("(set AGENT_MODEL to a tool-capable Ollama model to see real answers)\n");
        }

        Agent agent = DefaultAgent.builder()
                .model(model)
                .systemPrompt("You are a personal-finance assistant. Use 'categorize_merchant' to "
                        + "classify a merchant, 'budget_check' to compare a category's monthly spend "
                        + "to its budget, and 'sql' for any other question over the 'transactions' "
                        + "table (id, txn_date 'YYYY-MM-DD', merchant, category, amount). Be concise.")
                .tool(new CategorizeMerchantTool())
                .tool(new BudgetTool(db, MONTHLY_BUDGETS))
                .tool(new SqlTool(db, 50))
                .maxSteps(8)
                .build();

        String[] questions = {
            "What spending category does 'Blue Bottle Coffee' belong to?",
            "Was I within my Entertainment budget in March (month 3)?",
            "Which category is my biggest spending habit, and suggest one place to cut back.",
        };
        for (String q : questions) {
            System.out.println("> " + q);
            System.out.println(agent.run(new AgentRequest(q)).output() + "\n");
        }
    }
}
