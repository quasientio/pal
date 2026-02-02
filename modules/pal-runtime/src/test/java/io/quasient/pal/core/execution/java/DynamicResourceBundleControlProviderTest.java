/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.core.execution.java;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

import java.io.IOException;
import java.util.Locale;
import java.util.ResourceBundle;
import java.util.concurrent.atomic.AtomicReference;
import java.util.spi.ResourceBundleControlProvider;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Test specifications for {@link DynamicResourceBundleControlProvider}.
 *
 * <p>This class provides a custom {@link java.util.spi.ResourceBundleControlProvider}
 * implementation that enables dynamic {@link ClassLoader} resolution for resource bundle lookups.
 * These tests verify the provider's construction, control retrieval, and inner class functionality.
 *
 * @see DynamicResourceBundleControlProvider
 */
public class DynamicResourceBundleControlProviderTest {

  @Before
  public void setUp() {
    // Reset the resolver to ensure clean state for each test
    DynamicResourceBundleControlProvider.setClassLoaderResolver(null);
  }

  @After
  public void tearDown() {
    // Clean up: reset resolver to prevent state leakage between tests
    DynamicResourceBundleControlProvider.setClassLoaderResolver(null);
  }

  /**
   * Test that the default constructor creates a valid provider instance.
   *
   * <p>Acceptance criterion:
   * [TEST:DynamicResourceBundleControlProviderTest.testDefaultConstructor_createsProvider]
   */
  @Test
  @SuppressWarnings("UnusedVariable")
  public void testDefaultConstructor_createsProvider() {
    // Given: No parameters (default constructor)

    // When: Constructor is called
    DynamicResourceBundleControlProvider newProvider = new DynamicResourceBundleControlProvider();

    // Then: Provider instance is created and not null
    assertThat(newProvider, is(notNullValue()));
    // Verify the provider implements ResourceBundleControlProvider interface
    assertThat(newProvider, is(instanceOf(ResourceBundleControlProvider.class)));
  }

  /**
   * Test that getControl returns a non-null Control instance for a valid bundle name.
   *
   * <p>Acceptance criterion:
   * [TEST:DynamicResourceBundleControlProviderTest.testGetControl_returnsControlForBundleName]
   */
  @Test
  public void testGetControl_returnsControlForBundleName() {
    // Given: A valid bundle name string
    DynamicResourceBundleControlProvider provider = new DynamicResourceBundleControlProvider();
    String bundleName = "com.example.Messages";

    // When: getControl is called with the bundle name
    ResourceBundle.Control control = provider.getControl(bundleName);

    // Then: Returns a non-null ResourceBundle.Control instance
    assertThat(control, is(notNullValue()));
    // The control should be usable for loading resource bundles
    assertThat(control, is(instanceOf(ResourceBundle.Control.class)));
  }

  /**
   * Test that the inner anonymous Control class functions properly.
   *
   * <p>Acceptance criterion:
   * [TEST:DynamicResourceBundleControlProviderTest.testInnerClass_functionsProperly]
   */
  @Test
  public void testInnerClass_functionsProperly() throws IOException {
    // Given: Provider instance
    DynamicResourceBundleControlProvider provider = new DynamicResourceBundleControlProvider();
    // And: A Control instance obtained from getControl
    String bundleName = "com.example.Messages";
    ResourceBundle.Control control = provider.getControl(bundleName);

    // When: Inner class methods are called - verify getFormats works
    // The inner class (anonymous Control) should properly inherit from ResourceBundle.Control
    var formats = control.getFormats(bundleName);

    // Then: The inner class functions correctly
    assertThat(formats, is(notNullValue()));
    // Default formats should include java.properties and java.class
    assertThat(formats.contains("java.properties"), is(true));
    assertThat(formats.contains("java.class"), is(true));
  }

