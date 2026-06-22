package dev.vaijanath.aiagent.demos.finance;
import dev.vaijanath.aiagent.demos.Demos;
import dev.vaijanath.aiagent.demos.Governed;
import dev.vaijanath.aiagent.demos.SyntheticData;
import dev.vaijanath.aiagent.model.ModelPort;
import dev.vaijanath.aiagent.tool.Tool;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * A personal-finance assistant with a realistic, large toolkit (~24 tools): data lookups, data-aware
 * analyses over the user's actual transactions (top merchants, recurring subscriptions, budget
 * status, ...), and planning calculators. All tools are presented at once, so this also validates
 * that the agent picks the right tool when there are many. Needs {@code AGENT_MODEL}.
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
        List<Tool> toolkit = FinanceTools.toolkit(db, MONTHLY_BUDGETS);

        System.out.println("== PersonalFinanceDemo ==  model: " + model.name());
        System.out.println(toolkit.size() + " tools registered — the agent must pick the right one per question\n");
        if (Demos.isStub(model)) {
            System.out.println("(set AGENT_MODEL to a tool-capable Ollama model to see real answers)\n");
        }

        Governed.Result governed = Governed.agent(model, toolkit,
                "You are a personal-finance assistant with many tools. For each "
                        + "question, pick the single most appropriate tool and call it. Use 'sql' for "
                        + "questions about the user's actual transactions (table 'transactions': "
                        + "id, txn_date, merchant, category, amount). Be concise.");

        String[] questions = {
            "What spending category does 'Blue Bottle Coffee' belong to?",
            "What are my recurring subscriptions?",
            "Which 3 merchants do I spend the most at?",
            "How am I tracking against my category budgets in month 3?",
            "What were my 3 largest individual purchases?",
            "If I invest 10000 at 6% for 10 years with annual compounding, what is the future value?",
            "What's an 18% tip on a 54 dollar bill?",
        };
        for (String q : questions) {
            System.out.println("> " + q);
            var request = Governed.request("acme-fin", "member-1", "finance-session", q,
                    Duration.ofSeconds(60));
            System.out.println(governed.agent().run(request).output() + "\n");
        }

        Governed.printTrustReport(governed.audit(), governed.tokens());
    }
}
