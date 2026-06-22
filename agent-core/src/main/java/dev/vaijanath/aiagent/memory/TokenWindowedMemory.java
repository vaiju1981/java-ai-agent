package dev.vaijanath.aiagent.memory;

import dev.vaijanath.aiagent.model.Message;
import dev.vaijanath.aiagent.model.Role;
import dev.vaijanath.aiagent.model.Tokenizer;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;

/**
 * Short-term memory bounded by an estimated token budget rather than a message count: it keeps all
 * system messages plus the most recent non-system messages whose tokens (together with the system
 * messages) fit within {@code maxTokens}. More faithful to a model's context window than count-based
 * windowing, since a few long messages cost as much as many short ones. At least the latest non-system
 * message is always retained.
 */
public final class TokenWindowedMemory implements Memory {

    private final Tokenizer tokenizer;
    private final long maxTokens;
    private final List<Message> systemMessages = new ArrayList<>();
    private final Deque<Message> recent = new ArrayDeque<>();

    public TokenWindowedMemory(Tokenizer tokenizer, long maxTokens) {
        this.tokenizer = Objects.requireNonNull(tokenizer, "tokenizer");
        this.maxTokens = Math.max(1, maxTokens);
    }

    @Override
    public synchronized void add(Message message) {
        if (message.role() == Role.SYSTEM) {
            systemMessages.add(message);
            return;
        }
        recent.addLast(message);
        while (recent.size() > 1 && totalTokens() > maxTokens) {
            recent.removeFirst();
        }
    }

    @Override
    public synchronized List<Message> history() {
        List<Message> result = new ArrayList<>(systemMessages);
        result.addAll(recent);
        return List.copyOf(result);
    }

    private long totalTokens() {
        long total = 0;
        for (Message m : systemMessages) {
            total += tokenizer.countTokens(m.content());
        }
        for (Message m : recent) {
            total += tokenizer.countTokens(m.content());
        }
        return total;
    }
}
