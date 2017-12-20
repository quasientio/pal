package com.ittera.cometa.cxn;

import com.ittera.cometa.LogInfo;
import com.ittera.cometa.LogReply;
import com.ittera.cometa.PeerInfo;

import org.apache.commons.lang3.StringUtils;
import org.apache.zookeeper.AsyncCallback;
import org.apache.zookeeper.KeeperException.Code;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.After;
import org.junit.Test;

import static org.junit.Assert.*;

import java.util.Properties;
import java.util.UUID;
import java.util.Set;
import java.util.HashSet;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Naming convention to use: MethodName_StateUnderTest_ExpectedBehavior
 */
public class ZkClientTest {

	protected final static Logger logger = LoggerFactory.getLogger("tests");

	private static final String TESTS_ZK_ROOT_PATH = "/cometa_tests";

	private static final String zookeeperUrl = "localhost:2181";
	private static final Set<UUID> createdPeers = new HashSet<>();
	private static final Set<String> createdLogs = new HashSet<>();

	private ZkClient zkCli;

	@Before
	public void setup() throws Exception {
		zkCli = ZkClient.getConnectedClient(zookeeperUrl, TESTS_ZK_ROOT_PATH);
	}

	@After
	public void cleanup() throws Exception {
		zkCli.close();
	}

	@Test
	public void registerPeer_newPeer_peerCreated() throws Exception {

		UUID peerUuid = UUID.randomUUID();
		Properties peerProps = new Properties();
		peerProps.put("listenAddress", "tcp://127.0.0.1:5671");

		zkCli.registerPeer(peerUuid, peerProps);
		assertTrue(zkCli.peerExists(peerUuid));
		createdPeers.add(peerUuid);
	}


	@Test
	public void peerExists_existingPeer_true() throws Exception {

		UUID peerUuid = UUID.randomUUID();
		Properties peerProps = new Properties();
		peerProps.put("listenAddress", "tcp://127.0.0.1:5671");

		zkCli.registerPeer(peerUuid, peerProps);

		assertTrue(zkCli.peerExists(peerUuid));
		createdPeers.add(peerUuid);
	}

	@Test
	public void peerExists_nonExistingPeer_false() throws Exception {

		UUID peerUuid = UUID.randomUUID();

		assertFalse(zkCli.peerExists(peerUuid));
	}

	@Test
	public void unregisterPeer_existingPeer_peerDeleted() throws Exception {

		Properties peerProps = new Properties();
		peerProps.put("listenAddress", "tcp://127.0.0.1:5671");

		// create
		UUID peerUuid = UUID.randomUUID();
		zkCli.registerPeer(peerUuid, peerProps);

		assertTrue(zkCli.peerExists(peerUuid));
		createdPeers.add(peerUuid);

		// delete
		zkCli.unregisterPeer(peerUuid);
		assertFalse(zkCli.peerExists(peerUuid));
		createdPeers.remove(peerUuid);
	}

	@Test
	public void addLog_newLog_logCreated() throws Exception {

		String logName = "test.topic";

		LogInfo newLogInfo = zkCli.addLog(logName, "localhost:9092");
		String createdLogName = newLogInfo.getName();

		assertTrue(zkCli.logExists(createdLogName));
		assertNotNull(newLogInfo.getUuid());
		createdLogs.add(createdLogName);
	}

	@Test
	public void addLogRequest_newLogRequest_reqNodeCreated() throws Exception {

		String logName = "test.topic";

		LogInfo newLogInfo = zkCli.addLog(logName, "localhost:9092");
		String createdLogName = newLogInfo.getName();
		createdLogs.add(createdLogName);

		String someRequestUuid = UUID.randomUUID().toString();

		String reqNodeCreated = zkCli.addLogRequest(createdLogName, someRequestUuid);
		assertEquals(someRequestUuid, StringUtils.substringAfterLast(reqNodeCreated, "/"));
	}

