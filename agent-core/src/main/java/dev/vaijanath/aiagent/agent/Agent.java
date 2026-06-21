package dev.vaijanath.aiagent.agent;

/**
 * The agent-as-component seam. Anything that takes a request and produces a response is an Agent —
 * including, later, a wrapped Google ADK agent or a LangChain4j AiService. The runtime can
 * orchestrate Agents (sub-agents, deep agents) without knowing what is inside them.
 */
public interface Agent {

    AgentResponse run(AgentRequest request);
}
