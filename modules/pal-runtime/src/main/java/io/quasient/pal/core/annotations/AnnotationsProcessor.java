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

import io.quasient.pal.core.execution.java.ClassLoaderListener;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Listens for class loading events and delegates annotation processing to registered processors.
 *
 * <p>This class filters out core Java classes and Pal core classes, then invokes each registered
 * {@link AnnotationProcessor} that {@linkplain AnnotationProcessor#supports(Class) supports} the
 * loaded class. Processors are executed in ascending {@linkplain AnnotationProcessor#order()
 * order}. Failures in one processor are logged and do not prevent others from running.
 */
@Singleton
public class AnnotationsProcessor implements ClassLoaderListener {

  /**
   * Constant prefix representing the package of core Pal classes.
   *
   * <p>Classes whose fully qualified names start with this prefix are considered part of the Pal
   * core library and are excluded from annotation processing.
   */
  private static final String PAL_CORE_PREFIX = "io.quasient.pal";

  /**
   * Logger instance used for emitting diagnostic and trace information during annotation
   * processing.
   */
  private static final Logger logger = LoggerFactory.getLogger(AnnotationsProcessor.class);

  /**
   * All available annotation processors, sorted once by {@link AnnotationProcessor#order()}.
   *
   * <p>Injected via Guice multibindings; see {@code ProcessorsModule}.
   */
  private final List<AnnotationProcessor> processors;

  /**
   * Creates a new {@code AnnotationsProcessor}.
   *
   * @param processors the set of registered {@link AnnotationProcessor} implementations; must be
   *     non-null
   */
  @Inject
  public AnnotationsProcessor(Set<AnnotationProcessor> processors) {
    this.processors =
        processors.stream().sorted(Comparator.comparingInt(AnnotationProcessor::order)).toList();
  }

  /**
   * Determines if the specified class belongs to one of the core Java packages.
   *
   * @param clazz the class to evaluate; expected to be a non-null {@code Class<?>} instance
   * @return {@code true} if the class's fully qualified name starts with {@code java.}, {@code
   *     javax.}, or {@code sun.}; {@code false} otherwise
   */
  private boolean isCoreJavaClass(Class<?> clazz) {
    var name = clazz.getName();
    return name.startsWith("java.") || name.startsWith("javax.") || name.startsWith("sun.");
  }

  /**
   * Checks if the given class is part of the Pal core library based on its package prefix.
   *
   * @param clazz the class to be checked; expected to be a non-null {@code Class<?>} instance
   * @return {@code true} if the fully qualified name of the class starts with the configured Pal
   *     core prefix; {@code false} otherwise
   */
  private boolean isPalCoreClass(Class<?> clazz) {
    return clazz.getName().startsWith(PAL_CORE_PREFIX);
  }

  /**
   * Determines whether the specified class should be processed for annotations.
   *
   * <p>A class is eligible for processing if it does not belong to either the core Java libraries
   * or the Pal core package.
   *
   * @param clazz the class under consideration; expected to be a valid {@code Class<?>} instance
   * @return {@code true} if the class should undergo annotation processing; {@code false} otherwise
   */
  private boolean mustProcessClass(Class<?> clazz) {
    return !isCoreJavaClass(clazz) && !isPalCoreClass(clazz);
  }

  /**
   * Callback method invoked when a class is loaded by the class loader.
   *
   * <p>If the loaded class is eligible (i.e., it is neither a core Java class nor part of the Pal
   * core), this method iterates over the registered {@link AnnotationProcessor}s in order, invoking
   * {@link AnnotationProcessor#process(Class)} for those that return {@code true} from {@link
   * AnnotationProcessor#supports(Class)}. Exceptions thrown by individual processors are logged and
   * do not stop subsequent processors from running.
   *
   * @param clazz the class that has been loaded; must be a valid {@code Class<?>} instance
   */
  @Override
  public final void classLoaded(Class<?> clazz) {
    if (!mustProcessClass(clazz)) {
      return;
    }

    for (var p : processors) {
      try {
        if (p.supports(clazz)) {
          p.process(clazz);
        }
      } catch (Exception e) {
        logger.warn(
            "Annotation processor {} failed for class {}",
            p.getClass().getName(),
            clazz.getName(),
            e);
      }
    }

    if (logger.isTraceEnabled()) {
      logger.trace("Completed processing annotations for class '{}'", clazz.getName());
    }
  }
}
