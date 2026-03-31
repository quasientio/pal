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
import io.quasient.pal.core.internal.messages.InboundLogMsg;
import io.quasient.pal.core.internal.messages.PublishedOffsetMsg;
import io.quasient.pal.messages.OutboundMsg;
import io.quasient.pal.messages.colfer.ExecMessage;
import io.quasient.pal.messages.colfer.Message;
import io.quasient.pal.messages.types.MessageType;
import io.quasient.pal.serdes.colfer.MessageBuilder;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import net.openhft.chronicle.queue.ChronicleQueue;
import net.openhft.chronicle.queue.ExcerptAppender;
import net.openhft.chronicle.queue.RollCycles;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.zeromq.SocketType;
import org.zeromq.ZContext;
import org.zeromq.ZMQ;
import org.zeromq.ZMQException;
import zmq.ZError;

/**
 * Tests for offset-skipping behavior in {@link ChronicleSourceLogReader}.
 *
 * <p>When source log and WAL are the same Chronicle queue ({@code skipWrittenOffsets=true}), the
 * reader should skip messages whose indices have been published via the offset PUB socket — the
 * same mechanism used by the Kafka reader.
 */
public class ChronicleSourceLogReaderOffsetSkipTest extends ZmqEnabledTest {

  private static final Logger logger = LoggerFactory.getLogger("tests");

  private static final String DEALER_ADDRESS = "inproc://chronicle_skip_dealer";
  private static final String OFFSET_PUB_ADDRESS = "inproc://chronicle_skip_offsets";

  private ExecutorService execService;
  private ZContext zmqContext;
  private ChronicleSourceLogReader logReader;
  private final UUID peerUuid = UUID.randomUUID();
  private ServiceManager manager;
  private final ThreadGroup servicesThreadGroup = new ThreadGroup("services-thread-group");
  private Path tempDir;
  private Path queuePath;
  private DefaultChronicleQueueFactory queueFactory;

  /** PUB socket simulating the WAL writer's offset publisher. */
  private ZMQ.Socket offsetPubSocket;

  @Before
  public void setup() throws Exception {
    execService = Executors.newSingleThreadExecutor();
    zmqContext = createContext();
    queueFactory = new DefaultChronicleQueueFactory();

    tempDir = Files.createTempDirectory("chronicle-skip-test");
    queuePath = tempDir.resolve("test-queue");

    // Bind PUB socket BEFORE creating the reader — the reader's OffsetUpdater will connect to it
    offsetPubSocket = zmqContext.createSocket(SocketType.PUB);
    offsetPubSocket.bind(OFFSET_PUB_ADDRESS);

    logReader =
        new ChronicleSourceLogReader(
            peerUuid,
            zmqContext,
            SYNC_SOCKET_ADDRESS,
            servicesThreadGroup,
            "ChronicleSkipTest",
            DEALER_ADDRESS,
            OFFSET_PUB_ADDRESS,
            tempDir,
            queueFactory);
  }

  @After
  public void cleanup() throws Exception {
    if (manager != null) {
      manager.stopAsync().awaitStopped(5, TimeUnit.SECONDS);
    }
    closeContext(zmqContext);
    execService.shutdownNow();
    execService.awaitTermination(5, TimeUnit.SECONDS);

    if (tempDir != null && Files.exists(tempDir)) {
      try (Stream<Path> files = Files.walk(tempDir)) {
        files
            .sorted(Comparator.reverseOrder())
            .forEach(
                path -> {
                  try {
                    Files.delete(path);
                  } catch (IOException e) {
                    // Ignore cleanup errors
                  }
                });
      }
    }
  }

  /**
   * Writes {@code count} messages to the Chronicle queue at {@code queuePath}.
   *
   * @return ordered map of Chronicle index → message ID for each written message
   */
  private Map<Long, String> writeMessages(int count) throws Exception {
    Map<Long, String> indexToMsgId = new LinkedHashMap<>();
    MessageBuilder msgBuilder = new MessageBuilder();

    try (ChronicleQueue queue =
        queueFactory.create(queuePath, RollCycles.TEN_MINUTELY, 1000, 64 * 1024 * 1024)) {
      ExcerptAppender appender = queue.createAppender();

      for (int i = 0; i < count; i++) {
        ExecMessage execMsg = msgBuilder.buildEmptyConstructor(peerUuid, "java.lang.String");
        Message wrapper = msgBuilder.wrap(execMsg);
        OutboundMsg outboundMsg =
            new OutboundMsg(
                MessageType.EXEC_CONSTRUCTOR,
                ExecPhase.BEFORE,
                null,
                execMsg.getMessageId(),
                execMsg.getResponseToId(),
                wrapper);
        long index = outboundMsg.appendTo(appender);
        indexToMsgId.put(index, execMsg.getMessageId());
      }
    }
    return indexToMsgId;
  }

  /**
   * Publishes indices to the offset PUB socket, simulating the WAL writer publishing written
   * offsets.
   */
  private void publishSkipIndices(Set<Long> indices) {
    for (long index : indices) {
      new PublishedOffsetMsg(index, "skip-" + index).send(offsetPubSocket);
    }
  }

