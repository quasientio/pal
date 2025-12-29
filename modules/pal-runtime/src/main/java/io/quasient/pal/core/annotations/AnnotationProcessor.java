/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.core.annotations;

/**
 * SPI for components that perform annotation-driven processing on loaded classes.
 *
 * <p>Implementations should be idempotent and thread-safe. They will be invoked by {@link
 * AnnotationsProcessor} when classes are loaded and pass the {@link #supports(Class)} check.
 */
public interface AnnotationProcessor {

  /**
   * Indicates whether this processor is interested in the given class.
   *
   * <p>This method should be fast and side-effect free; it may be called frequently.
   *
   * @param clazz non-null class to evaluate
   * @return {@code true} if this processor wants to handle {@code clazz}; {@code false} otherwise
   */
  default boolean supports(Class<?> clazz) {
    return true;
  }

  /**
   * Performs processing for a previously supported class.
   *
   * <p>Implementations should tolerate multiple invocations for the same class and concurrent
   * execution.
   *
   * @param clazz non-null class to process
   */
  void process(Class<?> clazz);

  /**
   * Relative ordering hint used when multiple processors target the same class.
   *
   * <p>Smaller numbers run earlier. The default is {@code 0}.
   *
   * @return the relative order value
   */
  default int order() {
    return 0;
  }
}