	@Test
	public void addLogRequestAsync_newLogRequest_reqNodeCreated() throws Exception {

		String logName = "test.topic";

		LogInfo newLogInfo = zkCli.addLog(logName, "localhost:9092");
		String createdLogName = newLogInfo.getName();
		createdLogs.add(createdLogName);

		String someRequestUuid = UUID.randomUUID().toString();

		AsyncCallback.StringCallback cb = new AsyncCallback.StringCallback() {
			@Override
			public void processResult(int rc, String path, Object ctx, String name) {
				switch (Code.get(rc)) {
					case OK:
						((CountDownLatch) ctx).countDown();
						break;
					default:
						return;
				}
			}
		};

		CountDownLatch latch = new CountDownLatch(1);
		zkCli.addLogRequest(createdLogName, someRequestUuid, cb, latch);
		if (!latch.await(5, TimeUnit.SECONDS)) {
			fail("Timeout awaiting latch downcount - node not created?");
		}
	}

	@Test
	public void addLogRequest_noLog_illegalArgument() throws Exception {

		String logName = "someRandomLogName";
		String someRequestUuid = UUID.randomUUID().toString();

		try {
			zkCli.addLogRequest(logName, someRequestUuid);
			fail();
		} catch (IllegalArgumentException iae) {
			// OK
		}
	}

	@Test
	public void addLogReply_noLog_illegalArgument() throws Exception {

		String logName = "someRandomLogName";
		String someRequestUuid = UUID.randomUUID().toString();
		long someOffset = 32384893;

		try {
			zkCli.addLogReply(logName, new LogReply(UUID.randomUUID().toString(), null, someRequestUuid,
				someOffset));
			fail();
		} catch (IllegalArgumentException iae) {
			// OK
		}
	}

	@Test
	public void addLogReply_noReq_illegalArgument() throws Exception {

		String logName = "test.topic";
		long someOffset = 32384893;

		// create log
		LogInfo newLogInfo = zkCli.addLog(logName, "localhost:9092");
		String createdLogName = newLogInfo.getName();
		createdLogs.add(createdLogName);

		// we DON'T create req node
		String someRequestUuid = UUID.randomUUID().toString();

		// create rep node
		try {
			zkCli.addLogReply(createdLogName, new LogReply(UUID.randomUUID().toString(), null, someRequestUuid,
				someOffset));
			fail();
		} catch (IllegalArgumentException iae) {
			// OK
		}
	}

	@Test
	public void getReplies_noLog_illegalArgument() throws Exception {

		String logName = "someRandomLogName";
		String someRequestUuid = UUID.randomUUID().toString();

		// get replies to req
		try {
			zkCli.getRepliesTo(logName, someRequestUuid);
			fail();
		} catch (IllegalArgumentException iae) {
			// OK
		}
	}

	@Test
	public void getReplies_noReq_illegalArgument() throws Exception {
		String logName = "someRandomLogName";

		// create log
		LogInfo newLogInfo = zkCli.addLog(logName, "localhost:9092");
		String createdLogName = newLogInfo.getName();
		createdLogs.add(createdLogName);

		// we DON'T create req node
		String someRequestUuid = UUID.randomUUID().toString();

		// get replies to req
		try {
			zkCli.getRepliesTo(createdLogName, someRequestUuid);
			fail();
		} catch (IllegalArgumentException iae) {
			// OK
		}
	}

	@Test
	public void getReplies_noRepsExist_emptySet() throws Exception {

		String logName = "test.topic";

		// create log
		LogInfo newLogInfo = zkCli.addLog(logName, "localhost:9092");
		String createdLogName = newLogInfo.getName();
		createdLogs.add(createdLogName);

		// create req node
		String someRequestUuid = UUID.randomUUID().toString();
		String reqNodeCreated = zkCli.addLogRequest(createdLogName, someRequestUuid);

		// get replies to req
		Set<LogReply> replies = zkCli.getRepliesTo(createdLogName, someRequestUuid);
		assertTrue(replies.isEmpty());
	}

