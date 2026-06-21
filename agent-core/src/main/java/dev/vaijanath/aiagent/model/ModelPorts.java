package dev.vaijanath.aiagent.model;

import java.util.function.Consumer;

/** Helpers for working with {@link ModelPort}s. */
public final class ModelPorts {

    private ModelPorts() {}

    /**
     * Streams from {@code port} if it supports streaming; otherwise calls it normally and emits the
     * whole reply as a single chunk. Lets callers stream uniformly from any {@link ModelPort}.
     */
    public static ModelResponse stream(ModelPort port, ModelRequest request, Consumer<String> onToken) {
        if (port instanceof StreamingModelPort streaming) {
            return streaming.chatStream(request, onToken);
        }
        ModelResponse response = port.chat(request);
        if (response.text() != null && !response.text().isEmpty()) {
            onToken.accept(response.text());
        }
        return response;
    }
}
