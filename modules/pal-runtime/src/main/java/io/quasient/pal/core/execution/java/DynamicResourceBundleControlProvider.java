/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.quasient.pal.core.execution.java;

import java.io.IOException;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.spi.ResourceBundleControlProvider;

/**
 * Provides a custom {@link ResourceBundleControlProvider} implementation that leverages a dynamic
 * {@link ClassLoader} resolution strategy for resource bundle lookups.
 *
 * <p>When a resource bundle is requested, this provider uses a configurable {@link
 * ClassLoaderResolver} to determine the appropriate {@link ClassLoader} based on the resource
 * bundle's base name and locale. If the resolver is not set or returns {@code null}, the original
 * ClassLoader is used as a fallback.
 *
 * <p>This implementation is particularly useful in scenarios where resource bundles may need to be
 * loaded from different modules or class loading environments determined at runtime.
 */
public class DynamicResourceBundleControlProvider implements ResourceBundleControlProvider {

  /**
   * Functional interface to resolve the appropriate {@link ClassLoader} for loading resource
   * bundles at runtime.
   *
   * <p>Implementations should return a non-null {@link ClassLoader} based on the given resource
   * bundle base name and locale.
   */
  @FunctionalInterface
  public interface ClassLoaderResolver {
    /**
     * Determines the {@link ClassLoader} to be used for loading a resource bundle.
     *
     * @param baseName the base name of the resource bundle
     * @param locale the locale for which the resource bundle is desired
     * @return the {@link ClassLoader} to use, or {@code null} to indicate that the default should
     *     be used
     */
    ClassLoader getClassLoaderFor(String baseName, Locale locale);
  }

  /**
   * Holds the custom {@link ClassLoaderResolver} used to determine the {@link ClassLoader} at
   * runtime.
   *
   * <p>Declared volatile to ensure visibility across multiple threads.
   */
  private static volatile ClassLoaderResolver classLoaderResolver = null;

  /**
   * Sets the custom {@link ClassLoaderResolver} that dynamically resolves the {@link ClassLoader}
   * for resource bundle loading.
   *
   * <p>When this resolver is set, it will be used to determine the {@link ClassLoader} based on the
   * resource bundle's base name and locale. If set to {@code null}, dynamic resolution is disabled
   * and the default {@link ClassLoader} is used.
   *
   * @param resolver the custom resolver to set; may be {@code null} to disable dynamic resolution
   */
  public static void setClassLoaderResolver(ClassLoaderResolver resolver) {
    classLoaderResolver = resolver;
  }

  /**
   * Returns a custom {@link ResourceBundle.Control} implementation that employs dynamic {@link
   * ClassLoader} resolution based on the configured {@link ClassLoaderResolver}.
   *
   * <p>When a resource bundle is loaded, the returned control uses the resolver to determine which
   * {@link ClassLoader} should be used. If the resolver is not set or returns {@code null}, the
   * original {@code loader} parameter is used.
   *
   * @param baseName the base name for the requested resource bundle; may be utilized by the
   *     resolver for context
   * @return a custom {@link ResourceBundle.Control} that supports dynamic {@link ClassLoader}
   *     resolution
   */
  @Override
  public ResourceBundle.Control getControl(String baseName) {
    // return a custom ResourceBundle.Control that uses our resolver
    return new ResourceBundle.Control() {

      /**
       * {@inheritDoc}
       *
       * <p>This implementation attempts to use the custom {@link ClassLoaderResolver} to acquire a
       * suitable {@link ClassLoader}. If the resolver is absent or returns {@code null}, the
       * original provided {@code loader} is used as a fallback. Subsequently, the resource bundle
       * is loaded using the determined {@link ClassLoader}.
       *
       * @param baseName the base name of the resource bundle
       * @param locale the locale for which the resource bundle is desired
       * @param format the resource bundle format (for example, "java.properties")
       * @param loader the originally provided {@link ClassLoader} for loading the resource bundle
       * @param reload flag indicating whether to reload the resource bundle
       * @return the resource bundle loaded using the resolved {@link ClassLoader}
       * @throws IllegalAccessException if the resource bundle class or its nullary constructor is
       *     not accessible
       * @throws InstantiationException if the resource bundle class cannot be instantiated
       * @throws IOException if an I/O error occurs during loading
       */
      @Override
      public ResourceBundle newBundle(
          String baseName, Locale locale, String format, ClassLoader loader, boolean reload)
          throws IllegalAccessException, InstantiationException, IOException {

        // Try the original loader first (e.g. LaunchedClassLoader) — it already has access
        // to nested JARs and avoids issues with classloaders in transient states (such as
        // Tomcat's WebappClassLoaderBase which may be stopped during early initialization).
        ResourceBundle bundle = super.newBundle(baseName, locale, format, loader, reload);
        if (bundle != null) {
          return bundle;
        }

        // Original loader failed; try with our resolved ClassLoader as a fallback
        if (classLoaderResolver != null) {
          ClassLoader resolved = classLoaderResolver.getClassLoaderFor(baseName, locale);
          if (resolved != null && resolved != loader) {
            bundle = super.newBundle(baseName, locale, format, resolved, reload);
          }
        }

        return bundle;
      }
    };
  }
}
