package dev.vaijanath.aiagent.demos.support;

import dev.vaijanath.aiagent.demos.support.TicketTriager.TriageResult;
import dev.vaijanath.aiagent.langchain4j.OllamaModelPorts;
import dev.vaijanath.aiagent.model.StructuredOutput;
import java.util.ArrayList;
import java.util.List;

/**
 * Triages a batch of synthetic support tickets into structured {priority, category, team} using
 * {@link StructuredOutput} (JSON bound to a record, no parsing), then prints a summary. Showcases
 * reliable classification at scale. Needs {@code AGENT_MODEL}.
 */
public final class SupportTriageDemo {

    public static void main(String[] args) {
        String modelName = System.getenv("AGENT_MODEL");
        String baseUrl = System.getenv().getOrDefault("OLLAMA_BASE_URL", "http://localhost:11434");

        System.out.println("== SupportTriageDemo ==");
        if (modelName == null || modelName.isBlank()) {
            System.out.println("(set AGENT_MODEL to a JSON-capable Ollama model — triage needs structured output)");
            return;
        }

        StructuredOutput structured = OllamaModelPorts.ollamaStructured(baseUrl, modelName);
        TicketTriager triager = new TicketTriager(structured);
        List<Ticket> tickets = SyntheticTickets.generate(12);

        System.out.println("triaging " + tickets.size() + " tickets...\n");
        List<TriageResult> results = new ArrayList<>();
        for (Ticket t : tickets) {
            TriageResult r = triager.triage(t);
            results.add(r);
            System.out.printf("#%-2d [%-6s %-14s %-12s] %s%n",
                    t.id(), r.priority(), r.category(), r.team(), t.subject());
        }

        System.out.println("\n--- summary ---");
        System.out.println(TicketStats.summary(results));
    }
}
