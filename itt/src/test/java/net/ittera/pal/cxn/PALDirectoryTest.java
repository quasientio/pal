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

import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
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

/**
 * Naming convention to use: MethodName_StateUnderTest_ExpectedBehavior TODO: Tests should not use
 * the cache -> sleep()'s introduce brittleness.
 */
public class PALDirectoryTest {

  protected static final Logger logger = LoggerFactory.getLogger("tests");

  private static final String ETCD_ENDPOINT = "ip://localhost:2379";
  private static final int CACHE_UPDATE_DELAY = 100;

  private static final Set<UUID> createdPeers = new HashSet<>();
  private static final Set<String> createdLogs = new HashSet<>();

  private PALDirectory palDirectory;

  @Before
  public void setup() throws Exception {
    palDirectory = new PALDirectory(ETCD_ENDPOINT);
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
    peerProps.put("reqAddress", "tcp://127.0.0.1:5671");

    // pre-assertions
    assertThat(palDirectory.peerExists(peerUuid), is(false));

    // register
    palDirectory.registerPeer(peerUuid, peerProps);
    Thread.sleep(CACHE_UPDATE_DELAY); // allow some time for cache to get updated
    createdPeers.add(peerUuid);

    // verify
    assertThat(palDirectory.peerExists(peerUuid), is(true));
  }

  @Test
  public void getPeerInfo_noSuchPeer_exception() throws Exception {

    UUID peerUuid = UUID.randomUUID();

    assertThat(palDirectory.peerExists(peerUuid), is(false));
    assertThat(palDirectory.getPeerInfo(peerUuid), is(null));
  }

  @Test
  public void getPeerInfo_peerExists_peerInfo() throws Exception {
    UUID peerUuid = UUID.randomUUID();
    String peerName = "testing peer";
    Properties peerProps = new Properties();
    peerProps.put("reqAddress", "tcp://127.0.0.1:5671");
    peerProps.put("pubAddress", "tcp://localhost:7777");
    peerProps.put("jmxAddress", "localhost:9012");
    peerProps.put("name", peerName);
    palDirectory.registerPeer(peerUuid, peerProps);
    Thread.sleep(CACHE_UPDATE_DELAY); // allow some time for cache to get updated
    createdPeers.add(peerUuid);

    // pre-assertions
    assertThat(palDirectory.peerExists(peerUuid), is(true));

    PeerInfo peerInfo = palDirectory.getPeerInfo(peerUuid);

    // verify
    assertThat(peerInfo.getUuid(), is(peerUuid));
    assertThat(peerInfo.getName(), is(peerName));
    assertThat(peerInfo.getReqAddress(), is(notNullValue()));
    assertThat(peerInfo.getPubAddress(), is(notNullValue()));
    assertThat(peerInfo.getJmxAddress(), is(notNullValue()));
    assertThat(peerInfo.getReqAddress(), is(peerProps.get("reqAddress")));
    assertThat(peerInfo.getPubAddress(), is(peerProps.get("pubAddress")));
    assertThat(peerInfo.getJmxAddress(), is(peerProps.get("jmxAddress")));

    // verify ctime and mtime (which are in UTC) are within last second
    OffsetDateTime now = OffsetDateTime.now();
    assertThat(peerInfo.getCTime().isAfter(now.minus(1, ChronoUnit.SECONDS)), is(true));
    assertThat(peerInfo.getCTime().isBefore(now), is(true));
    assertThat(peerInfo.getMTime().isAfter(now.minus(1, ChronoUnit.SECONDS)), is(true));
    assertThat(peerInfo.getMTime().isBefore(now), is(true));
  }

  @Test
  public void unregisterPeer_existingPeer_peerDeleted() throws Exception {

    Properties peerProps = new Properties();
    peerProps.put("reqAddress", "tcp://127.0.0.1:5671");

    // create
    UUID peerUuid = UUID.randomUUID();
    palDirectory.registerPeer(peerUuid, peerProps);
    Thread.sleep(CACHE_UPDATE_DELAY); // allow some time for cache to get updated
    createdPeers.add(peerUuid);

    // pre-assertions
    assertThat(palDirectory.peerExists(peerUuid), is(true));

    // unregister
    palDirectory.unregisterPeer(peerUuid);
    Thread.sleep(CACHE_UPDATE_DELAY); // allow some time for cache to get updated

    // verify
    assertThat(palDirectory.peerExists(peerUuid), is(false));
  }

