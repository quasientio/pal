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
import static org.junit.Assert.assertThrows;

import io.quasient.pal.core.execution.java.CustomClassloader;
import io.quasient.pal.core.transport.WalWriter;
import io.quasient.pal.core.transport.zmq.publish.MessagePublisherConfig;
import io.quasient.pal.core.transport.zmq.publish.PublishingDropPolicy;
import java.net.URL;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.Properties;
import java.util.UUID;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.zeromq.ZContext;

public class PeerWiringWalWriterAndPolicyTest {

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
    // Required for publisher config
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
    // WAL type default for when WITH_WAL is enabled
    p.setProperty("wal.type", "CHRONICLE");
    return p;
  }

  @Test
  public void provideWalWriter_returnsNull_whenWithoutWal() {
    Properties props = baseProps();
    CustomClassloader cl = new CustomClassloader(new URL[] {}, ClassLoader.getSystemClassLoader());
    PeerWiring wiring = new PeerWiring(props, EnumSet.noneOf(RunOptions.class), ctx, cl);
    WalWriter writer = wiring.provideWalWriter(null, null);
    assertThat(writer == null, is(true));
  }

  @Test
  public void providePublishingDropPolicy_parsesEnum() {
    Properties props = baseProps();
    props.setProperty("pub.drop.policy", "DROP_OLD");
    CustomClassloader cl = new CustomClassloader(new URL[] {}, ClassLoader.getSystemClassLoader());
    PeerWiring wiring = new PeerWiring(props, EnumSet.noneOf(RunOptions.class), ctx, cl);
    PublishingDropPolicy pol = wiring.providePublishingDropPolicy();
    assertThat(pol, is(PublishingDropPolicy.DROP_OLD));
  }

  @Test
  public void walQueue_none_returnsNull_pubQueue_none_throws() {
    Properties props = baseProps();
    props.setProperty("wal.queue.type", "NONE");
    props.setProperty("pub.queue.type", "NONE");
    CustomClassloader cl = new CustomClassloader(new URL[] {}, ClassLoader.getSystemClassLoader());
    PeerWiring wiring = new PeerWiring(props, EnumSet.noneOf(RunOptions.class), ctx, cl);
    // wal NONE → null
    var walQ = wiring.provideWalQueue();
    assertThat(walQ == null, is(true));
    // pub NONE → throws
    assertThrows(IllegalArgumentException.class, wiring::providePubQueue);
  }

  @Test
  public void provideMessagePublisherConfig_buildsFromProps() {
    Properties props = baseProps();
    props.setProperty("pub.drop.policy", "NONE");
    CustomClassloader cl = new CustomClassloader(new URL[] {}, ClassLoader.getSystemClassLoader());
    PeerWiring wiring = new PeerWiring(props, EnumSet.noneOf(RunOptions.class), ctx, cl);
    PublishingDropPolicy pol = wiring.providePublishingDropPolicy();
    MessagePublisherConfig cfg = wiring.provideMessagePublisherConfig(pol);
    assertThat(cfg.zmqPubAddress(), is("inproc://pub"));
    assertThat(cfg.dropPolicy(), is(PublishingDropPolicy.NONE));
    assertThat(cfg.zmqSndHWM(), is(1000));
  }

  @Test
  public void provideChronicleBaseDir_readsProperty() {
    Properties props = baseProps();
    CustomClassloader cl = new CustomClassloader(new URL[] {}, ClassLoader.getSystemClassLoader());
    PeerWiring wiring = new PeerWiring(props, EnumSet.noneOf(RunOptions.class), ctx, cl);
    Path base = wiring.provideChronicleBaseDir();
    assertThat(base, notNullValue());
  }
}
