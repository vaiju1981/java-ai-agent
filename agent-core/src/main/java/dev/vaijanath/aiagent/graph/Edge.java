package dev.vaijanath.aiagent.graph;

/**
 * Chooses the next node from the state the current node produced — the conditional transition of a
 * {@link GraphAgent}. Return a node name to continue, or {@link GraphAgent#END} (or an unknown name)
 * to finish. Because the choice is made each visit, an edge can route back to an earlier node, so the
 * graph supports cycles (bounded by the step budget).
 */
@FunctionalInterface
public interface Edge {

    String next(String state);
}
