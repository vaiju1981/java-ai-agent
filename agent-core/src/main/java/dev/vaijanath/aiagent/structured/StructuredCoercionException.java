package dev.vaijanath.aiagent.structured;

/**
 * Thrown when a completed agent answer could not be coerced into the requested type after the
 * configured number of attempts. Distinct from a blocked or stopped turn (which yields a
 * {@link StructuredResult} with a {@code null} value): this signals a genuine coercion failure.
 */
public final class StructuredCoercionException extends RuntimeException {

    public StructuredCoercionException(String message, Throwable cause) {
        super(message, cause);
    }
}