	@Test
	public void getReplies_replyExists_nonEmptySet() throws Exception {

		String logName = "test.topic";
		long someOffset = 32384893;

		// create log
		LogInfo newLogInfo = zkCli.addLog(logName, "localhost:9092");
		String createdLogName = newLogInfo.getName();
		createdLogs.add(createdLogName);

		// create req node
		String someRequestUuid = UUID.randomUUID().toString();
		String reqNodeCreated = zkCli.addLogRequest(createdLogName, someRequestUuid);

		// create rep node
		String someReplyUuid = UUID.randomUUID().toString();
		zkCli.addLogReply(createdLogName, new LogReply(someReplyUuid, null, someRequestUuid, someOffset));

		// get replies to req
		Set<LogReply> replies = zkCli.getRepliesTo(createdLogName, someRequestUuid);
		assertFalse(replies.isEmpty());
	}

	@Test
	public void getReplies_multipleRepliesExist_allRepsSortedByOffset() throws Exception {

		String logName = "test.topic";
		long smallOffset = 39483;
		long largeOffset = 32384893;

		// create log
		LogInfo newLogInfo = zkCli.addLog(logName, "localhost:9092");
		String createdLogName = newLogInfo.getName();
		createdLogs.add(createdLogName);

		// create req node
		String someRequestUuid = UUID.randomUUID().toString();
		String reqNodeCreated = zkCli.addLogRequest(createdLogName, someRequestUuid);

		// create rep node #1
		String someReplyUuid = UUID.randomUUID().toString();
		zkCli.addLogReply(createdLogName, new LogReply(someReplyUuid, null, someRequestUuid, largeOffset));

		// create rep node #2 with lower offset then first reply
		someReplyUuid = UUID.randomUUID().toString();
		zkCli.addLogReply(createdLogName, new LogReply(someReplyUuid, null, someRequestUuid, smallOffset));

		// get replies to req
		Set<LogReply> replies = zkCli.getRepliesTo(createdLogName, someRequestUuid);
		assertFalse(replies.isEmpty());
		assertEquals(2, replies.size());
		long lastOffset = 0;

		// assert that replies are sorted by increasing offset
		for (LogReply reply : replies) {
			assertTrue(reply.getOffset() > lastOffset);
			lastOffset = reply.getOffset();
		}
	}

	@Test
	public void logExists_existingLog_true() throws Exception {

		String logName = "test.topic";

		LogInfo newLogInfo = zkCli.addLog(logName, "localhost:9092");
		String createdLogName = newLogInfo.getName();

		assertTrue(zkCli.logExists(createdLogName));
		createdLogs.add(createdLogName);
	}

	@Test
	public void getLastLog_someLogsMatch_last() throws Exception {

		String logNamePrefix = "test.topic";

		// make sure no logs around
		zkCli.deleteAllLogs(logNamePrefix);
		assertEquals(0, zkCli.getAllLogs().size());

		// create  a few
		String lastCreated = null;
		for (int i = 6; i > 0; i--) {
			LogInfo newLogInfo = zkCli.addLog(logNamePrefix, "localhost:9092");
			lastCreated = newLogInfo.getName();
			createdLogs.add(lastCreated);
		}

		assertEquals(lastCreated, zkCli.getLastLog(logNamePrefix).getName());
	}

	@Test
	public void getAllLogs_someLogsExist_all() throws Exception {

		String logNamePrefix = "test.topic";

		// make sure no logs around
		zkCli.deleteAllLogs(logNamePrefix);
		assertEquals(0, zkCli.getAllLogs().size());

		// create N nodes
		String lastCreated = null;
		int N = 30;
		for (int i = N; i > 0; i--) {
			LogInfo newLogInfo = zkCli.addLog(logNamePrefix, "localhost:9092");
			createdLogs.add(newLogInfo.getName());
		}

		assertEquals(N, zkCli.getAllLogs().size());
	}


	@Test
	public void logExists_nonExistingLog_false() throws Exception {
		String logName = "test.topic";

		assertFalse(zkCli.logExists(logName));
	}

	@Test
	public void deleteLog_existingLog_logDeleted() throws Exception {

		String logName = "test.topic";

		LogInfo newLogInfo = zkCli.addLog(logName, "localhost:9092");
		String createdLogName = newLogInfo.getName();

		assertTrue(zkCli.logExists(createdLogName));

		zkCli.deleteLogNamed(createdLogName);
		assertFalse(zkCli.logExists(createdLogName));
	}

