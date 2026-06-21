package dev.vaijanath.aiagent.store.jdbc;

/**
 * A different service instance committed the same conversation position first. The attempted turn
 * was rolled back completely; callers may reload the session and retry the whole turn.
 */
public final class ConcurrentConversationException extends IllegalStateException {

    ConcurrentConversationException(String tenant, String sessionId, Throwable cause) {
        super("conversation changed concurrently for " + tenant + "/" + sessionId, cause);
    }
}
