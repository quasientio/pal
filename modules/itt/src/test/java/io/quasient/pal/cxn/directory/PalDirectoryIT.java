/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.cxn.directory;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import io.quasient.pal.AbstractIntegrationTest;
import io.quasient.pal.common.directory.events.InterceptEvent;
import io.quasient.pal.common.directory.nodes.InterceptRequest;
import io.quasient.pal.common.directory.nodes.LogInfo;
import io.quasient.pal.common.directory.nodes.PeerInfo;
import io.quasient.pal.common.lang.intercept.InterceptType;
import io.quasient.pal.common.lang.intercept.InterceptableMethodCall;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// Naming convention to use: MethodName_StateUnderTest_ExpectedBehavior
public class PalDirectoryIT extends AbstractIntegrationTest {

  protected static final Logger logger = LoggerFactory.getLogger("tests");
  private static final Set<UUID> createdPeers = new HashSet<>();
  private static final Set<String> createdLogs = new HashSet<>();
  private static final Map<UUID, List<UUID>> createdInterceptRequests = new HashMap<>();
  private PalDirectory palDirectory;

  @Before
  public void setup() throws Exception {
    palDirectory = new PalDirectory(getPalDirectoryUrl());

    // Fail-fast: Ensure clean state before running tests
    // All test peers and logs should be cleaned up after each test
    Set<PeerInfo> existingPeers = palDirectory.listPeers();
    Set<LogInfo> existingLogs = palDirectory.listAllLogs();

    if (!existingPeers.isEmpty()) {
      logger.error(
          "Found {} existing peers in directory before test. Peers must be cleaned up after each test!",
          existingPeers.size());
      for (PeerInfo peer : existingPeers) {
        logger.error(
            "  - Peer: {} (UUID: {}, Name: {})", peer.getUuid(), peer.getUuid(), peer.getName());
      }
      throw new IllegalStateException(
          "Directory not clean: Found "
              + existingPeers.size()
              + " existing peers. "
              + "All tests must clean up their peers in @After tearDown()");
    }

    if (!existingLogs.isEmpty()) {
      logger.error(
          "Found {} existing logs in directory before test. Logs must be cleaned up after each test!",
          existingLogs.size());
      for (LogInfo log : existingLogs) {
        logger.error("  - Log: {} (UUID: {})", log.getName(), log.getUuid());
      }
      throw new IllegalStateException(
          "Directory not clean: Found "
              + existingLogs.size()
              + " existing logs. "
              + "All tests must clean up their logs in @After tearDown()");
    }
  }

  @After
  public void cleanup() {
    for (UUID peer : createdPeers) {
      try {
        palDirectory.deletePeer(peer);
        logger.info("Cleaned up created peer: {}", peer);
      } catch (Exception e) {
        logger.warn("Failed to delete peer {} during cleanup: {}", peer, e.getMessage());
      }
    }
    for (String log : createdLogs) {
      try {
        palDirectory.deleteLog(log);
        logger.info("Cleaned up created log: {}", log);
      } catch (Exception e) {
        logger.warn("Failed to delete log {} during cleanup: {}", log, e.getMessage());
      }
    }
    for (Map.Entry<UUID, List<UUID>> entry : createdInterceptRequests.entrySet()) {
      UUID peerUuid = entry.getKey();
      List<UUID> peerIntercepts = entry.getValue();
      for (UUID interceptReq : peerIntercepts) {
        try {
          palDirectory.deleteIntercept(peerUuid, interceptReq);
          logger.info("Cleaned up created intercept request: {}", interceptReq);
        } catch (Exception e) {
          logger.warn(
              "Failed to delete intercept {} for peer {} during cleanup: {}",
              interceptReq,
              peerUuid,
              e.getMessage());
        }
      }
    }
    palDirectory.close();
  }

  private void addInterceptRequestToCreated(UUID peerUuid, UUID interceptReqUuid) {
    List<UUID> peerIntercepts =
        createdInterceptRequests.computeIfAbsent(peerUuid, k -> new ArrayList<>());
    peerIntercepts.add(interceptReqUuid);
  }

  @Test
  public void peerExists_nonExistingPeer_false() throws Exception {
    UUID peerUuid = UUID.randomUUID();
    assertFalse(palDirectory.peerExists(peerUuid));
  }

  @Test
  public void createPeer_newPeer_peerCreated() throws Exception {
    final PeerInfo peerInfo = new PeerInfo(UUID.randomUUID());
    peerInfo.setZmqRpcAddress("tcp://127.0.0.1:5671");

    // pre-assertions
    assertFalse(palDirectory.peerExists(peerInfo.getUuid()));

    // create
    palDirectory.createPeer(peerInfo);
    createdPeers.add(peerInfo.getUuid());

    // verify
    assertTrue(palDirectory.peerExists(peerInfo.getUuid()));
  }

  @Test
  public void createPeer_peerUuidAlreadyExists_noErrorNoUpdate() throws Exception {
    UUID peerOneId = UUID.randomUUID();
    final PeerInfo peerOneInfo = new PeerInfo(peerOneId);
    peerOneInfo.setZmqRpcAddress("tcp://127.0.0.1:5671");

    // pre-assertions
    assertFalse(palDirectory.peerExists(peerOneInfo.getUuid()));

    // create
    palDirectory.createPeer(peerOneInfo);
    createdPeers.add(peerOneInfo.getUuid());

    // verify
    assertTrue(palDirectory.peerExists(peerOneInfo.getUuid()));

    // try to create another peer with same uuid
    final PeerInfo peerTwoInfo = new PeerInfo(peerOneId);
    peerTwoInfo.setZmqRpcAddress("tcp://127.0.0.1:5765");
    palDirectory.createPeer(peerTwoInfo);

    // retrieve peer, ensure it's peerOne
    PeerInfo createdPeerInfo = palDirectory.getPeer(peerOneId);
    assertEquals(peerOneInfo, createdPeerInfo);
  }

  @Test
  public void getPeerInfo_noSuchPeer_null() throws Exception {
    UUID peerUuid = UUID.randomUUID();
    assertFalse(palDirectory.peerExists(peerUuid));
    assertNull(palDirectory.getPeer(peerUuid));
  }

  @Test
  public void getPeerInfo_peerExists_peer() throws Exception {
    final PeerInfo peerInfo = new PeerInfo(UUID.randomUUID(), "testing peer");
    peerInfo.setZmqRpcAddress("tcp://127.0.0.1:5671");
    peerInfo.setPubAddress("tcp://localhost:7777");
    peerInfo.setJmxAddress("localhost:9012");
    palDirectory.createPeer(peerInfo);
    createdPeers.add(peerInfo.getUuid());

    // pre-assertions
    assertTrue(palDirectory.peerExists(peerInfo.getUuid()));

    PeerInfo retrievedPeerInfo = palDirectory.getPeer(peerInfo.getUuid());

    // verify
    assertEquals(peerInfo.getUuid(), retrievedPeerInfo.getUuid());
    assertEquals(peerInfo.getName(), retrievedPeerInfo.getName());
    assertNotNull(retrievedPeerInfo.getZmqRpcAddress());
    assertNotNull(retrievedPeerInfo.getPubAddress());
    assertNotNull(retrievedPeerInfo.getJmxAddress());
    assertEquals(peerInfo.getZmqRpcAddress(), retrievedPeerInfo.getZmqRpcAddress());
    assertEquals(peerInfo.getPubAddress(), retrievedPeerInfo.getPubAddress());
    assertEquals(peerInfo.getJmxAddress(), retrievedPeerInfo.getJmxAddress());

    // verify CTime and MTime (which are in UTC) are within last second
    OffsetDateTime now = OffsetDateTime.now(ZoneId.of("UTC"));
    assertTrue(retrievedPeerInfo.getCTime().isAfter(now.minusSeconds(1)));
    assertTrue(retrievedPeerInfo.getCTime().isBefore(now));
    assertTrue(retrievedPeerInfo.getMTime().isAfter(now.minusSeconds(1)));
    assertTrue(retrievedPeerInfo.getMTime().isBefore(now));
  }

