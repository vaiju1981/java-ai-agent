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
 * Memory that stays within a token budget by rolling older turns into a running summary instead of
 * dropping them. System messages and the most recent {@code minRecent} non-system messages are kept
 * verbatim; once the budget is exceeded, the oldest non-system messages are folded — one at a time,
 * oldest first — into a single summary via the {@link Summarizer}, surfaced to the model as a system
 * message. Unlike windowing, the gist of earlier turns is never silently lost.
 */
public final class SummarizingMemory implements Memory {

    private static final String SUMMARY_PREFIX = "Summary of earlier conversation:\n";

    private final Tokenizer tokenizer;
    private final Summarizer summarizer;
    private final long maxTokens;
    private final int minRecent;
    private final List<Message> systemMessages = new ArrayList<>();
    private final Deque<Message> recent = new ArrayDeque<>();
    private String summary; // null until the first fold

    public SummarizingMemory(Tokenizer tokenizer, Summarizer summarizer, long maxTokens, int minRecent) {
        this.tokenizer = Objects.requireNonNull(tokenizer, "tokenizer");
        this.summarizer = Objects.requireNonNull(summarizer, "summarizer");
        this.maxTokens = Math.max(1, maxTokens);
        this.minRecent = Math.max(1, minRecent);
    }

    @Override
    public synchronized void add(Message message) {
        if (message.role() == Role.SYSTEM) {
            systemMessages.add(message);
            return;
        }
        recent.addLast(message);
        while (recent.size() > minRecent && totalTokens() > maxTokens) {
            fold(recent.removeFirst());
        }
    }

    @Override
    public synchronized List<Message> history() {
        List<Message> result = new ArrayList<>(systemMessages);
        if (summary != null) {
            result.add(Message.system(SUMMARY_PREFIX + summary));
        }
        result.addAll(recent);
        return List.copyOf(result);
    }

    /** Re-summarizes the prior summary together with one newly-evicted message. */
    private void fold(Message older) {
        List<Message> toSummarize = new ArrayList<>();
        if (summary != null) {
            toSummarize.add(Message.assistant(SUMMARY_PREFIX + summary));
        }
        toSummarize.add(older);
        summary = summarizer.summarize(toSummarize);
    }

    private long totalTokens() {
        long total = 0;
        for (Message m : systemMessages) {
            total += tokenizer.countTokens(m.content());
        }
        if (summary != null) {
            total += tokenizer.countTokens(SUMMARY_PREFIX + summary);
        }
        for (Message m : recent) {
            total += tokenizer.countTokens(m.content());
        }
        return total;
    }
}
