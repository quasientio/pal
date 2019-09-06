package com.ittera.cometa.cxn;

import com.ittera.cometa.LogInfo;
import com.ittera.cometa.LogReply;
import com.ittera.cometa.LogRequest;
import com.ittera.cometa.PeerInfo;

import org.apache.curator.framework.api.BackgroundCallback;
import org.apache.curator.framework.api.CuratorEventType;
import org.apache.curator.test.TestingServer;

import org.apache.zookeeper.KeeperException;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

/**
 * Naming convention to use: MethodName_StateUnderTest_ExpectedBehavior
 */
public class PALDirectoryTest {

	protected final static Logger logger = LoggerFactory.getLogger("tests");

	private static final int TEST_PORT = 2182;
	private static final String CONNECTION_STR = String.format("localhost:%d", TEST_PORT);

	private static final Set<UUID> createdPeers = new HashSet<>();
	private static final Set<String> createdLogs = new HashSet<>();

	private PALDirectory palDirectory;
	private TestingServer testingServer;

	@Before
	public void setup() throws Exception {
		testingServer = new TestingServer(TEST_PORT, true);
		palDirectory = new PALDirectory(CONNECTION_STR);
	}

	@After
	public void cleanup() throws Exception {
		for (UUID peer : createdPeers) {
			palDirectory.unregisterPeer(peer);
			logger.info("Cleaned up created peer: {}", peer);
		}
		for (String log : createdLogs) {
			palDirectory.unregisterLog(log);
			logger.info("Cleaned up created log: {}", log);
		}
		palDirectory.close();
		testingServer.close();
	}

	@Test
	public void peerExists_nonExistingPeer_false() throws Exception {
		UUID peerUuid = UUID.randomUUID();
		assertThat(palDirectory.peerExists(peerUuid), is(false));
	}

	@Test
	public void registerPeer_newPeer_peerCreated() throws Exception {

		UUID peerUuid = UUID.randomUUID();
		Properties peerProps = new Properties();
		peerProps.put("listenAddress", "tcp://127.0.0.1:5671");

		// pre-assertions
		assertThat(palDirectory.peerExists(peerUuid), is(false));

		// register
		palDirectory.registerPeer(peerUuid, peerProps);
		createdPeers.add(peerUuid);

		// verify
		assertThat(palDirectory.peerExists(peerUuid), is(true));
	}

	@Test
	public void getPeerInfo_noSuchPeer_exception() throws Exception {

		UUID peerUuid = UUID.randomUUID();

		// pre-assertions
		assertThat(palDirectory.peerExists(peerUuid), is(false));

		try {
			palDirectory.getPeerInfo(peerUuid);
			fail();
		} catch (NoPeerInfoNodeException e) {
			// OK
		}
	}

	@Test
	public void getPeerInfo_peerExists_peerInfo() throws Exception {
		UUID peerUuid = UUID.randomUUID();
		Properties peerProps = new Properties();
		peerProps.put("listenAddress", "tcp://127.0.0.1:5671");
		palDirectory.registerPeer(peerUuid, peerProps);
		createdPeers.add(peerUuid);

		// pre-assertions
		assertThat(palDirectory.peerExists(peerUuid), is(true));

		PeerInfo peerInfo = palDirectory.getPeerInfo(peerUuid);

		// verify
		assertThat(peerInfo.getUuid(), is(peerUuid));
		assertThat(peerInfo.getListenAddress(), is(peerProps.get("listenAddress")));
	}

	@Test
	public void unregisterPeer_existingPeer_peerDeleted() throws Exception {

		Properties peerProps = new Properties();
		peerProps.put("listenAddress", "tcp://127.0.0.1:5671");

		// create
		UUID peerUuid = UUID.randomUUID();
		palDirectory.registerPeer(peerUuid, peerProps);
		createdPeers.add(peerUuid);

		// pre-assertions
		assertThat(palDirectory.peerExists(peerUuid), is(true));

		// unregister
		palDirectory.unregisterPeer(peerUuid);

		// verify
		assertThat(palDirectory.peerExists(peerUuid), is(false));
	}

	@Test
	public void getAllPeers_noPeers_emptySet() throws Exception {
		Set<PeerInfo> allPeers = palDirectory.getAllPeers();
		// verify
		assertThat(allPeers.isEmpty(), is(true));
	}

