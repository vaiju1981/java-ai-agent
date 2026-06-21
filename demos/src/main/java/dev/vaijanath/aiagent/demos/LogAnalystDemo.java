package dev.vaijanath.aiagent.demos;

import dev.vaijanath.aiagent.agent.Agent;
import dev.vaijanath.aiagent.agent.AgentRequest;
import dev.vaijanath.aiagent.agent.DefaultAgent;
import dev.vaijanath.aiagent.model.ModelPort;

/**
 * A log/metrics analyst over ~10,000 synthetic request logs in SQLite. Same scaling story as the
 * data-analyst demo, with a different dataset shape: the model writes SQL, the {@link SqlTool} runs
 * it, and only aggregates return. Needs {@code AGENT_MODEL}.
 */
public final class LogAnalystDemo {

    public static void main(String[] args) throws Exception {
        ModelPort model = Demos.modelFromEnv();
        String db = SyntheticLogs.createLogsDb(10_000);
        int rows = SyntheticLogs.count(db, "logs");

        System.out.println("== LogAnalystDemo ==  model: " + model.name());
        System.out.println("synthetic SQLite DB: " + rows + " log lines\n");
        if (Demos.isStub(model)) {
            System.out.println("(set AGENT_MODEL to a SQL-capable Ollama model — the stub can't write SQL)\n");
        }

        Agent agent = DefaultAgent.builder()
                .model(model)
                .systemPrompt("You are a site-reliability analyst. The SQLite table 'logs' has columns: "
                        + "id (int), ts (TEXT ISO datetime), level (TEXT: INFO/WARN/ERROR), endpoint "
                        + "(TEXT), status (int HTTP code), latency_ms (int). Answer every question with "
                        + "ONE read-only SELECT via the 'sql' tool, then summarize in plain language.")
                .tool(new SqlTool(db, 50))
                .maxSteps(6)
                .build();

        String[] questions = {
            "What is the overall error rate (percentage of logs with status >= 500)?",
            "Which 5 endpoints have the highest average latency_ms? Show the averages.",
            "How many ERROR logs occurred, and which endpoint produced the most of them?",
        };
        for (String q : questions) {
            System.out.println("> " + q);
            System.out.println(agent.run(new AgentRequest(q)).output() + "\n");
        }
    }
}
