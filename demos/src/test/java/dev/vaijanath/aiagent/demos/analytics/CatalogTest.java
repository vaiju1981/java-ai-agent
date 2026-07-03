package dev.vaijanath.aiagent.demos.analytics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CatalogTest {

    @TempDir
    Path tempDir;

    @Test
    void excludeSuppressesAndIncludeAddsARelationship() {
        Path file = tempDir.resolve("catalog.json");
        Catalog catalog = Catalog.load(file);
        Relationship discovered = new Relationship("a", List.of("x"), "b", List.of("x"), "many_to_one", 1.0);

        catalog.addExclude(new Catalog.ExcludeRef("a", List.of("x"), "b", List.of("x")));
        assertTrue(catalog.apply(List.of(discovered)).isEmpty(), "excluded relationship must be dropped");

        catalog.addInclude(new Catalog.RelationshipRef("c", List.of("y"), "d", List.of("y"), "many_to_one"));
        List<Relationship> applied = catalog.apply(List.of(discovered));
        assertTrue(applied.stream().anyMatch(r -> r.fromTable().equals("c") && r.toTable().equals("d")),
                "included relationship must be added");
    }

    @Test
    void excludeMatchesOnToColumnsTooNotJustFromColumns() {
        Path file = tempDir.resolve("catalog3.json");
        Catalog catalog = Catalog.load(file);
        Relationship viaOrderId = new Relationship("orders", List.of("customerId"), "customers",
                List.of("id"), "many_to_one", 1.0);
        Relationship viaEmail = new Relationship("orders", List.of("customerId"), "customers",
                List.of("email"), "many_to_one", 1.0);

        catalog.addExclude(new Catalog.ExcludeRef("orders", List.of("customerId"), "customers", List.of("id")));
        List<Relationship> applied = catalog.apply(List.of(viaOrderId, viaEmail));

        assertTrue(applied.stream().anyMatch(r -> r.toColumns().equals(List.of("email"))),
                "relationship with a different toColumns must survive");
        assertTrue(applied.stream().noneMatch(r -> r.toColumns().equals(List.of("id"))),
                "relationship matching all four fields must be excluded");
    }

    @Test
    void descriptionsPersistAcrossReload() {
        Path file = tempDir.resolve("catalog2.json");
        Catalog.load(file).setColumnDescription("orders", "trueNetWin", "net win adjusted for promos");

        Catalog reloaded = Catalog.load(file);
        assertEquals("net win adjusted for promos", reloaded.descriptionForColumn("orders", "trueNetWin"));
        assertNull(reloaded.descriptionForColumn("orders", "other"));
    }
}
