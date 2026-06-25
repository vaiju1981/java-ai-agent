package dev.vaijanath.aiagent.a2a;

/** Thrown when an A2A call fails — a network error, a non-200 response, or a malformed payload. */
public final class A2aException extends RuntimeException {

    public A2aException(String message) {
        super(message);
    }

    public A2aException(String message, Throwable cause) {
        super(message, cause);
    }
}
