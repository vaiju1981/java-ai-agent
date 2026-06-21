package dev.vaijanath.aiagent.model;

import java.util.function.Consumer;

/**
 * A {@link ModelPort} that can stream its reply token-by-token. {@code chat} is provided as a default
 * that simply discards the stream, so a streaming port is still a drop-in {@link ModelPort}.
 */
@FunctionalInterface
public interface StreamingModelPort extends ModelPort {

    /** Streams text chunks to {@code onToken} as they arrive and returns the final response. */
    ModelResponse chatStream(ModelRequest request, Consumer<String> onToken);

    @Override
    default ModelResponse chat(ModelRequest request) {
        return chatStream(request, token -> { });
    }
}
