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

import static java.lang.String.format;

import io.etcd.jetcd.ByteSequence;
import io.etcd.jetcd.Client;
import io.etcd.jetcd.KV;
import io.etcd.jetcd.KeyValue;
import io.etcd.jetcd.Watch;
import io.etcd.jetcd.kv.DeleteResponse;
import io.etcd.jetcd.kv.GetResponse;
import io.etcd.jetcd.kv.PutResponse;
import io.etcd.jetcd.maintenance.StatusResponse;
import io.etcd.jetcd.options.DeleteOption;
import io.etcd.jetcd.options.GetOption;
import io.etcd.jetcd.options.WatchOption;
import io.etcd.jetcd.watch.WatchEvent;
import io.etcd.jetcd.watch.WatchResponse;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.ittera.pal.common.directory.events.InterceptEvent;
import net.ittera.pal.common.directory.events.InterceptNodeListener;
import net.ittera.pal.common.directory.nodes.InterceptRequest;
import net.ittera.pal.common.directory.nodes.LogInfo;
import net.ittera.pal.common.directory.nodes.PeerInfo;
import net.ittera.pal.common.util.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PalDirectory implements AutoCloseable {

  private static final Logger logger = LoggerFactory.getLogger(PalDirectory.class);

  // etcd client keepalive settings
  private static final Duration ETCD_KEEP_ALIVE_TIME = Duration.ofSeconds(60);
  private static final Duration ETCD_KEEP_ALIVE_TIMEOUT = Duration.ofSeconds(20);

  // constant to denote pal directory URL is not provided
  public static final String NO_URL = "<none>";

  // root paths
  private static final String DEFAULT_PAL_NAMESPACE = "pal";
  private static final String PEERS_DIR = "peers";
  private static final String LOGS_DIR = "logs";
  private static final String INTERCEPTS_DIR = "intercepts";

  private static Charset loadedCharset;

  private final String directoryUrl;
  private final Client client;
  private final KV kvClient;
  private final String namespace;
  private final List<InterceptNodeListener> interceptListeners = new ArrayList<>();

  public PalDirectory(String connectionString) {
    this(connectionString, null, false);
  }

  public PalDirectory(String connectionString, boolean blocking) {
    this(connectionString, null, blocking);
  }

  public PalDirectory(List<URI> endpoints) {
    this(endpoints.stream().map(URI::toString).collect(Collectors.joining(",")), null);
  }

  public PalDirectory(String endpoints, String namespace, boolean blocking) {
    this.directoryUrl = endpoints;
    logger.info("Will connect to etcd endpoints: {}", endpoints);
    this.client =
        Client.builder()
            .target(endpoints)
            .keepaliveTime(ETCD_KEEP_ALIVE_TIME)
            .keepaliveTimeout(ETCD_KEEP_ALIVE_TIMEOUT)
            .keepaliveWithoutCalls(true)
            .build();

    if (blocking) {
      // perform a status check to block until connected
      try {
        // block until the etcd cluster responds with status
        StatusResponse status = client.getMaintenanceClient().statusMember(endpoints).get();
        logger.info("Connected to etcd cluster: {}", status);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt(); // Restore the interrupted status
        throw new RuntimeException("Thread was interrupted while connecting to etcd", e);
      } catch (ExecutionException e) {
        throw new RuntimeException("Failed to connect to etcd cluster", e.getCause());
      }
    }

    this.kvClient = client.getKVClient();
    Watch watchClient = client.getWatchClient();
    this.namespace = namespace != null ? namespace : DEFAULT_PAL_NAMESPACE;

    watchClient.watch(
        getInterceptsPathKey(),
        WatchOption.builder().isPrefix(true).build(),
        this::interceptEventConsumer);
  }

  public PalDirectory(String endpoints, String namespace) {
    this(endpoints, namespace, false);
  }

  public String getDirectoryUrl() {
    return directoryUrl;
  }

  // <editor-fold desc="Peer methods">
  public boolean peerExists(UUID peerUuid) throws ExecutionException, InterruptedException {
    return kvClient
            .get(ByteSequence.from(getPeerPath(peerUuid).getBytes(getEncodingCharset())))
            .get()
            .getCount()
        != 0;
  }

  public void registerPeer(PeerInfo peerInfo) throws ExecutionException, InterruptedException {
    if (peerExists(peerInfo.getUuid())) {
      logger.warn("Skipping registration of existing peer with uuid: {}", peerInfo.getUuid());
      return;
    }
    final Instant now = Instant.now();
    if (peerInfo.getCTime() == null) {
      peerInfo.setCtime(now.toEpochMilli());
    }
    if (peerInfo.getMTime() == null) {
      peerInfo.setMtime(now.toEpochMilli());
    }
    final ByteSequence peerKey =
        ByteSequence.from(getPeerPath(peerInfo.getUuid()).getBytes(getEncodingCharset()));
    final ByteSequence peerData =
        ByteSequence.from(peerInfo.toJson().getBytes(getEncodingCharset()));
    kvClient.put(peerKey, peerData).get();
    logger.info("Registered peer w/uuid: {}, {}", peerInfo.getUuid(), peerInfo);
  }

  public void registerPeerInLog(PeerInfo peerInfo, LogInfo logInfo)
      throws ExecutionException, InterruptedException, NoPeerInfoNodeException {
    if (!peerExists(peerInfo.getUuid())) {
      throw new NoPeerInfoNodeException(
          format("Peer w/uuid %s does not exist", peerInfo.getUuid()));
    }
    final String peerLogsInPath = getPeerLogsInPath(peerInfo.getUuid());
    final ByteSequence peerLogsInPathKey =
        ByteSequence.from(peerLogsInPath.getBytes(getEncodingCharset()));
    final ByteSequence logData =
        ByteSequence.from(logInfo.getUuid().toString().getBytes(getEncodingCharset()));
    kvClient.put(peerLogsInPathKey, logData).get();
    logger.info(
        "Registered IN log w/name: {} for peer w/uuid: {}", logInfo.getName(), peerInfo.getUuid());
  }

  public UUID getPeerInLog(UUID peerUuid) throws ExecutionException, InterruptedException {
    final String peerLogsInPath = getPeerLogsInPath(peerUuid);
    final ByteSequence peerLogsInPathKey =
        ByteSequence.from(peerLogsInPath.getBytes(getEncodingCharset()));
    final GetResponse response =
        kvClient.get(peerLogsInPathKey, GetOption.builder().isPrefix(true).build()).get();
    List<KeyValue> kvs = response.getKvs();
    if (kvs.isEmpty()) {
      return null;
    }
    if (kvs.size() > 1) {
      throw new IllegalStateException("More than one in-log found for peer w/uuid: " + peerUuid);
    }
    return UUID.fromString(kvs.get(0).getValue().toString(getEncodingCharset()));
  }

  public void registerPeerOutLog(PeerInfo peerInfo, LogInfo logInfo)
      throws ExecutionException, InterruptedException, NoPeerInfoNodeException {
    if (!peerExists(peerInfo.getUuid())) {
      throw new NoPeerInfoNodeException(
          format("Peer w/uuid %s does not exist", peerInfo.getUuid()));
    }
    final String peerLogsOutPath = getPeerLogsOutPath(peerInfo.getUuid());
    final ByteSequence peerLogsOutPathKey =
        ByteSequence.from(peerLogsOutPath.getBytes(getEncodingCharset()));
    final ByteSequence logData =
        ByteSequence.from(logInfo.getUuid().toString().getBytes(getEncodingCharset()));
    kvClient.put(peerLogsOutPathKey, logData).get();
    logger.info(
        "Registered OUT log w/name: {} for peer w/uuid: {}", logInfo.getName(), peerInfo.getUuid());
  }

  public UUID getPeerOutLog(UUID peerUuid) throws ExecutionException, InterruptedException {
    final String peerLogsOutPath = getPeerLogsOutPath(peerUuid);
    final ByteSequence peerLogsOutPathKey =
        ByteSequence.from(peerLogsOutPath.getBytes(getEncodingCharset()));
    final GetResponse response =
        kvClient.get(peerLogsOutPathKey, GetOption.builder().isPrefix(true).build()).get();
    List<KeyValue> kvs = response.getKvs();
    if (kvs.isEmpty()) {
      return null;
    }
    if (kvs.size() > 1) {
      throw new IllegalStateException("More than one out-log found for peer w/uuid: " + peerUuid);
    }
    return UUID.fromString(kvs.get(0).getValue().toString(getEncodingCharset()));
  }

  public PeerInfo getPeerInfo(UUID peerUuid) throws ExecutionException, InterruptedException {
    final GetResponse getResponse =
        kvClient.get(ByteSequence.from(getPeerPath(peerUuid).getBytes(getEncodingCharset()))).get();
    if (getResponse.getCount() == 0) {
      logger.warn("Node for peer w/uuid: {} does not exist", peerUuid);
      return null;
    }
    return PeerInfo.fromJson(getResponse.getKvs().get(0).getValue().toString(getEncodingCharset()));
  }

  public Set<PeerInfo> getAllPeers() throws ExecutionException, InterruptedException {
    final GetResponse response =
        kvClient.get(getPeersPathKey(), GetOption.builder().isPrefix(true).build()).get();
    final Set<PeerInfo> allPeers = new TreeSet<>();
    for (KeyValue kv : response.getKvs()) {
      // skip root peers path
      if (kv.getKey().equals(getPeersPathKey())) {
        continue;
      }
      // only add peers, not logs inside the peer's path
      String keyPath = kv.getKey().toString(getEncodingCharset());
      long slashCount = keyPath.chars().filter(ch -> ch == '/').count();
      if (slashCount == 3) {
        allPeers.add(PeerInfo.fromJson(kv.getValue().toString(getEncodingCharset())));
      }
    }
    return allPeers;
  }

  public long unregisterAllPeersWithExcludes(@Nullable Set<UUID> excludePeers)
      throws ExecutionException, InterruptedException {
    long deleted = 0;
    if (excludePeers != null && !excludePeers.isEmpty()) {
      for (UUID peerUuid :
          getAllPeers().stream().map(PeerInfo::getUuid).collect(Collectors.toSet())) {
        if (!excludePeers.contains(peerUuid)) {
          unregisterPeer(peerUuid);
          deleted++;
        }
      }
    } else {
      final DeleteResponse deleteResponse =
          kvClient.delete(getPeersPathKey(), DeleteOption.builder().isPrefix(true).build()).get();
      deleted = deleteResponse.getDeleted();
    }
    if (deleted == 0) {
      logger.warn("No peers found to unregister");
    } else {
      logger.info("Unregistered {} peers", deleted);
    }
    return deleted;
  }

  public long unregisterAllPeers() throws ExecutionException, InterruptedException {
    return unregisterAllPeersWithExcludes(null);
  }

  public void unregisterPeer(UUID peerUuid) throws ExecutionException, InterruptedException {
    DeleteResponse deleteResponse =
        kvClient
            .delete(
                ByteSequence.from(getPeerPath(peerUuid).getBytes(getEncodingCharset())),
                DeleteOption.builder().isPrefix(true).build())
            .get();
    if (deleteResponse.getDeleted() == 1) {
      logger.info("Unregistered peer with uuid: {}", peerUuid);
    } else {
      logger.warn("Could not unregister peer with uuid: {}, peer does not exist.", peerUuid);
    }
  }

  // </editor-fold>

  // <editor-fold desc="Intercept request methods">
  private InterceptEvent createInterceptEvent(WatchEvent event) {
    final InterceptEvent.Type type;
    switch (event.getEventType()) {
      case PUT:
        type = InterceptEvent.Type.INTERCEPT_ADDED;
        break;
      case DELETE:
        type = InterceptEvent.Type.INTERCEPT_REMOVED;
        break;
      default:
        logger.error("Unexpected event of type: {}", event.getEventType().name());
        return null;
    }
    String path = event.getKeyValue().getKey().toString(getEncodingCharset());
    String[] parts =
        Arrays.stream(path.split("/")).filter(s -> !s.isEmpty()).toArray(String[]::new);
    if (parts.length == 4) {
      try {
        final UUID peerUuid = UUID.fromString(parts[2]);
        final String interceptId = parts[3];
        final byte[] data = event.getKeyValue().getValue().getBytes();
        final InterceptRequest<?> interceptRequest;
        logger.debug(
            "Creating intercept event from path: '{}' with value: '{}'",
            path,
            new String(data, getEncodingCharset()));
        if (type == InterceptEvent.Type.INTERCEPT_ADDED) {
          interceptRequest = InterceptRequest.fromBytes(data, getEncodingCharset());
        } else {
          interceptRequest = null;
        }
        return new InterceptEvent(type, path, peerUuid, interceptId, interceptRequest);
      } catch (IllegalArgumentException e) {
        logger.warn("Invalid UUID or unexpected path of len=4: {}", path, e);
      }
    }
    return null;
  }

  private void interceptEventConsumer(WatchResponse watchResponse) {
    for (WatchEvent event : watchResponse.getEvents()) {
      switch (event.getEventType()) {
        case PUT, DELETE -> {
          if (logger.isDebugEnabled()) {
            logger.debug(
                "New intercept event {} -> key:{} - value:{}",
                event.getEventType().name(),
                event.getKeyValue().getKey().toString(),
                event.getKeyValue().getValue().toString());
          }
          final InterceptEvent interceptEvent = createInterceptEvent(event);
          if (interceptEvent != null) {
            interceptListeners.forEach(l -> l.interceptEvent(interceptEvent));
          }
        }
        case UNRECOGNIZED ->
            logger.warn(
                "New intercepts UNRECOGNIZED event -> key:{} - value:{}",
                event.getKeyValue().getKey().toString(),
                event.getKeyValue().getValue().toString());
        default ->
            throw new IllegalStateException("Unexpected event type: " + event.getEventType());
      }
    }
  }

  public void registerIntercept(InterceptRequest<?> interceptRequest)
      throws ExecutionException, InterruptedException, NoPeerInfoNodeException {
    if (!peerExists(interceptRequest.getPeer())) {
      throw new NoPeerInfoNodeException(
          format("Peer w/uuid %s does not exist", interceptRequest.getPeer()));
    }
    final byte[] interceptData = interceptRequest.toBytes(getEncodingCharset());
    final String interceptPath =
        format(
            "%s/%s",
            getInterceptsPathForPeer(interceptRequest.getPeer()), interceptRequest.getUuid());
    kvClient
        .put(
            ByteSequence.from(interceptPath.getBytes(getEncodingCharset())),
            ByteSequence.from(interceptData))
        .get();
    if (logger.isDebugEnabled()) {
      logger.debug(
          "created new node for intercept request: {} at path: {}",
          interceptRequest,
          interceptPath);
    }
  }

  public CompletableFuture<PutResponse> registerInterceptAsync(InterceptRequest<?> interceptRequest)
      throws NoPeerInfoNodeException, ExecutionException, InterruptedException {
    if (!peerExists(interceptRequest.getPeer())) {
      throw new NoPeerInfoNodeException(
          format("Peer w/uuid %s does not exist", interceptRequest.getPeer()));
    }
    final byte[] interceptData = interceptRequest.toBytes(getEncodingCharset());
    final String interceptPath =
        format(
            "%s/%s",
            getInterceptsPathForPeer(interceptRequest.getPeer()), interceptRequest.getUuid());
    return kvClient.put(
        ByteSequence.from(interceptPath.getBytes(getEncodingCharset())),
        ByteSequence.from(interceptData));
  }

  public Set<InterceptRequest<?>> getPeerInterceptRequests(UUID peerUuid)
      throws ExecutionException, InterruptedException {
    final Set<InterceptRequest<?>> interceptRequests = new HashSet<>();
    final String peerInterceptsPath = getInterceptsPathForPeer(peerUuid);
    final ByteSequence peerInterceptsPathKey =
        ByteSequence.from(peerInterceptsPath.getBytes(getEncodingCharset()));
    final GetResponse response =
        kvClient.get(peerInterceptsPathKey, GetOption.builder().isPrefix(true).build()).get();
    for (KeyValue kv : response.getKvs()) {
      final String interceptPath = kv.getKey().toString(getEncodingCharset());
      interceptRequests.add(getInterceptRequest(interceptPath));
    }
    return interceptRequests;
  }

  public Set<InterceptRequest<?>> getAllInterceptRequests() {
    final Set<InterceptRequest<?>> interceptRequests = new HashSet<>();
    try {
      final GetResponse response =
          kvClient.get(getInterceptsPathKey(), GetOption.builder().isPrefix(true).build()).get();
      for (KeyValue kv : response.getKvs()) {
        final String interceptPath = kv.getKey().toString(getEncodingCharset());
        interceptRequests.add(getInterceptRequest(interceptPath));
      }
    } catch (ExecutionException | InterruptedException e) {
      logger.error("Error getting all intercept requests", e);
    }
    return interceptRequests;
  }

  public InterceptRequest<?> getInterceptRequest(String interceptPath)
      throws ExecutionException, InterruptedException {
    final byte[] data;
    List<KeyValue> kvs =
        kvClient
            .get(ByteSequence.from(interceptPath.getBytes(getEncodingCharset())))
            .get()
            .getKvs();
    if (kvs.isEmpty()) {
      return null;
    }
    data = kvs.get(0).getValue().getBytes();
    return InterceptRequest.fromBytes(data, getEncodingCharset());
  }

  public void addInterceptNodeListener(InterceptNodeListener listener) {
    interceptListeners.add(listener);
    if (logger.isDebugEnabled()) {
      logger.debug("Added intercept node listener of class: {}", listener.getClass().getName());
    }
  }

  public void unregisterPeerInterceptRequests(UUID peerUuid)
      throws ExecutionException, InterruptedException {
    if (logger.isDebugEnabled()) {
      logger.debug("Unregistering all intercept requests for peer w/uuid: {}", peerUuid);
    }
    final String peerInterceptsPath = getInterceptsPathForPeer(peerUuid);
    final DeleteResponse deleteResponse =
        kvClient
            .delete(
                ByteSequence.from(peerInterceptsPath.getBytes(getEncodingCharset())),
                DeleteOption.builder().isPrefix(true).build())
            .get();
    if (deleteResponse.getDeleted() == 0) {
      logger.warn("No intercept requests found for peer w/uuid: {}", peerUuid);
    } else {
      logger.info(
          "Unregistered {} intercept request(s) for peer w/uuid: {}",
          deleteResponse.getDeleted(),
          peerUuid);
    }
  }

  public void unregisterPeerInterceptRequest(UUID peerUuid, UUID interceptRequestUuid)
      throws ExecutionException, InterruptedException {
    final String peerInterceptsPath = getInterceptsPathForPeer(peerUuid);
    final DeleteResponse deleteResponse =
        kvClient
            .delete(
                ByteSequence.from(
                    format("%s/%s", peerInterceptsPath, interceptRequestUuid.toString())
                        .getBytes(getEncodingCharset())))
            .get();
    if (deleteResponse.getDeleted() == 0) {
      logger.warn(
          "No intercept request w/uuid: {} found for peer w/uuid: {}",
          interceptRequestUuid,
          peerUuid);
    } else {
      logger.info(
          "Unregistered intercept request w/uuid: {} for peer w/uuid: {}",
          interceptRequestUuid,
          peerUuid);
    }
  }

  // </editor-fold>

  // <editor-fold desc="Log methods">
  public void registerLog(LogInfo logInfo) throws ExecutionException, InterruptedException {
    Objects.requireNonNull(logInfo, logInfo.getBootstrapServers());
    GetResponse getResponse =
        kvClient
            .get(ByteSequence.from(getLogPath(logInfo.getName()).getBytes(getEncodingCharset())))
            .get();
    if (getResponse.getCount() != 0) {
      logger.info("Skipping registration of existing log with name: {}", logInfo.getName());
      return;
    }
    if (logInfo.getUuid() == null) {
      logInfo.setUuid(UUID.randomUUID());
    }
    final Instant now = Instant.now();
    if (logInfo.getCTime() == null) {
      logInfo.setCtime(now.toEpochMilli());
    }
    if (logInfo.getMTime() == null) {
      logInfo.setMtime(now.toEpochMilli());
    }
    final ByteSequence logKey =
        ByteSequence.from(getLogPath(logInfo.getName()).getBytes(getEncodingCharset()));
    final ByteSequence logData = ByteSequence.from(logInfo.toJson().getBytes(getEncodingCharset()));
    kvClient.put(logKey, logData).get();
    logger.info(
        "Registered given log node: {} with uuid: {}", logInfo.getName(), logInfo.getUuid());
  }

  public LogInfo newLog(String logNamePrefix, String logServers)
      throws ExecutionException, InterruptedException {
    Objects.requireNonNull(logNamePrefix, logServers);
    final LogInfo lastLogWithPrefix = getLastLogWithPrefix(logNamePrefix);

    // generate name of new log with given prefix and monotonically increasing counter
    final String newLogName;
    if (lastLogWithPrefix == null) {
      newLogName = format("%s%010d", logNamePrefix, 1);
    } else {
      String lastLogIdxStr = Strings.stringAfter(lastLogWithPrefix.getName(), logNamePrefix);
      newLogName = format("%s%010d", logNamePrefix, Long.parseLong(lastLogIdxStr) + 1);
    }
    // create and save new LogInfo
    final LogInfo newLogInfo = new LogInfo(newLogName);
    newLogInfo.setUuid(UUID.randomUUID());
    newLogInfo.setBootstrapServers(logServers);
    final Instant now = Instant.now();
    newLogInfo.setCtime(now.toEpochMilli());
    newLogInfo.setMtime(now.toEpochMilli());
    final ByteSequence newLogKey =
        ByteSequence.from(getLogPath(newLogName).getBytes(getEncodingCharset()));
    final ByteSequence newLogData =
        ByteSequence.from(newLogInfo.toJson().getBytes(getEncodingCharset()));
    kvClient.put(newLogKey, newLogData).get();
    logger.info("Created new log: {} with uuid: {}", newLogName, newLogInfo.getUuid());
    return newLogInfo;
  }

  public LogInfo getLogInfo(String logName) throws ExecutionException, InterruptedException {
    final GetResponse getResponse =
        kvClient.get(ByteSequence.from(getLogPath(logName).getBytes(getEncodingCharset()))).get();
    if (getResponse.getCount() == 0) {
      logger.warn("Node for log w/name: {} does not exist", logName);
      return null;
    }
    return LogInfo.fromJson(getResponse.getKvs().get(0).getValue().toString(getEncodingCharset()));
  }

  public boolean logExists(String logName) throws ExecutionException, InterruptedException {
    return kvClient
            .get(ByteSequence.from(getLogPath(logName).getBytes(getEncodingCharset())))
            .get()
            .getCount()
        != 0;
  }

  public Set<LogInfo> getAllLogsWithPrefix(String logNamePrefix)
      throws ExecutionException, InterruptedException {
    final GetResponse getResponse =
        kvClient
            .get(
                ByteSequence.from(
                    format("%s/%s", getLogsPath(), logNamePrefix).getBytes(getEncodingCharset())),
                GetOption.builder()
                    .withSortField(GetOption.SortTarget.CREATE)
                    .withSortOrder(GetOption.SortOrder.ASCEND)
                    .isPrefix(true)
                    .build())
            .get();
    final Set<LogInfo> logs = new TreeSet<>();
    for (KeyValue kv : getResponse.getKvs()) {
      logs.add(LogInfo.fromJson(kv.getValue().toString(getEncodingCharset())));
    }
    if (logger.isDebugEnabled()) {
      logger.debug("returning from getAllLogsWithPrefix: {}", logs);
    }
    return logs;
  }

  public Set<LogInfo> getAllLogs() throws ExecutionException, InterruptedException {
    final GetResponse getResponse =
        kvClient.get(getLogsPathKey(), GetOption.builder().isPrefix(true).build()).get();
    final Set<LogInfo> allLogs = new TreeSet<>();
    for (KeyValue kv : getResponse.getKvs()) {
      // skip root logs path
      if (kv.getKey().equals(getLogsPathKey())) {
        continue;
      }
      allLogs.add(LogInfo.fromJson(kv.getValue().toString(getEncodingCharset())));
    }
    if (logger.isDebugEnabled()) {
      logger.debug("returning from getAllLogs: {}", allLogs);
    }
    return allLogs;
  }

  public LogInfo getLastLogWithPrefix(String logNamePrefix)
      throws ExecutionException, InterruptedException {
    final List<String> logNames =
        getAllLogsWithPrefix(logNamePrefix).stream().map(LogInfo::getName).toList();
    long maxLogIndex = -1;
    String lastLog = null;
    for (String log : logNames) {
      // parse index in log names and set max
      String logIdxStr = Strings.stringAfter(log, logNamePrefix);
      long logIdx = Long.parseLong(logIdxStr);
      if (logIdx > maxLogIndex) {
        maxLogIndex = logIdx;
        lastLog = log;
      }
    }
    logger.info("With prefix '{}' got last log = {}", logNamePrefix, lastLog);
    return getLogInfo(lastLog);
  }

  public int getLogCount(String logNamePrefix) throws ExecutionException, InterruptedException {
    return getAllLogsWithPrefix(logNamePrefix).size();
  }

  public void unregisterLog(String logName) throws ExecutionException, InterruptedException {
    LogInfo logToUnregister = getLogInfo(logName);
    if (logToUnregister == null) {
      logger.warn("Cannot unregister log with name: {}, log does not exist.", logName);
      return;
    }
    // verify that no peer is using the log
    for (PeerInfo peer : getAllPeers()) {
      boolean isLogUsed =
          Stream.of(getPeerInLog(peer.getUuid()), getPeerOutLog(peer.getUuid()))
              .anyMatch(logUuid -> logToUnregister.getUuid().equals(logUuid));
      if (isLogUsed) {
        throw new IllegalArgumentException(
            format(
                "Cannot unregister log with name: %s, peer w/uuid: %s is using it",
                logName, peer.getUuid()));
      }
    }

    DeleteResponse deleteResponse =
        kvClient
            .delete(ByteSequence.from(getLogPath(logName).getBytes(getEncodingCharset())))
            .get();
    if (deleteResponse.getDeleted() == 1) {
      logger.info("Unregistered (i.e. deleted) log: {}", logName);
    } else {
      logger.warn("Could not unregister log with name: {}, log does not exist.", logName);
    }
  }

  long unregisterLogs(String logNamePrefix) throws ExecutionException, InterruptedException {
    final DeleteResponse deleteResponse =
        kvClient
            .delete(
                ByteSequence.from(
                    format("%s/%s", getLogsPath(), logNamePrefix).getBytes(getEncodingCharset())),
                DeleteOption.builder().isPrefix(true).build())
            .get();

    long deleted = deleteResponse.getDeleted();
    if (deleted == 0) {
      logger.warn("No logs found to unregister with prefix: {}", logNamePrefix);
    } else {
      logger.info("Unregistered {} logs with prefix: {}", deleted, logNamePrefix);
    }
    return deleted;
  }

  long unregisterAllLogsWithExcludes(@Nullable Set<UUID> excludeLogs)
      throws ExecutionException, InterruptedException {
    long deleted = 0;
    if (excludeLogs != null && !excludeLogs.isEmpty()) {
      for (LogInfo log : getAllLogs()) {
        if (!excludeLogs.contains(log.getUuid())) {
          unregisterLog(log.getName());
          deleted++;
        }
      }
    } else {
      DeleteResponse deleteResponse =
          kvClient.delete(getLogsPathKey(), DeleteOption.builder().isPrefix(true).build()).get();
      deleted = deleteResponse.getDeleted();
    }
    if (deleted == 0) {
      logger.warn("No logs found to unregister");
    } else {
      logger.info("Unregistered {} logs", deleted);
    }
    return deleted;
  }

  // </editor-fold>

  // <editor-fold desc="Misc methods">
  @Override
  public void close() {
    kvClient.close();
    client.close();
    logger.info("Closed directory {}", directoryUrl);
  }

  // </editor-fold>

  // <editor-fold desc="private helpers">
  private static Charset getEncodingCharset() {
    if (loadedCharset == null) {
      loadedCharset = StandardCharsets.UTF_8;
    }
    return loadedCharset;
  }

  private String getPeerPath(UUID peerUuid) {
    return format("%s/%s", getPeersPath(), peerUuid);
  }

  private String getPeerLogsInPath(UUID peerUuid) {
    return format("%s/logs/in", getPeerPath(peerUuid));
  }

  private String getPeerLogsOutPath(UUID peerUuid) {
    return format("%s/logs/out", getPeerPath(peerUuid));
  }

  private String getLogPath(String logName) {
    return format("%s/%s", getLogsPath(), logName);
  }

  private String getPeersPath() {
    return format("/%s/%s", namespace, PEERS_DIR);
  }

  private ByteSequence getPeersPathKey() {
    return ByteSequence.from(getPeersPath().getBytes(getEncodingCharset()));
  }

  private String getLogsPath() {
    return format("/%s/%s", namespace, LOGS_DIR);
  }

  private ByteSequence getLogsPathKey() {
    return ByteSequence.from(getLogsPath().getBytes(getEncodingCharset()));
  }

  private String getInterceptsPath() {
    return format("/%s/%s", namespace, INTERCEPTS_DIR);
  }

  private ByteSequence getInterceptsPathKey() {
    return ByteSequence.from(getInterceptsPath().getBytes(getEncodingCharset()));
  }

  private String getInterceptsPathForPeer(UUID peerUuid) {
    return format("%s/%s", getInterceptsPath(), peerUuid);
  }
  // </editor-fold>
}