  @Test
  public void deletePeer_existingPeer_peerDeleted() throws Exception {
    // create
    final PeerInfo peerInfo = new PeerInfo(UUID.randomUUID());
    peerInfo.setZmqRpcAddress("tcp://127.0.0.1:5671");
    palDirectory.createPeer(peerInfo);
    createdPeers.add(peerInfo.getUuid());

    // pre-assertions
    assertTrue(palDirectory.peerExists(peerInfo.getUuid()));

    // delete
    palDirectory.deletePeer(peerInfo.getUuid());

    // verify
    assertFalse(palDirectory.peerExists(peerInfo.getUuid()));
  }

  @Test
  public void deleteAllPeers_existingPeers_PeersDeleted() throws Exception {

    // create
    int peersToCreate = 5;
    for (int i = 0; i < peersToCreate; i++) {
      // create a peer
      final PeerInfo peerInfo = new PeerInfo(UUID.randomUUID());
      peerInfo.setZmqRpcAddress("tcp://127.0.0.1:5671");
      palDirectory.createPeer(peerInfo);
      createdPeers.add(peerInfo.getUuid());
    }

    // verify
    assertEquals(peersToCreate, palDirectory.listPeers().size());

    // delete all
    palDirectory.purgePeersExcept(new HashSet<>());

    assertEquals(0, palDirectory.listPeers().size());
  }

  @Test
  public void listPeers_noPeers_emptySet() throws Exception {
    Set<PeerInfo> allPeers = palDirectory.listPeers();
    // verify
    assertTrue(allPeers.isEmpty());
  }

  @Test
  public void listPeers_somePeers_nonEmptySet() throws Exception {
    int peersToCreate = 2;
    for (int i = 0; i < peersToCreate; i++) {
      // create a peer
      final PeerInfo peerInfo = new PeerInfo(UUID.randomUUID());
      peerInfo.setZmqRpcAddress("tcp://127.0.0.1:5671");
      palDirectory.createPeer(peerInfo);
      createdPeers.add(peerInfo.getUuid());
    }

    // verify
    assertEquals(peersToCreate, palDirectory.listPeers().size());
  }

  @Test
  public void logExists_nonExistingLog_false() throws Exception {
    String logName = "test.blah";
    // verify
    assertFalse(palDirectory.logExists(logName));
  }

  @Test
  public void newLog_createLogPrefix_logWithAutoNameCreated() throws Exception {

    String logNamePrefix = "test.topic";

    LogInfo newLogInfo = palDirectory.createAutoLog(logNamePrefix, getKafkaServers());
    String createdLogName = newLogInfo.getName();
    createdLogs.add(createdLogName);

    // verify
    assertTrue(newLogInfo.getName().startsWith(logNamePrefix));
    assertNotNull(newLogInfo.getBootstrapServers());
    assertTrue(palDirectory.logExists(createdLogName));
    assertNotNull(newLogInfo.getUuid());
  }

  /** Test concurrent creation of logs with the same prefix must yield unique names */
  @Test
  public void createLog_WithAutoName_concurrentWriters_uniqueNames() throws Exception {
    final String prefix = "concurrent.topic"; // use a test-specific prefix
    final int writers = 32; // number of concurrent clients
    //  figure-out where the new sequence should start
    LogInfo lastExisting = palDirectory.getLatestLogWithPrefix(prefix);
    long startIndex =
        (lastExisting == null)
            ? 1
            : Long.parseLong(lastExisting.getName().substring(prefix.length())) + 1;
    logger.debug("startIndex = {}", startIndex);

    ExecutorService pool = Executors.newFixedThreadPool(writers);
    CountDownLatch readyLatch = new CountDownLatch(writers); // ensure all threads queued
    CountDownLatch startLatch = new CountDownLatch(1); // fire them simultaneously
    Set<String> names = ConcurrentHashMap.newKeySet();

    for (int i = 0; i < writers; i++) {
      @SuppressWarnings("unused")
      var unused =
          pool.submit(
              () -> {
                readyLatch.countDown(); // signal “I’m ready”
                startLatch.await(); // wait for the green light
                LogInfo info = palDirectory.createAutoLog(prefix, getKafkaServers());
                names.add(info.getName());
                createdLogs.add(info.getName()); // register for @After cleanup
                return null;
              });
    }

    // Wait until all tasks are queued, then start them together
    readyLatch.await();
    startLatch.countDown();

    pool.shutdown();
    assertTrue("Timed out waiting for tasks", pool.awaitTermination(30, TimeUnit.SECONDS));

    //  Assertions
    assertEquals("Each writer must create one *distinct* log", writers, names.size());

    int finalCount = palDirectory.countLogsWithPrefix(prefix);
    assertEquals(
        "Directory should have gained exactly <writers> logs",
        startIndex + writers - 1,
        finalCount);

    // contiguity check relative to *startIndex*
    List<Long> indexes =
        names.stream().map(n -> Long.parseLong(n.substring(prefix.length()))).sorted().toList();

    List<Long> sorted = indexes.stream().sorted().toList();
    assertEquals(writers, sorted.size()); // uniqueness
    assertEquals(sorted.get(0) + writers - 1, (long) sorted.get(writers - 1)); // no gaps
    for (int i = 1; i < sorted.size(); i++) {
      assertEquals(sorted.get(i - 1) + 1, (long) sorted.get(i)); // strictly +1 each step
    }
  }

  @Test
  public void createLog_logDoesNotExist_logCreated() throws Exception {

    String logName = "test.topic";

    // pre-assertions
    assertFalse(palDirectory.logExists(logName));

    // create
    LogInfo newLogInfo = new LogInfo(logName, getKafkaServers());
    palDirectory.createLog(newLogInfo);
    createdLogs.add(logName);

    // verify
    LogInfo retrievedLogInfo = palDirectory.getLogInfo(logName);
    assertTrue(palDirectory.logExists(logName));
    assertEquals(newLogInfo, retrievedLogInfo);
    assertNotNull(retrievedLogInfo.getUuid());
  }

