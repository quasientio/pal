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

import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import net.ittera.pal.common.directory.nodes.LogInfo;
import net.ittera.pal.common.directory.nodes.LogReply;
import net.ittera.pal.common.directory.nodes.LogRequest;
import net.ittera.pal.messages.MessageBuilder;
import net.ittera.pal.messages.ProtobufMessageBuilder;
import net.ittera.pal.messages.protobuf.Exec.ExecMessage;
import net.ittera.pal.messages.protobuf.Wrappers.Message;
import org.apache.curator.test.TestingServer;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ExecMessageFutureTest {

  protected static final Logger logger = LoggerFactory.getLogger("tests");

  private static final int TEST_PORT = 2182;
  private static final String CONNECTION_STR = String.format("localhost:%d", TEST_PORT);

  private static final Set<String> createdLogs = new HashSet<>();

  private final MessageBuilder messageBuilder = new ProtobufMessageBuilder();
  private static final UUID peerUuid = UUID.randomUUID();
  private ThinPeer thinPeer; // mocked!
  private PALDirectory palDirectory;
  private TestingServer testingServer;
  private final ExecutorService executorService = Executors.newSingleThreadExecutor();

  @Before
  public void setup() throws Exception {
    testingServer = new TestingServer(TEST_PORT, true);
    this.palDirectory = new PALDirectory(CONNECTION_STR);
  }

  @After
  public void cleanup() throws Exception {
    for (String log : createdLogs) {
      palDirectory.unregisterLog(log);
      logger.info("Cleaned up created log: {}", log);
    }
    palDirectory.close();
    testingServer.close();
  }

  @Test
  public void replyAddedInNoTime() throws Exception {
    testWithVariableReplyProcessingTime(null);
  }

  @Test
  public void replyAddedQuickly() throws Exception {
    testWithVariableReplyProcessingTime(10L);
  }

  @Test
  public void replyAddedSlowly() throws Exception {
    testWithVariableReplyProcessingTime(100L);
  }

  @Test
  public void replyAddedVerySlowly() throws Exception {
    testWithVariableReplyProcessingTime(1000L);
  }

  private void testWithVariableReplyProcessingTime(Long replyProcessingTime) throws Exception {
    // create log
    String logName = "message_future_test";
    LogInfo newLogInfo = palDirectory.registerLog(logName);
    createdLogs.add(logName);

    // create LogRequest object, but don't add to directory just yet
    UUID requestMsgUuid = UUID.randomUUID();
    LogRequest logRequest = new LogRequest(requestMsgUuid);

    // create reply message (ofc a constructor call is not a reply but who cares)
    ExecMessage fakeReplyMessage =
        messageBuilder.buildEmptyConstructor(peerUuid, "java.lang.String");
    Message fakeReplyWrapper = messageBuilder.wrap(fakeReplyMessage);

    // create reply node, but don't add to directory just yet
    UUID replyUuid = UUID.fromString(fakeReplyMessage.getMessageUuid());
    Long someOffset = 5L;
    LogReply logReply = new LogReply(replyUuid, peerUuid, requestMsgUuid, someOffset);

    // set up mock ThinPeer
    thinPeer = mock(ThinPeer.class);
    when(thinPeer.getMessageAtOffset(someOffset)).thenReturn(fakeReplyWrapper);

    // create message future
    final ExecMessageFuture messageFuture =
        new ExecMessageFuture(thinPeer, palDirectory, executorService, logName, logRequest);

    // OK, now asynchronously create req node
    palDirectory.addLogRequestAsync(logName, logRequest, messageFuture);

    /* wait a little and then add the Reply node
    WAIT TIME IS IMPORTANT since it will change which of ExecMessageFuture's callback gets the 'child added' event
    */
    if (replyProcessingTime != null) {
      Thread.sleep(replyProcessingTime);
    }

    // now asynchronously create rep node and wait for it to be created
    CountDownLatch latch = new CountDownLatch(1);
    palDirectory.addLogReplyAsync(
        logName, logReply, (curatorFramework, curatorEvent) -> latch.countDown());
    if (!latch.await(3, TimeUnit.SECONDS)) {
      fail("Timeout awaiting latch downcount - node not created?");
    }

    // wait for Future to complete
    ExecMessage replyMsg = messageFuture.get();

    // verify
    assertThat(replyMsg, is(fakeReplyMessage));

    // verify mock calls
    verify(thinPeer, times(1)).getMessageAtOffset(someOffset);
  }
}
