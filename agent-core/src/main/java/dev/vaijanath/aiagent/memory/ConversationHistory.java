package dev.vaijanath.aiagent.memory;

import dev.vaijanath.aiagent.model.Message;
import java.time.Instant;
import java.util.List;

/**
 * Seam for browsing and managing stored conversations: list a tenant's sessions, read one session's
 * messages, and delete a session. It is kept separate from {@link ConversationStore} (the serve seam) so a store opts
 * into history browsing by implementing this <em>additional</em> interface — the core contract is
 * unchanged, and a store that cannot enumerate (for example an opaque remote one) simply doesn't
 * implement it. Both {@link InMemoryConversationStore} and the JDBC store do.
 *
 * <p>Results are scoped by {@code tenant}, the same isolation key as {@link ConversationStore}, so an
 * application that maps a user to a tenant gets per-user history for free.
 */
public interface ConversationHistory {

    /** Summaries of the {@code tenant}'s sessions, most-recently-active first. */
    List<SessionSummary> listSessions(String tenant);

    /** The full, ordered message history of one session — empty if it has none. */
    List<Message> messages(String tenant, String sessionId);

    /** Permanently deletes one session's stored history for a tenant; a no-op if it doesn't exist. */
    void delete(String tenant, String sessionId);

    /** A session at a glance: its id, how many messages it holds, and when it was last active. */
    record SessionSummary(String sessionId, long messageCount, Instant lastActivity) {}
}