	@Test
	public void deleteAllLogs_matchingLogs_allMatchingDeleted() throws Exception {

		String logNamePrefix = "test.topic";

		// create  a few
		String lastCreated = null;
		for (int i = 0; i > 10; i--) {
			LogInfo newLogInfo = zkCli.addLog(logNamePrefix, "localhost:9092");
			createdLogs.add(newLogInfo.getName());
		}

		assertNotEquals(0, zkCli.getLogCount(logNamePrefix));
		zkCli.deleteAllLogs(logNamePrefix);
		assertEquals(0, zkCli.getLogCount(logNamePrefix));
	}

	@Test
	public void getLogProperties_existingLog_logInfo() throws Exception {

		String logName = "test.topic";
		String bootstrapServers = "localhost:9092";

		LogInfo newLogInfo = zkCli.addLog(logName, bootstrapServers);
		createdLogs.add(newLogInfo.getName());

		// now compare
		assertEquals(bootstrapServers, newLogInfo.getBootstrapServers());
	}

	@Test
	public void getPeerProperties_existingPeer_peerProperties() throws Exception {

		Properties peerProps = new Properties();
		peerProps.put("listenAddress", "tcp://127.0.0.1:5671");

		// create
		UUID peerUuid = UUID.randomUUID();
		zkCli.registerPeer(peerUuid, peerProps);
		createdPeers.add(peerUuid);

		// now load and compare
		Properties propsLoaded = zkCli.getPeerProperties(peerUuid);
		assertEquals(peerProps, propsLoaded);

	}

	@Test
	public void isConnectionEstablished_notConnected_false() throws Exception {

		PeerLogDirectory detachedZkCli = ZkClient.getDisconnectedClient();
		assertFalse(detachedZkCli.isConnectionEstablished());
	}

	@Test
	public void isConnectionEstablished_connected_true() throws Exception {

		assertTrue(zkCli.isConnectionEstablished());
	}

	@Test
	public void getAllPeers_noPeers_emptySet() throws Exception {

		// first make sure we have no peers
		deleteCreatedPeers();

		Set<PeerInfo> allPeers = zkCli.getAllPeers();
		assertTrue(allPeers.isEmpty());
	}

	@Test
	public void getAllPeers_somePeers_nonEmptySet() throws Exception {

		// create a peer
		UUID peerUuid = UUID.randomUUID();
		Properties peerProps = new Properties();
		peerProps.put("listenAddress", "tcp://127.0.0.1:5671");

		zkCli.registerPeer(peerUuid, peerProps);
		createdPeers.add(peerUuid);

		// create a second peer
		peerUuid = UUID.randomUUID();
		peerProps = new Properties();
		peerProps.put("listenAddress", "tcp://127.0.0.1:5671");

		zkCli.registerPeer(peerUuid, peerProps);
		createdPeers.add(peerUuid);

		// now check
		Set<PeerInfo> allPeers = zkCli.getAllPeers();
		assertEquals(2, allPeers.size());

	}

	private static void deleteCreatedPeers() throws Exception {
		PeerLogDirectory zkCli = ZkClient.getConnectedClient(zookeeperUrl, TESTS_ZK_ROOT_PATH);

		for (UUID peer : createdPeers) {
			zkCli.unregisterPeer(peer);
			logger.info("Cleaned up left over peer: {}", peer);
		}

		zkCli.close();
	}

	private static void deleteCreatedLogs() throws Exception {
		PeerLogDirectory zkCli = ZkClient.getConnectedClient(zookeeperUrl, TESTS_ZK_ROOT_PATH);

		for (String log : createdLogs) {
			zkCli.deleteLogNamed(log);
			logger.info("Cleaned up left over log: {}", log);
		}

		zkCli.close();
	}

	private static void deleteTestRootPaths() throws Exception {
		PeerLogDirectory zkCli = ZkClient.getConnectedClient(zookeeperUrl, TESTS_ZK_ROOT_PATH);

		zkCli.deleteRootPaths();
	}

	@AfterClass
	public static void deleteCreatedPeersAndLogs() throws Exception {

		deleteCreatedPeers();
		deleteCreatedLogs();
		deleteTestRootPaths();
	}
}
