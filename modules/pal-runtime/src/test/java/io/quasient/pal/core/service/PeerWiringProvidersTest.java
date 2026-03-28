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
package io.quasient.pal.core.service;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

import io.quasient.pal.common.lang.intercept.CheckedExceptionPolicy;
import io.quasient.pal.common.lang.intercept.ExceptionPropagationPolicy;
import io.quasient.pal.common.lang.intercept.InterceptType;
import io.quasient.pal.core.execution.java.CustomClassloader;
import io.quasient.pal.core.intercept.ExceptionPolicyConfig;
import io.quasient.pal.core.internal.concurrent.HwmMessageQueue;
import io.quasient.pal.messages.OutboundMsg;
import java.net.URL;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.zeromq.ZContext;

public class PeerWiringProvidersTest {

  private ZContext ctx;

  @Before
  public void setUp() {
    ctx = new ZContext(1);
  }

  @After
  public void tearDown() {
    ctx.close();
  }

  private Properties baseProps() {
    Properties p = new Properties();
    p.setProperty("id", UUID.randomUUID().toString());
    p.setProperty("wal.queue.type", "CHUNKED");
    p.setProperty("wal.queue.initial", "1024");
    p.setProperty("wal.queue.max", "2048");
    p.setProperty("pub.queue.type", "CHUNKED");
    p.setProperty("pub.queue.initial", "1024");
    p.setProperty("pub.queue.max", "2048");
    p.setProperty("wal.chronicle.base_dir", System.getProperty("java.io.tmpdir"));
    p.setProperty("session.svc", "inproc://session");
    return p;
  }

  @Test
  public void providers_returnExpectedValues_withoutSessions() {
    Properties props = baseProps();
    CustomClassloader cl = new CustomClassloader(new URL[] {}, ClassLoader.getSystemClassLoader());
    PeerWiring wiring = new PeerWiring(props, EnumSet.noneOf(RunOptions.class), ctx, cl);

    // Chronicle base dir
    Path base = wiring.provideChronicleBaseDir();
    assertThat(base, notNullValue());

    // wal queue / pub queue
    HwmMessageQueue<OutboundMsg> walQ = wiring.provideWalQueue();
    HwmMessageQueue<OutboundMsg> pubQ = wiring.providePubQueue();
    assertThat(walQ, notNullValue());
    assertThat(pubQ, notNullValue());

    // walFailed flag
    AtomicBoolean walFailed = wiring.provideWalFailedFlag();
    assertThat(walFailed.get(), is(false));

    // session endpoint not provided when WITH_SESSIONS is absent
    String session = wiring.provideSessionServiceEndpoint();
    assertThat(session == null, is(true));

    // peer uuid and thread group
    assertThat(wiring.providePeerUuid(), notNullValue());
    assertThat(wiring.provideServiceThreadGroup(), notNullValue());
  }

  @Test
  public void sessionEndpointProvided_whenWithSessions() {
    Properties props = baseProps();
    CustomClassloader cl = new CustomClassloader(new URL[] {}, ClassLoader.getSystemClassLoader());
    PeerWiring wiring = new PeerWiring(props, EnumSet.of(RunOptions.WITH_SESSIONS), ctx, cl);
    assertThat(wiring.provideSessionServiceEndpoint(), is("inproc://session"));
  }

  @Test
  public void exceptionPolicyConfig_usesDefaultsWhenNotConfigured() {
    Properties props = baseProps();
    CustomClassloader cl = new CustomClassloader(new URL[] {}, ClassLoader.getSystemClassLoader());
    PeerWiring wiring = new PeerWiring(props, EnumSet.noneOf(RunOptions.class), ctx, cl);

    ExceptionPolicyConfig config = wiring.provideExceptionPolicyConfig();

    // Verify defaults
    assertThat(
        config.getGlobalPropagationPolicy(),
        is(ExceptionPropagationPolicy.PROPAGATE_CONTROLLED_ONLY));
    assertThat(config.getGlobalCheckedExceptionPolicy(), is(CheckedExceptionPolicy.WRAP));
  }

