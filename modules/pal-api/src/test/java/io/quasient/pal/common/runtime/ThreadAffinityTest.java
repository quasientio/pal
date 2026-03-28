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
package io.quasient.pal.common.runtime;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
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
  public void fxThreadConstantHasExpectedValue() {
    // Given/When: ThreadAffinity.FX_THREAD constant accessed
    String value = ThreadAffinity.FX_THREAD;

    // Then: Equals "fx-thread"
    assertThat(value, is("fx-thread"));
  }

  /**
   * Test specification: constructorIsPrivate
   *
   * <p>Verifies that the ThreadAffinity class has a private constructor and cannot be instantiated
   * via reflection.
   *
   * <p>Given: ThreadAffinity class When: Attempting to instantiate via reflection Then: Constructor
   * is private
   */
  @Test
  public void constructorIsPrivate() throws Exception {
    // Given: ThreadAffinity class
    Constructor<?> constructor = ThreadAffinity.class.getDeclaredConstructor();

    // Then: Constructor is private
    assertThat(Modifier.isPrivate(constructor.getModifiers()), is(true));
  }
}