	@Test
	public void getAllPeers_somePeers_nonEmptySet() throws Exception {
		int peersToCreate = 2;
		for (int i = 0; i < peersToCreate; i++) {
			// create a peer
			UUID peerUuid = UUID.randomUUID();
			Properties peerProps = new Properties();
			peerProps.put("listenAddress", "tcp://127.0.0.1:5671");
			palDirectory.registerPeer(peerUuid, peerProps);
			createdPeers.add(peerUuid);
		}

		// verify
		assertThat(palDirectory.getAllPeers().size(), is(peersToCreate));
	}

	@Test
	public void logExists_nonExistingLog_false() throws Exception {
		String logName = "test.blah";
		// verify
		assertThat(palDirectory.logExists(logName), is(false));
	}

	@Test
	public void newLog_newLogPrefix_logCreated() throws Exception {

		String logNamePrefix = "test.topic";

		LogInfo newLogInfo = palDirectory.newLog(logNamePrefix);
		String createdLogName = newLogInfo.getName();
		createdLogs.add(createdLogName);

		// verify
		assertThat(newLogInfo.getName(), startsWith(logNamePrefix));
		assertThat(newLogInfo.getBootstrapServers(), notNullValue());
		assertThat(palDirectory.logExists(createdLogName), is(true));
		assertThat(newLogInfo.getUuid(), notNullValue());
	}

	@Test
	public void registerLog_logNotRegistered_logRegistered() throws Exception {

		String logName = "test.topic";

		// pre-assertions
		assertThat(palDirectory.logExists(logName), is(false));

		// register
		LogInfo newLogInfo = palDirectory.registerLog(logName);
		String createdLogName = newLogInfo.getName();
		createdLogs.add(createdLogName);

		// verify
		assertThat(logName, is(createdLogName));
		assertThat(newLogInfo.getBootstrapServers(), notNullValue());
		assertThat(palDirectory.logExists(createdLogName), is(true));
		assertThat(newLogInfo.getUuid(), notNullValue());
	}

	@Test
	public void getLogInfo_noSuchLog_exception() throws Exception {
		String logName = "test.strange_topic";

		// pre-assertions
		assertThat(palDirectory.logExists(logName), is(false));

		try {
			palDirectory.getLogInfo(logName);
			fail();
		} catch (NoLogInfoNodeException e) {
			// OK
		}
	}

	@Test
	public void getLogInfo_logExists_logInfo() throws Exception {
		String logName = "test.topic";

		// register logInfo
		LogInfo newLogInfo = palDirectory.registerLog(logName);
		String createdLogName = newLogInfo.getName();
		createdLogs.add(createdLogName);

		// pre-assertions
		assertThat(palDirectory.logExists(logName), is(true));

		LogInfo returnedLogInfo = palDirectory.getLogInfo(logName);

		// verify
		assertThat(returnedLogInfo, is(newLogInfo));
	}

	@Test
	public void getAllLogs_someLogsExist_all() throws Exception {
		String logNamePrefix = "test.topic";

		// create N logs
		int logsToCreate = 10;
		for (int i = 0; i < logsToCreate; i++) {
			LogInfo newLogInfo = palDirectory.newLog(logNamePrefix);
			createdLogs.add(newLogInfo.getName());
		}

		// verify
		assertThat(palDirectory.getAllLogs().size(), is(logsToCreate));
	}

	@Test
	public void getLogCount_noMatchingLogsExist_zero() throws Exception {
		String logNamePrefix = "strange.topic";
		assertThat(palDirectory.getLogCount(logNamePrefix), is(0));
	}

	@Test
	public void getLogCount_matchingLogsExist_rightCount() throws Exception {
		String logNamePrefix = "test.topic";
		// create  a few logs
		int logsToCreate = 10;
		for (int i = 0; i < logsToCreate; i++) {
			LogInfo newLogInfo = palDirectory.newLog(logNamePrefix);
			createdLogs.add(newLogInfo.getName());
		}
		assertThat(palDirectory.getLogCount(logNamePrefix), is(logsToCreate));
	}

	@Test
	public void getLastLog_someLogsMatch_last() throws Exception {

		String logNamePrefix = "test.topic";

		// pre-assertions
		assertThat(palDirectory.getAllLogs().size(), is(0));

		// create  a few logs
		int logsToCreate = 10;
		String lastCreated = null;
		for (int i = 0; i < logsToCreate; i++) {
			LogInfo newLogInfo = palDirectory.newLog(logNamePrefix);
			lastCreated = newLogInfo.getName();
			createdLogs.add(lastCreated);
		}

		assertThat(palDirectory.getLastLog(logNamePrefix).getName(), is(lastCreated));
	}

