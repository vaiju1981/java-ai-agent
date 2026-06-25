package dev.vaijanath.aiagent.model;

import java.util.Map;
import java.util.Objects;

/**
 * Turns token usage into cost, given a <b>bring-your-own</b> price table keyed by model name
 * ({@link ModelPort#name()}). No prices are bundled — you register the ones you use.
 *
 * <p>A model with no registered price is treated as {@link TokenPrice#FREE} (cost {@code 0}), which is
 * the right default for local models (Ollama) and keeps an un-priced model from inventing a number; to
 * find such gaps, compare your table's keys against {@code TokenAccountingObserver.tokensByModel()}.
 *
 * <pre>{@code
 * Pricing pricing = new Pricing(Map.of(
 *     "openai:gpt-4o", new TokenPrice(2.50, 10.00),
 *     "anthropic:claude-sonnet-4-6", new TokenPrice(3.00, 15.00)));
 * double total = pricing.total(accounting.tokensByModel());
 * }</pre>
 */
public final class Pricing {

    private final Map<String, TokenPrice> prices;

    public Pricing(Map<String, TokenPrice> prices) {
        this.prices = Map.copyOf(Objects.requireNonNull(prices, "prices"));
    }

    /** The price registered for {@code model}, or {@link TokenPrice#FREE} if none is. */
    public TokenPrice priceOf(String model) {
        return prices.getOrDefault(model, TokenPrice.FREE);
    }

    /** The cost of one model's usage at its registered price (0 if the model isn't priced). */
    public double cost(String model, Usage usage) {
        return priceOf(model).cost(usage);
    }

    /** Total cost across a per-model usage breakdown (e.g. {@code TokenAccountingObserver.tokensByModel()}). */
    public double total(Map<String, Usage> usageByModel) {
        double sum = 0;
        for (Map.Entry<String, Usage> e : usageByModel.entrySet()) {
            sum += cost(e.getKey(), e.getValue());
        }
        return sum;
    }
}
