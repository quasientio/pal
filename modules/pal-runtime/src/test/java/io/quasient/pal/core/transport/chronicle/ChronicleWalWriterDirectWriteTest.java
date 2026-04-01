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
package io.quasient.pal.core.transport.chronicle;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

import com.google.common.util.concurrent.Service;
import com.google.common.util.concurrent.ServiceManager;
import io.quasient.pal.common.directory.nodes.LogInfo;
import io.quasient.pal.common.runtime.ExecPhase;
import io.quasient.pal.core.ZmqEnabledTest;
import io.quasient.pal.messages.OutboundMsg;
import io.quasient.pal.messages.colfer.Message;
import io.quasient.pal.messages.types.MessageType;
import io.quasient.pal.serdes.colfer.MessageBuilder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import net.openhft.chronicle.queue.ExcerptTailer;
import net.openhft.chronicle.queue.RollCycles;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.zeromq.ZContext;

/** Direct-write (queueless) tests for ChronicleWalWriter. */
public class ChronicleWalWriterDirectWriteTest extends ZmqEnabledTest {

  private static final UUID PEER_ID = UUID.randomUUID();
  private static final LogInfo WAL_INFO = new LogInfo("direct_app", "n/a");

  private ChronicleWalWriter writer;
  private ZContext ctx;
  private Path baseDir;
  private ServiceManager manager;
  private final MessageBuilder builder = new MessageBuilder();

  private static OutboundMsg wrap(Message body) {
    String msgId = body.getExecMessage().getMessageId();
    String respId = body.getExecMessage().getResponseToId();
    return new OutboundMsg(
        MessageType.EXEC_CONSTRUCTOR, ExecPhase.BEFORE, null, msgId, respId, body);
  }

  @Before
  public void setUp() throws Exception {
    baseDir = Files.createTempDirectory("chronicle-wal-direct");
    AtomicBoolean walFailed = new AtomicBoolean(false);

    ctx = createContext();
    writer =
        new ChronicleWalWriter(
            PEER_ID,
            ctx,
            SYNC_SOCKET_ADDRESS,
            new ThreadGroup("services-thread-group"),
            "ChronicleWalWriter.Direct",
            /* walQueue */ null, // direct-write mode
            walFailed,
            /* offset.pub */ "inproc://offsets",
            /* flushOnClose */ "true",
            baseDir,
            "TEN_MINUTELY",
            null,
            null,
            /* indexSpacing */ null,
            /* offsetsRingSize */ null,
            new DefaultChronicleQueueFactory());

    writer.writeToLog(WAL_INFO, /* publishOffsets */ true);

    Set<Service> services = new HashSet<>(Collections.singletonList(writer));
    manager = new ServiceManager(services);
    manager.startAsync().awaitHealthy();
    collectGoSignals(services.size(), ctx);
  }

  @After
  public void cleanup() throws Exception {
    if (manager != null) {
      manager.stopAsync().awaitStopped();
    }
    closeContext(ctx);
  }

  @Test
  public void directWrite_writesAndFlushOnClose() throws Exception {
    // write a couple of messages from current thread using direct-write API
    for (int i = 0; i < 3; i++) {
      Message m = builder.wrap(builder.buildEmptyConstructor(PEER_ID, "java.lang.String"));
      writer.writeMessage(wrap(m));
    }

    // stop service to trigger flushOnClose and proper resource shutdown
    manager.stopAsync().awaitStopped();

    // re-open queue and verify messages exist
    Path qPath = baseDir.resolve(WAL_INFO.getName());
    try (var queue =
            new DefaultChronicleQueueFactory()
                .create(qPath, RollCycles.TEN_MINUTELY, 256, 128 * 1024 * 1024);
        ExcerptTailer tailer = queue.createTailer()) {
      int count = 0;
      while (true) {
        OutboundMsg om = OutboundMsg.readNext(tailer);
        if (om == null) break;
        count++;
      }
      assertThat(count, is(3));
    }
  }
}