	@Test
	public void deleteAllLogs_matchingLogs_allMatchingDeleted() throws Exception {
		String logNamePrefix = "test.topic";

		// create  a few with the prefix
		int logsToCreate = 10;
		for (int i = 0; i < logsToCreate; i++) {
			LogInfo newLogInfo = palDirectory.newLog(logNamePrefix);
			createdLogs.add(newLogInfo.getName());
		}

		// create a few with another prefix
		for (int i = 0; i < 3; i++) {
			LogInfo newLogInfo = palDirectory.newLog("some.other.prefix");
			createdLogs.add(newLogInfo.getName());
		}

		// pre-assertions
		assertThat(palDirectory.getLogCount(logNamePrefix), is(logsToCreate));

		// unregister with prefix
		palDirectory.unregisterLogs(logNamePrefix);

		// verify
		assertThat(palDirectory.getLogCount(logNamePrefix), is(0));
	}

	@Test
	public void deleteLog_existingLog_logDeleted() throws Exception {
		String logNamePrefix = "test.topic";

		LogInfo newLogInfo = palDirectory.newLog(logNamePrefix);
		String createdLogName = newLogInfo.getName();
		// pre-assertions
		assertThat(palDirectory.logExists(createdLogName), is(true));

		palDirectory.unregisterLog(createdLogName);

		// verify
		assertThat(palDirectory.logExists(createdLogName), is(false));
	}

	@Test
	public void addLogRequestAsync_newLogRequest_reqNodeCreated() throws Exception {
		String logNamePrefix = "test.topic";
		LogInfo newLogInfo = palDirectory.newLog(logNamePrefix);
		String createdLogName = newLogInfo.getName();
		createdLogs.add(createdLogName);

		LogRequest someRequest = new LogRequest(UUID.randomUUID());
		CountDownLatch latch = new CountDownLatch(1);

		BackgroundCallback callback = (curatorFramework, curatorEvent) -> {
			if (curatorEvent.getType().equals(CuratorEventType.CREATE)) {
				if (KeeperException.Code.get(curatorEvent.getResultCode()) == KeeperException.Code.OK) {
					latch.countDown();
				}
			}
		};

		// pre-assertions
		assertThat(palDirectory.getLogRequestsCount(createdLogName), is(0));

		palDirectory.addLogRequestAsync(createdLogName, someRequest, callback);
		if (!latch.await(3, TimeUnit.SECONDS)) {
			fail("Timeout awaiting latch downcount - node not created?");
		}

		// verify
		assertThat(palDirectory.getLogRequestsCount(createdLogName), is(1));
	}

	@Test
	public void addLogRequestAsync_noLog_noLogInfoNodeException() throws Exception {
		String logName = "someRandomLogName";
		UUID someRequestUuid = UUID.randomUUID();

		// pre-assertions
		assertThat(palDirectory.logExists(logName), is(false));

		try {
			palDirectory.addLogRequestAsync(logName, new LogRequest(someRequestUuid),
				(curatorFramework, curatorEvent) -> fail("we shouldn't be here"));
			fail();
		} catch (NoLogInfoNodeException nne) {
			// OK
		}

		// verify -> count method should fail with same Exception
		try {
			palDirectory.getLogRequestsCount(logName);
		} catch (NoLogInfoNodeException nne) {
			// OK
		}
	}

	@Test
	public void addLogReplyAsync_noLog_noLogInfoNodeException() throws Exception {
		String logName = "someRandomLogName";
		UUID someRequestUuid = UUID.randomUUID();
		UUID somePeerUuid = UUID.randomUUID();
		long someOffset = 32384893;
		LogReply logReply = new LogReply(UUID.randomUUID(), somePeerUuid, someRequestUuid, someOffset);

		// pre-assertions
		assertThat(palDirectory.logExists(logName), is(false));

		try {
			palDirectory.addLogReplyAsync(logName, logReply,
				(curatorFramework, curatorEvent) -> fail("we shouldn't be here"));
			fail();
		} catch (NoLogInfoNodeException nne) {
			// OK
		}
	}

	@Test
	public void addLogReplyAsync_noReq_noLogRequestNodeException() throws Exception {
		String logNamePrefix = "test.topic";
		long someOffset = 32384893;

		// create log
		LogInfo newLogInfo = palDirectory.newLog(logNamePrefix);
		String createdLogName = newLogInfo.getName();
		createdLogs.add(createdLogName);

		// we DON'T create any req node
		UUID someRequestUuid = UUID.randomUUID();
		UUID somePeerUuid = UUID.randomUUID();

		// pre-assertions
		assertThat(palDirectory.logExists(createdLogName), is(true));
		assertThat(palDirectory.getLogRequestsCount(createdLogName), is(0));

		LogReply logReply = new LogReply(UUID.randomUUID(), somePeerUuid, someRequestUuid, someOffset);
		// now try to create rep node
		try {
			palDirectory.addLogReplyAsync(createdLogName, logReply,
				(curatorFramework, curatorEvent) -> fail("we shouldn't be here"));
			fail();
		} catch (NoLogRequestNodeException nne) {
			// OK
		}
	}