  @Test
  public void exceptionPolicyConfig_usesGlobalPolicyFromSystemProperty() {
    Properties props = baseProps();
    props.setProperty("pal.intercept.exception-policy.default", "PROPAGATE_ALL");
    props.setProperty("pal.intercept.checked-exception-policy.default", "REJECT");

    CustomClassloader cl = new CustomClassloader(new URL[] {}, ClassLoader.getSystemClassLoader());
    PeerWiring wiring = new PeerWiring(props, EnumSet.noneOf(RunOptions.class), ctx, cl);

    ExceptionPolicyConfig config = wiring.provideExceptionPolicyConfig();

    assertThat(config.getGlobalPropagationPolicy(), is(ExceptionPropagationPolicy.PROPAGATE_ALL));
    assertThat(config.getGlobalCheckedExceptionPolicy(), is(CheckedExceptionPolicy.REJECT));
  }

  @Test
  public void exceptionPolicyConfig_usesPerTypePolicyFromSystemProperty() {
    Properties props = baseProps();
    props.setProperty("pal.intercept.exception-policy.before", "SWALLOW_ALL");
    props.setProperty("pal.intercept.checked-exception-policy.after", "ALLOW_ALL");

    CustomClassloader cl = new CustomClassloader(new URL[] {}, ClassLoader.getSystemClassLoader());
    PeerWiring wiring = new PeerWiring(props, EnumSet.noneOf(RunOptions.class), ctx, cl);

    ExceptionPolicyConfig config = wiring.provideExceptionPolicyConfig();

    assertThat(
        config.getPropagationPolicyForType(InterceptType.BEFORE),
        is(ExceptionPropagationPolicy.SWALLOW_ALL));
    assertThat(
        config.getCheckedExceptionPolicyForType(InterceptType.AFTER),
        is(CheckedExceptionPolicy.ALLOW_ALL));
  }

  @Test
  public void exceptionPolicyConfig_hardcodesAsyncToSwallowAll() {
    Properties props = baseProps();
    // Even if we try to set ASYNC types differently, they should be hardcoded to SWALLOW_ALL
    props.setProperty("pal.intercept.exception-policy.before-async", "PROPAGATE_ALL");
    props.setProperty("pal.intercept.exception-policy.after-async", "PROPAGATE_ALL");

    CustomClassloader cl = new CustomClassloader(new URL[] {}, ClassLoader.getSystemClassLoader());
    PeerWiring wiring = new PeerWiring(props, EnumSet.noneOf(RunOptions.class), ctx, cl);

    ExceptionPolicyConfig config = wiring.provideExceptionPolicyConfig();

    // ASYNC types must always be SWALLOW_ALL
    assertThat(
        config.getPropagationPolicyForType(InterceptType.BEFORE_ASYNC),
        is(ExceptionPropagationPolicy.SWALLOW_ALL));
    assertThat(
        config.getPropagationPolicyForType(InterceptType.AFTER_ASYNC),
        is(ExceptionPropagationPolicy.SWALLOW_ALL));
  }

  @Test
  public void exceptionPolicyConfig_ignoresInvalidPolicyValues() {
    Properties props = baseProps();
    props.setProperty("pal.intercept.exception-policy.default", "INVALID_POLICY");
    props.setProperty("pal.intercept.checked-exception-policy.default", "ALSO_INVALID");

    CustomClassloader cl = new CustomClassloader(new URL[] {}, ClassLoader.getSystemClassLoader());
    PeerWiring wiring = new PeerWiring(props, EnumSet.noneOf(RunOptions.class), ctx, cl);

    ExceptionPolicyConfig config = wiring.provideExceptionPolicyConfig();

    // Should fall back to defaults when invalid values are provided
    assertThat(
        config.getGlobalPropagationPolicy(),
        is(ExceptionPropagationPolicy.PROPAGATE_CONTROLLED_ONLY));
    assertThat(config.getGlobalCheckedExceptionPolicy(), is(CheckedExceptionPolicy.WRAP));
  }

