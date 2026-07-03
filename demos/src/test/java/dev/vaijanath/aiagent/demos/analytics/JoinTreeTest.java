package dev.vaijanath.aiagent.demos.analytics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class JoinTreeTest {

    private static SemanticModel modelOf(Relationship... rels) {
        return new SemanticModel(List.of(), List.of(), List.of(), List.of(), List.of(rels));
    }

    @Test
    void connectsThreeTablesInAChain() {
        Relationship ab = new Relationship("a", List.of("k"), "b", List.of("k"), "many_to_one", 1.0);
        Relationship bc = new Relationship("b", List.of("k"), "c", List.of("k"), "many_to_one", 1.0);
        JoinTree tree = JoinTree.connect(modelOf(ab, bc), Set.of("a", "c"), Set.of("a"));
        assertEquals(Set.of("a", "b", "c"), tree.tables(), "bridges through 'b' even though it wasn't requested");
    }

    @Test
    void throwsAClearErrorWhenNoPathConnects() {
        // Set.of(...) iteration order is JVM-run-dependent, so whichever of "a"/"z" isn't picked as BFS
        // root is the one reported unreachable -- assert on whichever one that turns out to be, not a
        // hardcoded name, so this doesn't flake depending on root selection.
        Relationship ab = new Relationship("a", List.of("k"), "b", List.of("k"), "many_to_one", 1.0);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> JoinTree.connect(modelOf(ab), Set.of("a", "z"), Set.of("a")));
        assertTrue(ex.getMessage().contains("a") || ex.getMessage().contains("z"),
                "error should name the unreachable table: " + ex.getMessage());
    }

    @Test
    void rejectsPlansOverTheTableCap() {
        List<Relationship> rels = new java.util.ArrayList<>();
        for (int i = 0; i < 7; i++) {
            rels.add(new Relationship("t" + i, List.of("k"), "t" + (i + 1), List.of("k"), "many_to_one", 1.0));
        }
        SemanticModel model = modelOf(rels.toArray(new Relationship[0]));
        assertThrows(UnsupportedOperationException.class,
                () -> JoinTree.connect(model, Set.of("t0", "t7"), Set.of("t0")));
    }

    @Test
    void prunesUnrelatedBranchesBeforeTheTableCap() {
        // Create a branching graph: 'a' fans out to 'b' (requested) and separately to 'c','d','e','f','g','h'.
        // Total reachable: 8 tables (a,b,c,d,e,f,g,h), exceeding MAX_JOIN_TABLES=6.
        // But the minimal tree connecting only {a,b} should have 2 tables and succeed.
        Relationship ab = new Relationship("a", List.of("k"), "b", List.of("k"), "many_to_one", 1.0);
        Relationship ac = new Relationship("a", List.of("k"), "c", List.of("k"), "many_to_one", 1.0);
        Relationship ad = new Relationship("a", List.of("k"), "d", List.of("k"), "many_to_one", 1.0);
        Relationship ae = new Relationship("a", List.of("k"), "e", List.of("k"), "many_to_one", 1.0);
        Relationship af = new Relationship("a", List.of("k"), "f", List.of("k"), "many_to_one", 1.0);
        Relationship ag = new Relationship("a", List.of("k"), "g", List.of("k"), "many_to_one", 1.0);
        Relationship ah = new Relationship("a", List.of("k"), "h", List.of("k"), "many_to_one", 1.0);

        SemanticModel model = modelOf(ab, ac, ad, ae, af, ag, ah);
        JoinTree tree = JoinTree.connect(model, Set.of("a", "b"), Set.of("a"));

        assertEquals(Set.of("a", "b"), tree.tables(), "unrelated branches should be pruned away");
    }
}