  @Test
  public void registerPeerLogs_peerRegistered_peerLogsRegistered() throws Exception {

    // create new peer
    final PeerInfo peerInfo = new PeerInfo(UUID.randomUUID());
    peerInfo.setZmqRpcAddress("tcp://127.0.0.1:5671");
    palDirectory.createPeer(peerInfo);
    createdPeers.add(peerInfo.getUuid());

    // create and register source and writeAhead logs for the new peer
    LogInfo sourceLogInfo = palDirectory.createAutoLog("test_source_log", getKafkaServers());
    LogInfo writeAheadLogInfo = palDirectory.createAutoLog("test_wal", getKafkaServers());
    palDirectory.setSourceLog(peerInfo, sourceLogInfo, null);
    palDirectory.setWalLog(peerInfo, writeAheadLogInfo, null);
    createdLogs.add(sourceLogInfo.getName());
    createdLogs.add(writeAheadLogInfo.getName());

    assertEquals(1, palDirectory.listPeers().size());
    assertEquals(2, palDirectory.listAllLogs().size());

    // load them
    UUID peerSourceLog = palDirectory.getSourceLogId(peerInfo.getUuid());
    UUID peerWriteAheadLog = palDirectory.getWalId(peerInfo.getUuid());

    // verify
    assertEquals(sourceLogInfo.getUuid(), peerSourceLog);
    assertEquals(writeAheadLogInfo.getUuid(), peerWriteAheadLog);

    // try to delete the peer's logs
    try {
      palDirectory.deleteLog(sourceLogInfo.getName());
      fail("Should have raised IllegalArgumentException because the log is in use by a peer");
    } catch (IllegalArgumentException e) {
      // ok
    }
    try {
      palDirectory.deleteLog(writeAheadLogInfo.getName());
      fail("Should have raised IllegalArgumentException because the log is in use by a peer");
    } catch (IllegalArgumentException e) {
      // ok
    }
  }

  @Test
  public void getLogInfo_noSuchLog_exception() throws Exception {
    String logName = "test.strange_topic";
    assertFalse(palDirectory.logExists(logName));
    assertNull(palDirectory.getLogInfo(logName));
  }

  @Test
  public void getLogInfo_logExists_logInfo() throws Exception {
    String logName = "test.topic";

    // create logInfo
    LogInfo newLogInfo = new LogInfo(logName, getKafkaServers());
    palDirectory.createLog(newLogInfo);
    createdLogs.add(logName);
    assertTrue(palDirectory.logExists(logName));

    LogInfo retrievedLogInfo = palDirectory.getLogInfo(logName);

    // verify returned logInfo
    assertEquals(newLogInfo, retrievedLogInfo);
    assertNotNull(retrievedLogInfo.getUuid());

    // verify CTime and MTime (which are in UTC) are within last second
    OffsetDateTime now = OffsetDateTime.now(ZoneId.of("UTC"));
    assertTrue(retrievedLogInfo.getCTime().isAfter(now.minusSeconds(1)));
    assertTrue(retrievedLogInfo.getCTime().isBefore(now));
    assertTrue(retrievedLogInfo.getMTime().isAfter(now.minusSeconds(1)));
    assertTrue(retrievedLogInfo.getMTime().isBefore(now));
  }

  @Test
  public void listAllLogs_someLogsCreated_all() throws Exception {
    String logNamePrefix = "test.topic";

    // create N logs
    int logsToCreate = 10;
    for (int i = 0; i < logsToCreate; i++) {
      LogInfo newLogInfo = palDirectory.createAutoLog(logNamePrefix, getKafkaServers());
      createdLogs.add(newLogInfo.getName());
    }

    // verify
    assertEquals(logsToCreate, palDirectory.listAllLogs().size());
  }

  @Test
  public void listLogsWithPrefix_multiplePrefix_onlyMatchingReturned() throws Exception {
    String prefix1 = "test.topic";
    String prefix2 = "other.topic";
    String prefix3 = "another.log";

    // create logs with different prefixes
    int logsPerPrefix = 5;
    for (int i = 0; i < logsPerPrefix; i++) {
      LogInfo log1 = palDirectory.createAutoLog(prefix1, getKafkaServers());
      LogInfo log2 = palDirectory.createAutoLog(prefix2, getKafkaServers());
      LogInfo log3 = palDirectory.createAutoLog(prefix3, getKafkaServers());
      createdLogs.add(log1.getName());
      createdLogs.add(log2.getName());
      createdLogs.add(log3.getName());
    }

    // verify each prefix returns only its logs
    Set<LogInfo> logsWithPrefix1 = palDirectory.listLogsWithPrefix(prefix1);
    assertEquals(logsPerPrefix, logsWithPrefix1.size());
    for (LogInfo log : logsWithPrefix1) {
      assertTrue(log.getName().startsWith(prefix1));
    }

    Set<LogInfo> logsWithPrefix2 = palDirectory.listLogsWithPrefix(prefix2);
    assertEquals(logsPerPrefix, logsWithPrefix2.size());
    for (LogInfo log : logsWithPrefix2) {
      assertTrue(log.getName().startsWith(prefix2));
    }

    Set<LogInfo> logsWithPrefix3 = palDirectory.listLogsWithPrefix(prefix3);
    assertEquals(logsPerPrefix, logsWithPrefix3.size());
    for (LogInfo log : logsWithPrefix3) {
      assertTrue(log.getName().startsWith(prefix3));
    }
  }

  @Test
  public void countLogsWithPrefix_noMatchingLogsExist_zero() throws Exception {
    String logNamePrefix = "strange.topic";
    assertEquals(0, palDirectory.countLogsWithPrefix(logNamePrefix));
  }

  @Test
  public void countLogsWithPrefix_multiplePrefixes_onlyMatchingCounted() throws Exception {
    String prefix1 = "count.test";
    String prefix2 = "count.other";
    String prefix3 = "different.prefix";

    // create logs with different prefixes
    int logsForPrefix1 = 7;
    int logsForPrefix2 = 3;
    int logsForPrefix3 = 5;

    for (int i = 0; i < logsForPrefix1; i++) {
      LogInfo log = palDirectory.createAutoLog(prefix1, getKafkaServers());
      createdLogs.add(log.getName());
    }
    for (int i = 0; i < logsForPrefix2; i++) {
      LogInfo log = palDirectory.createAutoLog(prefix2, getKafkaServers());
      createdLogs.add(log.getName());
    }
    for (int i = 0; i < logsForPrefix3; i++) {
      LogInfo log = palDirectory.createAutoLog(prefix3, getKafkaServers());
      createdLogs.add(log.getName());
    }

    // verify each prefix counts only its logs
    assertEquals(logsForPrefix1, palDirectory.countLogsWithPrefix(prefix1));
    assertEquals(logsForPrefix2, palDirectory.countLogsWithPrefix(prefix2));
    assertEquals(logsForPrefix3, palDirectory.countLogsWithPrefix(prefix3));

    // verify total
    assertEquals(
        logsForPrefix1 + logsForPrefix2 + logsForPrefix3, palDirectory.listAllLogs().size());
  }