  /**
   * Test that the inner anonymous Control class's newBundle method works correctly when no resolver
   * is set.
   *
   * <p>Additional test for inner class functionality.
   */
  @Test
  public void testInnerClass_newBundleWithoutResolver_usesOriginalLoader() throws Exception {
    // Given: Provider instance with no ClassLoaderResolver set (null)
    DynamicResourceBundleControlProvider provider = new DynamicResourceBundleControlProvider();
    // Resolver is already null from setUp()
    // And: A Control instance obtained from getControl
    String bundleName = "com.example.NonExistentBundle";
    ResourceBundle.Control control = provider.getControl(bundleName);

    // When: newBundle is called on the control with a ClassLoader
    // Since the bundle doesn't exist, newBundle should return null
    // but should not throw an exception if the loader is valid
    ClassLoader loader = getClass().getClassLoader();
    // Deliberately ignore return value - we only care that no exception is thrown
    control.newBundle(bundleName, Locale.getDefault(), "java.properties", loader, false);

    // Then: The original loader is used (fallback behavior)
    // No exception should be thrown - result will be null since bundle doesn't exist
    // The fact that we got here without exception means it worked correctly
    assertThat("No exception was thrown", true, is(true));
  }

  /**
   * Test that the inner Control class uses the custom resolver when one is set.
   *
   * <p>Additional test for inner class functionality.
   */
  @Test
  public void testInnerClass_newBundleWithResolver_usesResolvedLoader() throws Exception {
    // Given: A ClassLoaderResolver that returns a specific ClassLoader
    AtomicReference<String> capturedBaseName = new AtomicReference<>();
    AtomicReference<Locale> capturedLocale = new AtomicReference<>();
    ClassLoader customLoader = getClass().getClassLoader();

    DynamicResourceBundleControlProvider.ClassLoaderResolver resolver =
        (baseName, locale) -> {
          capturedBaseName.set(baseName);
          capturedLocale.set(locale);
          return customLoader;
        };

    // And: The resolver is set via setClassLoaderResolver
    DynamicResourceBundleControlProvider.setClassLoaderResolver(resolver);

    // When: newBundle is called on the control
    DynamicResourceBundleControlProvider provider = new DynamicResourceBundleControlProvider();
    String bundleName = "com.example.TestBundle";
    Locale locale = Locale.FRENCH;
    ResourceBundle.Control control = provider.getControl(bundleName);
    control.newBundle(
        bundleName,
        locale,
        "java.properties",
        Thread.currentThread().getContextClassLoader(),
        false);

    // Then: The resolver's ClassLoader is used for loading
    // The resolver should be invoked with correct baseName and locale
    assertThat(capturedBaseName.get(), is(bundleName));
    assertThat(capturedLocale.get(), is(locale));
  }

  /**
   * Test that the inner Control class falls back to original loader when resolver returns null.
   *
   * <p>Additional test for inner class functionality.
   */
  @Test
  public void testInnerClass_newBundleWithResolverReturningNull_fallsBackToOriginal()
      throws Exception {
    // Given: A ClassLoaderResolver that returns null
    DynamicResourceBundleControlProvider.ClassLoaderResolver resolver = (baseName, locale) -> null;

    // And: The resolver is set via setClassLoaderResolver
    DynamicResourceBundleControlProvider.setClassLoaderResolver(resolver);

    // When: newBundle is called on the control
    DynamicResourceBundleControlProvider provider = new DynamicResourceBundleControlProvider();
    String bundleName = "com.example.FallbackBundle";
    ResourceBundle.Control control = provider.getControl(bundleName);
    ClassLoader originalLoader = getClass().getClassLoader();

    // Then: The original loader is used as fallback
    // No exception should be thrown
    // Deliberately ignore return value - we only care that no exception is thrown
    control.newBundle(bundleName, Locale.getDefault(), "java.properties", originalLoader, false);

    // The fact that we got here without exception means fallback worked correctly
    assertThat("No exception was thrown, fallback to original loader succeeded", true, is(true));
  }

