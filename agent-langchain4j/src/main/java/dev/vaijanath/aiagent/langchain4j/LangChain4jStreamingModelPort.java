package dev.vaijanath.aiagent.langchain4j;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.output.TokenUsage;
import dev.vaijanath.aiagent.model.ModelRequest;
import dev.vaijanath.aiagent.model.ModelResponse;
import dev.vaijanath.aiagent.model.StreamingModelPort;
import dev.vaijanath.aiagent.model.Usage;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * A {@link StreamingModelPort} backed by any LangChain4j {@code StreamingChatModel} (e.g. Ollama).
 * Bridges LangChain4j's async streaming handler to a synchronous call that forwards each chunk to
 * {@code onToken} and returns the final response. Text-only (no tool-calling) in this phase.
 */
public final class LangChain4jStreamingModelPort implements StreamingModelPort {

    private final StreamingChatModel model;
    private final String name;

    public LangChain4jStreamingModelPort(StreamingChatModel model) {
        this(model, "langchain4j-stream");
    }

    public LangChain4jStreamingModelPort(StreamingChatModel model, String name) {
        this.model = Objects.requireNonNull(model, "model");
        this.name = name;
    }

    @Override
    public ModelResponse chatStream(ModelRequest request, Consumer<String> onToken) {
        List<ChatMessage> messages = LangChain4jMessages.toLangChain4j(request.messages());

        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<ChatResponse> complete = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        StringBuilder accumulated = new StringBuilder();

        model.chat(ChatRequest.builder().messages(messages).build(), new StreamingChatResponseHandler() {
            @Override
            public void onPartialResponse(String token) {
                if (token != null && !token.isEmpty()) {
                    accumulated.append(token);
                    onToken.accept(token);
                }
            }

            @Override
            public void onCompleteResponse(ChatResponse response) {
                complete.set(response);
                done.countDown();
            }

            @Override
            public void onError(Throwable error) {
                failure.set(error);
                done.countDown();
            }
        });

        try {
            done.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("interrupted while streaming", e);
        }
        if (failure.get() != null) {
            throw new RuntimeException("streaming failed", failure.get());
        }

        ChatResponse response = complete.get();
        String text = (response != null && response.aiMessage() != null)
                ? response.aiMessage().text()
                : accumulated.toString();
        Usage usage = (response != null) ? toUsage(response.tokenUsage()) : Usage.UNKNOWN;
        return new ModelResponse(text == null ? accumulated.toString() : text, List.of(), usage);
    }

    private static Usage toUsage(TokenUsage tokenUsage) {
        if (tokenUsage == null) {
            return Usage.UNKNOWN;
        }
        Integer in = tokenUsage.inputTokenCount();
        Integer out = tokenUsage.outputTokenCount();
        return new Usage(in == null ? 0 : in, out == null ? 0 : out);
    }

    @Override
    public String name() {
        return name;
    }
}
