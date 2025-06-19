package com.quasient.pal.core.rpc.exec.java.reflect;

import com.quasient.pal.core.InterceptAnnotationProcessor;
import com.quasient.pal.core.rpc.exec.java.ClassLoaderListener;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Listens for class loading events and delegates annotation processing on the loaded classes.
 *
 * <p>This class determines if a class is eligible for annotation processing based on whether it
 * belongs to the core Java packages or the Pal core package. For all other classes, it triggers the
 * {@link InterceptAnnotationProcessor} to process the annotations and logs the outcome.
 */
@Singleton
public class AnnotationsProcessor implements ClassLoaderListener {

  /**
   * Constant prefix representing the package of core Pal classes.
   *
   * <p>Classes whose fully qualified names start with this prefix are considered part of the Pal
   * core library and are excluded from annotation processing.
   */
  private static final String PAL_CORE_PREFIX = "com.quasient.pal";

  /**
   * Logger instance used for emitting diagnostic and trace information during annotation
   * processing.
   */
  private static final Logger logger = LoggerFactory.getLogger(AnnotationsProcessor.class);

  /**
   * Processor responsible for handling intercepted annotations on eligible classes.
   *
   * <p>This field is injected and utilized to process annotations after verifying that a loaded
   * class does not belong to core Java or Pal core packages.
   */
  @Inject public InterceptAnnotationProcessor interceptAnnotationProcessor;

  /**
   * Determines if the specified class belongs to one of the core Java packages.
   *
   * @param clazz the class to evaluate; expected to be a non-null {@code Class<?>} instance.
   * @return {@code true} if the class's fully qualified name starts with "java.", "javax.", or
   *     "sun.", indicating it is a core Java class; {@code false} otherwise.
   */
  private boolean isCoreJavaClass(Class<?> clazz) {
    var name = clazz.getName();
    return name.startsWith("java.") || name.startsWith("javax.") || name.startsWith("sun.");
  }

  /**
   * Checks if the given class is part of the Pal core library based on its package prefix.
   *
   * @param clazz the class to be checked; expected to be a non-null {@code Class<?>} instance.
   * @return {@code true} if the fully qualified name of the class starts with the configured Pal
   *     core prefix; {@code false} otherwise.
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
   * @param clazz the class under consideration; expected to be a valid {@code Class<?>} instance.
   * @return {@code true} if the class should undergo annotation processing; {@code false}
   *     otherwise.
   */
  private boolean mustProcessClass(Class<?> clazz) {
    return !isCoreJavaClass(clazz) && !isPalCoreClass(clazz);
  }

  /**
   * Callback method invoked when a class is loaded by the class loader.
   *
   * <p>If the loaded class is eligible for annotation processing (i.e. it is neither a core Java
   * class nor part of the Pal core), this method delegates the processing to the injected {@link
   * InterceptAnnotationProcessor}. Additionally, if trace logging is enabled, it logs the
   * completion of the annotation processing.
   *
   * @param clazz the class that has been loaded; must be a valid {@code Class<?>} instance.
   */
  @Override
  public final void classLoaded(Class<?> clazz) {
    if (mustProcessClass(clazz)) {
      interceptAnnotationProcessor.process(clazz);

      if (logger.isTraceEnabled()) {
        logger.trace("Completed processing annotations for class '{}'", clazz.getName());
      }
    }
  }
}
