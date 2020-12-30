/*
   Copyright (c) 2020 Contributors listed in the AUTHORS file

   This file is part of PAL, the friendly java runtime.

   PAL is free software: you can redistribute it and/or modify
   it under the terms of the GNU General Public License as published by
   the Free Software Foundation, either version 3 of the License, or
   (at your option) any later version.

   PAL is distributed in the hope that it will be useful,
   but WITHOUT ANY WARRANTY; without even the implied warranty of
   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
   GNU General Public License for more details.

   You should have received a copy of the GNU General Public License
   along with this program. If not, see <http://www.gnu.org/licenses/>.
*/

package net.ittera.pal.cxn;

import static org.junit.Assert.fail;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import net.ittera.pal.common.directory.nodes.LogInfo;
import net.ittera.pal.messages.MessageBuilder;
import net.ittera.pal.messages.ProtobufMessageBuilder;
import net.ittera.pal.messages.protobuf.Exec.ExecMessage;
import org.apache.curator.test.TestingServer;
import org.apache.kafka.clients.consumer.MockConsumer;
import org.apache.kafka.clients.consumer.OffsetResetStrategy;
import org.apache.kafka.clients.producer.MockProducer;
import org.apache.kafka.common.Cluster;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ThinPeerTest {

  private static final Logger logger = LoggerFactory.getLogger("tests");

  private final MessageBuilder msgBuilder = new ProtobufMessageBuilder();

  // PAL directory
  private TestingServer testingServer;
  private PALDirectory palDirectory;
  private static final int TEST_PORT = 2182;
  private static final String PAL_DIR_URL = String.format("localhost:%d", TEST_PORT);

  // mock Kafka producer & consumer
  private MockProducer<String, byte[]> producer;
  private MockConsumer<String, byte[]> consumer;

  private static final Set<UUID> createdPeers = new HashSet<>();
  private static final Set<LogInfo> createdLogs = new HashSet<>();
  private ThinPeer thinPeer;

  @Before
  public void setUp() throws Exception {
    // init PALDirectory
    testingServer = new TestingServer(TEST_PORT, true);
    palDirectory = new PALDirectory(PAL_DIR_URL);

    // init kafka producer & consumer
    producer = new MockProducer<>(Cluster.empty(), true, null, null, null);
    consumer = new MockConsumer<>(OffsetResetStrategy.EARLIEST);
  }

  @After
  public void tearDown() throws Exception {
    // close peer
    if (thinPeer != null && !thinPeer.isClosed()) {
      try {
        thinPeer.close();
      } catch (IllegalStateException e) {
        // we may close it after testing an uninitialized thin peer, so it's fine
      }
    }
    // unregister created peers and logs
    for (UUID peer : createdPeers) {
      palDirectory.unregisterPeer(peer);
      logger.info("Cleaned up created peer: {}", peer);
    }
    for (String log : createdLogs.stream().map(LogInfo::getName).collect(Collectors.toList())) {
      palDirectory.unregisterLog(log);
      logger.info("Cleaned up created log: {}", log);
    }
    // closed PAL Directory and ZK
    palDirectory.close();
    testingServer.close();
  }

  private LogInfo createLog(String name) throws Exception {
    LogInfo log = palDirectory.registerLog("testlog");
    createdLogs.add(log);
    return log;
  }

  private ThinPeer createUninitialized() throws Exception {
    return new ThinPeer()
        .withUUID(UUID.randomUUID())
        .withDirectory(palDirectory)
        .withConsumer(consumer)
        .withProducer(producer)
        .withLog(createLog("testlog"));
  }

  @Test
  public void initOK() throws Exception {
    thinPeer =
        new ThinPeer()
            .withDirectory(palDirectory)
            .withConsumer(consumer)
            .withProducer(producer)
            .withLog(createLog("testlog"))
            .init();
  }

  @Test
  public void initWithMissingDirectory() throws Exception {
    // no palDirectory nor palDirectoryUrl
    try {
      thinPeer =
          new ThinPeer()
              .withConsumer(consumer)
              .withProducer(producer)
              .withLog(createLog("testlog"))
              .init();
      fail("Should have raised RuntimeException");
    } catch (RuntimeException e) {
      // ok
    }
  }

  @Test
  public void initWithMissingConsumer() throws Exception {
    // no Consumer nor Consumer Properties
    try {
      thinPeer =
          new ThinPeer()
              .withDirectory(palDirectory)
              .withProducer(producer)
              .withLog(createLog("testlog"))
              .init();
      fail("Should have raised RuntimeException");
    } catch (RuntimeException e) {
      // ok
    }
  }

  @Test
  public void initWithMissingProducer() throws Exception {
    // no Producer nor Producer Properties
    try {
      thinPeer =
          new ThinPeer()
              .withDirectory(palDirectory)
              .withConsumer(consumer)
              .withLog(createLog("testlog"))
              .init();
      fail("Should have raised RuntimeException");
    } catch (RuntimeException e) {
      // ok
    }
  }

  @Test
  public void sendAndReceiveIllegalState() throws Exception {
    thinPeer = createUninitialized();
    ExecMessage msg = msgBuilder.buildEmptyConstructor(thinPeer.getPeerUuid(), "java.lang.String");
    try {
      thinPeer.sendAndReceive(msg, false);
      fail("Should have raised IllegalStateException");
    } catch (IllegalStateException e) {
      // ok
    }
  }

  //  @Test
  public void sendAndReceive() throws Exception {}

  //  @Test
  public void waitFor() {}

  //  @Test
  public void getMessageAtOffset() {}

  //  @Test
  public void getMessages() {}

  //  @Test
  public void getPeerUuid() {}

  //  @Test
  public void sendToLogAndForget() {}

  //  @Test
  public void sendToLogAndAsyncProcessReqAndRepNodes() {}

  //  @Test
  public void connectToPeer() {}

  //  @Test
  public void sendToPeer() {}

  //  @Test
  public void close() {}
}