  @Test
  public void unregisterAllPeers_existingPeers_allPeersDeleted() throws Exception {

    // create
    int peersToCreate = 5;
    for (int i = 0; i < peersToCreate; i++) {
      // create a peer
      UUID peerUuid = UUID.randomUUID();
      Properties peerProps = new Properties();
      peerProps.put("reqAddress", "tcp://127.0.0.1:5671");
      palDirectory.registerPeer(peerUuid, peerProps);
      Thread.sleep(CACHE_UPDATE_DELAY); // allow some time for cache to get updated
      createdPeers.add(peerUuid);
    }

    // verify
    assertThat(palDirectory.getAllPeers().size(), is(peersToCreate));

    // unregister all
    palDirectory.unregisterAllPeers();

    Thread.sleep(CACHE_UPDATE_DELAY); // allow some time for cache to get updated

    // verify
    assertThat(palDirectory.getAllPeers(), is(empty()));
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
      peerProps.put("reqAddress", "tcp://127.0.0.1:5671");
      palDirectory.registerPeer(peerUuid, peerProps);
      Thread.sleep(CACHE_UPDATE_DELAY); // allow some time for cache to get updated
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
    assertThat(palDirectory.logExists(logName), is(false));
    assertThat(palDirectory.getLogInfo(logName), is(null));
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

    // verify returned logInfo
    assertThat(returnedLogInfo, is(newLogInfo));

    // verify ctime and mtime (which are in UTC) are within last second
    OffsetDateTime now = OffsetDateTime.now();
    assertThat(returnedLogInfo.getCTime().isAfter(now.minus(1, ChronoUnit.SECONDS)), is(true));
    assertThat(returnedLogInfo.getCTime().isBefore(now), is(true));
    assertThat(returnedLogInfo.getMTime().isAfter(now.minus(1, ChronoUnit.SECONDS)), is(true));
    assertThat(returnedLogInfo.getMTime().isBefore(now), is(true));
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

    assertThat(palDirectory.getLastLogWithPrefix(logNamePrefix).getName(), is(lastCreated));
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
  public void deleteAllLogs_existingLogs_allLogsDeleted() throws Exception {
    String logNamePrefix = "test.topic";

    // create a few with the prefix
    int logsToCreate = 10;
    for (int i = 0; i < logsToCreate; i++) {
      LogInfo newLogInfo = palDirectory.newLog(logNamePrefix);
      createdLogs.add(newLogInfo.getName());
    }

    // pre-assertions
    assertThat(palDirectory.getAllLogs().size(), is(logsToCreate));

    // delete all
    palDirectory.unregisterAllLogs();

    // verify
    assertThat(palDirectory.getAllLogs(), is(empty()));
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
    UUID peerUuid = UUID.randomUUID();
    String peerName = "testing peer";
    Properties peerProps = new Properties();
    peerProps.put("name", peerName);
    palDirectory.registerPeer(peerUuid, peerProps);
    Thread.sleep(CACHE_UPDATE_DELAY); // allow some time for cache to get updated
    createdPeers.add(peerUuid);

    // pre-assertions
    assertThat(palDirectory.peerExists(peerUuid), is(true));
    assertThat(palDirectory.getPeerInterceptRequests(peerUuid).size(), is(0));

    // create intercept request
    InterceptRequest req =
        new InterceptRequest<>(
            UUID.randomUUID(),
            peerUuid,
            InterceptType.BEFORE,
            "java.io.PrintStream",
            "org.package.Callback",
            "callMe",
            new InterceptableMethodCall(
                "println", Arrays.asList("java.lang.String", "java.lang.Integer")));

    // register it
    palDirectory.registerInterceptAsync(req).get();

    Thread.sleep(CACHE_UPDATE_DELAY); // allow some time for cache to get updated
    assertThat(palDirectory.getPeerInterceptRequests(peerUuid).size(), is(1));
  }

  @Test
  public void registerIntercept_peerExists_registered() throws Exception {
    // create peer
    UUID peerUuid = UUID.randomUUID();
    String peerName = "testing peer";
    Properties peerProps = new Properties();
    peerProps.put("name", peerName);
    palDirectory.registerPeer(peerUuid, peerProps);
    Thread.sleep(CACHE_UPDATE_DELAY); // allow some time for cache to get updated
    createdPeers.add(peerUuid);

    // pre-assertions
    assertThat(palDirectory.peerExists(peerUuid), is(true));
    assertThat(palDirectory.getPeerInterceptRequests(peerUuid).size(), is(0));

    // create intercept request
    InterceptRequest req =
        new InterceptRequest<>(
            UUID.randomUUID(),
            peerUuid,
            InterceptType.BEFORE,
            "java.io.PrintStream",
            "org.package.Callback",
            "callMe",
            new InterceptableMethodCall(
                "println", Arrays.asList("java.lang.String", "java.lang.Integer")));

    // register it
    palDirectory.registerIntercept(req);
    Thread.sleep(CACHE_UPDATE_DELAY); // allow some time for cache to get updated
    assertThat(palDirectory.getPeerInterceptRequests(peerUuid).size(), is(1));
  }

  @Test
  public void getPeerInterceptRequests_noRequests_emptyList() throws Exception {
    // create peer
    UUID peerUuid = UUID.randomUUID();
    String peerName = "testing peer";
    Properties peerProps = new Properties();
    peerProps.put("name", peerName);
    palDirectory.registerPeer(peerUuid, peerProps);
    Thread.sleep(CACHE_UPDATE_DELAY); // allow some time for cache to get updated
    createdPeers.add(peerUuid);

    // pre-assertions
    assertThat(palDirectory.peerExists(peerUuid), is(true));

    assertThat(palDirectory.getPeerInterceptRequests(peerUuid), is(empty()));
  }

  @Test
  public void getPeerInterceptRequests_requestsExist_requestList() throws Exception {
    // create peer
    UUID peerUuid = UUID.randomUUID();
    String peerName = "testing peer";
    Properties peerProps = new Properties();
    peerProps.put("name", peerName);
    palDirectory.registerPeer(peerUuid, peerProps);
    Thread.sleep(CACHE_UPDATE_DELAY); // allow some time for cache to get updated
    createdPeers.add(peerUuid);

    // pre-assertions
    assertThat(palDirectory.peerExists(peerUuid), is(true));
    assertThat(palDirectory.getPeerInterceptRequests(peerUuid), is(empty()));

    // create 2 intercept requests
    Set<InterceptRequest> requests = new HashSet<>();
    final int totalPeerIntercepts = 2;
    for (int i = 0; i < totalPeerIntercepts; i++) {
      requests.add(
          new InterceptRequest<>(
              UUID.randomUUID(),
              peerUuid,
              InterceptType.BEFORE,
              "java.io.PrintStream",
              "org.package.Callback",
              "callMe",
              new InterceptableMethodCall(
                  "println", Arrays.asList("java.lang.String", "java.lang.Integer"))));
    }
    final CountDownLatch latch = new CountDownLatch(totalPeerIntercepts);

    // set listener
    // TODO : make this conditional, since we may be testing WITHOUT CACHING
    // if no caching, then call 'registerInterceptAsync' with a callback object
    palDirectory.addInterceptNodeListener(
        event -> {
          if (event.getType().equals(Type.INTERCEPT_ADDED)) {
            latch.countDown();
          }
        });

    // register them
    for (InterceptRequest interceptRequest : requests) {
      palDirectory.registerInterceptAsync(interceptRequest);
    }

    // wait for all listener events
    latch.await();

    // now retrieve and compare
    assertThat(palDirectory.getPeerInterceptRequests(peerUuid), is(requests));
  }

  @Test
  public void unregisterPeerInterceptRequests_requestsExist_unregistered() throws Exception {
    // create peer
    UUID peerUuid = UUID.randomUUID();
    String peerName = "testing peer";
    Properties peerProps = new Properties();
    peerProps.put("name", peerName);
    palDirectory.registerPeer(peerUuid, peerProps);
    Thread.sleep(CACHE_UPDATE_DELAY); // allow some time for cache to get updated
    createdPeers.add(peerUuid);

    // pre-assertions
    assertThat(palDirectory.getPeerInterceptRequests(peerUuid).size(), is(0));

    // create and register some intercept requests
    final int totalPeerIntercepts = 3;
    for (int i = 0; i < totalPeerIntercepts; i++) {
      palDirectory.registerIntercept(
          new InterceptRequest<>(
              UUID.randomUUID(),
              peerUuid,
              InterceptType.BEFORE,
              "java.io.PrintStream",
              "org.package.Callback",
              String.format("callMe%d", i),
              new InterceptableMethodCall(
                  "println", Arrays.asList("java.lang.String", "java.lang.Integer"))));
    }

    Thread.sleep(CACHE_UPDATE_DELAY); // allow some time for cache to get updated
    assertThat(palDirectory.getPeerInterceptRequests(peerUuid).size(), is(totalPeerIntercepts));

    // unregister them
    palDirectory.unregisterPeerInterceptRequests(peerUuid);
    Thread.sleep(CACHE_UPDATE_DELAY); // allow some time for cache to get updated
    assertThat(palDirectory.getPeerInterceptRequests(peerUuid).size(), is(0));
  }
}
