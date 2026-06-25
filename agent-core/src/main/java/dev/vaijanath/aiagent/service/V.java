package dev.vaijanath.aiagent.service;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Names a template variable for a {@link UserMessage} — e.g. {@code @V("city") String city} fills the
 * {@code {{city}}} placeholder with the argument's value.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface V {

    String value();
}
