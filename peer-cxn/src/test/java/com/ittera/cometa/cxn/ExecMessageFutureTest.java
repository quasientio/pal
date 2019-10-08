package com.ittera.cometa.cxn;

import com.ittera.cometa.LogInfo;
import com.ittera.cometa.LogReply;
import com.ittera.cometa.LogRequest;
import com.ittera.cometa.messages.MessageBuilder;
import com.ittera.cometa.messages.protobuf.ProtobufMessageBuilder;
import com.ittera.cometa.messages.protobuf.data.Wrappers.ExecMessage;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

import org.apache.curator.test.TestingServer;

import static org.mockito.Mockito.*;

public class ExecMessageFutureTest {

	protected final static Logger logger = LoggerFactory.getLogger("tests");

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
		ExecMessage fakeReplyMessage = messageBuilder.buildEmptyConstructor(peerUuid, "java.lang.String");

		// create reply node, but don't add to directory just yet
		UUID replyUuid = UUID.fromString(fakeReplyMessage.getMessageUuid());
		Long someOffset = 5L;
		LogReply logReply = new LogReply(replyUuid, peerUuid, requestMsgUuid, someOffset);

		// set up mock ThinPeer
		thinPeer = mock(ThinPeer.class);
		when(thinPeer.getMessageAtOffset(someOffset)).thenReturn(fakeReplyMessage);

		// create message future
		final ExecMessageFuture messageFuture = new ExecMessageFuture(thinPeer, palDirectory, executorService, logName,
			logRequest);

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
		palDirectory.addLogReplyAsync(logName, logReply, (curatorFramework, curatorEvent) -> latch.countDown());
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
