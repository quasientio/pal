/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.common.util;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks methods, constructors, or types to be excluded from JaCoCo coverage analysis.
 *
 * <p>JaCoCo (version 0.8.2+) automatically excludes code annotated with annotations whose simple
 * name contains "Generated". This annotation follows that convention to exclude code from coverage
 * reports.
 *
 * <h2>Primary Use Cases</h2>
 *
 * <ul>
 *   <li><b>Debug/trace logging guards</b>: Methods containing conditional logging checks like
 *       {@code if (logger.isDebugEnabled())} or {@code if (logger.isTraceEnabled())} where the
 *       branches cannot always be covered during testing without changing log levels.
 *   <li><b>Infrastructure code</b>: Low-level code that is difficult to test in isolation.
 *   <li><b>Generated boilerplate</b>: Code that is repetitive and doesn't benefit from coverage
 *       measurement.
 * </ul>
 *
 * <h2>Usage Examples</h2>
 *
 * <pre>
 * // Exclude an entire method from coverage
 * &#064;ExcludeFromCoverageGenerated
 * public void debugDump() {
 *     if (logger.isDebugEnabled()) {
 *         logger.debug("Expensive debug computation: {}", computeExpensive());
 *     }
 * }
 *
 * // Exclude an entire class from coverage
 * &#064;ExcludeFromCoverageGenerated
 * public class DiagnosticHelper {
 *     // ...
 * }
 * </pre>
 *
 * <h2>Important Notes</h2>
 *
 * <ul>
 *   <li>The annotation must have {@code RetentionPolicy.CLASS} or {@code RUNTIME} for JaCoCo to
 *       detect it.
 *   <li>The annotation name must contain "Generated" (case-sensitive) for JaCoCo to recognize it.
 *   <li>Use sparingly - prefer writing tests to cover code when possible.
 * </ul>
 *
 * @see <a href="https://www.jacoco.org/jacoco/trunk/doc/changes.html">JaCoCo Change History</a>
 */
@Documented
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.TYPE, ElementType.METHOD, ElementType.CONSTRUCTOR})
public @interface ExcludeFromCoverageGenerated {
  /**
   * Optional reason for excluding from coverage.
   *
   * @return the reason for exclusion, empty by default
   */
  String value() default "";
}
