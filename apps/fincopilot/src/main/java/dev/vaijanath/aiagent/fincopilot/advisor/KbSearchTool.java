package dev.vaijanath.aiagent.fincopilot.advisor;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.vaijanath.aiagent.rag.RetrievedChunk;
import dev.vaijanath.aiagent.rag.Retriever;
import dev.vaijanath.aiagent.tool.Tool;
import dev.vaijanath.aiagent.tool.ToolEffect;
import dev.vaijanath.aiagent.tool.ToolResult;
import dev.vaijanath.aiagent.tool.ToolSpec;
import java.util.List;

/**
 * Searches the curated finance knowledge base and returns passages with their source titles so the
 * model can ground advice and cite it. The KB is shared (not per-user), so this is a plain {@link Tool}.
 */
public final class KbSearchTool implements Tool {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String KB_TENANT = "kb";

    private final Retriever knowledgeBase;
    private final int topK;

    public KbSearchTool(Retriever knowledgeBase, int topK) {
        this.knowledgeBase = knowledgeBase;
        this.topK = Math.max(1, topK);
    }

    @Override
    public ToolSpec spec() {
        return new ToolSpec(
                "guidance_search",
                "Search the curated finance knowledge base for general guidance on a topic (e.g. budgeting, "
                        + "emergency funds, paying off debt). Returns passages with source titles to cite.",
                "{\"type\":\"object\",\"properties\":{\"query\":{\"type\":\"string\","
                        + "\"description\":\"what to look up\"}},\"required\":[\"query\"]}",
                ToolEffect.READ_ONLY);
    }

    @Override
    public ToolResult invoke(String argumentsJson) {
        String query = query(argumentsJson);
        if (query.isBlank()) {
            return ToolResult.error("guidance_search needs a 'query'");
        }
        List<RetrievedChunk> hits = knowledgeBase.retrieve(KB_TENANT, query, topK);
        if (hits.isEmpty()) {
            return ToolResult.ok("No guidance found for that topic.");
        }
        StringBuilder sb = new StringBuilder("Relevant guidance — cite the bracketed source titles:");
        for (RetrievedChunk hit : hits) {
            String title = hit.metadata().getOrDefault("title", hit.id());
            sb.append("\n[").append(title).append("] ").append(hit.text());
        }
        return ToolResult.ok(sb.toString());
    }

    private static String query(String argumentsJson) {
        try {
            JsonNode node =
                    MAPPER.readTree(argumentsJson == null || argumentsJson.isBlank() ? "{}" : argumentsJson);
            JsonNode query = node.get("query");
            return query == null || query.isNull() ? "" : query.asText().strip();
        } catch (JsonProcessingException e) {
            return "";
        }
    }
}
