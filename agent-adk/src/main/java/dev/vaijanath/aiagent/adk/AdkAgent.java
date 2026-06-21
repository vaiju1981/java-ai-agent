package dev.vaijanath.aiagent.adk;

import com.google.adk.agents.BaseAgent;
import com.google.adk.events.Event;
import com.google.adk.runner.InMemoryRunner;
import com.google.adk.runner.Runner;
import com.google.adk.sessions.Session;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import dev.vaijanath.aiagent.agent.Agent;
import dev.vaijanath.aiagent.agent.AgentRequest;
import dev.vaijanath.aiagent.agent.AgentResponse;
import java.util.List;
import java.util.Objects;

/**
 * Wraps a Google ADK agent as a java-ai-agent {@link Agent} (the agent-as-component seam): ADK is a
 * full agent framework, so it is consumed one level up — a wrapped ADK agent is just an
 * orchestratable black box that the runtime (deep agents, etc.) can drive like any other.
 *
 * <p>Per turn it creates an ADK session, sends the input as {@link Content}, drains the resulting
 * event stream, and returns the final response text.
 */
public final class AdkAgent implements Agent {

    private final Runner runner;
    private final String userId;

    /** Wrap an ADK agent using an in-memory runner and a default user id. */
    public AdkAgent(BaseAgent agent) {
        this(new InMemoryRunner(agent), "user");
    }

    /** Wrap an ADK agent via a fully-configured runner (custom session/artifact/memory services). */
    public AdkAgent(Runner runner, String userId) {
        this.runner = Objects.requireNonNull(runner, "runner");
        this.userId = Objects.requireNonNull(userId, "userId");
    }

    @Override
    public AgentResponse run(AgentRequest request) {
        Session session = runner.sessionService().createSession(runner.appName(), userId).blockingGet();
        Content content = Content.fromParts(Part.fromText(request.input()));
        List<Event> events = runner.runAsync(userId, session.id(), content).toList().blockingGet();
        return AgentResponse.completed(extractFinalText(events));
    }

    /** Reduces an ADK event stream to its answer: the last final-response text, else the last text. */
    static String extractFinalText(List<Event> events) {
        String lastFinal = "";
        String lastAny = "";
        for (Event e : events) {
            String text = e.content().map(Content::text).orElse("");
            if (text == null || text.isBlank()) {
                continue;
            }
            lastAny = text;
            if (e.finalResponse()) {
                lastFinal = text;
            }
        }
        return !lastFinal.isBlank() ? lastFinal : lastAny;
    }
}
