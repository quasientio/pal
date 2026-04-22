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
package io.quasient.pal.core.execution;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeFalse;

import org.junit.Test;

/**
 * Tests for {@code CdiRequestContextExecutor}, which resolves managed-bean targets via the
 * reflection-loaded {@code jakarta.enterprise.inject.spi.CDI} API.
 *
 * <p>The pal-runtime test classpath does not bundle a CDI container, so these tests focus on the
 * reflective discovery contract: the constructor must fail-fast when CDI is absent, and the {@code
 * resolveTarget} fallback documented in {@link InvocationExecutor#resolveTarget} must default to
 * {@code null} when the executor is unable to look up the bean.
 *
 * <p>End-to-end coverage of the CDI lookup itself is provided by the {@code quarkus-petclinic}
 * example replay, which exercises the executor against a real Quarkus Arc container.
 */
public class CdiRequestContextExecutorTest {

  /**
   * Constructor must throw {@link IllegalStateException} with a descriptive message when CDI is not
   * on the classpath. Mirrors the fail-fast pattern used by {@code JavaFxInvocationExecutor}.
   */
  @Test
  public void constructorFailsWithoutCdiOnClasspath() {
    assumeFalse(
        "CDI is on the classpath; this test only applies when it is absent", cdiAvailable());
    try {
      new CdiRequestContextExecutor(getClass().getClassLoader());
      fail("Expected IllegalStateException when CDI is not on classpath");
    } catch (IllegalStateException e) {
      assertThat(e.getMessage(), containsString("CDI not on classpath"));
    }
  }

  /**
   * Default {@link InvocationExecutor#resolveTarget} returns {@code null} so executors that do not
   * manage instances of the requested type let the dispatcher fall through to its existing
   * phantom-skip path.
   */
  @Test
  public void defaultResolveTargetReturnsNull() {
    InvocationExecutor executor = invocation -> invocation.call();
    assertThat(executor.resolveTarget(String.class), is(nullValue()));
  }

  /**
   * Checks whether CDI classes are available on the test classpath.
   *
   * @return {@code true} if {@code jakarta.enterprise.inject.spi.CDI} can be loaded
   */
  private static boolean cdiAvailable() {
    try {
      Class.forName("jakarta.enterprise.inject.spi.CDI");
      return true;
    } catch (ClassNotFoundException e) {
      return false;
    }
  }
}
