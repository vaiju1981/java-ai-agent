package dev.vaijanath.aiagent.checkpoint;

import java.util.Optional;

/**
 * Durable storage for an in-flight orchestration's progress, so a run that crashes can resume instead
 * of starting over. A {@code (tenant, runId)} pair keys one run: an orchestrator saves a
 * {@link Checkpoint} as it makes progress, restores it on a retry with the same key, and deletes it
 * once the run completes cleanly. Implementations must be safe for concurrent calls.
 *
 * <p>The default {@link InMemoryCheckpointStore} survives only within a process; a connector (e.g.
 * {@code SqliteCheckpointStore} in {@code agent-store-sqlite}) survives a restart. Wiring a store into
 * an orchestrator is opt-in — without one, behavior is unchanged and nothing is persisted.
 */
public interface CheckpointStore {

    /** The saved progress for {@code (tenant, runId)}, or empty if there is none. */
    Optional<Checkpoint> load(String tenant, String runId);

    /** Saves the progress for {@code (tenant, runId)}, replacing any existing snapshot. */
    void save(String tenant, String runId, Checkpoint checkpoint);

    /** Drops the saved progress for {@code (tenant, runId)} — called when a run finishes. */
    void delete(String tenant, String runId);
}
