/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.common.runtime;

import static org.junit.Assert.fail;

import org.junit.Ignore;
import org.junit.Test;

/**
 * Tests for the {@link ThreadAffinity} constants class.
 *
 * <p>Verifies that thread affinity constants have expected values and that the utility class cannot
 * be instantiated.
 */
public class ThreadAffinityTest {

  /**
   * Test specification: fxThreadConstantHasExpectedValue
   *
   * <p>Verifies that the FX_THREAD constant has the expected string value.
   *
   * <p>Given: ThreadAffinity.FX_THREAD constant When: Accessed Then: Equals "fx-thread"
   */
  @Test
  @Ignore("Awaiting implementation in #737")
  public void fxThreadConstantHasExpectedValue() {
    // Given: ThreadAffinity.FX_THREAD constant

    // When: Accessed
    // String value = ThreadAffinity.FX_THREAD;

    // Then: Equals "fx-thread"
    // assertThat(value, is("fx-thread"));

    // TODO(#737): Uncomment assertions when ThreadAffinity is implemented
    fail("Not yet implemented");
  }

  /**
   * Test specification: constructorIsPrivate
   *
   * <p>Verifies that the ThreadAffinity class has a private constructor and cannot be instantiated
   * via reflection.
   *
   * <p>Given: ThreadAffinity class When: Attempting to instantiate via reflection Then: Constructor
   * is private, throws exception
   */
  @Test
  @Ignore("Awaiting implementation in #737")
  public void constructorIsPrivate() throws Exception {
    // Given: ThreadAffinity class
    // Constructor<?> constructor = ThreadAffinity.class.getDeclaredConstructor();

    // When: Attempting to instantiate via reflection
    // assertThat(java.lang.reflect.Modifier.isPrivate(constructor.getModifiers()), is(true));
    // constructor.setAccessible(true);

    // Then: Constructor is accessible but class is a utility class with private constructor
    // try {
    //   constructor.newInstance();
    // } catch (InvocationTargetException expected) {
    //   // Expected if constructor throws
    // }

    // TODO(#737): Uncomment assertions when ThreadAffinity is implemented
    fail("Not yet implemented");
  }
}
