package net.ittera.pal.core.rpc.exec.java;

import java.io.IOException;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.spi.ResourceBundleControlProvider;

/**
 * A ResourceBundleControlProvider that forces ResourceBundle lookups to use a configurable
 * ClassLoader.
 */
public class DynamicResourceBundleControlProvider implements ResourceBundleControlProvider {

  /**
   * This functional interface is how we'll dynamically decide which ClassLoader to use at runtime.
   */
  @FunctionalInterface
  public interface ClassLoaderResolver {
    ClassLoader getClassLoaderFor(String baseName, Locale locale);
  }

  private static volatile ClassLoaderResolver classLoaderResolver = null;

  public static void setClassLoaderResolver(ClassLoaderResolver resolver) {
    classLoaderResolver = resolver;
  }

  @Override
  public ResourceBundle.Control getControl(String baseName) {
    // return a custom ResourceBundle.Control that uses our resolver
    return new ResourceBundle.Control() {

      @Override
      public ResourceBundle newBundle(
          String baseName, Locale locale, String format, ClassLoader loader, boolean reload)
          throws IllegalAccessException, InstantiationException, IOException {

        ClassLoader actualLoader = loader; // fallback

        // if we have a resolver, use its suggested ClassLoader:
        if (classLoaderResolver != null) {
          actualLoader = classLoaderResolver.getClassLoaderFor(baseName, locale);
          if (actualLoader == null) {
            // If resolver returns null for some reason, fallback to original
            actualLoader = loader;
          }
        }

        // load the bundle with the chosen ClassLoader
        return super.newBundle(baseName, locale, format, actualLoader, reload);
      }
    };
  }
}
