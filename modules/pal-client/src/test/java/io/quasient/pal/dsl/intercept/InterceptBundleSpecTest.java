/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.dsl.intercept;

import static org.junit.Assert.fail;

import org.junit.Ignore;
import org.junit.Test;

/**
 * Unit tests for the {@code InterceptBundleSpec} value class.
 *
 * <p>These test stubs define the contract for {@code InterceptBundleSpec}. Each test documents
 * expected behavior via Given/When/Then comments. Implementation will be provided in #1233.
 */
public class InterceptBundleSpecTest {

  @Test
  @Ignore("Awaiting implementation in #1233")
  public void builder_setsAllFields() {
    // Given: An InterceptBundleSpec builder with name, defaults, and 2 intercept specs
    // When: build() is called
    // Then: getName() returns the bundle name,
    //       getDefaults() returns the configured defaults,
    //       getIntercepts() returns a list of 2 InterceptSpecs

    // TODO(#1233): Implement test logic
    fail("Not yet implemented");
  }

  @Test
  @Ignore("Awaiting implementation in #1233")
  public void builder_requiresBundleName() {
    // Given: An InterceptBundleSpec builder with intercepts set but bundle name omitted
    // When: build() is called
    // Then: NullPointerException or IllegalStateException is thrown

    // TODO(#1233): Implement test logic
    fail("Not yet implemented");
  }

  @Test
  @Ignore("Awaiting implementation in #1233")
  public void builder_requiresAtLeastOneIntercept() {
    // Given: An InterceptBundleSpec builder with bundle name set but empty intercept list
    // When: build() is called
    // Then: IllegalStateException is thrown

    // TODO(#1233): Implement test logic
    fail("Not yet implemented");
  }

  @Test
  @Ignore("Awaiting implementation in #1233")
  public void builder_defaultsAreOptional() {
    // Given: An InterceptBundleSpec builder with name and intercepts but no defaults
    // When: build() is called
    // Then: getDefaults() returns a no-op defaults object (all fields null)

    // TODO(#1233): Implement test logic
    fail("Not yet implemented");
  }

  @Test
  @Ignore("Awaiting implementation in #1233")
  public void interceptsListIsImmutable() {
    // Given: A built InterceptBundleSpec with intercepts
    // When: getIntercepts() is called and an attempt is made to add to the returned list
    // Then: UnsupportedOperationException is thrown

    // TODO(#1233): Implement test logic
    fail("Not yet implemented");
  }
}
