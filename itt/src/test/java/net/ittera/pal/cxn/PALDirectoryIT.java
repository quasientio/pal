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

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.stream.Collectors;
import net.ittera.pal.common.directory.events.InterceptEvent.Type;
import net.ittera.pal.common.directory.nodes.InterceptRequest;
import net.ittera.pal.common.directory.nodes.LogInfo;
import net.ittera.pal.common.directory.nodes.PeerInfo;
import net.ittera.pal.common.lang.intercept.InterceptType;
import net.ittera.pal.common.lang.intercept.InterceptableMethodCall;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Naming convention to use: MethodName_StateUnderTest_ExpectedBehavior */
public class PALDirectoryIT {

  protected static final Logger logger = LoggerFactory.getLogger("tests");

  private static final String ETCD_ENDPOINT = "ip://localhost:2379";
  private static final String KAFKA_SERVERS = "kafka1:9092,kafka2:9094";

  private static final Set<UUID> createdPeers = new HashSet<>();
  private static final Set<String> createdLogs = new HashSet<>();
  private static Set<UUID> preExistingPeers;
  private static Set<UUID> preExistingLogs;
  private static final Map<UUID, List<UUID>> createdInterceptRequests = new HashMap<>();

  private PALDirectory palDirectory;

  @Before
  public void setup() throws Exception {
    palDirectory = new PALDirectory(ETCD_ENDPOINT);
    preExistingPeers =
        palDirectory.getAllPeers().stream().map(PeerInfo::getUuid).collect(Collectors.toSet());
    preExistingLogs =
        palDirectory.getAllLogs().stream().map(LogInfo::getUuid).collect(Collectors.toSet());
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
    for (Map.Entry<UUID, List<UUID>> entry : createdInterceptRequests.entrySet()) {
      UUID peerUuid = entry.getKey();
      List<UUID> peerIntercepts = entry.getValue();
      for (UUID interceptReq : peerIntercepts) {
        palDirectory.unregisterPeerInterceptRequest(peerUuid, interceptReq);
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
    assertThat(palDirectory.peerExists(peerUuid), is(false));
  }

  @Test
  public void registerPeer_newPeer_peerCreated() throws Exception {
    final PeerInfo peerInfo = new PeerInfo(UUID.randomUUID());
    peerInfo.setReqAddress("tcp://127.0.0.1:5671");

    // pre-assertions
    assertThat(palDirectory.peerExists(peerInfo.getUuid()), is(false));

    // register
    palDirectory.registerPeer(peerInfo);
    createdPeers.add(peerInfo.getUuid());

    // verify
    assertThat(palDirectory.peerExists(peerInfo.getUuid()), is(true));
  }

  @Test
  public void getPeerInfo_noSuchPeer_null() throws Exception {
    UUID peerUuid = UUID.randomUUID();
    assertThat(palDirectory.peerExists(peerUuid), is(false));
    assertThat(palDirectory.getPeerInfo(peerUuid), is(nullValue()));
  }

  @Test
  public void getPeerInfo_peerExists_peerInfo() throws Exception {
    final PeerInfo peerInfo = new PeerInfo(UUID.randomUUID(), "testing peer");
    peerInfo.setReqAddress("tcp://127.0.0.1:5671");
    peerInfo.setPubAddress("tcp://localhost:7777");
    peerInfo.setJmxAddress("localhost:9012");
    palDirectory.registerPeer(peerInfo);
    createdPeers.add(peerInfo.getUuid());

    // pre-assertions
    assertThat(palDirectory.peerExists(peerInfo.getUuid()), is(true));

    PeerInfo retrievedPeerInfo = palDirectory.getPeerInfo(peerInfo.getUuid());

    // verify
    assertThat(retrievedPeerInfo.getUuid(), is(peerInfo.getUuid()));
    assertThat(retrievedPeerInfo.getName(), is(peerInfo.getName()));
    assertThat(retrievedPeerInfo.getReqAddress(), is(notNullValue()));
    assertThat(retrievedPeerInfo.getPubAddress(), is(notNullValue()));
    assertThat(retrievedPeerInfo.getJmxAddress(), is(notNullValue()));
    assertThat(retrievedPeerInfo.getReqAddress(), is(peerInfo.getReqAddress()));
    assertThat(retrievedPeerInfo.getPubAddress(), is(peerInfo.getPubAddress()));
    assertThat(retrievedPeerInfo.getJmxAddress(), is(peerInfo.getJmxAddress()));

    // verify ctime and mtime (which are in UTC) are within last second
    OffsetDateTime now = OffsetDateTime.now();
    assertThat(retrievedPeerInfo.getCTime().isAfter(now.minus(1, ChronoUnit.SECONDS)), is(true));
    assertThat(retrievedPeerInfo.getCTime().isBefore(now), is(true));
    assertThat(retrievedPeerInfo.getMTime().isAfter(now.minus(1, ChronoUnit.SECONDS)), is(true));
    assertThat(retrievedPeerInfo.getMTime().isBefore(now), is(true));
  }

  @Test
  public void unregisterPeer_existingPeer_peerDeleted() throws Exception {
    // create
    final PeerInfo peerInfo = new PeerInfo(UUID.randomUUID());
    peerInfo.setReqAddress("tcp://127.0.0.1:5671");
    palDirectory.registerPeer(peerInfo);
    createdPeers.add(peerInfo.getUuid());

    // pre-assertions
    assertThat(palDirectory.peerExists(peerInfo.getUuid()), is(true));

    // unregister
    palDirectory.unregisterPeer(peerInfo.getUuid());

    // verify
    assertThat(palDirectory.peerExists(peerInfo.getUuid()), is(false));
  }

  @Test
  public void unregisterAllPeers_existingPeers_allPeersDeleted() throws Exception {

    // create
    int peersToCreate = 5;
    for (int i = 0; i < peersToCreate; i++) {
      // create a peer
      final PeerInfo peerInfo = new PeerInfo(UUID.randomUUID());
      peerInfo.setReqAddress("tcp://127.0.0.1:5671");
      palDirectory.registerPeer(peerInfo);
      createdPeers.add(peerInfo.getUuid());
    }

    // verify
    assertThat(
        palDirectory.getAllPeers().stream()
            .filter(p -> !preExistingPeers.contains(p.getUuid()))
            .collect(Collectors.toSet())
            .size(),
        is(peersToCreate));

    // unregister all - exclude pre-existing
    palDirectory.unregisterAllPeersWithExcludes(preExistingPeers);

    assertThat(palDirectory.getAllPeers().size(), is(preExistingPeers.size()));
  }

  @Test
  public void getAllPeers_noPeers_emptySet() throws Exception {
    Set<PeerInfo> allPeers = palDirectory.getAllPeers();
    // verify
    assertThat(
        allPeers.stream()
            .filter(p -> !preExistingPeers.contains(p.getUuid()))
            .collect(Collectors.toSet())
            .isEmpty(),
        is(true));
  }

  @Test
  public void getAllPeers_somePeers_nonEmptySet() throws Exception {
    int peersToCreate = 2;
    for (int i = 0; i < peersToCreate; i++) {
      // create a peer
      final PeerInfo peerInfo = new PeerInfo(UUID.randomUUID());
      peerInfo.setReqAddress("tcp://127.0.0.1:5671");
      palDirectory.registerPeer(peerInfo);
      createdPeers.add(peerInfo.getUuid());
    }

    // verify
    assertThat(
        palDirectory.getAllPeers().stream()
            .filter(p -> !preExistingPeers.contains(p.getUuid()))
            .collect(Collectors.toSet())
            .size(),
        is(peersToCreate));
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

    LogInfo newLogInfo = palDirectory.newLog(logNamePrefix, KAFKA_SERVERS);
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
    LogInfo newLogInfo = new LogInfo(logName, KAFKA_SERVERS);
    palDirectory.registerLog(newLogInfo);
    createdLogs.add(logName);

    // verify
    LogInfo retrievedLogInfo = palDirectory.getLogInfo(logName);
    assertThat(palDirectory.logExists(logName), is(true));
    assertThat(retrievedLogInfo, is(newLogInfo));
    assertThat(retrievedLogInfo.getUuid(), notNullValue());
  }

  @Test
  public void getLogInfo_noSuchLog_exception() throws Exception {
    String logName = "test.strange_topic";
    assertThat(palDirectory.logExists(logName), is(false));
    assertThat(palDirectory.getLogInfo(logName), is(nullValue()));
  }

  @Test
  public void getLogInfo_logExists_logInfo() throws Exception {
    String logName = "test.topic";

    // register logInfo
    LogInfo newLogInfo = new LogInfo(logName, KAFKA_SERVERS);
    palDirectory.registerLog(newLogInfo);
    createdLogs.add(logName);
    assertThat(palDirectory.logExists(logName), is(true));

    LogInfo retrievedLogInfo = palDirectory.getLogInfo(logName);

    // verify returned logInfo
    assertThat(retrievedLogInfo, is(newLogInfo));
    assertThat(retrievedLogInfo.getUuid(), notNullValue());

    // verify ctime and mtime (which are in UTC) are within last second
    OffsetDateTime now = OffsetDateTime.now();
    assertThat(retrievedLogInfo.getCTime().isAfter(now.minus(1, ChronoUnit.SECONDS)), is(true));
    assertThat(retrievedLogInfo.getCTime().isBefore(now), is(true));
    assertThat(retrievedLogInfo.getMTime().isAfter(now.minus(1, ChronoUnit.SECONDS)), is(true));
    assertThat(retrievedLogInfo.getMTime().isBefore(now), is(true));
  }

  @Test
  public void getAllLogs_someLogsCreated_all() throws Exception {
    String logNamePrefix = "test.topic";

    // create N logs
    int logsToCreate = 10;
    for (int i = 0; i < logsToCreate; i++) {
      LogInfo newLogInfo = palDirectory.newLog(logNamePrefix, KAFKA_SERVERS);
      createdLogs.add(newLogInfo.getName());
    }

    // verify
    assertThat(palDirectory.getAllLogs().size(), is(preExistingLogs.size() + logsToCreate));
  }

  @Test
  public void getAllLogsWithPrefix_someLogsExist_all() throws Exception {
    String logNamePrefix = "test.topic";

    // create N logs
    int logsToCreate = 10;
    for (int i = 0; i < logsToCreate; i++) {
      LogInfo newLogInfo = palDirectory.newLog(logNamePrefix, KAFKA_SERVERS);
      createdLogs.add(newLogInfo.getName());
    }

    // verify
    assertThat(palDirectory.getAllLogsWithPrefix(logNamePrefix).size(), is(logsToCreate));
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
      LogInfo newLogInfo = palDirectory.newLog(logNamePrefix, KAFKA_SERVERS);
      createdLogs.add(newLogInfo.getName());
    }
    assertThat(palDirectory.getLogCount(logNamePrefix), is(logsToCreate));
  }

  @Test
  public void getLastLog_someLogsMatch_last() throws Exception {
    String logNamePrefix = "test.topic";

    // create  a few logs
    int logsToCreate = 10;
    String lastCreated = null;
    for (int i = 0; i < logsToCreate; i++) {
      LogInfo newLogInfo = palDirectory.newLog(logNamePrefix, KAFKA_SERVERS);
      lastCreated = newLogInfo.getName();
      createdLogs.add(lastCreated);
    }

    assertThat(palDirectory.getLastLogWithPrefix(logNamePrefix).getName(), is(lastCreated));
  }

  @Test
  public void deleteAllLogs_matchingLogs_allMatchingDeleted() throws Exception {
    String logNamePrefix = "test.topic";

    // create  a few with the prefix
    int logsToCreate = 10;
    for (int i = 0; i < logsToCreate; i++) {
      LogInfo newLogInfo = palDirectory.newLog(logNamePrefix, KAFKA_SERVERS);
      createdLogs.add(newLogInfo.getName());
    }

    // create a few with another prefix
    for (int i = 0; i < 3; i++) {
      LogInfo newLogInfo = palDirectory.newLog("some.other.prefix", KAFKA_SERVERS);
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
  public void unregisterAllLogs_existingLogs_allLogsDeleted() throws Exception {
    Set<LogInfo> preExistingLogs = palDirectory.getAllLogs();
    String logNamePrefix = "test.topic";

    // create a few with the prefix
    int logsToCreate = 10;
    for (int i = 0; i < logsToCreate; i++) {
      LogInfo newLogInfo = palDirectory.newLog(logNamePrefix, KAFKA_SERVERS);
      createdLogs.add(newLogInfo.getName());
    }

    // pre-assertions
    assertThat(palDirectory.getAllLogs().size(), is(preExistingLogs.size() + logsToCreate));

    // delete all
    palDirectory.unregisterAllLogsWithExcludes(
        preExistingLogs.stream().map(LogInfo::getUuid).collect(Collectors.toSet()));

    // verify
    assertThat(palDirectory.getAllLogs().size(), is(preExistingLogs.size()));
  }

  @Test
  public void deleteLog_existingLog_logDeleted() throws Exception {
    String logNamePrefix = "test.topic";

    LogInfo newLogInfo = palDirectory.newLog(logNamePrefix, KAFKA_SERVERS);
    String createdLogName = newLogInfo.getName();
    // pre-assertions
    assertThat(palDirectory.logExists(createdLogName), is(true));

    palDirectory.unregisterLog(createdLogName);

    // verify
    assertThat(palDirectory.logExists(createdLogName), is(false));
  }

  @Test
  public void registerInterceptAsync_noSuchPeer_exception() throws Exception {
    InterceptRequest req =
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
      palDirectory.registerInterceptAsync(req);
      fail("Should have raised NoPeerInfoNodeException");
    } catch (NoPeerInfoNodeException e) {
      // ok
    }
  }

  @Test
  public void registerInterceptAsync_peerExists_registered() throws Exception {
    // create peer
    final PeerInfo peerInfo = new PeerInfo(UUID.randomUUID(), "testing peer");
    palDirectory.registerPeer(peerInfo);
    createdPeers.add(peerInfo.getUuid());

    // pre-assertions
    assertThat(palDirectory.peerExists(peerInfo.getUuid()), is(true));
    assertThat(palDirectory.getPeerInterceptRequests(peerInfo.getUuid()).size(), is(0));

    // create intercept request
    InterceptRequest req =
        new InterceptRequest<>(
            UUID.randomUUID(),
            peerInfo.getUuid(),
            InterceptType.BEFORE,
            "java.io.PrintStream",
            "org.package.Callback",
            "callMe",
            new InterceptableMethodCall(
                "println", Arrays.asList("java.lang.String", "java.lang.Integer")));

    // register it
    palDirectory.registerInterceptAsync(req).get();
    addInterceptRequestToCreated(peerInfo.getUuid(), req.getUuid());

    assertThat(palDirectory.getPeerInterceptRequests(peerInfo.getUuid()).size(), is(1));
  }

  @Test
  public void registerIntercept_peerExists_registered() throws Exception {
    // create peer
    final PeerInfo peerInfo = new PeerInfo(UUID.randomUUID(), "testing peer");
    palDirectory.registerPeer(peerInfo);
    createdPeers.add(peerInfo.getUuid());

    // pre-assertions
    assertThat(palDirectory.peerExists(peerInfo.getUuid()), is(true));
    assertThat(palDirectory.getPeerInterceptRequests(peerInfo.getUuid()).size(), is(0));

    // create intercept request
    InterceptRequest req =
        new InterceptRequest<>(
            UUID.randomUUID(),
            peerInfo.getUuid(),
            InterceptType.BEFORE,
            "java.io.PrintStream",
            "org.package.Callback",
            "callMe",
            new InterceptableMethodCall(
                "println", Arrays.asList("java.lang.String", "java.lang.Integer")));

    // register it
    palDirectory.registerIntercept(req);
    addInterceptRequestToCreated(peerInfo.getUuid(), req.getUuid());
    assertThat(palDirectory.getPeerInterceptRequests(peerInfo.getUuid()).size(), is(1));
  }

  public void unregisterIntercept_interceptExists_unregistered() throws Exception {
    // create peer
    final PeerInfo peerInfo = new PeerInfo(UUID.randomUUID(), "testing peer");
    palDirectory.registerPeer(peerInfo);
    createdPeers.add(peerInfo.getUuid());

    // pre-assertions
    assertThat(palDirectory.peerExists(peerInfo.getUuid()), is(true));
    assertThat(palDirectory.getPeerInterceptRequests(peerInfo.getUuid()).size(), is(0));

    // create intercept request
    InterceptRequest req =
        new InterceptRequest<>(
            UUID.randomUUID(),
            peerInfo.getUuid(),
            InterceptType.BEFORE,
            "java.io.PrintStream",
            "org.package.Callback",
            "callMe",
            new InterceptableMethodCall(
                "println", Arrays.asList("java.lang.String", "java.lang.Integer")));

    // register it
    palDirectory.registerIntercept(req);
    addInterceptRequestToCreated(peerInfo.getUuid(), req.getUuid());
    assertThat(palDirectory.getPeerInterceptRequests(peerInfo.getUuid()).size(), is(1));

    // now unregister
    palDirectory.unregisterPeerInterceptRequest(peerInfo.getUuid(), req.getUuid());
    assertThat(palDirectory.getPeerInterceptRequests(peerInfo.getUuid()).size(), is(0));
  }

  @Test
  public void getPeerInterceptRequests_noRequests_emptyList() throws Exception {
    final PeerInfo peerInfo = new PeerInfo(UUID.randomUUID(), "testing peer");
    palDirectory.registerPeer(peerInfo);
    createdPeers.add(peerInfo.getUuid());

    // pre-assertions
    assertThat(palDirectory.peerExists(peerInfo.getUuid()), is(true));

    assertThat(palDirectory.getPeerInterceptRequests(peerInfo.getUuid()), is(empty()));
  }

  @Test
  public void getPeerInterceptRequests_requestsExist_requestList() throws Exception {
    // create peer
    final PeerInfo peerInfo = new PeerInfo(UUID.randomUUID(), "testing peer");
    palDirectory.registerPeer(peerInfo);
    createdPeers.add(peerInfo.getUuid());

    // pre-assertions
    assertThat(palDirectory.peerExists(peerInfo.getUuid()), is(true));
    assertThat(palDirectory.getPeerInterceptRequests(peerInfo.getUuid()), is(empty()));

    // create 2 intercept requests
    Set<InterceptRequest> requests = new HashSet<>();
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
    palDirectory.addInterceptNodeListener(
        event -> {
          if (event.getType().equals(Type.INTERCEPT_ADDED)) {
            latch.countDown();
          }
        });

    // register them
    for (InterceptRequest interceptRequest : requests) {
      palDirectory.registerInterceptAsync(interceptRequest);
      addInterceptRequestToCreated(peerInfo.getUuid(), interceptRequest.getUuid());
    }

    // wait for all listener events
    latch.await();

    // now retrieve and compare
    assertThat(palDirectory.getPeerInterceptRequests(peerInfo.getUuid()), is(requests));
  }

  @Test
  public void unregisterPeerInterceptRequests_requestsExist_unregistered() throws Exception {
    // create peer
    final PeerInfo peerInfo = new PeerInfo(UUID.randomUUID(), "testing peer");
    palDirectory.registerPeer(peerInfo);
    createdPeers.add(peerInfo.getUuid());

    // pre-assertions
    assertThat(palDirectory.getPeerInterceptRequests(peerInfo.getUuid()).size(), is(0));

    // create and register some intercept requests
    final int totalPeerIntercepts = 3;
    for (int i = 0; i < totalPeerIntercepts; i++) {
      palDirectory.registerIntercept(
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

    assertThat(
        palDirectory.getPeerInterceptRequests(peerInfo.getUuid()).size(), is(totalPeerIntercepts));

    // unregister them
    palDirectory.unregisterPeerInterceptRequests(peerInfo.getUuid());
    assertThat(palDirectory.getPeerInterceptRequests(peerInfo.getUuid()).size(), is(0));
  }
}
