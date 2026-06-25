package dev.vaijanath.aiagent.model;

/**
 * Per-token pricing for a model, expressed per 1,000,000 tokens — the unit vendors quote in.
 *
 * <p><b>Bring your own numbers.</b> The library ships no price table: list prices change often and
 * many accounts bill at negotiated or committed-use rates, so a baked-in number would be wrong as often
 * as right. Supply current prices for the models you use (typically via a {@link Pricing} table).
 * {@link #FREE} models a local or no-cost model (e.g. Ollama).
 *
 * <pre>{@code
 * TokenPrice gpt4o = new TokenPrice(2.50, 10.00); // $/1M input, $/1M output
 * double cost = gpt4o.cost(new Usage(1_000, 500)); // 0.0025 + 0.005 = 0.0075
 * }</pre>
 */
public record TokenPrice(double inputPer1M, double outputPer1M) {

    /** A local/no-cost model: every call is free. */
    public static final TokenPrice FREE = new TokenPrice(0, 0);

    public TokenPrice {
        if (inputPer1M < 0 || outputPer1M < 0) {
            throw new IllegalArgumentException("prices must be >= 0");
        }
    }

    /** The cost, in the price's currency, of the given token usage at this price. */
    public double cost(Usage usage) {
        return inputPer1M * usage.inputTokens() / 1_000_000.0
                + outputPer1M * usage.outputTokens() / 1_000_000.0;
    }
}
