package com.cmt.e2e.framework.junit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks an E2E migration test class and binds it to a scenario name.
 * The name resolves to {@code src/test/resources/snapshots/<name>/} and
 * {@code src/test/resources/queries/<name>.sql}.
 *
 * <p>Scenario id grammar: {@code <source>_to_<target>[__<discriminator>]}.
 *
 * <p>{@link #options()} explicitly lists every CMT option whose value
 * affects this TC's verification — relying on CMT defaults silently
 * couples the test to a default that can shift between releases.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface MigrationE2E {
    String name();
    String[] options() default {};
}
