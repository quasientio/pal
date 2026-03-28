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

import io.quasient.pal.core.execution.java.CustomClassloader;
import io.quasient.pal.core.replay.ReplayPolicy;
import java.lang.reflect.Method;
import java.net.URL;
import java.util.EnumSet;
import java.util.Properties;
import java.util.UUID;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.zeromq.ZContext;

/**
 * Tests for the replay policy construction logic in {@link PeerWiring}.
 *
 * <p>Verifies that the {@code buildReplayPolicy()} method correctly constructs a {@link
 * ReplayPolicy} from the configured properties.
 */
public class PeerWiringReplayPolicyTest {

  /** ZeroMQ context for PeerWiring construction. */
  private ZContext ctx;

  /** Sets up the ZeroMQ context. */
  @Before
  public void setUp() {
    ctx = new ZContext(1);
  }

  /** Tears down the ZeroMQ context. */
  @After
  public void tearDown() {
    ctx.close();
  }

  /** Tests that buildReplayPolicy returns default policy when no config properties are set. */
  @Test
  public void buildReplayPolicy_returnsDefault_whenNoConfig() throws Exception {
    Properties props = baseProps();
    PeerWiring wiring = createWiring(props);

    ReplayPolicy policy = invokeBuildReplayPolicy(wiring);
    assertThat(policy, notNullValue());
    assertThat(policy.getRules().isEmpty(), is(true));
    assertThat(policy.getDefaultAction(), is(ReplayPolicy.ReplayAction.RE_EXECUTE));
  }

  /** Tests that buildReplayPolicy uses ReplayPolicyParser when shield-io is set. */
  @Test
  public void buildReplayPolicy_usesParser_whenShieldIoSet() throws Exception {
    Properties props = baseProps();
    props.setProperty("replay.shield.io", "true");
    PeerWiring wiring = createWiring(props);

    ReplayPolicy policy = invokeBuildReplayPolicy(wiring);
    assertThat(policy, notNullValue());
    assertThat(policy.getRules().isEmpty(), is(false));
  }

  /** Tests that buildReplayPolicy parses re-execute patterns from properties. */
  @Test
  public void buildReplayPolicy_parsesReExecutePatterns() throws Exception {
    Properties props = baseProps();
    props.setProperty("replay.re-execute.patterns", "com.example.**");
    PeerWiring wiring = createWiring(props);

    ReplayPolicy policy = invokeBuildReplayPolicy(wiring);
    assertThat(policy, notNullValue());
    assertThat(policy.getRules().isEmpty(), is(false));
    assertThat(policy.getRules().get(0).getAction(), is(ReplayPolicy.ReplayAction.RE_EXECUTE));
  }

  /** Tests that buildReplayPolicy parses stub patterns from properties. */
  @Test
  public void buildReplayPolicy_parsesStubPatterns() throws Exception {
    Properties props = baseProps();
    props.setProperty("replay.stub.patterns", "java.io.**");
    PeerWiring wiring = createWiring(props);

    ReplayPolicy policy = invokeBuildReplayPolicy(wiring);
    assertThat(policy, notNullValue());
    assertThat(policy.getRules().isEmpty(), is(false));
    assertThat(policy.getRules().get(0).getAction(), is(ReplayPolicy.ReplayAction.STUB_FROM_WAL));
  }

  /** Tests that buildReplayPolicy sets default to STUB_FROM_WAL when stub-all-else is true. */
  @Test
  public void buildReplayPolicy_stubAllElse_setsDefault() throws Exception {
    Properties props = baseProps();
    props.setProperty("replay.stub.all.else", "true");
    PeerWiring wiring = createWiring(props);

    ReplayPolicy policy = invokeBuildReplayPolicy(wiring);
    assertThat(policy, notNullValue());
    assertThat(policy.getDefaultAction(), is(ReplayPolicy.ReplayAction.STUB_FROM_WAL));
  }

  // ===========================================================================
  // Helper methods
  // ===========================================================================

  /** Creates a base set of properties required by PeerWiring. */
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
    p.setProperty("pub.spsc_size", "1024");
    p.setProperty("pub.batch_size", "64");
    p.setProperty("pub.flush_on_close", "true");
    p.setProperty("out.pub", "inproc://pub");
    p.setProperty("pub.zmq.linger", "0");
    p.setProperty("pub.zmq.send_timeout", "10");
    p.setProperty("pub.zmq.send_hwm", "1000");
    p.setProperty("pub.drop.policy", "NONE");
    p.setProperty("pub.drop.hwm_pct", "80");
    p.setProperty("pub.drop.keep_pct", "60");
    p.setProperty("wal.type", "CHRONICLE");
    return p;
  }

  /** Creates a PeerWiring instance with the given properties. */
  private PeerWiring createWiring(Properties props) {
    CustomClassloader cl = new CustomClassloader(new URL[] {}, ClassLoader.getSystemClassLoader());
    return new PeerWiring(props, EnumSet.noneOf(RunOptions.class), ctx, cl);
  }

  /** Invokes the private buildReplayPolicy method on PeerWiring via reflection. */
  private static ReplayPolicy invokeBuildReplayPolicy(PeerWiring wiring) throws Exception {
    Method m = PeerWiring.class.getDeclaredMethod("buildReplayPolicy");
    m.setAccessible(true);
    return (ReplayPolicy) m.invoke(wiring);
  }
}