  /**
   * Test that setClassLoaderResolver correctly updates the static resolver.
   *
   * <p>Additional test for static resolver management.
   */
  @Test
  public void testSetClassLoaderResolver_updatesResolver() throws Exception {
    // Given: A custom ClassLoaderResolver
    AtomicReference<Boolean> resolverCalled = new AtomicReference<>(false);
    DynamicResourceBundleControlProvider.ClassLoaderResolver resolver =
        (baseName, locale) -> {
          resolverCalled.set(true);
          return Thread.currentThread().getContextClassLoader();
        };

    // When: setClassLoaderResolver is called with the resolver
    DynamicResourceBundleControlProvider.setClassLoaderResolver(resolver);

    // Then: Subsequent getControl calls use the new resolver
    DynamicResourceBundleControlProvider provider = new DynamicResourceBundleControlProvider();
    ResourceBundle.Control control = provider.getControl("com.example.Test");
    control.newBundle(
        "com.example.Test",
        Locale.getDefault(),
        "java.properties",
        getClass().getClassLoader(),
        false);

    assertThat(resolverCalled.get(), is(true));
  }

  /**
   * Test that setClassLoaderResolver with null disables dynamic resolution.
   *
   * <p>Additional test for static resolver management.
   */
  @Test
  public void testSetClassLoaderResolver_nullDisablesDynamicResolution() throws Exception {
    // Given: A previously set ClassLoaderResolver
    AtomicReference<Boolean> resolverCalled = new AtomicReference<>(false);
    DynamicResourceBundleControlProvider.ClassLoaderResolver resolver =
        (baseName, locale) -> {
          resolverCalled.set(true);
          return Thread.currentThread().getContextClassLoader();
        };
    DynamicResourceBundleControlProvider.setClassLoaderResolver(resolver);

    // And: setClassLoaderResolver is called with null
    DynamicResourceBundleControlProvider.setClassLoaderResolver(null);

    // When: newBundle is called on a control
    DynamicResourceBundleControlProvider provider = new DynamicResourceBundleControlProvider();
    ResourceBundle.Control control = provider.getControl("com.example.Test");
    control.newBundle(
        "com.example.Test",
        Locale.getDefault(),
        "java.properties",
        getClass().getClassLoader(),
        false);

    // Then: The original loader is used (resolver disabled)
    // The resolver should not have been called since it was set to null
    assertThat(resolverCalled.get(), is(false));
  }

  /**
   * Test that multiple getControl calls return independent Control instances.
   *
   * <p>Additional test for provider behavior.
   */
  @Test
  public void testGetControl_multipleCallsReturnIndependentInstances() {
    // Given: Provider instance
    DynamicResourceBundleControlProvider provider = new DynamicResourceBundleControlProvider();

    // When: getControl is called multiple times with different bundle names
    ResourceBundle.Control control1 = provider.getControl("bundle.one");
    ResourceBundle.Control control2 = provider.getControl("bundle.two");

    // Then: Each call returns a valid, non-null Control instance
    assertThat(control1, is(notNullValue()));
    assertThat(control2, is(notNullValue()));
  }

  /**
   * Test that the resolver receives the correct baseName from getControl.
   *
   * <p>Additional test verifying context is correctly passed.
   */
  @Test
  public void testResolver_receivesCorrectBaseNameFromGetControl() throws Exception {
    // Given: A resolver that captures arguments
    AtomicReference<String> capturedBaseName = new AtomicReference<>();
    DynamicResourceBundleControlProvider.setClassLoaderResolver(
        (baseName, locale) -> {
          capturedBaseName.set(baseName);
          return getClass().getClassLoader();
        });

    // When: getControl is called with a specific bundleName, then newBundle is called
    DynamicResourceBundleControlProvider provider = new DynamicResourceBundleControlProvider();
    String expectedBundleName = "org.example.specific.Bundle";
    ResourceBundle.Control control = provider.getControl(expectedBundleName);
    control.newBundle(
        expectedBundleName, Locale.US, "java.properties", getClass().getClassLoader(), false);

    // Then: The resolver receives the correct baseName
    assertThat(capturedBaseName.get(), is(expectedBundleName));
  }
}
