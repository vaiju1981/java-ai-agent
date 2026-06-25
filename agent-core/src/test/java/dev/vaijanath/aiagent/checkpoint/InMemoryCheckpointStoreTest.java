package dev.vaijanath.aiagent.checkpoint;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class InMemoryCheckpointStoreTest {

    private static Checkpoint cp(String task) {
        return new Checkpoint(task, List.of(new Checkpoint.Step(1, "a", List.of(), "DONE", "r")));
    }

    @Test
    void savesAndLoads() {
        CheckpointStore store = new InMemoryCheckpointStore();
        store.save("t", "run-1", cp("task"));

        assertEquals("task", store.load("t", "run-1").orElseThrow().task());
    }

    @Test
    void missingIsEmpty() {
        assertTrue(new InMemoryCheckpointStore().load("t", "nope").isEmpty());
    }

    @Test
    void saveOverwrites() {
        CheckpointStore store = new InMemoryCheckpointStore();
        store.save("t", "run-1", cp("first"));
        store.save("t", "run-1", cp("second"));

        assertEquals("second", store.load("t", "run-1").orElseThrow().task());
    }

    @Test
    void deleteRemoves() {
        CheckpointStore store = new InMemoryCheckpointStore();
        store.save("t", "run-1", cp("task"));
        store.delete("t", "run-1");

        assertTrue(store.load("t", "run-1").isEmpty());
    }

    @Test
    void isolatesByTenantAndRunId() {
        CheckpointStore store = new InMemoryCheckpointStore();
        store.save("tenant-a", "run-1", cp("A"));
        store.save("tenant-b", "run-1", cp("B"));

        assertEquals("A", store.load("tenant-a", "run-1").orElseThrow().task());
        assertEquals("B", store.load("tenant-b", "run-1").orElseThrow().task());
        assertTrue(store.load("tenant-a", "run-2").isEmpty());
    }
}