  /**
   * When {@code skipWrittenOffsets=true} and indices are published via the offset PUB socket, the
   * reader must not dispatch messages at those indices.
   *
   * <p>This simulates the scenario where source log and WAL are the same Chronicle queue: the WAL
   * writer publishes the indices of messages it wrote, and the reader skips them to avoid
   * re-processing its own output.
   */
  @Test
  public void run_skipWrittenOffsets_skipsPublishedIndices() throws Exception {
    // Given: 10 messages in the Chronicle queue
    int totalMessages = 10;
    Map<Long, String> written = writeMessages(totalMessages);
    List<Long> indices = new ArrayList<>(written.keySet());
    List<String> messageIds = new ArrayList<>(written.values());

    // Choose indices 3, 4, 5 (0-based) to skip
    Set<Long> skipIndices = new HashSet<>();
    skipIndices.add(indices.get(3));
    skipIndices.add(indices.get(4));
    skipIndices.add(indices.get(5));

    // Expected: messages 0,1,2,6,7,8,9 dispatched (7 out of 10)
    Set<String> expectedIds = new HashSet<>();
    for (int i = 0; i < totalMessages; i++) {
      if (!skipIndices.contains(indices.get(i))) {
        expectedIds.add(messageIds.get(i));
      }
    }
    int expectedCount = totalMessages - skipIndices.size();

    // Configure reader with skipWrittenOffsets=true
    LogInfo log = new LogInfo(queuePath.toString());
    log.setLogType(LogInfo.LogType.CHRONICLE);
    logReader.readFromLog(log, /* skipWrittenOffsets */ true, null, true);

    // Start worker to receive dispatched messages
    SkipTestWorker worker = new SkipTestWorker(zmqContext, DEALER_ADDRESS);
    @SuppressWarnings("unused")
    var workerFuture = execService.submit(worker);

    // Start reader service (opens connections, creates SUB + OffsetUpdater)
    Set<Service> services = new HashSet<>();
    services.add(logReader);
    manager = new ServiceManager(services);
    manager.startAsync().awaitHealthy(5, TimeUnit.SECONDS);
    collectGoSignals(services.size(), zmqContext);

    // Publish skip indices — small sleep to let SUB subscription propagate
    Thread.sleep(100);
    publishSkipIndices(skipIndices);

    // Small sleep to let OffsetUpdater receive the published indices
    Thread.sleep(100);

    // Now allow the reader to start processing
    logReader.acceptRequests(true);

    // Wait for expected messages
    int maxWait = 100;
    int waited = 0;
    while (worker.getMessagesProcessed() < expectedCount && waited < maxWait) {
      Thread.sleep(100);
      waited++;
    }

    // Then: only non-skipped messages are dispatched
    assertThat(
        "Should dispatch only non-skipped messages",
        worker.getMessagesProcessed(),
        is(expectedCount));
    assertThat(
        "Dispatched message IDs should match expected set",
        worker.getReceivedMessageIds(),
        is(expectedIds));
  }

  /**
   * Control test: when {@code skipWrittenOffsets=false}, all messages are dispatched regardless of
   * any published indices.
   */
  @Test
  public void run_skipWrittenOffsets_false_dispatchesAllMessages() throws Exception {
    // Given: 10 messages in the Chronicle queue
    int totalMessages = 10;
    Map<Long, String> written = writeMessages(totalMessages);
    Set<String> allMessageIds = new HashSet<>(written.values());

    // Configure reader with skipWrittenOffsets=false
    LogInfo log = new LogInfo(queuePath.toString());
    log.setLogType(LogInfo.LogType.CHRONICLE);
    logReader.readFromLog(log, /* skipWrittenOffsets */ false, null, false);

    // Start worker
    SkipTestWorker worker = new SkipTestWorker(zmqContext, DEALER_ADDRESS);
    @SuppressWarnings("unused")
    var workerFuture = execService.submit(worker);

    // Start reader service
    Set<Service> services = new HashSet<>();
    services.add(logReader);
    manager = new ServiceManager(services);
    manager.startAsync().awaitHealthy(5, TimeUnit.SECONDS);
    collectGoSignals(services.size(), zmqContext);

    logReader.acceptRequests(true);

    // Wait for all messages
    int maxWait = 100;
    int waited = 0;
    while (worker.getMessagesProcessed() < totalMessages && waited < maxWait) {
      Thread.sleep(100);
      waited++;
    }

    // Then: all messages dispatched
    assertThat("Should dispatch all messages", worker.getMessagesProcessed(), is(totalMessages));
    assertThat(
        "All message IDs should be received", worker.getReceivedMessageIds(), is(allMessageIds));
  }

  /** Minimal worker that collects dispatched message IDs for assertion. */
  static class SkipTestWorker implements Runnable {

    private final ZMQ.Socket socket;
    private final String dealerAddress;
    private final Set<String> receivedMessageIds = new HashSet<>();
    private final AtomicInteger messagesProcessed = new AtomicInteger(0);

    SkipTestWorker(ZContext context, String dealerAddress) {
      this.dealerAddress = dealerAddress;
      this.socket = context.createSocket(SocketType.REP);
    }

    @Override
    public void run() {
      socket.connect(dealerAddress);
      while (!Thread.interrupted()) {
        try {
          InboundLogMsg logMsg = InboundLogMsg.receive(socket, true);
          if (logMsg != null) {
            Message wrapper = new Message();
            wrapper.unmarshal(logMsg.getBody(), 0);
            if (wrapper.getExecMessage() != null) {
              ExecMessage msg = wrapper.getExecMessage();
              receivedMessageIds.add(msg.getMessageId());
              messagesProcessed.incrementAndGet();
            }
          }
        } catch (ZMQException ex) {
          if (ex.getErrorCode() == ZError.ETERM || ex.getErrorCode() == ZError.EINTR) {
            break;
          }
          throw ex;
        } catch (Exception e) {
          logger.error("error parsing received message", e);
        }
      }
      socket.close();
    }

    Set<String> getReceivedMessageIds() {
      return receivedMessageIds;
    }

    int getMessagesProcessed() {
      return messagesProcessed.get();
    }
  }
}