  @Test
  public void getLatestLogWithPrefix_multiplePrefixes_onlyMatchingPrefixConsidered()
      throws Exception {
    String prefix1 = "latest.app";
    String prefix2 = "latest.other";
    String prefix3 = "different.log";

    // create logs with different prefixes, interleaved
    String lastPrefix1 = null;
    String lastPrefix2 = null;
    String lastPrefix3 = null;

    for (int i = 0; i < 5; i++) {
      LogInfo log1 = palDirectory.createAutoLog(prefix1, getKafkaServers());
      lastPrefix1 = log1.getName();
      createdLogs.add(lastPrefix1);

      LogInfo log2 = palDirectory.createAutoLog(prefix2, getKafkaServers());
      lastPrefix2 = log2.getName();
      createdLogs.add(lastPrefix2);

      LogInfo log3 = palDirectory.createAutoLog(prefix3, getKafkaServers());
      lastPrefix3 = log3.getName();
      createdLogs.add(lastPrefix3);
    }

    // verify each prefix returns its own latest log
    assertEquals(lastPrefix1, palDirectory.getLatestLogWithPrefix(prefix1).getName());
    assertEquals(lastPrefix2, palDirectory.getLatestLogWithPrefix(prefix2).getName());
    assertEquals(lastPrefix3, palDirectory.getLatestLogWithPrefix(prefix3).getName());

    // verify the returned logs have the correct prefixes
    assertTrue(palDirectory.getLatestLogWithPrefix(prefix1).getName().startsWith(prefix1));
    assertTrue(palDirectory.getLatestLogWithPrefix(prefix2).getName().startsWith(prefix2));
    assertTrue(palDirectory.getLatestLogWithPrefix(prefix3).getName().startsWith(prefix3));
  }

  @Test
  public void deleteLogsWithPrefix_multiplePrefixes_onlyMatchingDeleted() throws Exception {
    String prefix1 = "delete.target";
    String prefix2 = "delete.other";
    String prefix3 = "keep.these";

    // create logs with different prefixes
    int logsForPrefix1 = 10;
    int logsForPrefix2 = 3;
    int logsForPrefix3 = 5;

    for (int i = 0; i < logsForPrefix1; i++) {
      LogInfo log = palDirectory.createAutoLog(prefix1, getKafkaServers());
      createdLogs.add(log.getName());
    }
    for (int i = 0; i < logsForPrefix2; i++) {
      LogInfo log = palDirectory.createAutoLog(prefix2, getKafkaServers());
      createdLogs.add(log.getName());
    }
    for (int i = 0; i < logsForPrefix3; i++) {
      LogInfo log = palDirectory.createAutoLog(prefix3, getKafkaServers());
      createdLogs.add(log.getName());
    }

    // pre-assertions - verify all logs exist
    assertEquals(logsForPrefix1, palDirectory.countLogsWithPrefix(prefix1));
    assertEquals(logsForPrefix2, palDirectory.countLogsWithPrefix(prefix2));
    assertEquals(logsForPrefix3, palDirectory.countLogsWithPrefix(prefix3));
    assertEquals(
        logsForPrefix1 + logsForPrefix2 + logsForPrefix3, palDirectory.listAllLogs().size());

    // delete logs with prefix1
    long logsDeleted = palDirectory.deleteLogsWithPrefix(prefix1);
    assertEquals(logsForPrefix1, logsDeleted);

    // verify only prefix1 logs are deleted, others remain
    assertEquals(0, palDirectory.countLogsWithPrefix(prefix1));
    assertEquals(logsForPrefix2, palDirectory.countLogsWithPrefix(prefix2));
    assertEquals(logsForPrefix3, palDirectory.countLogsWithPrefix(prefix3));
    assertEquals(logsForPrefix2 + logsForPrefix3, palDirectory.listAllLogs().size());
  }

  @Test
  public void deleteAllLogs_existingLogs_allLogsDeleted() throws Exception {
    String logNamePrefix = "test.topic";

    // create a few with the prefix
    int logsToCreate = 10;
    for (int i = 0; i < logsToCreate; i++) {
      LogInfo newLogInfo = palDirectory.createAutoLog(logNamePrefix, getKafkaServers());
      createdLogs.add(newLogInfo.getName());
    }

    // pre-assertions
    assertEquals(logsToCreate, palDirectory.listAllLogs().size());

    // delete all
    long deletedLogs = palDirectory.purgeLogsExcept(new HashSet<>());
    assertEquals(logsToCreate, deletedLogs);

    // verify
    assertEquals(0, palDirectory.listAllLogs().size());
  }

  @Test
  public void deleteLog_existingLog_logDeleted() throws Exception {
    String logNamePrefix = "test.topic";

    LogInfo newLogInfo = palDirectory.createAutoLog(logNamePrefix, getKafkaServers());
    String createdLogName = newLogInfo.getName();
    // pre-assertions
    assertTrue(palDirectory.logExists(createdLogName));

    palDirectory.deleteLog(createdLogName);

    // verify
    assertFalse(palDirectory.logExists(createdLogName));
  }

  @Test
  public void createIntercept_noSuchPeer_exception() throws Exception {
    InterceptRequest<InterceptableMethodCall> req =
        new InterceptRequest<>(
            UUID.randomUUID(),
            UUID.randomUUID(),
            InterceptType.BEFORE,
            "java.io.PrintStream",
            "org.package.Callback",
            "callMe",
            new InterceptableMethodCall(
                "println", Arrays.asList("java.lang.String", "java.lang.Integer")));

    try {
      palDirectory.createIntercept(req);
      fail("Should have raised NoPeerInfoNodeException");
    } catch (NoPeerInfoNodeException e) {
      // ok
    }
  }

  @Test
  public void createIntercept_alreadyExists_exception() throws Exception {
    // create peer
    final PeerInfo peerInfo = new PeerInfo(UUID.randomUUID(), "testing peer");
    palDirectory.createPeer(peerInfo);
    createdPeers.add(peerInfo.getUuid());

    // pre-assertions
    assertTrue(palDirectory.peerExists(peerInfo.getUuid()));
    assertEquals(0, palDirectory.listInterceptsForPeer(peerInfo.getUuid()).size());

    // create intercept request
    InterceptRequest<InterceptableMethodCall> req =
        new InterceptRequest<>(
            UUID.randomUUID(),
            peerInfo.getUuid(),
            InterceptType.BEFORE,
            "java.io.PrintStream",
            "org.package.Callback",
            "callMe",
            new InterceptableMethodCall(
                "println", Arrays.asList("java.lang.String", "java.lang.Integer")));

    // create it
    palDirectory.createIntercept(req);
    addInterceptRequestToCreated(peerInfo.getUuid(), req.getUuid());
    assertEquals(1, palDirectory.listInterceptsForPeer(peerInfo.getUuid()).size());

    // try to create again
    try {
      palDirectory.createIntercept(req);
      fail("Should have raised IllegalArgumentException");
    } catch (IllegalArgumentException e) {
      // ok
    }
  }

