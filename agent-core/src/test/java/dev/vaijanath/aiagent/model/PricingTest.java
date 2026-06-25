package dev.vaijanath.aiagent.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import org.junit.jupiter.api.Test;

class PricingTest {

    private static final double EPS = 1e-9;

    @Test
    void tokenPriceComputesCostPerMillion() {
        TokenPrice price = new TokenPrice(2.50, 10.00); // $/1M in, $/1M out
        // 1,000 in -> 0.0025 ; 500 out -> 0.005
        assertEquals(0.0075, price.cost(new Usage(1_000, 500)), EPS);
    }

    @Test
    void freeCostsNothing() {
        assertEquals(0.0, TokenPrice.FREE.cost(new Usage(1_000_000, 1_000_000)), EPS);
    }

    @Test
    void negativePricesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new TokenPrice(-1, 0));
        assertThrows(IllegalArgumentException.class, () -> new TokenPrice(0, -1));
    }

    @Test
    void pricingSumsAcrossModelsAndTreatsUnpricedAsFree() {
        Pricing pricing = new Pricing(Map.of(
                "openai:gpt-4o", new TokenPrice(2.50, 10.00),
                "anthropic:claude-sonnet-4-6", new TokenPrice(3.00, 15.00)));

        Map<String, Usage> byModel = Map.of(
                "openai:gpt-4o", new Usage(1_000, 500), // 0.0075
                "anthropic:claude-sonnet-4-6", new Usage(2_000, 1_000), // 0.006 + 0.015 = 0.021
                "ollama:gemma4", new Usage(100_000, 100_000)); // unpriced -> free

        assertEquals(0.0075, pricing.cost("openai:gpt-4o", byModel.get("openai:gpt-4o")), EPS);
        assertEquals(TokenPrice.FREE, pricing.priceOf("ollama:gemma4"));
        assertEquals(0.0285, pricing.total(byModel), EPS);
    }
}
