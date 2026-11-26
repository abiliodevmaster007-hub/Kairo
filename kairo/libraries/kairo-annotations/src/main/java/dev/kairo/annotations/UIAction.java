package dev.kairo.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a Java method as a declarative UI action target invoked from XML event attributes.
 * Resolved via explicit registration or generated dispatchers, never arbitrary reflection scanning.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface UIAction {
    String value() default "";
}
