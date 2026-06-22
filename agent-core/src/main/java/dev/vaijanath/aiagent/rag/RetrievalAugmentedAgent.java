package dev.vaijanath.aiagent.rag;

import dev.vaijanath.aiagent.agent.Agent;
import dev.vaijanath.aiagent.agent.AgentRequest;
import dev.vaijanath.aiagent.agent.AgentResponse;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Grounds an agent in retrieved context: before delegating, it retrieves the top-{@code k} chunks for
 * the user's input and prepends them to the prompt, so the wrapped agent answers from real sources
 * rather than parametric memory. When nothing is retrieved, it delegates the request unchanged.
 *
 * <p>The retrieved context is woven into the user turn (the only injection point the {@link Agent}
 * seam exposes), so it becomes part of what the wrapped agent stores in its conversation memory.
 */
public final class RetrievalAugmentedAgent implements Agent {

    private static final int DEFAULT_TOP_K = 4;

    private final Agent delegate;
    private final Retriever retriever;
    private final int topK;

    public RetrievalAugmentedAgent(Agent delegate, Retriever retriever) {
        this(delegate, retriever, DEFAULT_TOP_K);
    }

    public RetrievalAugmentedAgent(Agent delegate, Retriever retriever, int topK) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.retriever = Objects.requireNonNull(retriever, "retriever");
        this.topK = Math.max(1, topK);
    }

    @Override
    public AgentResponse run(AgentRequest request) {
        List<RetrievedChunk> chunks =
                retriever.retrieve(request.context().tenant(), request.input(), topK);
        if (chunks.isEmpty()) {
            return delegate.run(request);
        }
        String context = chunks.stream().map(c -> "- " + c.text()).collect(Collectors.joining("\n"));
        String augmented = "Answer using the retrieved context below. If it does not contain the answer, "
                + "say so rather than guessing.\n\nContext:\n" + context + "\n\nQuestion: " + request.input();
        return delegate.run(new AgentRequest(augmented, request.context()));
    }
}
