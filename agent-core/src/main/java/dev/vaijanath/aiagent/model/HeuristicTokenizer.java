package dev.vaijanath.aiagent.model;

/**
 * A dependency-free token estimate: roughly one token per four characters (the common rule of thumb
 * for English text). Good enough for budgeting and memory windowing; swap in a provider tokenizer
 * when exact counts matter.
 */
public final class HeuristicTokenizer implements Tokenizer {

    private static final int CHARS_PER_TOKEN = 4;

    @Override
    public int countTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        return (text.length() + CHARS_PER_TOKEN - 1) / CHARS_PER_TOKEN;
    }
}
