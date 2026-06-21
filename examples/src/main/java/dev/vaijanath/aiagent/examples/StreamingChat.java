package dev.vaijanath.aiagent.examples;

import dev.vaijanath.aiagent.langchain4j.OllamaModelPorts;
import dev.vaijanath.aiagent.model.Message;
import dev.vaijanath.aiagent.model.ModelPort;
import dev.vaijanath.aiagent.model.ModelPorts;
import dev.vaijanath.aiagent.model.ModelRequest;
import dev.vaijanath.aiagent.model.StubModelPort;
import java.util.List;

/**
 * Streams a reply token-by-token. With {@code AGENT_MODEL} set, tokens print live as the model
 * generates them; offline, the stub falls back to emitting the whole reply at once via
 * {@link ModelPorts#stream}.
 */
public final class StreamingChat {

    public static void main(String[] args) {
        String modelName = System.getenv("AGENT_MODEL");
        String baseUrl = System.getenv().getOrDefault("OLLAMA_BASE_URL", "http://localhost:11434");
        ModelPort port = (modelName == null || modelName.isBlank())
                ? new StubModelPort()
                : OllamaModelPorts.ollamaStreaming(baseUrl, modelName);

        System.out.println("== StreamingChat ==  model: " + port.name() + "\n");
        System.out.println("> Write a short haiku about Java virtual threads.\n");

        ModelRequest request = ModelRequest.of(List.of(
                Message.user("Write a short haiku about Java virtual threads.")));
        ModelPorts.stream(port, request, System.out::print); // tokens print as they arrive
        System.out.println();
    }
}