  @Test
  public void createIntercept_peerExists_created() throws Exception {
    // create peer
    final PeerInfo peerInfo = new PeerInfo(UUID.randomUUID(), "testing peer");
    palDirectory.createPeer(peerInfo);
    createdPeers.add(peerInfo.getUuid());

    // pre-assertions
    assertTrue(palDirectory.peerExists(peerInfo.getUuid()));
    assertEquals(0, palDirectory.listInterceptsForPeer(peerInfo.getUuid()).size());

    // create intercept request
    InterceptRequest<InterceptableMethodCall> req =
        new InterceptRequest<>(
            UUID.randomUUID(),
            peerInfo.getUuid(),
            InterceptType.BEFORE,
            "java.io.PrintStream",
            "org.package.Callback",
            "callMe",
            new InterceptableMethodCall(
                "println", Arrays.asList("java.lang.String", "java.lang.Integer")));

    // create it
    palDirectory.createIntercept(req);
    addInterceptRequestToCreated(peerInfo.getUuid(), req.getUuid());
    assertEquals(1, palDirectory.listInterceptsForPeer(peerInfo.getUuid()).size());
  }

  /* ------------------------------------------------------------------ */
  /*            I N T E R C E P T   T T L   T E S T S                   */
  /* ------------------------------------------------------------------ */

  /**
   * Path A: intercept is bound to the peer’s live lease and vanishes when that lease is revoked.
   */
  @Test
  public void createIntercept_peerLease_autoRemovedOnLeaseRevoke() throws Exception {

    // 1) create peer + live lease (TTL 5 s)
    PeerInfo peer = new PeerInfo(UUID.randomUUID(), "ttl-peer");
    palDirectory.createPeer(peer);
    createdPeers.add(peer.getUuid());

    PeerLease lease = palDirectory.createPeerLease(peer.getUuid(), 5); // keep-alive inside

    // 2) create an intercept with ttl=0 → should attach to peer lease
    InterceptRequest<InterceptableMethodCall> req =
        new InterceptRequest<>(
            UUID.randomUUID(),
            peer.getUuid(),
            InterceptType.BEFORE,
            "java.lang.System",
            "org.Callback",
            "noop",
            new InterceptableMethodCall("currentTimeMillis", List.of()));

    palDirectory.createIntercept(req); // ttlSeconds defaults to 0
    // (=peer lease)

    assertEquals(1, palDirectory.listInterceptsForPeer(peer.getUuid()).size());

    // 3) revoke lease – should delete /state + intercept
    lease.close(); // stops KA and revoke(…)

    // 4) wait up to 6 s for etcd to apply the revoke
    for (int i = 0; i < 12; i++) {
      if (palDirectory.listInterceptsForPeer(peer.getUuid()).isEmpty()) {
        break;
      }
      TimeUnit.MILLISECONDS.sleep(500);
    }
    assertTrue(
        "Intercept should disappear after lease revoke",
        palDirectory.listInterceptsForPeer(peer.getUuid()).isEmpty());
  }

  /** Path B: dedicated one-off TTL – intercept auto-expires even while the peer stays alive. */
  @Test
  public void createIntercept_dedicatedTTL_autoExpires() throws Exception {

    PeerInfo peer = new PeerInfo(UUID.randomUUID(), "ttl-peer-2");
    palDirectory.createPeer(peer);
    createdPeers.add(peer.getUuid());

    // No peer lease on purpose
    InterceptRequest<InterceptableMethodCall> req =
        new InterceptRequest<>(
            UUID.randomUUID(),
            peer.getUuid(),
            InterceptType.BEFORE,
            "java.lang.System",
            "org.Callback",
            "noop",
            new InterceptableMethodCall("nanoTime", List.of()));

    palDirectory.createIntercept(req, 3); // 3-second dedicated lease
    assertEquals(1, palDirectory.listInterceptsForPeer(peer.getUuid()).size());

    // Wait 4 s – should expire
    TimeUnit.SECONDS.sleep(4);
    assertTrue(palDirectory.listInterceptsForPeer(peer.getUuid()).isEmpty());
    // peer still lives
    assertNotNull(palDirectory.getPeer(peer.getUuid()));
  }

  /** Path C: ttlSeconds=0 and peer has *no* live lease → intercept persists. */
  @Test
  public void createIntercept_noLease_persists() throws Exception {

    PeerInfo peer = new PeerInfo(UUID.randomUUID(), "nolease-peer");
    palDirectory.createPeer(peer);
    createdPeers.add(peer.getUuid());

    InterceptRequest<InterceptableMethodCall> req =
        new InterceptRequest<>(
            UUID.randomUUID(),
            peer.getUuid(),
            InterceptType.BEFORE,
            "java.lang.System",
            "org.Callback",
            "noop",
            new InterceptableMethodCall("currentTimeMillis", List.of()));

    palDirectory.createIntercept(req); // ttlSeconds = 0 → no lease
    addInterceptRequestToCreated(peer.getUuid(), req.getUuid());

    assertEquals(1, palDirectory.listInterceptsForPeer(peer.getUuid()).size());

    // Wait 4 s – should still be there
    TimeUnit.SECONDS.sleep(4); // this is random, we could wait forever here
    assertEquals(1, palDirectory.listInterceptsForPeer(peer.getUuid()).size());
  }

  @Test
  public void deleteIntercept_interceptExists_deleted() throws Exception {
    // create peer
    final PeerInfo peerInfo = new PeerInfo(UUID.randomUUID(), "testing peer");
    palDirectory.createPeer(peerInfo);
    createdPeers.add(peerInfo.getUuid());

    // pre-assertions
    assertTrue(palDirectory.peerExists(peerInfo.getUuid()));
    assertEquals(0, palDirectory.listInterceptsForPeer(peerInfo.getUuid()).size());

    // create intercept request
    InterceptRequest<InterceptableMethodCall> req =
        new InterceptRequest<>(
            UUID.randomUUID(),
            peerInfo.getUuid(),
            InterceptType.BEFORE,
            "java.io.PrintStream",
            "org.package.Callback",
            "callMe",
            new InterceptableMethodCall(
                "println", Arrays.asList("java.lang.String", "java.lang.Integer")));

    // create it
    palDirectory.createIntercept(req);
    addInterceptRequestToCreated(peerInfo.getUuid(), req.getUuid());
    assertEquals(1, palDirectory.listInterceptsForPeer(peerInfo.getUuid()).size());

    // now delete it
    palDirectory.deleteIntercept(peerInfo.getUuid(), req.getUuid());
    assertEquals(0, palDirectory.listInterceptsForPeer(peerInfo.getUuid()).size());
  }

  @Test
  public void listInterceptsForPeer_emptyList() throws Exception {
    final PeerInfo peerInfo = new PeerInfo(UUID.randomUUID(), "testing peer");
    palDirectory.createPeer(peerInfo);
    createdPeers.add(peerInfo.getUuid());

    // pre-assertions
    assertTrue(palDirectory.peerExists(peerInfo.getUuid()));

    assertTrue(palDirectory.listInterceptsForPeer(peerInfo.getUuid()).isEmpty());
  }