  @Test
  public void exceptionPolicyConfig_handlesCaseInsensitiveInput() {
    Properties props = baseProps();
    props.setProperty("pal.intercept.exception-policy.default", "propagate_all");
    props.setProperty("pal.intercept.checked-exception-policy.default", "wrap");

    CustomClassloader cl = new CustomClassloader(new URL[] {}, ClassLoader.getSystemClassLoader());
    PeerWiring wiring = new PeerWiring(props, EnumSet.noneOf(RunOptions.class), ctx, cl);

    ExceptionPolicyConfig config = wiring.provideExceptionPolicyConfig();

    assertThat(config.getGlobalPropagationPolicy(), is(ExceptionPropagationPolicy.PROPAGATE_ALL));
    assertThat(config.getGlobalCheckedExceptionPolicy(), is(CheckedExceptionPolicy.WRAP));
  }

  // ============================================================================
  // Test specifications for PeerWiring exception policy coverage
  // ============================================================================

  /**
   * Test specification for default exception policy configuration.
   *
   * <p>Verifies that when no exception policy properties are configured, the method creates an
   * ExceptionPolicyConfig with all default values.
   */
  @Test
  public void testProvideExceptionPolicyConfig_defaultPolicy_createsConfig() {
    // Given: No exception policy properties configured (using base properties only)
    Properties props = baseProps();
    CustomClassloader cl = new CustomClassloader(new URL[] {}, ClassLoader.getSystemClassLoader());
    PeerWiring wiring = new PeerWiring(props, EnumSet.noneOf(RunOptions.class), ctx, cl);

    // When: provideExceptionPolicyConfig() is called
    ExceptionPolicyConfig config = wiring.provideExceptionPolicyConfig();

    // Then: ExceptionPolicyConfig is created with default values
    // Global propagation policy = PROPAGATE_CONTROLLED_ONLY (default)
    assertThat(
        config.getGlobalPropagationPolicy(),
        is(ExceptionPropagationPolicy.PROPAGATE_CONTROLLED_ONLY));
    // Global checked exception policy = WRAP (default)
    assertThat(config.getGlobalCheckedExceptionPolicy(), is(CheckedExceptionPolicy.WRAP));

    // No per-type overrides for BEFORE, AFTER, AROUND (returns null)
    assertThat(config.getPropagationPolicyForType(InterceptType.BEFORE), is((Object) null));
    assertThat(config.getPropagationPolicyForType(InterceptType.AFTER), is((Object) null));
    assertThat(config.getPropagationPolicyForType(InterceptType.AROUND), is((Object) null));
    assertThat(config.getCheckedExceptionPolicyForType(InterceptType.BEFORE), is((Object) null));
    assertThat(config.getCheckedExceptionPolicyForType(InterceptType.AFTER), is((Object) null));
    assertThat(config.getCheckedExceptionPolicyForType(InterceptType.AROUND), is((Object) null));

    // ASYNC types hardcoded to SWALLOW_ALL
    assertThat(
        config.getPropagationPolicyForType(InterceptType.BEFORE_ASYNC),
        is(ExceptionPropagationPolicy.SWALLOW_ALL));
    assertThat(
        config.getPropagationPolicyForType(InterceptType.AFTER_ASYNC),
        is(ExceptionPropagationPolicy.SWALLOW_ALL));
  }

