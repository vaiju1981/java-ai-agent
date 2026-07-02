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
        Relationship ab = new Relationship("a", List.of("k"), "b", List.of("k"), "many_to_one", 1.0);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> JoinTree.connect(modelOf(ab), Set.of("a", "z"), Set.of("a")));
        assertTrue(ex.getMessage().contains("z"), "error should name the unreachable table: " + ex.getMessage());
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
}
