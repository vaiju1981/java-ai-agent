package dev.vaijanath.aiagent.service;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A prompt template for an {@link AiServices} method. {@code {{name}}} placeholders are filled from the
 * method's {@link V}-annotated parameters; the rendered text becomes the agent's input.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface UserMessage {

    String value();
}