  /**
   * Test specification for retry-friendly exception policy configuration.
   *
   * <p>Verifies that PROPAGATE_EXPLICIT_ONLY and PROPAGATE_CONTROLLED_ONLY policies are correctly
   * parsed and applied. These policies allow controlled exception handling suitable for retry
   * scenarios.
   */
  @Test
  public void testProvideExceptionPolicyConfig_retryPolicy_createsConfig() {
    // Given: Exception policy properties configured for retry-friendly behavior
    Properties props = baseProps();
    props.setProperty("pal.intercept.exception-policy.default", "PROPAGATE_EXPLICIT_ONLY");
    props.setProperty("pal.intercept.checked-exception-policy.default", "WRAP");
    props.setProperty("pal.intercept.exception-policy.around", "PROPAGATE_CONTROLLED_ONLY");

    CustomClassloader cl = new CustomClassloader(new URL[] {}, ClassLoader.getSystemClassLoader());
    PeerWiring wiring = new PeerWiring(props, EnumSet.noneOf(RunOptions.class), ctx, cl);

    // When: provideExceptionPolicyConfig() is called
    ExceptionPolicyConfig config = wiring.provideExceptionPolicyConfig();

    // Then: ExceptionPolicyConfig is created with retry-friendly configuration
    // Global propagation policy = PROPAGATE_EXPLICIT_ONLY
    assertThat(
        config.getGlobalPropagationPolicy(),
        is(ExceptionPropagationPolicy.PROPAGATE_EXPLICIT_ONLY));
    // Global checked exception policy = WRAP
    assertThat(config.getGlobalCheckedExceptionPolicy(), is(CheckedExceptionPolicy.WRAP));
    // AROUND type uses PROPAGATE_CONTROLLED_ONLY override
    assertThat(
        config.getPropagationPolicyForType(InterceptType.AROUND),
        is(ExceptionPropagationPolicy.PROPAGATE_CONTROLLED_ONLY));
    // BEFORE and AFTER not configured, so null
    assertThat(config.getPropagationPolicyForType(InterceptType.BEFORE), is((Object) null));
    assertThat(config.getPropagationPolicyForType(InterceptType.AFTER), is((Object) null));
  }

  /**
   * Test specification for fail-fast exception policy configuration.
   *
   * <p>Verifies that PROPAGATE_ALL propagation policy combined with REJECT checked exception policy
   * creates a fail-fast configuration where all exceptions propagate immediately.
   */
  @Test
  public void testProvideExceptionPolicyConfig_failFastPolicy_createsConfig() {
    // Given: Exception policy properties configured for fail-fast behavior
    Properties props = baseProps();
    props.setProperty("pal.intercept.exception-policy.default", "PROPAGATE_ALL");
    props.setProperty("pal.intercept.checked-exception-policy.default", "REJECT");
    props.setProperty("pal.intercept.exception-policy.before", "PROPAGATE_ALL");
    props.setProperty("pal.intercept.exception-policy.after", "PROPAGATE_ALL");
    props.setProperty("pal.intercept.checked-exception-policy.before", "REJECT");
    props.setProperty("pal.intercept.checked-exception-policy.after", "REJECT");

    CustomClassloader cl = new CustomClassloader(new URL[] {}, ClassLoader.getSystemClassLoader());
    PeerWiring wiring = new PeerWiring(props, EnumSet.noneOf(RunOptions.class), ctx, cl);

    // When: provideExceptionPolicyConfig() is called
    ExceptionPolicyConfig config = wiring.provideExceptionPolicyConfig();

    // Then: ExceptionPolicyConfig is created with fail-fast configuration
    // Global propagation policy = PROPAGATE_ALL
    assertThat(config.getGlobalPropagationPolicy(), is(ExceptionPropagationPolicy.PROPAGATE_ALL));
    // Global checked exception policy = REJECT
    assertThat(config.getGlobalCheckedExceptionPolicy(), is(CheckedExceptionPolicy.REJECT));

    // Per-type BEFORE and AFTER policies match fail-fast configuration
    assertThat(
        config.getPropagationPolicyForType(InterceptType.BEFORE),
        is(ExceptionPropagationPolicy.PROPAGATE_ALL));
    assertThat(
        config.getPropagationPolicyForType(InterceptType.AFTER),
        is(ExceptionPropagationPolicy.PROPAGATE_ALL));
    assertThat(
        config.getCheckedExceptionPolicyForType(InterceptType.BEFORE),
        is(CheckedExceptionPolicy.REJECT));
    assertThat(
        config.getCheckedExceptionPolicyForType(InterceptType.AFTER),
        is(CheckedExceptionPolicy.REJECT));

    // ASYNC types remain SWALLOW_ALL (hardcoded override)
    assertThat(
        config.getPropagationPolicyForType(InterceptType.BEFORE_ASYNC),
        is(ExceptionPropagationPolicy.SWALLOW_ALL));
    assertThat(
        config.getPropagationPolicyForType(InterceptType.AFTER_ASYNC),
        is(ExceptionPropagationPolicy.SWALLOW_ALL));
  }

