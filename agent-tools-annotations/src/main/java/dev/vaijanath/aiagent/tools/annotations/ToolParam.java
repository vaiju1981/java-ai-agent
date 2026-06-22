package dev.vaijanath.aiagent.tools.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Optional metadata for a tool-method parameter. Without it a parameter is required and its schema
 * name is the reflective parameter name (the build compiles with {@code -parameters}, so real names
 * survive). Make a parameter optional by giving it an object type (e.g. {@code Integer}, not
 * {@code int}) and {@code required = false}.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.PARAMETER)
public @interface ToolParam {

    /** Description surfaced in the parameter's JSON schema. */
    String description() default "";

    /** Whether the model must supply this parameter. */
    boolean required() default true;

    /** Override the schema property name; defaults to the reflective parameter name. */
    String name() default "";
}
