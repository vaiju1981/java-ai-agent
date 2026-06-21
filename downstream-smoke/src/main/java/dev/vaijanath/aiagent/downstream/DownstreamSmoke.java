package dev.vaijanath.aiagent.downstream;

import com.google.adk.agents.BaseAgent;
import dev.langchain4j.mcp.client.McpClient;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.StreamingChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.vaijanath.aiagent.adk.AdkAgent;
import dev.vaijanath.aiagent.agent.Agent;
import dev.vaijanath.aiagent.langchain4j.LangChain4jEpisodicStore;
import dev.vaijanath.aiagent.langchain4j.LangChain4jModelPort;
import dev.vaijanath.aiagent.langchain4j.LangChain4jStreamingModelPort;
import dev.vaijanath.aiagent.mcp.McpTools;
import dev.vaijanath.aiagent.memory.EpisodicStore;
import dev.vaijanath.aiagent.model.ModelPort;
import dev.vaijanath.aiagent.model.StreamingModelPort;
import dev.vaijanath.aiagent.observe.AgentObserver;
import dev.vaijanath.aiagent.observe.otel.OtelAgentObserver;
import dev.vaijanath.aiagent.springai.SpringAiModelPort;
import dev.vaijanath.aiagent.tool.Tool;
import io.opentelemetry.api.trace.Tracer;
import java.util.List;

/**
 * Compiles against each adapter's public entry points using only the dependency types those adapters
 * expose <em>transitively</em> (LangChain4j / Spring AI {@code ChatModel}, ADK {@code BaseAgent}, the
 * MCP client, OpenTelemetry {@code Tracer}). If any adapter declared such a dependency
 * {@code implementation} instead of {@code api}, this module would fail to compile — making it a
 * downstream-consumer check on the published dependency scopes. Methods need not run; compilation is
 * the test.
 */
public final class DownstreamSmoke {

    private DownstreamSmoke() {}

    public static ModelPort langchain4j(ChatModel model) {
        return new LangChain4jModelPort(model);
    }

    public static StreamingModelPort langchain4jStreaming(StreamingChatModel model) {
        return new LangChain4jStreamingModelPort(model);
    }

    public static EpisodicStore langchain4jMemory(EmbeddingModel embeddings) {
        return new LangChain4jEpisodicStore(embeddings);
    }

    public static ModelPort springAi(org.springframework.ai.chat.model.ChatModel model) {
        return new SpringAiModelPort(model);
    }

    public static Agent adk(BaseAgent agent) {
        return new AdkAgent(agent);
    }

    public static List<Tool> mcp(McpClient client) {
        return McpTools.from(client);
    }

    public static AgentObserver otel(Tracer tracer) {
        return new OtelAgentObserver(tracer);
    }
}
