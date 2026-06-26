package dev.vaijanath.aiagent.examples;

import dev.vaijanath.aiagent.memory.Memory;
import dev.vaijanath.aiagent.memory.TokenWindowedMemory;
import dev.vaijanath.aiagent.model.HeuristicTokenizer;
import dev.vaijanath.aiagent.model.Message;
import dev.vaijanath.aiagent.model.Tokenizer;
import java.util.List;

/**
 * Bounding context as a conversation grows. {@link TokenWindowedMemory} keeps the system message plus the
 * most recent turns that fit a token budget, dropping the oldest — so the prompt never exceeds the model's
 * window. ({@code SummarizingMemory} compresses older turns with a model instead of dropping them.)
 *
 * <p>Deterministic — no model needed. Distinct from {@code MemoryAcrossSessions}, which persists learnings
 * across runs; this is about in-conversation context size.
 */
public final class MemoryStrategies {

    private MemoryStrategies() {}

    public static void main(String[] args) {
        Tokenizer tokenizer = new HeuristicTokenizer();
        Memory windowed = new TokenWindowedMemory(tokenizer, 160); // small budget so eviction is visible

        windowed.add(Message.system("You are a concise travel assistant."));
        int turns = 12;
        for (int i = 1; i <= turns; i++) {
            windowed.add(Message.user("Turn " + i + ": what's a must-see in city number " + i + "?"));
            windowed.add(Message.assistant(
                    "In city " + i + ", don't miss the old town square and the riverside walk at dusk."));
        }

        List<Message> kept = windowed.history();
        System.out.printf("Added %d messages across %d turns.%n", 1 + turns * 2, turns);
        System.out.printf("Token-windowed memory keeps %d: the system message + the most recent turns "
                + "that fit the 160-token budget.%n", kept.size());
        System.out.println("  first kept: " + kept.get(0).content());
        System.out.println("  last kept:  " + kept.get(kept.size() - 1).content());
    }
}