  @Test
  public void listInterceptsForPeerExist_requestList() throws Exception {
    // create peer
    final PeerInfo peerInfo = new PeerInfo(UUID.randomUUID(), "testing peer");
    palDirectory.createPeer(peerInfo);
    createdPeers.add(peerInfo.getUuid());

    // pre-assertions
    assertTrue(palDirectory.peerExists(peerInfo.getUuid()));
    assertTrue(palDirectory.listInterceptsForPeer(peerInfo.getUuid()).isEmpty());

    // create 2 intercept requests
    Set<InterceptRequest<InterceptableMethodCall>> requests = new HashSet<>();
    final int totalPeerIntercepts = 2;
    for (int i = 0; i < totalPeerIntercepts; i++) {
      requests.add(
          new InterceptRequest<>(
              UUID.randomUUID(),
              peerInfo.getUuid(),
              InterceptType.BEFORE,
              "java.io.PrintStream",
              "org.package.Callback",
              "callMe",
              new InterceptableMethodCall(
                  "println", Arrays.asList("java.lang.String", "java.lang.Integer"))));
    }
    final CountDownLatch latch = new CountDownLatch(totalPeerIntercepts);

    // set listener
    palDirectory.addInterceptListener(
        event -> {
          if (event.type().equals(InterceptEvent.Type.INTERCEPT_ADDED)) {
            latch.countDown();
          }
        });

    // create them
    for (InterceptRequest<InterceptableMethodCall> interceptRequest : requests) {
      palDirectory.createIntercept(interceptRequest);
      addInterceptRequestToCreated(peerInfo.getUuid(), interceptRequest.getUuid());
    }

    // wait for all listener events
    latch.await();

    // now retrieve and compare
    assertEquals(requests, palDirectory.listInterceptsForPeer(peerInfo.getUuid()));
  }

  @Test
  public void listAllInterceptsExist_requestList() throws Exception {
    // create two different peers
    final PeerInfo peerInfo1 = new PeerInfo(UUID.randomUUID(), "testing peer 1");
    palDirectory.createPeer(peerInfo1);
    createdPeers.add(peerInfo1.getUuid());

    final PeerInfo peerInfo2 = new PeerInfo(UUID.randomUUID(), "testing peer 2");
    palDirectory.createPeer(peerInfo2);
    createdPeers.add(peerInfo2.getUuid());

    // pre-assertions
    assertTrue(palDirectory.peerExists(peerInfo1.getUuid()));
    assertTrue(palDirectory.peerExists(peerInfo2.getUuid()));
    assertTrue(palDirectory.listInterceptsForPeer(peerInfo1.getUuid()).isEmpty());
    assertTrue(palDirectory.listInterceptsForPeer(peerInfo2.getUuid()).isEmpty());

    // create 2 intercept requests for peer 1
    Set<InterceptRequest<InterceptableMethodCall>> requestsPeer1 = new HashSet<>();
    final int totalPeerIntercepts = 2;
    for (int i = 0; i < totalPeerIntercepts; i++) {
      requestsPeer1.add(
          new InterceptRequest<>(
              UUID.randomUUID(),
              peerInfo1.getUuid(),
              InterceptType.BEFORE,
              "java.io.PrintStream",
              "org.package.Callback",
              "callMe",
              new InterceptableMethodCall(
                  "println", Arrays.asList("java.lang.String", "java.lang.Integer"))));
    }

    // create 2 intercept requests for peer 2
    Set<InterceptRequest<InterceptableMethodCall>> requestsPeer2 = new HashSet<>();
    for (int i = 0; i < totalPeerIntercepts; i++) {
      requestsPeer2.add(
          new InterceptRequest<>(
              UUID.randomUUID(),
              peerInfo2.getUuid(),
              InterceptType.BEFORE,
              "java.io.PrintStream",
              "org.package.Callback",
              "callMe",
              new InterceptableMethodCall(
                  "println", Arrays.asList("java.lang.String", "java.lang.Integer"))));
    }

    final CountDownLatch latch = new CountDownLatch(totalPeerIntercepts * 2);

    // set listener
    palDirectory.addInterceptListener(
        event -> {
          if (event.type().equals(InterceptEvent.Type.INTERCEPT_ADDED)) {
            latch.countDown();
          }
        });

    // create them
    for (InterceptRequest<InterceptableMethodCall> interceptRequest : requestsPeer1) {
      palDirectory.createIntercept(interceptRequest);
      addInterceptRequestToCreated(peerInfo1.getUuid(), interceptRequest.getUuid());
    }
    for (InterceptRequest<InterceptableMethodCall> interceptRequest : requestsPeer2) {
      palDirectory.createIntercept(interceptRequest);
      addInterceptRequestToCreated(peerInfo2.getUuid(), interceptRequest.getUuid());
    }

    // wait for all listener events
    latch.await();

    // now retrieve and compare
    Set<InterceptRequest<InterceptableMethodCall>> allInterceptRequests = new HashSet<>();
    allInterceptRequests.addAll(requestsPeer1);
    allInterceptRequests.addAll(requestsPeer2);

    assertEquals(palDirectory.listAllIntercepts(), allInterceptRequests);
  }

  @Test
  public void deleteInterceptRequests_forPeerExist_deleted() throws Exception {
    // create peer
    final PeerInfo peerInfo = new PeerInfo(UUID.randomUUID(), "testing peer");
    palDirectory.createPeer(peerInfo);
    createdPeers.add(peerInfo.getUuid());

    // pre-assertions
    assertEquals(0, palDirectory.listInterceptsForPeer(peerInfo.getUuid()).size());

    // create some intercept requests
    final int totalPeerIntercepts = 3;
    for (int i = 0; i < totalPeerIntercepts; i++) {
      palDirectory.createIntercept(
          new InterceptRequest<>(
              UUID.randomUUID(),
              peerInfo.getUuid(),
              InterceptType.BEFORE,
              "java.io.PrintStream",
              "org.package.Callback",
              String.format("callMe%d", i),
              new InterceptableMethodCall(
                  "println", Arrays.asList("java.lang.String", "java.lang.Integer"))));
    }

    assertEquals(
        totalPeerIntercepts, palDirectory.listInterceptsForPeer(peerInfo.getUuid()).size());

    // delete them
    palDirectory.deleteInterceptsForPeer(peerInfo.getUuid());
    assertEquals(0, palDirectory.listInterceptsForPeer(peerInfo.getUuid()).size());
  }

  // =========================================================================
  // 1:N Log Mapping Tests - Multiple logs can share the same filename
  // =========================================================================

