package dev.vaijanath.aiagent.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.vaijanath.aiagent.checkpoint.Checkpoint;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SqliteCheckpointStoreTest {

    @TempDir
    Path dir;

    private SqliteCheckpointStore store() {
        return new SqliteCheckpointStore(dir.resolve("checkpoints.db"));
    }

    private static Checkpoint sample(String task) {
        return new Checkpoint(task, List.of(
                new Checkpoint.Step(1, "one", List.of(), "DONE", "result-1"),
                new Checkpoint.Step(2, "two", List.of(1), "PENDING", "")));
    }

    @Test
    void savesAndLoadsRoundTrip() {
        SqliteCheckpointStore store = store();
        store.save("acme", "run-1", sample("write a report"));

        Checkpoint loaded = store.load("acme", "run-1").orElseThrow();
        assertEquals("write a report", loaded.task());
        assertEquals(2, loaded.steps().size());
        assertEquals("DONE", loaded.steps().get(0).status());
        assertEquals("result-1", loaded.steps().get(0).result());
        assertEquals(2, loaded.steps().get(1).index());
        assertEquals(List.of(1), loaded.steps().get(1).dependsOn()); // dependency graph survives
    }

    @Test
    void survivesAcrossStoreInstances() {
        store().save("acme", "run-1", sample("task")); // first "process"

        // A fresh instance over the same file == a restart: the checkpoint is still there.
        Optional<Checkpoint> loaded =
                new SqliteCheckpointStore(dir.resolve("checkpoints.db")).load("acme", "run-1");

        assertTrue(loaded.isPresent());
        assertEquals(2, loaded.orElseThrow().steps().size());
    }

    @Test
    void saveOverwritesPreviousProgress() {
        SqliteCheckpointStore store = store();
        store.save("acme", "run-1", sample("v1"));
        store.save("acme", "run-1", new Checkpoint("v2", List.of(
                new Checkpoint.Step(1, "one", List.of(), "DONE", "r"))));

        Checkpoint loaded = store.load("acme", "run-1").orElseThrow();
        assertEquals("v2", loaded.task());
        assertEquals(1, loaded.steps().size()); // old second step is gone
    }

    @Test
    void deleteRemovesEverythingForThatRun() {
        SqliteCheckpointStore store = store();
        store.save("acme", "run-1", sample("task"));
        store.delete("acme", "run-1");

        assertTrue(store.load("acme", "run-1").isEmpty());
    }

    @Test
    void missingRunIsEmpty() {
        assertTrue(store().load("acme", "absent").isEmpty());
    }

    @Test
    void isolatesByTenantAndRunId() {
        SqliteCheckpointStore store = store();
        store.save("tenant-a", "run-1", sample("A"));
        store.save("tenant-b", "run-1", sample("B"));

        assertEquals("A", store.load("tenant-a", "run-1").orElseThrow().task());
        assertEquals("B", store.load("tenant-b", "run-1").orElseThrow().task());
        assertTrue(store.load("tenant-a", "run-2").isEmpty());
    }
}