  /**
   * Test specification for comprehensive exception policy branch coverage.
   *
   * <p>Verifies all branches of the provideExceptionPolicyConfig method are exercised including:
   * all ExceptionPropagationPolicy values, all CheckedExceptionPolicy values, all InterceptType
   * configurations, invalid property handling for per-type policies, and ASYNC type hardcoding.
   */
  @Test
  public void testProvideExceptionPolicyConfig_allPolicies_coverAllBranches() {
    // Branch 1: Per-type propagation policy AROUND with PROPAGATE_EXPLICIT_ONLY
    {
      Properties props = baseProps();
      props.setProperty("pal.intercept.exception-policy.around", "PROPAGATE_EXPLICIT_ONLY");
      CustomClassloader cl =
          new CustomClassloader(new URL[] {}, ClassLoader.getSystemClassLoader());
      PeerWiring wiring = new PeerWiring(props, EnumSet.noneOf(RunOptions.class), ctx, cl);
      ExceptionPolicyConfig config = wiring.provideExceptionPolicyConfig();
      assertThat(
          config.getPropagationPolicyForType(InterceptType.AROUND),
          is(ExceptionPropagationPolicy.PROPAGATE_EXPLICIT_ONLY));
    }

    // Branch 2: Per-type checked exception policy BEFORE with WRAP
    {
      Properties props = baseProps();
      props.setProperty("pal.intercept.checked-exception-policy.before", "WRAP");
      CustomClassloader cl =
          new CustomClassloader(new URL[] {}, ClassLoader.getSystemClassLoader());
      PeerWiring wiring = new PeerWiring(props, EnumSet.noneOf(RunOptions.class), ctx, cl);
      ExceptionPolicyConfig config = wiring.provideExceptionPolicyConfig();
      assertThat(
          config.getCheckedExceptionPolicyForType(InterceptType.BEFORE),
          is(CheckedExceptionPolicy.WRAP));
    }

    // Branch 3: Per-type checked exception policy AROUND with REJECT
    {
      Properties props = baseProps();
      props.setProperty("pal.intercept.checked-exception-policy.around", "REJECT");
      CustomClassloader cl =
          new CustomClassloader(new URL[] {}, ClassLoader.getSystemClassLoader());
      PeerWiring wiring = new PeerWiring(props, EnumSet.noneOf(RunOptions.class), ctx, cl);
      ExceptionPolicyConfig config = wiring.provideExceptionPolicyConfig();
      assertThat(
          config.getCheckedExceptionPolicyForType(InterceptType.AROUND),
          is(CheckedExceptionPolicy.REJECT));
    }

    // Branch 4: Invalid per-type propagation policy for AFTER
    // When invalid, the per-type policy remains null (no override applied)
    {
      Properties props = baseProps();
      props.setProperty("pal.intercept.exception-policy.after", "INVALID_VALUE");
      CustomClassloader cl =
          new CustomClassloader(new URL[] {}, ClassLoader.getSystemClassLoader());
      PeerWiring wiring = new PeerWiring(props, EnumSet.noneOf(RunOptions.class), ctx, cl);
      ExceptionPolicyConfig config = wiring.provideExceptionPolicyConfig();
      // Invalid value should result in null (no per-type override), not affect global
      assertThat(config.getPropagationPolicyForType(InterceptType.AFTER), is((Object) null));
      // Global default should still be PROPAGATE_CONTROLLED_ONLY
      assertThat(
          config.getGlobalPropagationPolicy(),
          is(ExceptionPropagationPolicy.PROPAGATE_CONTROLLED_ONLY));
    }

    // Branch 5: Invalid per-type checked exception policy for BEFORE
    {
      Properties props = baseProps();
      props.setProperty("pal.intercept.checked-exception-policy.before", "INVALID_VALUE");
      CustomClassloader cl =
          new CustomClassloader(new URL[] {}, ClassLoader.getSystemClassLoader());
      PeerWiring wiring = new PeerWiring(props, EnumSet.noneOf(RunOptions.class), ctx, cl);
      ExceptionPolicyConfig config = wiring.provideExceptionPolicyConfig();
      // Invalid value should result in null (no per-type override)
      assertThat(config.getCheckedExceptionPolicyForType(InterceptType.BEFORE), is((Object) null));
      // Global default should still be WRAP
      assertThat(config.getGlobalCheckedExceptionPolicy(), is(CheckedExceptionPolicy.WRAP));
    }

    // Branch 6: All InterceptTypes exercised in per-type loops with SWALLOW_ALL
    {
      Properties props = baseProps();
      props.setProperty("pal.intercept.exception-policy.before", "SWALLOW_ALL");
      props.setProperty("pal.intercept.exception-policy.after", "SWALLOW_ALL");
      props.setProperty("pal.intercept.exception-policy.around", "SWALLOW_ALL");
      props.setProperty("pal.intercept.checked-exception-policy.before", "ALLOW_ALL");
      props.setProperty("pal.intercept.checked-exception-policy.after", "ALLOW_ALL");
      props.setProperty("pal.intercept.checked-exception-policy.around", "ALLOW_ALL");
      CustomClassloader cl =
          new CustomClassloader(new URL[] {}, ClassLoader.getSystemClassLoader());
      PeerWiring wiring = new PeerWiring(props, EnumSet.noneOf(RunOptions.class), ctx, cl);
      ExceptionPolicyConfig config = wiring.provideExceptionPolicyConfig();
      // Verify each type has its configured propagation policy
      assertThat(
          config.getPropagationPolicyForType(InterceptType.BEFORE),
          is(ExceptionPropagationPolicy.SWALLOW_ALL));
      assertThat(
          config.getPropagationPolicyForType(InterceptType.AFTER),
          is(ExceptionPropagationPolicy.SWALLOW_ALL));
      assertThat(
          config.getPropagationPolicyForType(InterceptType.AROUND),
          is(ExceptionPropagationPolicy.SWALLOW_ALL));
      // Verify each type has its configured checked exception policy
      assertThat(
          config.getCheckedExceptionPolicyForType(InterceptType.BEFORE),
          is(CheckedExceptionPolicy.ALLOW_ALL));
      assertThat(
          config.getCheckedExceptionPolicyForType(InterceptType.AFTER),
          is(CheckedExceptionPolicy.ALLOW_ALL));
      assertThat(
          config.getCheckedExceptionPolicyForType(InterceptType.AROUND),
          is(CheckedExceptionPolicy.ALLOW_ALL));
    }

    // Branch 7: ASYNC types override verification
    // Even if we attempt to set ASYNC types via properties, they should be hardcoded to SWALLOW_ALL
    {
      Properties props = baseProps();
      props.setProperty("pal.intercept.exception-policy.before-async", "PROPAGATE_ALL");
      props.setProperty("pal.intercept.exception-policy.after-async", "PROPAGATE_ALL");
      CustomClassloader cl =
          new CustomClassloader(new URL[] {}, ClassLoader.getSystemClassLoader());
      PeerWiring wiring = new PeerWiring(props, EnumSet.noneOf(RunOptions.class), ctx, cl);
      ExceptionPolicyConfig config = wiring.provideExceptionPolicyConfig();
      // ASYNC types must always be SWALLOW_ALL regardless of properties
      assertThat(
          config.getPropagationPolicyForType(InterceptType.BEFORE_ASYNC),
          is(ExceptionPropagationPolicy.SWALLOW_ALL));
      assertThat(
          config.getPropagationPolicyForType(InterceptType.AFTER_ASYNC),
          is(ExceptionPropagationPolicy.SWALLOW_ALL));
    }
  }
}
