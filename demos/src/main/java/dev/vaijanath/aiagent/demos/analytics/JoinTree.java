package dev.vaijanath.aiagent.demos.analytics;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A minimal spanning tree of tables connecting a query's requested tables, built by BFS over the
 * SemanticModel's discovered relationships. Bridges through tables that weren't directly requested
 * (e.g. a middle table in a 3-hop chain) when needed to connect the ones that were.
 */
final class JoinTree {

    private static final int MAX_JOIN_TABLES = 6;

    record Edge(String parent, String child, Relationship relationship, boolean parentIsFrom) {}

    private final String root;
    private final List<Edge> edges;
    private final Set<String> factTables;

    private JoinTree(String root, List<Edge> edges, Set<String> factTables) {
        this.root = root;
        this.edges = edges;
        this.factTables = factTables;
    }

    String root() {
        return root;
    }

    List<Edge> edges() {
        return edges;
    }

    Set<String> factTables() {
        return factTables;
    }

    Set<String> tables() {
        Set<String> t = new LinkedHashSet<>(List.of(root));
        edges.forEach(e -> t.add(e.child()));
        return t;
    }

    static JoinTree connect(SemanticModel model, Set<String> requestedTables, Set<String> factTables) {
        Map<String, List<Edge>> adjacency = new LinkedHashMap<>();
        for (Relationship r : model.relationships()) {
            adjacency.computeIfAbsent(r.fromTable(), k -> new ArrayList<>())
                    .add(new Edge(r.fromTable(), r.toTable(), r, true));
            adjacency.computeIfAbsent(r.toTable(), k -> new ArrayList<>())
                    .add(new Edge(r.toTable(), r.fromTable(), r, false));
        }
        String root = requestedTables.iterator().next();
        Set<String> visited = new LinkedHashSet<>(List.of(root));
        Map<String, Edge> parentEdge = new LinkedHashMap<>();
        Deque<String> queue = new ArrayDeque<>(List.of(root));
        while (!queue.isEmpty()) {
            String table = queue.poll();
            for (Edge e : adjacency.getOrDefault(table, List.of())) {
                if (visited.add(e.child())) {
                    parentEdge.put(e.child(), e);
                    queue.add(e.child());
                }
            }
        }
        if (!visited.containsAll(requestedTables)) {
            List<String> missing = requestedTables.stream().filter(t -> !visited.contains(t)).sorted().toList();
            throw new IllegalArgumentException("no discovered relationship path connects: " + missing);
        }
        List<Edge> tree = prune(parentEdge, requestedTables);
        Set<String> planTables = new LinkedHashSet<>(List.of(root));
        tree.forEach(e -> planTables.add(e.child()));
        if (planTables.size() > MAX_JOIN_TABLES) {
            throw new UnsupportedOperationException(
                    "query spans more than " + MAX_JOIN_TABLES + " tables; not planned yet");
        }
        return new JoinTree(root, tree, factTables);
    }

    private static List<Edge> prune(Map<String, Edge> parentEdge, Set<String> requestedTables) {
        Set<String> kept = new LinkedHashSet<>();
        List<Edge> edges = new ArrayList<>();
        for (String table : requestedTables) {
            String cur = table;
            while (parentEdge.containsKey(cur) && kept.add(cur)) {
                Edge e = parentEdge.get(cur);
                edges.add(e);
                cur = e.parent();
            }
        }
        return edges;
    }
}
