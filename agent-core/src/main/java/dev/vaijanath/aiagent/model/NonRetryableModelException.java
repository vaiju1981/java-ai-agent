package dev.vaijanath.aiagent.model;

/**
 * Signals a model error that retrying cannot fix — e.g. authentication failure, a malformed request,
 * or a content-policy rejection (typically an HTTP 4xx). {@link ResilientModelPort} fails fast on
 * this instead of burning its remaining attempts (and quota) on an error that will never succeed.
 *
 * <p>Adapters (e.g. {@code LangChain4jModelPort}, {@code SpringAiModelPort}) should translate the
 * underlying client's non-retryable failures into this type; everything else is treated as transient
 * and retried.
 */
public class NonRetryableModelException extends RuntimeException {

    public NonRetryableModelException(String message) {
        super(message);
    }

    public NonRetryableModelException(String message, Throwable cause) {
        super(message, cause);
    }
}
