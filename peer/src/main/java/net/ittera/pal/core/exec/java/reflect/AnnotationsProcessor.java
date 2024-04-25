package net.ittera.pal.core.exec.java.reflect;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import net.ittera.pal.core.InterceptAnnotationProcessor;
import net.ittera.pal.core.exec.java.ClassLoaderListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class AnnotationsProcessor implements ClassLoaderListener {
  private static final String PAL_CORE_PREFIX = "net.ittera.pal";
  private static final Logger logger = LoggerFactory.getLogger(AnnotationsProcessor.class);
  @Inject public InterceptAnnotationProcessor interceptAnnotationProcessor;

  private boolean isCoreJavaClass(Class<?> clazz) {
    var name = clazz.getName();
    return name.startsWith("java.") || name.startsWith("javax.") || name.startsWith("sun.");
  }

  private boolean isPalCoreClass(Class<?> clazz) {
    return clazz.getName().startsWith(PAL_CORE_PREFIX);
  }

  private boolean mustProcessClass(Class<?> clazz) {
    return !isCoreJavaClass(clazz) && !isPalCoreClass(clazz);
  }

  @Override
  public final void classLoaded(Class<?> clazz) {
    if (mustProcessClass(clazz)) {
      interceptAnnotationProcessor.process(clazz);
      logger.debug("Completed processing annotations for class '{}'", clazz.getName());
    }
  }
}
