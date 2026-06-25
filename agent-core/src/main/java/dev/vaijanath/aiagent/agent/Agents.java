package dev.vaijanath.aiagent.agent;

import dev.vaijanath.aiagent.tool.ContextualTool;
import dev.vaijanath.aiagent.tool.ToolCallContext;
import dev.vaijanath.aiagent.tool.ToolEffect;
import dev.vaijanath.aiagent.tool.ToolInvocation;
import dev.vaijanath.aiagent.tool.ToolResult;
import dev.vaijanath.aiagent.tool.ToolSpec;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Exposes an {@link Agent} as a {@link dev.vaijanath.aiagent.tool.Tool}, so one agent can call another
 * as a function — the "agents as tools" pattern (OpenAI Agents SDK, Anthropic). Give a coordinating
 * agent a few specialist agents as tools and its model decides, per turn, which specialist(s) to
 * invoke and with what request — peer agents collaborating, with no extra orchestration code.
 *
 * <p>The wrapper is a {@link ContextualTool}: the caller's identity, tenant, trace, and deadline flow
 * into the specialist (in a fresh child session), and the specialist's own guardrails and tool
 * authorization still apply — so a tree of agents-as-tools stays governed end to end.
 *
 * <pre>{@code
 * Agent manager = DefaultAgent.builder()
 *     .model(model)
 *     .tool(Agents.asTool("research", "gathers and verifies facts", researchAgent))
 *     .tool(Agents.asTool("write", "drafts prose", writerAgent, ToolEffect.READ_ONLY))
 *     .build();
 * }</pre>
 */
public final class Agents {

    private Agents() {}

    /** The single tool parameter the calling model fills in with the specialist's request. */
    public static final String REQUEST_PARAM = "request";

    /**
     * Wraps {@code agent} as an <b>effectful</b> tool named {@code name} (the safe default — a
     * specialist can do whatever its own tools allow, so it is gated by {@code denyEffectful}
     * authorization unless explicitly allowed). Use {@link #asTool(String, String, Agent, ToolEffect)}
     * to declare a read-only specialist.
     */
    public static ContextualTool asTool(String name, String description, Agent agent) {
        return asTool(name, description, agent, ToolEffect.EFFECTFUL);
    }

    /** Wraps {@code agent} as a tool with an explicit {@link ToolEffect}. */
    public static ContextualTool asTool(String name, String description, Agent agent, ToolEffect effect) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(agent, "agent");
        Objects.requireNonNull(effect, "effect");
        String schema = "{\"type\":\"object\",\"properties\":{\"" + REQUEST_PARAM
                + "\":{\"type\":\"string\",\"description\":\"the request to send to the "
                + name + " agent\"}},\"required\":[\"" + REQUEST_PARAM + "\"]}";
        return new AgentTool(new ToolSpec(name, description, schema, effect), agent);
    }

    private record AgentTool(ToolSpec spec, Agent agent) implements ContextualTool {

        @Override
        public ToolSpec spec() {
            return spec;
        }

        @Override
        public ToolResult invoke(ToolInvocation invocation) {
            String request = extractString(invocation.argumentsJson(), REQUEST_PARAM);
            if (request == null || request.isBlank()) {
                request = invocation.argumentsJson(); // fall back to the raw arguments
            }
            ToolCallContext ctx = invocation.context();
            // A fresh child session, carrying the caller's identity/tenant/trace/deadline through.
            RequestContext childSession = new RequestContext(
                    UUID.randomUUID().toString(),
                    ctx.principal(),
                    ctx.tenant(),
                    ctx.traceId(),
                    ctx.deadline(),
                    Map.of());
            AgentResponse response = agent.run(new AgentRequest(request, childSession));
            return response.blocked()
                    ? ToolResult.error(response.output())
                    : ToolResult.ok(response.output());
        }
    }

    /**
     * Reads the string value of a top-level field from a JSON object, honoring standard escapes —
     * enough to pull one argument out of a tool call without a JSON dependency in the zero-dependency
     * core. Returns {@code null} if the field is absent or not a string (the caller then falls back to
     * the raw arguments). Package-private for testing.
     */
    static String extractString(String json, String field) {
        if (json == null) {
            return null;
        }
        try {
            int key = json.indexOf('"' + field + '"');
            if (key < 0) {
                return null;
            }
            int i = key + field.length() + 2;
            while (i < json.length() && json.charAt(i) != '"') {
                char c = json.charAt(i);
                if (c != ':' && !Character.isWhitespace(c)) {
                    return null; // the value is not a string (e.g. a number or object)
                }
                i++;
            }
            i++; // move past the opening quote of the value
            StringBuilder sb = new StringBuilder();
            while (i < json.length()) {
                char c = json.charAt(i++);
                if (c == '"') {
                    return sb.toString();
                }
                if (c == '\\' && i < json.length()) {
                    char escaped = json.charAt(i++);
                    switch (escaped) {
                        case '"' -> sb.append('"');
                        case '\\' -> sb.append('\\');
                        case '/' -> sb.append('/');
                        case 'n' -> sb.append('\n');
                        case 't' -> sb.append('\t');
                        case 'r' -> sb.append('\r');
                        case 'b' -> sb.append('\b');
                        case 'f' -> sb.append('\f');
                        case 'u' -> {
                            sb.append((char) Integer.parseInt(json.substring(i, i + 4), 16));
                            i += 4;
                        }
                        default -> sb.append(escaped);
                    }
                } else {
                    sb.append(c);
                }
            }
            return sb.toString();
        } catch (RuntimeException e) {
            return null; // malformed -> let the caller fall back to the raw arguments
        }
    }
}