	@Test
	public void getReplies_noLog_noLogInfoNodeException() throws Exception {
		String logName = "someRandomLogName";
		LogRequest someRequest = new LogRequest(UUID.randomUUID());

		// get replies to req
		try {
			palDirectory.getRepliesTo(logName, someRequest);
			fail();
		} catch (NoLogInfoNodeException nne) {
			// OK
		}
	}

	@Test
	public void getReplies_noReq_noLogRequestNodeException() throws Exception {
		String logNamePrefix = "someRandomLogName";

		// create log
		LogInfo newLogInfo = palDirectory.newLog(logNamePrefix);
		String createdLogName = newLogInfo.getName();
		createdLogs.add(createdLogName);

		// we DON'T create req node
		LogRequest someRequest = new LogRequest(UUID.randomUUID());

		// pre-assertions
		assertThat(palDirectory.logExists(createdLogName), is(true));
		assertThat(palDirectory.getLogRequestsCount(createdLogName), is(0));

		// get replies to req
		try {
			palDirectory.getRepliesTo(createdLogName, someRequest);
			fail();
		} catch (NoLogRequestNodeException nne) {
			// OK
		}
	}

	@Test
	public void getReplies_noRepsExist_emptySet() throws Exception {
		String logNamePrefix = "test.topic";

		// create log
		LogInfo newLogInfo = palDirectory.newLog(logNamePrefix);
		String createdLogName = newLogInfo.getName();
		createdLogs.add(createdLogName);

		// create req node
		LogRequest someRequest = new LogRequest(UUID.randomUUID());
		CountDownLatch countDownLatch = new CountDownLatch(1);
		palDirectory.addLogRequestAsync(createdLogName, someRequest,
			(curatorFramework, curatorEvent) -> countDownLatch.countDown()
		);
		// wait
		if (!countDownLatch.await(3, TimeUnit.SECONDS)) {
			fail("Timeout awaiting latch downcount - node not created?");
		}

		// pre-assertions
		assertThat(palDirectory.logExists(createdLogName), is(true));
		assertThat(palDirectory.getLogRequestsCount(createdLogName), is(1));

		// get replies to req
		Set<LogReply> replies = palDirectory.getRepliesTo(createdLogName, someRequest);

		assertThat(replies.isEmpty(), is(true));
	}

	@Test
	public void getReplies_replyExists_nonEmptySet() throws Exception {
		String logNamePrefix = "test.topic";
		long someOffset = 32384893;

		// create log
		LogInfo newLogInfo = palDirectory.newLog(logNamePrefix);
		String createdLogName = newLogInfo.getName();
		createdLogs.add(createdLogName);

		// create req node
		LogRequest someRequest = new LogRequest(UUID.randomUUID());
		CountDownLatch countDownLatch = new CountDownLatch(1);
		palDirectory.addLogRequestAsync(createdLogName, someRequest,
			(curatorFramework, curatorEvent) -> countDownLatch.countDown()
		);
		// wait
		if (!countDownLatch.await(3, TimeUnit.SECONDS)) {
			fail("Timeout awaiting latch downcount - node not created?");
		}

		// create rep node
		CountDownLatch latch = new CountDownLatch(1);
		UUID replyUuid = UUID.randomUUID();
		LogReply logReply = new LogReply(replyUuid, UUID.randomUUID(), someRequest.getUuid(), someOffset);
		palDirectory.addLogReplyAsync(createdLogName, logReply,
			(curatorFramework, curatorEvent) -> latch.countDown()
		);
		// wait
		if (!latch.await(3, TimeUnit.SECONDS)) {
			fail("Timeout awaiting latch downcount - node not created?");
		}

		// pre-assertions
		assertThat(palDirectory.logExists(createdLogName), is(true));
		assertThat(palDirectory.getLogRequestsCount(createdLogName), is(1));

		// get replies to req
		Set<LogReply> replies = palDirectory.getRepliesTo(createdLogName, someRequest);

		// verify
		assertThat(replies.isEmpty(), is(false));
		assertThat(replies.iterator().next().getUuid(), is(replyUuid));
	}

