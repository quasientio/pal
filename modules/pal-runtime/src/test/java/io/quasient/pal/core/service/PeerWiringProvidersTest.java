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
}
