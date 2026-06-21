package dev.vaijanath.aiagent.model;

/** Token usage reported by a model. {@link #UNKNOWN} means the provider didn't report counts. */
public record Usage(long inputTokens, long outputTokens) {

    public static final Usage UNKNOWN = new Usage(0, 0);

    public long totalTokens() {
        return inputTokens + outputTokens;
    }

    public Usage plus(Usage other) {
        return new Usage(inputTokens + other.inputTokens, outputTokens + other.outputTokens);
    }
}