	@Test
	public void deleteRequest_reqAndRepliesExist_deleted() throws Exception {
		String logNamePrefix = "test.topic";
		long someOffset = 32384893;

		// create log
		LogInfo newLogInfo = palDirectory.newLog(logNamePrefix);
		String createdLogName = newLogInfo.getName();
		createdLogs.add(createdLogName);

		// create req node
		LogRequest someRequest = new LogRequest(UUID.randomUUID());
		CountDownLatch countDownLatch = new CountDownLatch(1);
		palDirectory.addLogRequestAsync(createdLogName, someRequest,
			(curatorFramework, curatorEvent) -> countDownLatch.countDown()
		);
		// wait
		if (!countDownLatch.await(3, TimeUnit.SECONDS)) {
			fail("Timeout awaiting latch downcount - node not created?");
		}

		// create 3 rep nodes
		int repNodesToCreate = 3;
		Set<LogReply> logRepliesCreated = new TreeSet<>();
		for (int i = 0; i < repNodesToCreate; i++) {
			CountDownLatch latch = new CountDownLatch(1);
			LogReply logReply = new LogReply(UUID.randomUUID(), UUID.randomUUID(), someRequest.getUuid(), someOffset + i);
			logRepliesCreated.add(logReply);
			palDirectory.addLogReplyAsync(createdLogName, logReply,
				(curatorFramework, curatorEvent) -> latch.countDown()
			);
			// wait
			if (!latch.await(3, TimeUnit.SECONDS)) {
				fail("Timeout awaiting latch downcount - node not created?");
			}
		}

		// pre-assertions
		assertThat(palDirectory.logExists(createdLogName), is(true));
		assertThat(palDirectory.getLogRequestsCount(createdLogName), is(1));
		assertThat(palDirectory.getRepliesTo(createdLogName, someRequest).size(), is(repNodesToCreate));

		palDirectory.deleteLogRequestAsync(createdLogName, someRequest);

		// delete has no callback - give it some time
		Thread.sleep(500);

		// verify
		assertThat(palDirectory.logExists(createdLogName), is(true));
		assertThat(palDirectory.getLogRequestsCount(createdLogName), is(0));
	}

	@Test
	public void getReplies_multipleRepliesExist_allRepsSortedByOffset() throws Exception {

		String logNamePrefix = "test.topic";
		long smallOffset = 39483;
		long largeOffset = 32384893;

		// create log
		LogInfo newLogInfo = palDirectory.newLog(logNamePrefix);
		String createdLogName = newLogInfo.getName();
		createdLogs.add(createdLogName);

		// create req node
		LogRequest someRequest = new LogRequest(UUID.randomUUID());
		UUID somePeerUuid = UUID.randomUUID();
		CountDownLatch latch1 = new CountDownLatch(1);
		palDirectory.addLogRequestAsync(createdLogName, someRequest,
			(curatorFramework, curatorEvent) -> latch1.countDown()
		);
		// wait
		if (!latch1.await(3, TimeUnit.SECONDS)) {
			fail("Timeout awaiting latch downcount - node not created?");
		}

		// pre-assertions
		assertThat(palDirectory.logExists(createdLogName), is(true));
		assertThat(palDirectory.getLogRequestsCount(createdLogName), is(1));

		// create rep node #1
		CountDownLatch latch2 = new CountDownLatch(1);
		LogReply logReply = new LogReply(UUID.randomUUID(), somePeerUuid, someRequest.getUuid(), largeOffset);
		palDirectory.addLogReplyAsync(createdLogName, logReply,
			(curatorFramework, curatorEvent) -> latch2.countDown()
		);
		// wait
		if (!latch2.await(3, TimeUnit.SECONDS)) {
			fail("Timeout awaiting latch downcount - node not created?");
		}
		assertThat(palDirectory.getRepliesTo(createdLogName, someRequest).size(), is(1));

		// create rep node #2 with lower offset than first reply
		CountDownLatch latch3 = new CountDownLatch(1);
		logReply = new LogReply(UUID.randomUUID(), somePeerUuid, someRequest.getUuid(), smallOffset);
		palDirectory.addLogReplyAsync(createdLogName, logReply,
			(curatorFramework, curatorEvent) -> latch3.countDown()
		);
		// wait
		if (!latch3.await(3, TimeUnit.SECONDS)) {
			fail("Timeout awaiting latch downcount - node not created?");
		}

		// get replies to req
		Set<LogReply> replies = palDirectory.getRepliesTo(createdLogName, someRequest);

		// verify
		assertThat(replies.size(), is(2));
		// assert that replies are sorted by increasing offset
		long lastOffset = 0;
		for (LogReply reply : replies) {
			assertThat(reply.getOffset(), greaterThan(lastOffset));
			lastOffset = reply.getOffset();
		}
	}
}