  @Test
  public void createLog_multipleChronicleLogsWithSameFilename_allCreated() throws Exception {
    // Create multiple Chronicle logs with same filename but different full paths
    LogInfo log1 = new LogInfo("/tmp/wal/mylog");
    log1.setLogType(LogInfo.LogType.CHRONICLE);
    palDirectory.createLog(log1);
    createdLogs.add(log1.getName());

    LogInfo log2 = new LogInfo("/tmp/wal2/mylog");
    log2.setLogType(LogInfo.LogType.CHRONICLE);
    palDirectory.createLog(log2);
    createdLogs.add(log2.getName());

    LogInfo log3 = new LogInfo("/var/logs/mylog");
    log3.setLogType(LogInfo.LogType.CHRONICLE);
    palDirectory.createLog(log3);
    createdLogs.add(log3.getName());

    // All three logs should exist
    assertTrue(palDirectory.logExists(log1.getUuid()));
    assertTrue(palDirectory.logExists(log2.getUuid()));
    assertTrue(palDirectory.logExists(log3.getUuid()));

    // All should have different UUIDs
    assertNotNull(log1.getUuid());
    assertNotNull(log2.getUuid());
    assertNotNull(log3.getUuid());
    assertNotEquals(log1.getUuid(), log2.getUuid());
    assertNotEquals(log2.getUuid(), log3.getUuid());
    assertNotEquals(log1.getUuid(), log3.getUuid());

    // Verify we can retrieve each by UUID
    assertEquals(log1.getName(), palDirectory.getLogInfo(log1.getUuid()).getName());
    assertEquals(log2.getName(), palDirectory.getLogInfo(log2.getUuid()).getName());
    assertEquals(log3.getName(), palDirectory.getLogInfo(log3.getUuid()).getName());

    // Clean up by UUID (required because multiple logs share the same filename)
    palDirectory.deleteLog(log1.getUuid());
    palDirectory.deleteLog(log2.getUuid());
    palDirectory.deleteLog(log3.getUuid());
    createdLogs.remove(log1.getName());
    createdLogs.remove(log2.getName());
    createdLogs.remove(log3.getName());
  }

  @Test
  public void createLog_multipleKafkaLogsWithSameName_allCreated() throws Exception {
    String logName = "app-log";

    // Create multiple Kafka logs with same name but different bootstrap servers
    LogInfo log1 = new LogInfo(logName, "localhost:9092");
    log1.setLogType(LogInfo.LogType.KAFKA);
    palDirectory.createLog(log1);
    createdLogs.add(log1.getName());

    LogInfo log2 = new LogInfo(logName, "prod-kafka:9092");
    log2.setLogType(LogInfo.LogType.KAFKA);
    palDirectory.createLog(log2);
    createdLogs.add(log2.getName());

    LogInfo log3 = new LogInfo(logName, "staging-kafka:9092");
    log3.setLogType(LogInfo.LogType.KAFKA);
    palDirectory.createLog(log3);
    createdLogs.add(log3.getName());

    // All three logs should exist
    assertTrue(palDirectory.logExists(log1.getUuid()));
    assertTrue(palDirectory.logExists(log2.getUuid()));
    assertTrue(palDirectory.logExists(log3.getUuid()));

    // All should have different UUIDs
    assertNotNull(log1.getUuid());
    assertNotNull(log2.getUuid());
    assertNotNull(log3.getUuid());
    assertNotEquals(log1.getUuid(), log2.getUuid());
    assertNotEquals(log2.getUuid(), log3.getUuid());
    assertNotEquals(log1.getUuid(), log3.getUuid());

    // Verify we can retrieve each by UUID and servers match
    assertEquals("localhost:9092", palDirectory.getLogInfo(log1.getUuid()).getBootstrapServers());
    assertEquals("prod-kafka:9092", palDirectory.getLogInfo(log2.getUuid()).getBootstrapServers());
    assertEquals(
        "staging-kafka:9092", palDirectory.getLogInfo(log3.getUuid()).getBootstrapServers());

    // Clean up by UUID (required because multiple logs share the same name)
    palDirectory.deleteLog(log1.getUuid());
    palDirectory.deleteLog(log2.getUuid());
    palDirectory.deleteLog(log3.getUuid());
    createdLogs.remove(log1.getName());
    createdLogs.remove(log2.getName());
    createdLogs.remove(log3.getName());
  }

  @Test
  public void getLogsInfoByName_multipleLogsWithSameFilename_allReturned() throws Exception {
    // Create multiple logs with same filename
    LogInfo log1 = new LogInfo("/tmp/wal/mylog");
    log1.setLogType(LogInfo.LogType.CHRONICLE);
    palDirectory.createLog(log1);
    createdLogs.add(log1.getName());

    LogInfo log2 = new LogInfo("/tmp/wal2/mylog");
    log2.setLogType(LogInfo.LogType.CHRONICLE);
    palDirectory.createLog(log2);
    createdLogs.add(log2.getName());

    LogInfo log3 = new LogInfo("/var/logs/mylog");
    log3.setLogType(LogInfo.LogType.CHRONICLE);
    palDirectory.createLog(log3);
    createdLogs.add(log3.getName());

    // getLogsInfoByName should return all three logs
    List<LogInfo> logs = palDirectory.getLogsInfoByName("/any/path/mylog");
    assertEquals(3, logs.size());

    // Verify all UUIDs are present
    Set<UUID> uuids = new HashSet<>();
    for (LogInfo log : logs) {
      uuids.add(log.getUuid());
    }
    assertTrue(uuids.contains(log1.getUuid()));
    assertTrue(uuids.contains(log2.getUuid()));
    assertTrue(uuids.contains(log3.getUuid()));

    // Clean up by UUID (required because multiple logs share the same filename)
    palDirectory.deleteLog(log1.getUuid());
    palDirectory.deleteLog(log2.getUuid());
    palDirectory.deleteLog(log3.getUuid());
    createdLogs.remove(log1.getName());
    createdLogs.remove(log2.getName());
    createdLogs.remove(log3.getName());
  }

  @Test
  public void getLogInfo_multipleLogsWithSameFilename_throwsIllegalStateException()
      throws Exception {
    // Create multiple logs with same filename
    LogInfo log1 = new LogInfo("/tmp/wal/mylog");
    log1.setLogType(LogInfo.LogType.CHRONICLE);
    palDirectory.createLog(log1);
    createdLogs.add(log1.getName());

    LogInfo log2 = new LogInfo("/tmp/wal2/mylog");
    log2.setLogType(LogInfo.LogType.CHRONICLE);
    palDirectory.createLog(log2);
    createdLogs.add(log2.getName());

    // getLogInfo(String) should throw IllegalStateException when multiple matches exist
    try {
      palDirectory.getLogInfo("/any/path/mylog");
      fail("Expected IllegalStateException when multiple logs match the name");
    } catch (IllegalStateException e) {
      // Expected - verify error message contains UUIDs
      String message = e.getMessage();
      assertTrue(message.contains("Multiple logs"));
      assertTrue(message.contains(log1.getUuid().toString()));
      assertTrue(message.contains(log2.getUuid().toString()));
      assertTrue(message.contains("mylog"));
    }

    // Clean up by UUID (required because multiple logs share the same filename)
    palDirectory.deleteLog(log1.getUuid());
    palDirectory.deleteLog(log2.getUuid());
    createdLogs.remove(log1.getName());
    createdLogs.remove(log2.getName());
  }

