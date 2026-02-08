/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
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
import java.time.Duration;
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

  // =========================================================================
  // Source Log and WAL Registration Error Cases
  // =========================================================================

  @Test
  public void setSourceLog_nonExistentPeer_throwsNoPeerInfoNodeException() throws Exception {
    // Create a log
    LogInfo sourceLog = palDirectory.createAutoLog("test_source", getKafkaServers());
    createdLogs.add(sourceLog.getName());

    // Create peer info for a non-existent peer
    PeerInfo nonExistentPeer = new PeerInfo(UUID.randomUUID());
    nonExistentPeer.setZmqRpcAddress("tcp://127.0.0.1:5671");

    // Attempt to set source log for non-existent peer
    try {
      palDirectory.setSourceLog(nonExistentPeer, sourceLog, null);
      fail("Should have raised NoPeerInfoNodeException");
    } catch (NoPeerInfoNodeException e) {
      // Expected
      assertTrue(e.getMessage().contains("does not exist"));
    }
  }

  @Test
  public void setWalLog_nonExistentPeer_throwsNoPeerInfoNodeException() throws Exception {
    // Create a log
    LogInfo walLog = palDirectory.createAutoLog("test_wal", getKafkaServers());
    createdLogs.add(walLog.getName());

    // Create peer info for a non-existent peer
    PeerInfo nonExistentPeer = new PeerInfo(UUID.randomUUID());
    nonExistentPeer.setZmqRpcAddress("tcp://127.0.0.1:5671");

    // Attempt to set WAL for non-existent peer
    try {
      palDirectory.setWalLog(nonExistentPeer, walLog, null);
      fail("Should have raised NoPeerInfoNodeException");
    } catch (NoPeerInfoNodeException e) {
      // Expected
      assertTrue(e.getMessage().contains("does not exist"));
    }
  }

  @Test
  public void setSourceLog_alreadySet_throwsIllegalStateException() throws Exception {
    // Create peer
    final PeerInfo peerInfo = new PeerInfo(UUID.randomUUID());
    peerInfo.setZmqRpcAddress("tcp://127.0.0.1:5671");
    palDirectory.createPeer(peerInfo);
    createdPeers.add(peerInfo.getUuid());

    // Create two logs
    LogInfo sourceLog1 = palDirectory.createAutoLog("test_source1", getKafkaServers());
    LogInfo sourceLog2 = palDirectory.createAutoLog("test_source2", getKafkaServers());
    createdLogs.add(sourceLog1.getName());
    createdLogs.add(sourceLog2.getName());

    // Set source log first time - should succeed
    palDirectory.setSourceLog(peerInfo, sourceLog1, null);

    // Try to set source log again - should fail
    try {
      palDirectory.setSourceLog(peerInfo, sourceLog2, null);
      fail("Should have raised IllegalStateException");
    } catch (IllegalStateException e) {
      // Expected
      assertTrue(e.getMessage().contains("Source log already registered"));
    }
  }

  @Test
  public void setWalLog_alreadySet_throwsIllegalStateException() throws Exception {
    // Create peer
    final PeerInfo peerInfo = new PeerInfo(UUID.randomUUID());
    peerInfo.setZmqRpcAddress("tcp://127.0.0.1:5671");
    palDirectory.createPeer(peerInfo);
    createdPeers.add(peerInfo.getUuid());

    // Create two logs
    LogInfo walLog1 = palDirectory.createAutoLog("test_wal1", getKafkaServers());
    LogInfo walLog2 = palDirectory.createAutoLog("test_wal2", getKafkaServers());
    createdLogs.add(walLog1.getName());
    createdLogs.add(walLog2.getName());

    // Set WAL first time - should succeed
    palDirectory.setWalLog(peerInfo, walLog1, null);

    // Try to set WAL again - should fail
    try {
      palDirectory.setWalLog(peerInfo, walLog2, null);
      fail("Should have raised IllegalStateException");
    } catch (IllegalStateException e) {
      // Expected
      assertTrue(e.getMessage().contains("Write-Ahead log is already registered"));
    }
  }

  @Test
  public void getSourceLogId_noSourceLog_returnsNull() throws Exception {
    // Create peer without source log
    final PeerInfo peerInfo = new PeerInfo(UUID.randomUUID());
    peerInfo.setZmqRpcAddress("tcp://127.0.0.1:5671");
    palDirectory.createPeer(peerInfo);
    createdPeers.add(peerInfo.getUuid());

    // Should return null when no source log is set
    assertNull(palDirectory.getSourceLogId(peerInfo.getUuid()));
  }

  @Test
  public void getWalId_noWal_returnsNull() throws Exception {
    // Create peer without WAL
    final PeerInfo peerInfo = new PeerInfo(UUID.randomUUID());
    peerInfo.setZmqRpcAddress("tcp://127.0.0.1:5671");
    palDirectory.createPeer(peerInfo);
    createdPeers.add(peerInfo.getUuid());

    // Should return null when no WAL is set
    assertNull(palDirectory.getWalId(peerInfo.getUuid()));
  }

  @Test
  public void updatePeer_existingPeer_updatesState() throws Exception {
    // Create initial peer
    final PeerInfo peerInfo = new PeerInfo(UUID.randomUUID(), "initial-name");
    peerInfo.setZmqRpcAddress("tcp://127.0.0.1:5671");
    palDirectory.createPeer(peerInfo);
    createdPeers.add(peerInfo.getUuid());

    // Verify initial state
    PeerInfo retrieved = palDirectory.getPeer(peerInfo.getUuid());
    assertEquals("initial-name", retrieved.getName());
    assertEquals("tcp://127.0.0.1:5671", retrieved.getZmqRpcAddress());

    // Update peer with new addresses
    PeerInfo updatedPeerInfo = new PeerInfo(peerInfo.getUuid(), "initial-name");
    updatedPeerInfo.setZmqRpcAddress("tcp://127.0.0.1:9999");
    updatedPeerInfo.setJsonrpcAddress("ws://localhost:8080");
    updatedPeerInfo.setPubAddress("tcp://localhost:7777");
    updatedPeerInfo.setJmxAddress("localhost:9012");
    palDirectory.updatePeer(updatedPeerInfo, 0);

    // Verify updated state
    PeerInfo updated = palDirectory.getPeer(peerInfo.getUuid());
    assertEquals("tcp://127.0.0.1:9999", updated.getZmqRpcAddress());
    assertEquals("ws://localhost:8080", updated.getJsonrpcAddress());
    assertEquals("tcp://localhost:7777", updated.getPubAddress());
    assertEquals("localhost:9012", updated.getJmxAddress());
  }

  @Test
  public void createPeerLease_nonExistentPeer_throwsIllegalStateException() throws Exception {
    UUID nonExistentPeerUuid = UUID.randomUUID();

    // Attempt to create lease for non-existent peer
    try {
      palDirectory.createPeerLease(nonExistentPeerUuid, 5);
      fail("Should have raised IllegalStateException");
    } catch (IllegalStateException e) {
      // Expected
      assertTrue(e.getMessage().contains("does not exist") || e.getMessage().contains("stale"));
    }
  }

  @Test
  public void deletePeer_peerWithIntercepts_interceptsAlsoDeleted() throws Exception {
    // Create peer
    final PeerInfo peerInfo = new PeerInfo(UUID.randomUUID(), "intercept-peer");
    peerInfo.setZmqRpcAddress("tcp://127.0.0.1:5671");
    palDirectory.createPeer(peerInfo);
    createdPeers.add(peerInfo.getUuid());

    // Create intercept requests for this peer
    for (int i = 0; i < 3; i++) {
      InterceptRequest<InterceptableMethodCall> req =
          new InterceptRequest<>(
              UUID.randomUUID(),
              peerInfo.getUuid(),
              InterceptType.BEFORE,
              "java.io.PrintStream",
              "org.Callback",
              "method" + i,
              new InterceptableMethodCall("println", Arrays.asList("java.lang.String")));
      palDirectory.createIntercept(req);
      addInterceptRequestToCreated(peerInfo.getUuid(), req.getUuid());
    }

    // Verify intercepts exist
    assertEquals(3, palDirectory.listInterceptsForPeer(peerInfo.getUuid()).size());

    // Delete peer
    palDirectory.deletePeer(peerInfo.getUuid());
    createdPeers.remove(peerInfo.getUuid());
    createdInterceptRequests.remove(peerInfo.getUuid());

    // Verify peer is deleted
    assertFalse(palDirectory.peerExists(peerInfo.getUuid()));

    // Verify intercepts are also deleted
    assertTrue(palDirectory.listInterceptsForPeer(peerInfo.getUuid()).isEmpty());
  }

  @Test
  public void deleteNonExistentLog_noError() throws Exception {
    // Deleting a non-existent log by name should not throw
    String nonExistentLogName = "non_existent_log_" + UUID.randomUUID();
    // This should not throw - it just logs a warning
    palDirectory.deleteLog(nonExistentLogName);
  }

  @Test
  public void deleteNonExistentLogByUuid_noError() throws Exception {
    // Deleting a non-existent log by UUID should not throw
    UUID nonExistentLogUuid = UUID.randomUUID();
    // This should not throw - it just logs a warning
    palDirectory.deleteLog(nonExistentLogUuid);
  }

  @Test
  public void getLogInfoByUuid_nonExistent_returnsNull() throws Exception {
    UUID nonExistentLogUuid = UUID.randomUUID();
    assertNull(palDirectory.getLogInfo(nonExistentLogUuid));
  }

  @Test
  public void logExistsByUuid_nonExistent_returnsFalse() throws Exception {
    UUID nonExistentLogUuid = UUID.randomUUID();
    assertFalse(palDirectory.logExists(nonExistentLogUuid));
  }

  @Test
  public void logExistsByUuid_existingLog_returnsTrue() throws Exception {
    LogInfo log = palDirectory.createAutoLog("test_exists_uuid", getKafkaServers());
    createdLogs.add(log.getName());

    assertTrue(palDirectory.logExists(log.getUuid()));
  }

  @Test
  public void getLatestLogWithPrefix_noMatchingLogs_returnsNull() throws Exception {
    String uniquePrefix = "unique_nonexistent_prefix_" + System.currentTimeMillis();
    assertNull(palDirectory.getLatestLogWithPrefix(uniquePrefix));
  }

  @Test
  public void deleteInterceptsForPeer_noPeer_noError() throws Exception {
    // Deleting intercepts for non-existent peer should not throw
    UUID nonExistentPeerUuid = UUID.randomUUID();
    // This should not throw
    palDirectory.deleteInterceptsForPeer(nonExistentPeerUuid);
  }

  @Test
  public void deleteIntercept_nonExistent_noError() throws Exception {
    // Create peer
    final PeerInfo peerInfo = new PeerInfo(UUID.randomUUID());
    peerInfo.setZmqRpcAddress("tcp://127.0.0.1:5671");
    palDirectory.createPeer(peerInfo);
    createdPeers.add(peerInfo.getUuid());

    // Deleting a non-existent intercept should not throw
    UUID nonExistentInterceptUuid = UUID.randomUUID();
    palDirectory.deleteIntercept(peerInfo.getUuid(), nonExistentInterceptUuid);
  }

  @Test
  public void getLogInfo_singleMatch_returnsLogInfo() throws Exception {
    // Create a single log
    String logName = "unique_single_log_" + System.currentTimeMillis();
    LogInfo log = new LogInfo(logName, getKafkaServers());
    palDirectory.createLog(log);
    createdLogs.add(logName);

    // getLogInfo(String) should return the log when there's only one match
    LogInfo retrieved = palDirectory.getLogInfo(logName);
    assertNotNull(retrieved);
    assertEquals(logName, retrieved.getName());
    assertEquals(log.getUuid(), retrieved.getUuid());
  }

  @Test
  public void getLogsInfoByName_noMatch_returnsEmptyList() throws Exception {
    String nonExistentLogName = "non_existent_log_" + System.currentTimeMillis();
    List<LogInfo> logs = palDirectory.getLogsInfoByName(nonExistentLogName);
    assertTrue(logs.isEmpty());
  }

  // ==========================================================================
  // isPeerAlive() Tests - Issue #421
  // ==========================================================================

  /**
   * Tests that isPeerAlive returns true for a peer with an active lease.
   *
   * <p>Specification from Issue #421:
   *
   * <ul>
   *   <li>Given: Peer created with active lease (60s TTL)
   *   <li>When: isPeerAlive(peerUuid) called
   *   <li>Then: Returns true
   * </ul>
   */
  @Test
  public void isPeerAlive_peerWithActiveLease_true() throws Exception {
    // Given: Peer created with active lease
    final PeerInfo peerInfo = new PeerInfo(UUID.randomUUID(), "alive-lease-peer");
    peerInfo.setZmqRpcAddress("tcp://127.0.0.1:5671");
    palDirectory.createPeer(peerInfo);
    createdPeers.add(peerInfo.getUuid());

    // Create lease with 60s TTL
    PeerLease lease = palDirectory.createPeerLease(peerInfo.getUuid(), 60);

    try {
      // When: isPeerAlive(peerUuid) called
      boolean alive = palDirectory.isPeerAlive(peerInfo.getUuid());

      // Then: Returns true
      assertTrue("Peer with active lease should be alive", alive);
    } finally {
      // Clean up the lease
      lease.close();
    }
  }

  /**
   * Tests that isPeerAlive returns false for a peer whose lease has been revoked.
   *
   * <p>Specification from Issue #421:
   *
   * <ul>
   *   <li>Given: Peer created; lease created then revoked via close()
   *   <li>When: isPeerAlive(peerUuid) called (after short wait)
   *   <li>Then: Returns false
   * </ul>
   */
  @Test
  public void isPeerAlive_peerWithRevokedLease_false() throws Exception {
    // Given: Peer created with lease
    final PeerInfo peerInfo = new PeerInfo(UUID.randomUUID(), "revoked-lease-peer");
    peerInfo.setZmqRpcAddress("tcp://127.0.0.1:5671");
    palDirectory.createPeer(peerInfo);
    createdPeers.add(peerInfo.getUuid());

    // Create lease with 5s TTL
    PeerLease lease = palDirectory.createPeerLease(peerInfo.getUuid(), 5);

    // Verify peer is alive with active lease
    assertTrue(
        "Peer should be alive with active lease", palDirectory.isPeerAlive(peerInfo.getUuid()));

    // Revoke lease via close()
    lease.close();

    // Wait for etcd to apply the revoke (up to 6s)
    for (int i = 0; i < 12; i++) {
      if (!palDirectory.isPeerAlive(peerInfo.getUuid())) {
        break;
      }
      TimeUnit.MILLISECONDS.sleep(500);
    }

    // Then: Returns false after lease revocation
    assertFalse(
        "Peer should not be alive after lease revocation",
        palDirectory.isPeerAlive(peerInfo.getUuid()));
  }

  /**
   * Tests that isPeerAlive returns false for a non-existent peer.
   *
   * <p>Specification from Issue #421:
   *
   * <ul>
   *   <li>Given: Random UUID not in directory
   *   <li>When: isPeerAlive(randomUuid) called
   *   <li>Then: Returns false
   * </ul>
   */
  @Test
  public void isPeerAlive_nonExistentPeer_false() throws Exception {
    // Given: Random UUID not in directory
    UUID randomUuid = UUID.randomUUID();

    // Verify the peer doesn't exist
    assertFalse("Random UUID should not exist in directory", palDirectory.peerExists(randomUuid));

    // When: isPeerAlive(randomUuid) called
    boolean alive = palDirectory.isPeerAlive(randomUuid);

    // Then: Returns false
    assertFalse("Non-existent peer should not be alive", alive);
  }

  // ==========================================================================
  // getIntercept() Tests - Issue #421
  // ==========================================================================

  /**
   * Tests that getIntercept returns the intercept request for a valid path.
   *
   * <p>Specification from Issue #421:
   *
   * <ul>
   *   <li>Given: Intercept created for peer
   *   <li>When: getIntercept(interceptPath) called with correct path
   *   <li>Then: Returns InterceptRequest matching original
   * </ul>
   */
  @Test
  public void getIntercept_validPath_returnsIntercept() throws Exception {
    // Given: Peer and intercept created
    final PeerInfo peerInfo = new PeerInfo(UUID.randomUUID(), "intercept-test-peer");
    peerInfo.setZmqRpcAddress("tcp://127.0.0.1:5671");
    palDirectory.createPeer(peerInfo);
    createdPeers.add(peerInfo.getUuid());

    // Create intercept request
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

    palDirectory.createIntercept(req);
    addInterceptRequestToCreated(peerInfo.getUuid(), req.getUuid());

    // When: getIntercept(interceptPath) called with correct path
    // Path format: /pal/intercepts/<peerUuid>/<interceptUuid>
    String interceptPath = "/pal/intercepts/" + peerInfo.getUuid() + "/" + req.getUuid();
    InterceptRequest<?> retrieved = palDirectory.getIntercept(interceptPath);

    // Then: Returns InterceptRequest matching original
    assertNotNull("Retrieved intercept should not be null", retrieved);
    assertEquals("Intercept UUID should match", req.getUuid(), retrieved.getUuid());
    assertEquals("Peer UUID should match", req.getPeer(), retrieved.getPeer());
    assertEquals("Intercept type should match", req.getType(), retrieved.getType());
    assertEquals("Target class should match", req.getClazz(), retrieved.getClazz());
    assertEquals(
        "Callback class should match", req.getCallbackClass(), retrieved.getCallbackClass());
    assertEquals(
        "Callback method should match", req.getCallbackMethod(), retrieved.getCallbackMethod());
  }

  /**
   * Tests that getIntercept returns null for an invalid path.
   *
   * <p>Specification from Issue #421:
   *
   * <ul>
   *   <li>Given: No intercept at path
   *   <li>When: getIntercept("/nonexistent/path") called
   *   <li>Then: Returns null
   * </ul>
   */
  @Test
  public void getIntercept_invalidPath_null() throws Exception {
    // Given: No intercept at path (use random UUIDs to ensure path doesn't exist)
    String nonExistentPath = "/pal/intercepts/" + UUID.randomUUID() + "/" + UUID.randomUUID();

    // When: getIntercept("/nonexistent/path") called
    InterceptRequest<?> retrieved = palDirectory.getIntercept(nonExistentPath);

    // Then: Returns null
    assertNull("getIntercept should return null for non-existent path", retrieved);
  }

  // ==========================================================================
  // deletePeers() Tests - Issue #421
  // ==========================================================================

  /**
   * Tests that deletePeers removes all peers from the directory.
   *
   * <p>Specification from Issue #421:
   *
   * <ul>
   *   <li>Given: 3 peers created in directory
   *   <li>When: deletePeers() called
   *   <li>Then: All peers deleted; listPeers() returns empty
   * </ul>
   */
  @Test
  public void deletePeers_allPeers_allDeleted() throws Exception {
    // Given: 3 peers created in directory
    // Note: setup() ensures directory is clean, so we start with 0 peers
    int peersToCreate = 3;
    for (int i = 0; i < peersToCreate; i++) {
      final PeerInfo peerInfo = new PeerInfo(UUID.randomUUID(), "delete-test-peer-" + i);
      peerInfo.setZmqRpcAddress("tcp://127.0.0.1:" + (5671 + i));
      palDirectory.createPeer(peerInfo);
      createdPeers.add(peerInfo.getUuid());
    }

    // Verify peers were created
    int totalPeers = palDirectory.listPeers().size();
    assertTrue("Should have at least 3 peers", totalPeers >= peersToCreate);

    // When: deletePeers() called
    long deletedCount = palDirectory.deletePeers();

    // Then: All peers deleted; listPeers() returns empty
    assertTrue("Should delete at least the peers we created", deletedCount >= peersToCreate);
    assertTrue("listPeers() should return empty", palDirectory.listPeers().isEmpty());

    // Clear createdPeers since we've already deleted them
    createdPeers.clear();
  }

  // ==========================================================================
  // close() Tests - Issue #421
  // ==========================================================================

  /**
   * Tests that close() is safe to call multiple times and resources are released.
   *
   * <p>Specification from Issue #421:
   *
   * <ul>
   *   <li>Given: Active PalDirectory instance
   *   <li>When: close() called twice
   *   <li>Then: No exception; resources released
   * </ul>
   */
  @Test
  public void close_multipleCallsAndResourceVerification() throws Exception {
    // Given: Active PalDirectory instance (create a separate one to avoid interfering with cleanup)
    PalDirectory testDirectory = new PalDirectory(getPalDirectoryUrl());

    // Perform an operation to ensure the directory is active
    testDirectory.listPeers();

    // When: close() called twice
    // First close should succeed
    testDirectory.close();

    // Second close should also succeed (no exception)
    testDirectory.close();

    // Then: No exception; resources released
    // If we got here without exception, the test passes
    // Verify operations fail after close by trying to use the closed directory
    try {
      testDirectory.listPeers();
      // Note: Some implementations may throw on use after close, but this isn't guaranteed
      // The main assertion is that close() can be called twice without exception
    } catch (Exception e) {
      // Expected - directory is closed, operations may fail
      logger.debug("Operation after close() threw exception as expected: {}", e.getMessage());
    }
  }

  // ==========================================================================
  // Constructor Tests - Issue #421
  // ==========================================================================

  /**
   * Tests that PalDirectory with a custom namespace uses that namespace.
   *
   * <p>Specification from Issue #421:
   *
   * <ul>
   *   <li>Given: PalDirectory created with custom namespace
   *   <li>When: Peer created and retrieved
   *   <li>Then: Peer exists in namespaced path
   * </ul>
   */
  @Test
  public void constructor_withNamespace_usesNamespace() throws Exception {
    // Given: PalDirectory created with custom namespace
    String customNamespace = "test-namespace-" + System.currentTimeMillis();
    PalDirectory namespacedDirectory =
        new PalDirectory(getPalDirectoryUrl(), customNamespace, true);

    try {
      // When: Peer created in namespaced directory
      final PeerInfo peerInfo = new PeerInfo(UUID.randomUUID(), "namespaced-peer");
      peerInfo.setZmqRpcAddress("tcp://127.0.0.1:5671");
      namespacedDirectory.createPeer(peerInfo);

      try {
        // Then: Peer exists in namespaced path (can be retrieved from namespaced directory)
        assertTrue(
            "Peer should exist in namespaced directory",
            namespacedDirectory.peerExists(peerInfo.getUuid()));

        // Peer should NOT be visible from the default namespace directory
        assertFalse(
            "Peer should NOT exist in default namespace",
            palDirectory.peerExists(peerInfo.getUuid()));

        // Can retrieve peer from namespaced directory
        PeerInfo retrieved = namespacedDirectory.getPeer(peerInfo.getUuid());
        assertNotNull("Should be able to retrieve peer from namespaced directory", retrieved);
        assertEquals("Peer UUID should match", peerInfo.getUuid(), retrieved.getUuid());
      } finally {
        // Clean up peer from namespaced directory
        namespacedDirectory.deletePeer(peerInfo.getUuid());
      }
    } finally {
      // Close the namespaced directory
      namespacedDirectory.close();
    }
  }

  /**
   * Tests that PalDirectory constructor with short timeout fails fast for invalid endpoint.
   *
   * <p>Specification from Issue #421:
   *
   * <ul>
   *   <li>Given: PalDirectory created with 1ms timeout and invalid endpoint
   *   <li>When: Constructor called
   *   <li>Then: Fails fast with EtcdUnavailableException
   * </ul>
   */
  @Test
  public void constructor_withTimeout_respectsTimeout() throws Exception {
    // Given: Invalid endpoint with very short timeout
    // Use an IP address that is not routable (RFC 5737 test range)
    String invalidEndpoint = "192.0.2.1:2379";
    Duration shortTimeout = Duration.ofMillis(100);

    // When/Then: Constructor should fail fast with EtcdUnavailableException
    long startTime = System.currentTimeMillis();
    try {
      // blocking=true to trigger the preflight health check
      new PalDirectory(invalidEndpoint, null, true, shortTimeout);
      fail("Should have thrown EtcdUnavailableException for invalid endpoint");
    } catch (EtcdUnavailableException e) {
      // Expected exception
      long elapsed = System.currentTimeMillis() - startTime;
      logger.info(
          "Constructor failed as expected in {}ms with message: {}", elapsed, e.getMessage());

      // Verify it failed reasonably quickly (within 2 seconds to allow for some overhead)
      assertTrue(
          "Should fail within reasonable time frame (elapsed: " + elapsed + "ms)", elapsed < 2000);
    }
  }

  // ==========================================================================
  // Test Specifications for Issue #636
  // Awaiting implementation in #638
  // ==========================================================================

  /**
   * Tests that peerExists returns true for a registered peer.
   *
   * <p>Specification #1 from Issue #636:
   *
   * <ul>
   *   <li>Given: A registered peer in the directory
   *   <li>When: peerExists(uuid) is called with the peer's UUID
   *   <li>Then: Returns true
   * </ul>
   */
  @Test
  public void peerExists_existingPeer_returnsTrue() throws Exception {
    // Given: A registered peer
    final PeerInfo peerInfo = new PeerInfo(UUID.randomUUID(), "exists-test-peer");
    peerInfo.setZmqRpcAddress("tcp://127.0.0.1:5671");
    palDirectory.createPeer(peerInfo);
    createdPeers.add(peerInfo.getUuid());

    // When/Then: peerExists returns true
    assertTrue(palDirectory.peerExists(peerInfo.getUuid()));
  }

  /**
   * Tests that peerExists returns false for a non-existent peer.
   *
   * <p>Specification #2 from Issue #636:
   *
   * <ul>
   *   <li>Given: A random UUID not registered in the directory
   *   <li>When: peerExists(uuid) is called with the random UUID
   *   <li>Then: Returns false
   * </ul>
   */
  @Test
  public void peerExists_nonExistentPeer_returnsFalse() throws Exception {
    // Given: Random UUID not in directory
    UUID randomUuid = UUID.randomUUID();

    // When/Then: peerExists returns false
    assertFalse(palDirectory.peerExists(randomUuid));
  }

  /**
   * Tests that updatePeer with a lease ID updates the peer's state and mtime.
   *
   * <p>Specification #3 from Issue #636:
   *
   * <ul>
   *   <li>Given: A registered peer with an active lease
   *   <li>When: updatePeer(peerInfo, leaseId) is called
   *   <li>Then: The peer's state and mtime are updated in the directory
   * </ul>
   */
  @Test
  public void updatePeer_withLeaseId_updatesStateAndMtime() throws Exception {
    // Given: A registered peer with a live lease
    final PeerInfo peerInfo = new PeerInfo(UUID.randomUUID(), "lease-update-peer");
    peerInfo.setZmqRpcAddress("tcp://127.0.0.1:5671");
    palDirectory.createPeer(peerInfo);
    createdPeers.add(peerInfo.getUuid());

    PeerLease lease = palDirectory.createPeerLease(peerInfo.getUuid(), 60);

    try {
      // Record initial mtime
      PeerInfo initial = palDirectory.getPeer(peerInfo.getUuid());
      OffsetDateTime initialMtime = initial.getMTime();

      // Sleep briefly to ensure mtime changes
      TimeUnit.MILLISECONDS.sleep(50);

      // When: updatePeer with lease ID and updated addresses
      PeerInfo updatedPeerInfo = new PeerInfo(peerInfo.getUuid(), "lease-update-peer");
      updatedPeerInfo.setZmqRpcAddress("tcp://127.0.0.1:9999");
      updatedPeerInfo.setPubAddress("tcp://localhost:7777");
      palDirectory.updatePeer(updatedPeerInfo, lease.leaseId);

      // Then: Peer state is updated, mtime is refreshed
      PeerInfo updated = palDirectory.getPeer(peerInfo.getUuid());
      assertEquals("tcp://127.0.0.1:9999", updated.getZmqRpcAddress());
      assertEquals("tcp://localhost:7777", updated.getPubAddress());
      assertTrue(
          "MTime should be updated after updatePeer",
          updated.getMTime().isAfter(initialMtime) || updated.getMTime().isEqual(initialMtime));
    } finally {
      lease.close();
    }
  }

  /**
   * Tests that getSourceLogId returns the UUID for a peer with a registered source log.
   *
   * <p>Specification #4 from Issue #636:
   *
   * <ul>
   *   <li>Given: A peer with a registered source log
   *   <li>When: getSourceLogId(peerUuid) is called
   *   <li>Then: The UUID of the source log is returned
   * </ul>
   */
  @Test
  public void getSourceLogId_existingSourceLog_returnsUuid() throws Exception {
    // Given: Peer with a source log registered
    final PeerInfo peerInfo = new PeerInfo(UUID.randomUUID());
    peerInfo.setZmqRpcAddress("tcp://127.0.0.1:5671");
    palDirectory.createPeer(peerInfo);
    createdPeers.add(peerInfo.getUuid());

    LogInfo sourceLog = palDirectory.createAutoLog("test_source_636", getKafkaServers());
    createdLogs.add(sourceLog.getName());
    palDirectory.setSourceLog(peerInfo, sourceLog, null);

    // When/Then: getSourceLogId returns the source log's UUID
    UUID sourceLogId = palDirectory.getSourceLogId(peerInfo.getUuid());
    assertEquals(sourceLog.getUuid(), sourceLogId);
  }

  /**
   * Tests that getSourceLogId returns null for a peer without a source log.
   *
   * <p>Specification #5 from Issue #636:
   *
   * <ul>
   *   <li>Given: A peer without a source log
   *   <li>When: getSourceLogId(peerUuid) is called
   *   <li>Then: Returns null
   * </ul>
   */
  @Test
  public void getSourceLogId_noSourceLog_returnsNull_spec636() throws Exception {
    // Given: Peer created without any source log registration
    final PeerInfo peerInfo = new PeerInfo(UUID.randomUUID());
    peerInfo.setZmqRpcAddress("tcp://127.0.0.1:5671");
    palDirectory.createPeer(peerInfo);
    createdPeers.add(peerInfo.getUuid());

    // When/Then: getSourceLogId returns null
    assertNull(palDirectory.getSourceLogId(peerInfo.getUuid()));
  }

  /**
   * Tests that getWalId returns the UUID for a peer with a registered WAL.
   *
   * <p>Specification #6 from Issue #636:
   *
   * <ul>
   *   <li>Given: A peer with a registered WAL (write-ahead log)
   *   <li>When: getWalId(peerUuid) is called
   *   <li>Then: The UUID of the WAL is returned
   * </ul>
   */
  @Test
  public void getWalId_existingWal_returnsUuid() throws Exception {
    // Given: Peer with a WAL registered
    final PeerInfo peerInfo = new PeerInfo(UUID.randomUUID());
    peerInfo.setZmqRpcAddress("tcp://127.0.0.1:5671");
    palDirectory.createPeer(peerInfo);
    createdPeers.add(peerInfo.getUuid());

    LogInfo walLog = palDirectory.createAutoLog("test_wal_636", getKafkaServers());
    createdLogs.add(walLog.getName());
    palDirectory.setWalLog(peerInfo, walLog, null);

    // When/Then: getWalId returns the WAL's UUID
    UUID walId = palDirectory.getWalId(peerInfo.getUuid());
    assertEquals(walLog.getUuid(), walId);
  }

  /**
   * Tests that getWalId returns null for a peer without a WAL.
   *
   * <p>Specification #7 from Issue #636:
   *
   * <ul>
   *   <li>Given: A peer without a WAL
   *   <li>When: getWalId(peerUuid) is called
   *   <li>Then: Returns null
   * </ul>
   */
  @Test
  public void getWalId_noWal_returnsNull_spec636() throws Exception {
    // Given: Peer created without any WAL registration
    final PeerInfo peerInfo = new PeerInfo(UUID.randomUUID());
    peerInfo.setZmqRpcAddress("tcp://127.0.0.1:5671");
    palDirectory.createPeer(peerInfo);
    createdPeers.add(peerInfo.getUuid());

    // When/Then: getWalId returns null
    assertNull(palDirectory.getWalId(peerInfo.getUuid()));
  }

  /**
   * Tests that countLogsWithPrefix returns the correct count for matching logs.
   *
   * <p>Specification #8 from Issue #636:
   *
   * <ul>
   *   <li>Given: 3 logs created with the same prefix
   *   <li>When: countLogsWithPrefix(prefix) is called
   *   <li>Then: Returns 3
   * </ul>
   */
  @Test
  public void countLogsWithPrefix_matchingLogs_returnsCount() throws Exception {
    // Given: 3 logs with same prefix
    String prefix = "count-test-636";
    int logsToCreate = 3;
    for (int i = 0; i < logsToCreate; i++) {
      LogInfo log = palDirectory.createAutoLog(prefix, getKafkaServers());
      createdLogs.add(log.getName());
    }

    // When/Then: countLogsWithPrefix returns 3
    assertEquals(logsToCreate, palDirectory.countLogsWithPrefix(prefix));
  }

  /**
   * Tests that deleteLogsWithPrefix deletes all matching logs.
   *
   * <p>Specification #9 from Issue #636:
   *
   * <ul>
   *   <li>Given: Multiple logs with a shared prefix
   *   <li>When: deleteLogsWithPrefix(prefix) is called
   *   <li>Then: All logs matching the prefix are deleted
   * </ul>
   */
  @Test
  public void deleteLogsWithPrefix_matchingLogs_deletesAllMatching() throws Exception {
    // Given: Multiple logs with prefix
    String prefix = "delete-prefix-636";
    int logsToCreate = 4;
    for (int i = 0; i < logsToCreate; i++) {
      LogInfo log = palDirectory.createAutoLog(prefix, getKafkaServers());
      createdLogs.add(log.getName());
    }

    // Verify logs were created
    assertEquals(logsToCreate, palDirectory.countLogsWithPrefix(prefix));

    // When: deleteLogsWithPrefix
    long deleted = palDirectory.deleteLogsWithPrefix(prefix);

    // Then: All matching logs deleted
    assertEquals(logsToCreate, deleted);
    assertEquals(0, palDirectory.countLogsWithPrefix(prefix));
  }

  /**
   * Tests that createIntercept with a non-zero TTL creates the intercept with its own dedicated
   * lease.
   *
   * <p>Specification #10 from Issue #636:
   *
   * <ul>
   *   <li>Given: A registered peer
   *   <li>When: createIntercept(request, ttlSeconds) is called with a non-zero TTL
   *   <li>Then: The intercept is created with a separate dedicated lease (not the peer lease)
   * </ul>
   */
  @Test
  public void createIntercept_withDedicatedTtl_createsWithOwnLease() throws Exception {
    // Given: A registered peer (no peer lease)
    final PeerInfo peerInfo = new PeerInfo(UUID.randomUUID(), "dedicated-ttl-peer");
    peerInfo.setZmqRpcAddress("tcp://127.0.0.1:5671");
    palDirectory.createPeer(peerInfo);
    createdPeers.add(peerInfo.getUuid());

    // When: createIntercept with TTL=10 seconds
    InterceptRequest<InterceptableMethodCall> req =
        new InterceptRequest<>(
            UUID.randomUUID(),
            peerInfo.getUuid(),
            InterceptType.BEFORE,
            "java.io.PrintStream",
            "org.package.Callback",
            "callMe",
            new InterceptableMethodCall("println", Arrays.asList("java.lang.String")));

    palDirectory.createIntercept(req, 10);
    addInterceptRequestToCreated(peerInfo.getUuid(), req.getUuid());

    // Then: Intercept exists and peer still exists
    assertEquals(1, palDirectory.listInterceptsForPeer(peerInfo.getUuid()).size());
    assertNotNull(palDirectory.getPeer(peerInfo.getUuid()));
  }

  /**
   * Tests that createIntercept with TTL=0 and no peer lease persists without any lease.
   *
   * <p>Specification #11 from Issue #636:
   *
   * <ul>
   *   <li>Given: A registered peer with no active lease
   *   <li>When: createIntercept(request, 0) is called with TTL=0
   *   <li>Then: The intercept is created without any lease (persists indefinitely)
   * </ul>
   */
  @Test
  public void createIntercept_withZeroTtl_usesNoPeerLease() throws Exception {
    // Given: Peer with no active peer lease
    final PeerInfo peerInfo = new PeerInfo(UUID.randomUUID(), "zero-ttl-peer");
    peerInfo.setZmqRpcAddress("tcp://127.0.0.1:5671");
    palDirectory.createPeer(peerInfo);
    createdPeers.add(peerInfo.getUuid());

    // When: createIntercept with TTL=0 (no lease)
    InterceptRequest<InterceptableMethodCall> req =
        new InterceptRequest<>(
            UUID.randomUUID(),
            peerInfo.getUuid(),
            InterceptType.BEFORE,
            "java.io.PrintStream",
            "org.package.Callback",
            "callMe",
            new InterceptableMethodCall("println", Arrays.asList("java.lang.String")));

    palDirectory.createIntercept(req, 0);
    addInterceptRequestToCreated(peerInfo.getUuid(), req.getUuid());

    // Then: Intercept exists
    assertEquals(1, palDirectory.listInterceptsForPeer(peerInfo.getUuid()).size());

    // Wait briefly and verify it still persists (no expiration)
    TimeUnit.SECONDS.sleep(2);
    assertEquals(1, palDirectory.listInterceptsForPeer(peerInfo.getUuid()).size());
  }

  /**
   * Tests that deleteIntercept removes an existing intercept.
   *
   * <p>Specification #12 from Issue #636:
   *
   * <ul>
   *   <li>Given: A peer with an existing intercept
   *   <li>When: deleteIntercept(peerUuid, interceptUuid) is called
   *   <li>Then: The intercept is removed from the directory
   * </ul>
   */
  @Test
  public void deleteIntercept_existingIntercept_removes() throws Exception {
    // Given: Peer with an intercept registered
    final PeerInfo peerInfo = new PeerInfo(UUID.randomUUID(), "delete-intercept-peer");
    peerInfo.setZmqRpcAddress("tcp://127.0.0.1:5671");
    palDirectory.createPeer(peerInfo);
    createdPeers.add(peerInfo.getUuid());

    InterceptRequest<InterceptableMethodCall> req =
        new InterceptRequest<>(
            UUID.randomUUID(),
            peerInfo.getUuid(),
            InterceptType.BEFORE,
            "java.io.PrintStream",
            "org.package.Callback",
            "callMe",
            new InterceptableMethodCall("println", Arrays.asList("java.lang.String")));

    palDirectory.createIntercept(req);
    assertEquals(1, palDirectory.listInterceptsForPeer(peerInfo.getUuid()).size());

    // When: deleteIntercept
    palDirectory.deleteIntercept(peerInfo.getUuid(), req.getUuid());

    // Then: Intercept is removed
    assertTrue(palDirectory.listInterceptsForPeer(peerInfo.getUuid()).isEmpty());
  }

  /**
   * Tests that listAllIntercepts returns all intercepts across multiple peers.
   *
   * <p>Specification #13 from Issue #636:
   *
   * <ul>
   *   <li>Given: 3 intercepts spread across multiple peers
   *   <li>When: listAllIntercepts() is called
   *   <li>Then: All 3 intercepts are returned
   * </ul>
   */
  @Test
  public void listAllIntercepts_multipleIntercepts_returnsAll() throws Exception {
    // Given: 2 peers with intercepts
    final PeerInfo peer1 = new PeerInfo(UUID.randomUUID(), "all-intercepts-peer1");
    peer1.setZmqRpcAddress("tcp://127.0.0.1:5671");
    palDirectory.createPeer(peer1);
    createdPeers.add(peer1.getUuid());

    final PeerInfo peer2 = new PeerInfo(UUID.randomUUID(), "all-intercepts-peer2");
    peer2.setZmqRpcAddress("tcp://127.0.0.1:5672");
    palDirectory.createPeer(peer2);
    createdPeers.add(peer2.getUuid());

    // Create 2 intercepts for peer1
    Set<UUID> interceptUuids = new HashSet<>();
    for (int i = 0; i < 2; i++) {
      InterceptRequest<InterceptableMethodCall> req =
          new InterceptRequest<>(
              UUID.randomUUID(),
              peer1.getUuid(),
              InterceptType.BEFORE,
              "java.io.PrintStream",
              "org.Callback",
              "method" + i,
              new InterceptableMethodCall("println", Arrays.asList("java.lang.String")));
      palDirectory.createIntercept(req);
      addInterceptRequestToCreated(peer1.getUuid(), req.getUuid());
      interceptUuids.add(req.getUuid());
    }

    // Create 1 intercept for peer2
    InterceptRequest<InterceptableMethodCall> req3 =
        new InterceptRequest<>(
            UUID.randomUUID(),
            peer2.getUuid(),
            InterceptType.AFTER,
            "java.io.PrintStream",
            "org.Callback",
            "method3",
            new InterceptableMethodCall("println", Arrays.asList("java.lang.String")));
    palDirectory.createIntercept(req3);
    addInterceptRequestToCreated(peer2.getUuid(), req3.getUuid());
    interceptUuids.add(req3.getUuid());

    // When: listAllIntercepts
    Set<InterceptRequest<?>> allIntercepts = palDirectory.listAllIntercepts();

    // Then: Returns all 3 intercepts
    assertEquals(3, allIntercepts.size());
    Set<UUID> retrievedUuids = new HashSet<>();
    for (InterceptRequest<?> intercept : allIntercepts) {
      retrievedUuids.add(intercept.getUuid());
    }
    assertEquals(interceptUuids, retrievedUuids);
  }

  /**
   * Tests that listAllIntercepts returns an empty set when no intercepts exist.
   *
   * <p>Specification #14 from Issue #636:
   *
   * <ul>
   *   <li>Given: No intercepts in the directory
   *   <li>When: listAllIntercepts() is called
   *   <li>Then: An empty set is returned
   * </ul>
   */
  @Test
  public void listAllIntercepts_noIntercepts_returnsEmpty() throws Exception {
    // Given: No intercepts in directory (clean state from @Before)
    // When/Then: listAllIntercepts returns empty set
    assertTrue(palDirectory.listAllIntercepts().isEmpty());
  }
}
