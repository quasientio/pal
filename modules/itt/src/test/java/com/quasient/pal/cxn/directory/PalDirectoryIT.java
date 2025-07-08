/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.cxn.directory;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.quasient.pal.AbstractIntegrationTest;
import com.quasient.pal.common.directory.events.InterceptEvent;
import com.quasient.pal.common.directory.nodes.InterceptRequest;
import com.quasient.pal.common.directory.nodes.LogInfo;
import com.quasient.pal.common.directory.nodes.PeerInfo;
import com.quasient.pal.common.lang.intercept.InterceptType;
import com.quasient.pal.common.lang.intercept.InterceptableMethodCall;
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
import java.util.stream.Collectors;
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
  private static Set<UUID> preExistingPeers;
  private static Set<UUID> preExistingLogs;
  private static final Map<UUID, List<UUID>> createdInterceptRequests = new HashMap<>();
  private PalDirectory palDirectory;

  @Before
  public void setup() throws Exception {
    palDirectory = new PalDirectory(getPalDirectoryUrl());
    preExistingPeers =
        palDirectory.listPeers().stream().map(PeerInfo::getUuid).collect(Collectors.toSet());
    preExistingLogs =
        palDirectory.listAllLogs().stream().map(LogInfo::getUuid).collect(Collectors.toSet());
  }

  @After
  public void cleanup() throws Exception {
    for (UUID peer : createdPeers) {
      palDirectory.deletePeer(peer);
      logger.info("Cleaned up created peer: {}", peer);
    }
    for (String log : createdLogs) {
      palDirectory.deleteLog(log);
      logger.info("Cleaned up created log: {}", log);
    }
    for (Map.Entry<UUID, List<UUID>> entry : createdInterceptRequests.entrySet()) {
      UUID peerUuid = entry.getKey();
      List<UUID> peerIntercepts = entry.getValue();
      for (UUID interceptReq : peerIntercepts) {
        palDirectory.deleteIntercept(peerUuid, interceptReq);
        logger.info("Cleaned up created intercept request: {}", interceptReq);
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
    assertEquals(
        peersToCreate,
        palDirectory.listPeers().stream()
            .filter(p -> !preExistingPeers.contains(p.getUuid()))
            .collect(Collectors.toSet())
            .size());

    // delete all - exclude pre-existing
    palDirectory.purgePeersExcept(preExistingPeers);

    assertEquals(preExistingPeers.size(), palDirectory.listPeers().size());
  }

  @Test
  public void listPeers_noPeers_emptySet() throws Exception {
    Set<PeerInfo> allPeers = palDirectory.listPeers();
    // verify
    assertTrue(
        allPeers.stream()
            .filter(p -> !preExistingPeers.contains(p.getUuid()))
            .collect(Collectors.toSet())
            .isEmpty());
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
    assertEquals(
        peersToCreate,
        palDirectory.listPeers().stream()
            .filter(p -> !preExistingPeers.contains(p.getUuid()))
            .collect(Collectors.toSet())
            .size());
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

    assertEquals(1, palDirectory.listPeers().size() - preExistingPeers.size());
    assertEquals(2, palDirectory.listAllLogs().size() - preExistingLogs.size());

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
    assertEquals(preExistingLogs.size() + logsToCreate, palDirectory.listAllLogs().size());
  }

  @Test
  public void list() throws Exception {
    String logNamePrefix = "test.topic";

    // create N logs
    int logsToCreate = 10;
    for (int i = 0; i < logsToCreate; i++) {
      LogInfo newLogInfo = palDirectory.createAutoLog(logNamePrefix, getKafkaServers());
      createdLogs.add(newLogInfo.getName());
    }

    // verify
    assertEquals(logsToCreate, palDirectory.listLogsWithPrefix(logNamePrefix).size());
  }

  @Test
  public void countLogsWithPrefix_noMatchingLogsExist_zero() throws Exception {
    String logNamePrefix = "strange.topic";
    assertEquals(0, palDirectory.countLogsWithPrefix(logNamePrefix));
  }

  @Test
  public void countLogsWithPrefix() throws Exception {
    String logNamePrefix = "test.topic";
    // create  a few logs
    int logsToCreate = 10;
    for (int i = 0; i < logsToCreate; i++) {
      LogInfo newLogInfo = palDirectory.createAutoLog(logNamePrefix, getKafkaServers());
      createdLogs.add(newLogInfo.getName());
    }
    assertEquals(logsToCreate, palDirectory.countLogsWithPrefix(logNamePrefix));
  }

  @Test
  public void getLastLog_someLogsMatch_last() throws Exception {
    String logNamePrefix = "test.topic";

    // create  a few logs
    int logsToCreate = 10;
    String lastCreated = null;
    for (int i = 0; i < logsToCreate; i++) {
      LogInfo newLogInfo = palDirectory.createAutoLog(logNamePrefix, getKafkaServers());
      lastCreated = newLogInfo.getName();
      createdLogs.add(lastCreated);
    }

    assertEquals(lastCreated, palDirectory.getLatestLogWithPrefix(logNamePrefix).getName());
  }

  @Test
  public void deleteAllLogs_matchingLogs_allMatchingDeleted() throws Exception {
    String logNamePrefix = "test.topic";

    // create  a few with the prefix
    int logsToCreate = 10;
    for (int i = 0; i < logsToCreate; i++) {
      LogInfo newLogInfo = palDirectory.createAutoLog(logNamePrefix, getKafkaServers());
      createdLogs.add(newLogInfo.getName());
    }

    // create a few with another prefix
    for (int i = 0; i < 3; i++) {
      LogInfo newLogInfo = palDirectory.createAutoLog("some.other.prefix", getKafkaServers());
      createdLogs.add(newLogInfo.getName());
    }

    // pre-assertions
    assertEquals(logsToCreate, palDirectory.countLogsWithPrefix(logNamePrefix));

    // delete with prefix
    long logsDeleted = palDirectory.deleteLogsWithPrefix(logNamePrefix);
    assertEquals(logsToCreate, logsDeleted);

    // verify
    assertEquals(0, palDirectory.countLogsWithPrefix(logNamePrefix));
  }

  @Test
  public void deleteAllLogs_existingLogs_allLogsDeleted() throws Exception {
    Set<LogInfo> preExistingLogs = palDirectory.listAllLogs();
    String logNamePrefix = "test.topic";

    // create a few with the prefix
    int logsToCreate = 10;
    for (int i = 0; i < logsToCreate; i++) {
      LogInfo newLogInfo = palDirectory.createAutoLog(logNamePrefix, getKafkaServers());
      createdLogs.add(newLogInfo.getName());
    }

    // pre-assertions
    assertEquals(preExistingLogs.size() + logsToCreate, palDirectory.listAllLogs().size());

    // delete all
    long deletedLogs =
        palDirectory.purgeLogsExcept(
            preExistingLogs.stream().map(LogInfo::getUuid).collect(Collectors.toSet()));
    assertEquals(logsToCreate, deletedLogs);

    // verify
    assertEquals(preExistingLogs.size(), palDirectory.listAllLogs().size());
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
}
