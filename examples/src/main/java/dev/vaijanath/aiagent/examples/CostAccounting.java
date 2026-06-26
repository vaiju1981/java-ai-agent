package dev.vaijanath.aiagent.examples;

import dev.vaijanath.aiagent.model.Pricing;
import dev.vaijanath.aiagent.model.TokenPrice;
import dev.vaijanath.aiagent.model.Usage;
import dev.vaijanath.aiagent.observe.TokenAccountingObserver;
import java.util.Map;

/**
 * Per-model token accounting and bring-your-own cost. A {@link TokenAccountingObserver} attributes usage
 * to the model that produced it (a supervisor on a large model, workers on a small one), and a
 * {@link Pricing} table turns tokens into dollars — no prices are bundled, and local models are free.
 *
 * <p>Deterministic: it feeds the observer representative usage rather than needing a live run; wire
 * {@code .observer(acct)} into any agent for live numbers.
 */
public final class CostAccounting {

    private CostAccounting() {}

    public static void main(String[] args) {
        TokenAccountingObserver acct = new TokenAccountingObserver();
        acct.onUsage("openai:gpt-4o", new Usage(1_200, 800));
        acct.onUsage("openai:gpt-4o", new Usage(900, 600));
        acct.onUsage("ollama:gemma4", new Usage(5_000, 2_500));

        Pricing pricing = new Pricing(Map.of(
                "openai:gpt-4o", new TokenPrice(2.50, 10.00), // $/1M input, $/1M output
                "ollama:gemma4", TokenPrice.FREE)); // local model: free

        Map<String, Usage> byModel = acct.tokensByModel();
        System.out.println("== CostAccounting ==  per-model token accounting + bring-your-own cost\n");
        byModel.forEach((m, u) -> System.out.printf(
                "  %-16s in=%-6d out=%-6d  cost=$%.4f%n", m, u.inputTokens(), u.outputTokens(), pricing.cost(m, u)));
        long totalTokens = byModel.values().stream().mapToLong(Usage::totalTokens).sum();
        System.out.printf("%n  total: %d tokens   $%.4f%n", totalTokens, pricing.total(byModel));
    }
}
