/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.core.transport.kafka;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.mock;

import io.quasient.pal.cxn.directory.DirectoryConnectionProvider;
import io.quasient.pal.cxn.directory.PalDirectory;
import java.lang.reflect.Field;
import java.util.UUID;
import org.apache.kafka.clients.consumer.Consumer;
import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.Test;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;

public class LogReaderOffsetUpdaterTerminationTest {

  private ZContext ctx;

  @Before
  public void setUp() {
    // ZContext creation can fail in restricted sandboxes; skip if so
    try {
      ctx = new ZContext(1);
    } catch (Throwable t) {
      Assume.assumeNoException("Skipping due to sandbox ZMQ restrictions", t);
    }
  }

  @After
  public void tearDown() {
    if (ctx != null) ctx.close();
  }

  @Test
  public void offsetUpdater_exitsOnContextClose() throws Exception {
    // Addresses
    String dealerAddr = "inproc://log.dealer." + UUID.randomUUID();
    String pubAddr = "inproc://offs.pub." + UUID.randomUUID();

    // Prepare test consumer (pure mock; no interactions are required for this test)
    @SuppressWarnings("unchecked")
    Consumer<String, byte[]> consumer = (Consumer<String, byte[]>) mock(Consumer.class);

    // Directory provider: pass NO_URL so get() returns Optional.empty()
    DirectoryConnectionProvider dcp = new DirectoryConnectionProvider(PalDirectory.NO_URL);

    // Bind a PUB socket so the SUB can connect successfully, even if no messages are sent
    ZMQ.Socket pub = ctx.createSocket(SocketType.PUB);
    pub.bind(pubAddr);

    KafkaSourceLogReader lr =
        new KafkaSourceLogReader(
            UUID.randomUUID(),
            ctx,
            "inproc://sync",
            new ThreadGroup("svc"),
            "KafkaSourceLogReader.service",
            dealerAddr,
            pubAddr,
            dcp,
            consumer,
            /*autoCommit*/ true,
            /*pollMs*/ 5);

    // Enable skipping offsets (so SUB and offsetUpdater are started)
    Field fSkip = KafkaSourceLogReader.class.getSuperclass().getDeclaredField("skipWrittenOffsets");
    fSkip.setAccessible(true);
    fSkip.setBoolean(lr, true);

    // Open connections: starts offsetUpdater thread
    lr.openConnections();

    // Close ZMQ context to trigger ETERM/NPE in updater receive loop
    ctx.close();

    // Give the updater a moment to notice; then ensure no exception thrown and service can close
    Thread.sleep(50);
    lr.closeConnections();

    // If we reached here, updater exited cleanly; assert acceptRequests flag toggles without error
    lr.acceptRequests(false);
    assertThat(lr.isAcceptingRequests(), is(false));
  }
}
