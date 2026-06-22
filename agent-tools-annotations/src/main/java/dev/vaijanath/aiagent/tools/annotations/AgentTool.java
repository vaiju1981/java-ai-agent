package dev.vaijanath.aiagent.tools.annotations;

import dev.vaijanath.aiagent.tool.ToolEffect;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a public method as an agent tool. Its parameters become the tool's JSON-schema parameters
 * (refine them with {@link ToolParam}); {@link ReflectiveTools} turns each annotated method into a
 * {@link dev.vaijanath.aiagent.tool.Tool}, so a tool is just a typed Java method — no hand-written
 * schema string and no manual argument parsing.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface AgentTool {

    /** Tool name advertised to the model; defaults to the method name. */
    String name() default "";

    /** What the tool does, for the model. */
    String description() default "";

    /** Capability class; defaults to the safe {@link ToolEffect#EFFECTFUL}. */
    ToolEffect effect() default ToolEffect.EFFECTFUL;
}
