/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.core.rpc.exec.java;

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
