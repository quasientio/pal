/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.core.service;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.fail;

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
import org.junit.Ignore;
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
  // Test specifications for issue #557 - PeerWiring exception policy coverage
  // Implementation tracked in issue #558
  // ============================================================================

  /**
   * Test specification for default exception policy configuration.
   *
   * <p>Verifies that when no exception policy properties are configured, the method creates an
   * ExceptionPolicyConfig with all default values.
   */
  @Test
  @Ignore("Awaiting implementation in #558")
  public void testProvideExceptionPolicyConfig_defaultPolicy_createsConfig() {
    // Given: No exception policy properties configured (using base properties only)
    // When: provideExceptionPolicyConfig() is called
    // Then: ExceptionPolicyConfig is created with:
    //   - Global propagation policy = PROPAGATE_CONTROLLED_ONLY (default)
    //   - Global checked exception policy = WRAP (default)
    //   - No per-type overrides configured
    //   - ASYNC types hardcoded to SWALLOW_ALL

    // TODO(#558): Implement test logic
    // - Create PeerWiring with baseProps() only (no exception policy properties)
    // - Call provideExceptionPolicyConfig()
    // - Verify getGlobalPropagationPolicy() returns PROPAGATE_CONTROLLED_ONLY
    // - Verify getGlobalCheckedExceptionPolicy() returns WRAP
    // - Verify getPropagationPolicyForType(BEFORE) returns global default
    // - Verify getPropagationPolicyForType(AFTER) returns global default
    // - Verify getPropagationPolicyForType(AROUND) returns global default
    // - Verify getCheckedExceptionPolicyForType(BEFORE) returns global default
    // - Verify getCheckedExceptionPolicyForType(AFTER) returns global default
    // - Verify getCheckedExceptionPolicyForType(AROUND) returns global default

    fail("Not yet implemented");
  }

  /**
   * Test specification for retry-friendly exception policy configuration.
   *
   * <p>Verifies that PROPAGATE_EXPLICIT_ONLY and PROPAGATE_CONTROLLED_ONLY policies are correctly
   * parsed and applied. These policies allow controlled exception handling suitable for retry
   * scenarios.
   */
  @Test
  @Ignore("Awaiting implementation in #558")
  public void testProvideExceptionPolicyConfig_retryPolicy_createsConfig() {
    // Given: Exception policy properties configured for retry-friendly behavior:
    //   - pal.intercept.exception-policy.default = PROPAGATE_EXPLICIT_ONLY
    //   - pal.intercept.checked-exception-policy.default = WRAP
    //   - pal.intercept.exception-policy.around = PROPAGATE_CONTROLLED_ONLY
    // When: provideExceptionPolicyConfig() is called
    // Then: ExceptionPolicyConfig is created with:
    //   - Global propagation policy = PROPAGATE_EXPLICIT_ONLY
    //   - Global checked exception policy = WRAP
    //   - AROUND type uses PROPAGATE_CONTROLLED_ONLY override

    // TODO(#558): Implement test logic
    // - Set properties for PROPAGATE_EXPLICIT_ONLY global policy
    // - Set properties for PROPAGATE_CONTROLLED_ONLY on AROUND type
    // - Create PeerWiring and call provideExceptionPolicyConfig()
    // - Verify getGlobalPropagationPolicy() returns PROPAGATE_EXPLICIT_ONLY
    // - Verify getPropagationPolicyForType(AROUND) returns PROPAGATE_CONTROLLED_ONLY
    // - Verify WRAP policy is applied for checked exceptions

    fail("Not yet implemented");
  }

  /**
   * Test specification for fail-fast exception policy configuration.
   *
   * <p>Verifies that PROPAGATE_ALL propagation policy combined with REJECT checked exception policy
   * creates a fail-fast configuration where all exceptions propagate immediately.
   */
  @Test
  @Ignore("Awaiting implementation in #558")
  public void testProvideExceptionPolicyConfig_failFastPolicy_createsConfig() {
    // Given: Exception policy properties configured for fail-fast behavior:
    //   - pal.intercept.exception-policy.default = PROPAGATE_ALL
    //   - pal.intercept.checked-exception-policy.default = REJECT
    //   - pal.intercept.exception-policy.before = PROPAGATE_ALL
    //   - pal.intercept.exception-policy.after = PROPAGATE_ALL
    //   - pal.intercept.checked-exception-policy.before = REJECT
    //   - pal.intercept.checked-exception-policy.after = REJECT
    // When: provideExceptionPolicyConfig() is called
    // Then: ExceptionPolicyConfig is created with:
    //   - Global propagation policy = PROPAGATE_ALL
    //   - Global checked exception policy = REJECT
    //   - Per-type BEFORE and AFTER policies match fail-fast configuration

    // TODO(#558): Implement test logic
    // - Set all exception policy properties to PROPAGATE_ALL
    // - Set all checked exception policy properties to REJECT
    // - Create PeerWiring and call provideExceptionPolicyConfig()
    // - Verify global policies are PROPAGATE_ALL and REJECT
    // - Verify per-type policies for BEFORE, AFTER are PROPAGATE_ALL and REJECT
    // - Verify ASYNC types remain SWALLOW_ALL (hardcoded override)

    fail("Not yet implemented");
  }

  /**
   * Test specification for comprehensive exception policy branch coverage.
   *
   * <p>Verifies all branches of the provideExceptionPolicyConfig method are exercised including:
   * all ExceptionPropagationPolicy values, all CheckedExceptionPolicy values, all InterceptType
   * configurations, invalid property handling for per-type policies, and ASYNC type hardcoding.
   */
  @Test
  @Ignore("Awaiting implementation in #558")
  public void testProvideExceptionPolicyConfig_allPolicies_coverAllBranches() {
    // Given: Various exception policy configurations exercising all branches:
    //   - All ExceptionPropagationPolicy values: PROPAGATE_ALL, PROPAGATE_EXPLICIT_ONLY,
    //     SWALLOW_ALL, PROPAGATE_CONTROLLED_ONLY
    //   - All CheckedExceptionPolicy values: WRAP, REJECT, ALLOW_ALL
    //   - Per-type policies for BEFORE, AFTER, AROUND
    //   - Invalid per-type propagation policy values (should log warning and skip)
    //   - Invalid per-type checked exception policy values (should log warning and skip)
    // When: provideExceptionPolicyConfig() is called with each configuration
    // Then: All policy branches are covered:
    //   - Global propagation policy parsing (valid and invalid)
    //   - Global checked exception policy parsing (valid and invalid)
    //   - Per-type propagation policy loop for BEFORE, AFTER, AROUND (valid and invalid)
    //   - Per-type checked exception policy loop for BEFORE, AFTER, AROUND (valid and invalid)
    //   - ASYNC type hardcoding always applies SWALLOW_ALL

    // TODO(#558): Implement test logic covering these branches:
    //
    // Branch 1: Per-type propagation policy AROUND with PROPAGATE_EXPLICIT_ONLY
    // - Set pal.intercept.exception-policy.around = PROPAGATE_EXPLICIT_ONLY
    // - Verify getPropagationPolicyForType(AROUND) returns PROPAGATE_EXPLICIT_ONLY
    //
    // Branch 2: Per-type checked exception policy BEFORE with WRAP
    // - Set pal.intercept.checked-exception-policy.before = WRAP
    // - Verify getCheckedExceptionPolicyForType(BEFORE) returns WRAP
    //
    // Branch 3: Per-type checked exception policy AROUND with REJECT
    // - Set pal.intercept.checked-exception-policy.around = REJECT
    // - Verify getCheckedExceptionPolicyForType(AROUND) returns REJECT
    //
    // Branch 4: Invalid per-type propagation policy for AFTER
    // - Set pal.intercept.exception-policy.after = INVALID_VALUE
    // - Verify getPropagationPolicyForType(AFTER) returns global default (not INVALID_VALUE)
    //
    // Branch 5: Invalid per-type checked exception policy for BEFORE
    // - Set pal.intercept.checked-exception-policy.before = INVALID_VALUE
    // - Verify getCheckedExceptionPolicyForType(BEFORE) returns global default (not INVALID_VALUE)
    //
    // Branch 6: All InterceptTypes exercised in per-type loops
    // - Configure policies for BEFORE, AFTER, and AROUND types
    // - Verify each type has its configured policy
    //
    // Branch 7: ASYNC types override verification
    // - Even if pal.intercept.exception-policy.before-async set to PROPAGATE_ALL
    // - Verify getPropagationPolicyForType(BEFORE_ASYNC) still returns SWALLOW_ALL
    // - Verify getPropagationPolicyForType(AFTER_ASYNC) still returns SWALLOW_ALL

    fail("Not yet implemented");
  }
}