  @Test
  public void deleteLog_multipleLogsWithSameFilename_throwsIllegalStateException()
      throws Exception {
    // Create multiple logs with same filename
    LogInfo log1 = new LogInfo("/tmp/wal/mylog");
    log1.setLogType(LogInfo.LogType.CHRONICLE);
    palDirectory.createLog(log1);
    createdLogs.add(log1.getName());

    LogInfo log2 = new LogInfo("/tmp/wal2/mylog");
    log2.setLogType(LogInfo.LogType.CHRONICLE);
    palDirectory.createLog(log2);
    createdLogs.add(log2.getName());

    // deleteLog(String) should throw IllegalStateException when multiple matches exist
    try {
      palDirectory.deleteLog("/any/path/mylog");
      fail("Expected IllegalStateException when multiple logs match the name");
    } catch (IllegalStateException e) {
      // Expected - verify error message contains UUIDs
      String message = e.getMessage();
      assertTrue(message.contains("Multiple logs"));
      assertTrue(message.contains(log1.getUuid().toString()));
      assertTrue(message.contains(log2.getUuid().toString()));
      assertTrue(message.contains("Specify by UUID"));
    }

    // Verify both logs still exist
    assertTrue(palDirectory.logExists(log1.getUuid()));
    assertTrue(palDirectory.logExists(log2.getUuid()));

    // Clean up by UUID (required because multiple logs share the same filename)
    palDirectory.deleteLog(log1.getUuid());
    palDirectory.deleteLog(log2.getUuid());
    createdLogs.remove(log1.getName());
    createdLogs.remove(log2.getName());
  }

  @Test
  public void deleteLog_multipleLogsWithSameFilename_deleteByUuidSucceeds() throws Exception {
    // Create multiple logs with same filename
    LogInfo log1 = new LogInfo("/tmp/wal/mylog");
    log1.setLogType(LogInfo.LogType.CHRONICLE);
    palDirectory.createLog(log1);
    createdLogs.add(log1.getName());

    LogInfo log2 = new LogInfo("/tmp/wal2/mylog");
    log2.setLogType(LogInfo.LogType.CHRONICLE);
    palDirectory.createLog(log2);
    createdLogs.add(log2.getName());

    LogInfo log3 = new LogInfo("/var/logs/mylog");
    log3.setLogType(LogInfo.LogType.CHRONICLE);
    palDirectory.createLog(log3);
    createdLogs.add(log3.getName());

    // Pre-assertion: all three exist
    assertEquals(3, palDirectory.getLogsInfoByName("/any/path/mylog").size());

    // Delete log2 by UUID
    palDirectory.deleteLog(log2.getUuid());
    createdLogs.remove(log2.getName());

    // Verify log2 is deleted but log1 and log3 remain
    assertTrue(palDirectory.logExists(log1.getUuid()));
    assertFalse(palDirectory.logExists(log2.getUuid()));
    assertTrue(palDirectory.logExists(log3.getUuid()));

    // getLogsInfoByName should now return only 2 logs
    List<LogInfo> remainingLogs = palDirectory.getLogsInfoByName("/any/path/mylog");
    assertEquals(2, remainingLogs.size());

    Set<UUID> remainingUuids = new HashSet<>();
    for (LogInfo log : remainingLogs) {
      remainingUuids.add(log.getUuid());
    }
    assertTrue(remainingUuids.contains(log1.getUuid()));
    assertFalse(remainingUuids.contains(log2.getUuid()));
    assertTrue(remainingUuids.contains(log3.getUuid()));

    // Clean up remaining logs by UUID (required because multiple logs share the same filename)
    palDirectory.deleteLog(log1.getUuid());
    palDirectory.deleteLog(log3.getUuid());
    createdLogs.remove(log1.getName());
    createdLogs.remove(log3.getName());
  }

  @Test
  public void createLog_duplicateChronicleLogWithSamePath_notCreated() throws Exception {
    // Create a Chronicle log
    LogInfo log1 = new LogInfo("/tmp/wal/mylog");
    log1.setLogType(LogInfo.LogType.CHRONICLE);
    palDirectory.createLog(log1);
    createdLogs.add(log1.getName());

    UUID firstUuid = log1.getUuid();

    // Try to create another Chronicle log with the exact same path
    LogInfo log2 = new LogInfo("/tmp/wal/mylog");
    log2.setLogType(LogInfo.LogType.CHRONICLE);
    palDirectory.createLog(log2);
    // Don't add to createdLogs since it shouldn't be created

    // Should only have one log with this filename
    List<LogInfo> logs = palDirectory.getLogsInfoByName("/tmp/wal/mylog");
    assertEquals(1, logs.size());
    assertEquals(firstUuid, logs.get(0).getUuid());
  }

  @Test
  public void createLog_duplicateKafkaLogWithSameNameAndServers_notCreated() throws Exception {
    String logName = "app-log";
    String servers = "localhost:9092";

    // Create a Kafka log
    LogInfo log1 = new LogInfo(logName, servers);
    log1.setLogType(LogInfo.LogType.KAFKA);
    palDirectory.createLog(log1);
    createdLogs.add(log1.getName());

    UUID firstUuid = log1.getUuid();

    // Try to create another Kafka log with the same name and servers
    LogInfo log2 = new LogInfo(logName, servers);
    log2.setLogType(LogInfo.LogType.KAFKA);
    palDirectory.createLog(log2);
    // Don't add to createdLogs since it shouldn't be created

    // Should only have one log with this name+servers combination
    List<LogInfo> logs = palDirectory.getLogsInfoByName(logName);
    assertEquals(1, logs.size());
    assertEquals(firstUuid, logs.get(0).getUuid());
    assertEquals(servers, logs.get(0).getBootstrapServers());
  }

  @Test
  public void listLogsWithPrefix_multipleLogsWithSameFilenamePrefix_allReturned() throws Exception {
    // Create logs with filenames starting with "app"
    LogInfo log1 = new LogInfo("/tmp/app-log");
    log1.setLogType(LogInfo.LogType.CHRONICLE);
    palDirectory.createLog(log1);
    createdLogs.add(log1.getName());

    LogInfo log2 = new LogInfo("/var/app-log");
    log2.setLogType(LogInfo.LogType.CHRONICLE);
    palDirectory.createLog(log2);
    createdLogs.add(log2.getName());

    LogInfo log3 = new LogInfo("/tmp/app-events");
    log3.setLogType(LogInfo.LogType.CHRONICLE);
    palDirectory.createLog(log3);
    createdLogs.add(log3.getName());

    // Create a log that doesn't match the prefix
    LogInfo log4 = new LogInfo("/tmp/other-log");
    log4.setLogType(LogInfo.LogType.CHRONICLE);
    palDirectory.createLog(log4);
    createdLogs.add(log4.getName());

    // List logs with filename prefix "app"
    Set<LogInfo> appLogs = palDirectory.listLogsWithPrefix("app");
    assertEquals(3, appLogs.size());

    Set<UUID> appLogUuids = new HashSet<>();
    for (LogInfo log : appLogs) {
      appLogUuids.add(log.getUuid());
    }
    assertTrue(appLogUuids.contains(log1.getUuid()));
    assertTrue(appLogUuids.contains(log2.getUuid()));
    assertTrue(appLogUuids.contains(log3.getUuid()));
    assertFalse(appLogUuids.contains(log4.getUuid()));

    // Clean up by UUID (log1 and log2 share the same filename "app-log")
    palDirectory.deleteLog(log1.getUuid());
    palDirectory.deleteLog(log2.getUuid());
    palDirectory.deleteLog(log3.getUuid());
    palDirectory.deleteLog(log4.getUuid());
    createdLogs.remove(log1.getName());
    createdLogs.remove(log2.getName());
    createdLogs.remove(log3.getName());
    createdLogs.remove(log4.getName());
  }
}
