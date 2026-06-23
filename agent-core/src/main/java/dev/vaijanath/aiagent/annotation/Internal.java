package dev.vaijanath.aiagent.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a public type or member as <b>internal</b>: it exists for this framework's own modules and is
 * not part of the supported public API. Internal elements may change or be removed in any release —
 * including patch releases — without notice, so applications should not depend on them.
 *
 * <p>See {@code docs/API-STABILITY.md} for the stability policy. The annotation is applied incrementally;
 * an unmarked type that is plainly an implementation detail (for example a {@code Default*} class behind a
 * seam) should still be treated as internal — prefer the documented interfaces and builders.
 */
@Documented
@Retention(RetentionPolicy.CLASS)
@Target({
    ElementType.TYPE,
    ElementType.METHOD,
    ElementType.CONSTRUCTOR,
    ElementType.FIELD,
    ElementType.PACKAGE
})
public @interface Internal {}
