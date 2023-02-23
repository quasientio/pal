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

import io.etcd.jetcd.*;
import io.etcd.jetcd.kv.DeleteResponse;
import io.etcd.jetcd.kv.GetResponse;
import io.etcd.jetcd.kv.PutResponse;
import io.etcd.jetcd.options.DeleteOption;
import io.etcd.jetcd.options.GetOption;
import io.etcd.jetcd.options.WatchOption;
import io.etcd.jetcd.watch.WatchEvent;
import io.etcd.jetcd.watch.WatchResponse;
import java.lang.reflect.Field;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnsupportedCharsetException;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import net.ittera.pal.common.directory.events.InterceptEvent;
import net.ittera.pal.common.directory.events.InterceptNodeListener;
import net.ittera.pal.common.directory.nodes.InfoNode;
import net.ittera.pal.common.directory.nodes.InterceptRequest;
import net.ittera.pal.common.directory.nodes.LogInfo;
import net.ittera.pal.common.directory.nodes.PeerInfo;
import net.ittera.pal.common.util.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PALDirectory implements AutoCloseable {

  private static final Logger logger = LoggerFactory.getLogger(PALDirectory.class);

  // constant to denote pal directory URL is not provided
  public static final String NO_URL = "<none>";

  // root paths
  private static final String DEFAULT_PAL_NAMESPACE = "pal";
  private static final String PEERS_DIR = "peers";
  private static final String LOGS_DIR = "logs";
  private static final String INTERCEPTS_DIR = "intercepts";

  private static final String KEYVALUE_SEP = "|";
  private static Charset loadedCharset;

  private final String directoryUrl;
  private final Client client;
  private static final Duration ETCD_KEEP_ALIVE_TIME_SECS = Duration.of(30, ChronoUnit.SECONDS);
  private final KV kvClient;
  private final Watch watchClient;
  private final String namespace;
  private final List<InterceptNodeListener> interceptListeners = new ArrayList<>();

  public PALDirectory(String connectionString) {
    this(connectionString, null);
  }

  public PALDirectory(List<URI> endpoints) {
    this(endpoints.stream().map(URI::toString).collect(Collectors.joining(",")), null);
  }

  public PALDirectory(String endpoints, String namespace) {
    this.directoryUrl = endpoints;
    logger.info("Will connect to etcd endpoints: {}", endpoints);
    this.client =
        Client.builder()
            .target(endpoints)
            //            .keepaliveTime(ETCD_KEEP_ALIVE_TIME_SECS)
            //            .keepaliveWithoutCalls(true)
            .build();
    this.kvClient = client.getKVClient();
    this.watchClient = client.getWatchClient();
    this.namespace = namespace != null ? namespace : DEFAULT_PAL_NAMESPACE;

    // TODO is this required now with etcd??
    try {
      createSubPaths();
    } catch (Exception e) {
      logger.error("Error creating subpaths", e);
    }

    watchClient.watch(
        getInterceptsPathKey(),
        WatchOption.newBuilder().isPrefix(true).build(),
        this::interceptEventConsumer);
  }

  // <editor-fold desc="Peer methods">
  public boolean peerExists(UUID peerUuid) throws ExecutionException, InterruptedException {
    return kvClient
            .get(ByteSequence.from(getPeerPath(peerUuid).getBytes(getEncodingCharset())))
            .get()
            .getCount()
        != 0;
  }

  public PutResponse registerPeer(UUID peerUuid, Properties peerProperties)
      throws ExecutionException, InterruptedException {
    if (peerExists(peerUuid)) {
      logger.info(
          "Skipping registration of existing peer with uuid: {} and properties: {}",
          peerUuid,
          peerProperties);
      return null;
    } else {
      final Instant now = Instant.now();
      peerProperties.setProperty("ctime", String.valueOf(now.toEpochMilli()));
      peerProperties.setProperty("mtime", String.valueOf(now.toEpochMilli()));
      final ByteSequence peerData = propertiesToByteSequence(peerProperties);
      final PutResponse putResponse =
          kvClient
              .put(
                  ByteSequence.from(getPeerPath(peerUuid).getBytes(getEncodingCharset())), peerData)
              .get();
      logger.info("Registered peer with uuid: {} and properties: {}", peerUuid, peerProperties);
      return putResponse;
    }
  }

  private static void setFieldValueUnlessNull(Object target, String fieldName, Object value) {
    if (value != null) {
      try {
        Field field = PeerInfo.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
        if (logger.isDebugEnabled()) {
          logger.debug("Reflectively set field: {} with value: {}", field, value);
        }
      } catch (IllegalAccessException | NoSuchFieldException e) {
        logger.error("Error setting field value in object", e);
      }
    }
  }

  public PeerInfo getPeerInfo(UUID peerUuid) throws ExecutionException, InterruptedException {
    if (!peerExists(peerUuid)) {
      logger.warn("Node for peer w/uuid: {} does not exist", peerUuid);
      return null;
    }

    final Properties props = getProperties(getPeerPath(peerUuid));
    final PeerInfo peerInfo = new PeerInfo(peerUuid);
    // set bean fields reflectively
    Stream.of("name", "reqAddress", "pubAddress", "jmxAddress")
        .forEach(fldName -> setFieldValueUnlessNull(peerInfo, fldName, props.getProperty(fldName)));

    fillTimeValuesFromProperties(peerInfo, props);
    return peerInfo;
  }

  public Set<PeerInfo> getAllPeers() throws ExecutionException, InterruptedException {
    final GetResponse response =
        kvClient.get(getPeersPathKey(), GetOption.newBuilder().isPrefix(true).build()).get();
    final Set<PeerInfo> allPeers = new TreeSet<>();
    for (KeyValue kv : response.getKvs()) {
      // skip root peers path
      if (kv.getKey().equals(getPeersPathKey())) {
        continue;
      }
      final String peerUuid =
          Strings.stringAfterLast(kv.getKey().toString(getEncodingCharset()), "/");
      PeerInfo peerInfo = getPeerInfo(UUID.fromString(peerUuid));
      if (peerInfo != null) {
        allPeers.add(peerInfo);
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
          kvClient
              .delete(getPeersPathKey(), DeleteOption.newBuilder().isPrefix(true).build())
              .get();
      deleted = deleteResponse.getDeleted();
    }
    logger.info("Unregistered {} peers", deleted);
    return deleted;
  }

  public long unregisterAllPeers() throws ExecutionException, InterruptedException {
    return unregisterAllPeersWithExcludes(null);
  }

  public DeleteResponse unregisterPeer(UUID peerUuid)
      throws ExecutionException, InterruptedException {
    DeleteResponse deleteResponse =
        kvClient
            .delete(ByteSequence.from(getPeerPath(peerUuid).getBytes(getEncodingCharset())))
            .get();
    if (deleteResponse.getDeleted() == 1) {
      logger.info("Unregistered peer with uuid: {}", peerUuid);
    } else {
      logger.info("Could not unregister peer with uuid: {}, peer does not exist!", peerUuid);
    }
    return deleteResponse;
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
        Arrays.stream(path.split("\\/")).filter(s -> s.length() > 0).toArray(String[]::new);
    if (parts.length == 4) {
      try {
        final UUID peerUuid = UUID.fromString(parts[2]);
        final UUID interceptUuid = UUID.fromString(parts[3]);
        final byte[] data = event.getKeyValue().getValue().getBytes();
        return new InterceptEvent(
            type,
            path,
            peerUuid,
            interceptUuid,
            InterceptRequest.fromBytes(data, getEncodingCharset()));
      } catch (IllegalArgumentException e) {
        logger.warn("Invalid UUID or unexpected path of len=4: {}", path);
      }
    }
    return null;
  }

  private void interceptEventConsumer(WatchResponse watchResponse) {
    for (WatchEvent event : watchResponse.getEvents()) {
      switch (event.getEventType()) {
        case PUT:
        case DELETE:
          if (logger.isDebugEnabled()) {
            logger.debug(
                "New intercepts {} -> key:{} - value:{}",
                event.getEventType().name(),
                event.getKeyValue().getKey().toString(),
                event.getKeyValue().getValue().toString());
          }
          final InterceptEvent interceptEvent = createInterceptEvent(event);
          if (interceptEvent != null) {
            interceptListeners.forEach(l -> l.interceptEvent(interceptEvent));
          }
          break;
        case UNRECOGNIZED:
          logger.warn(
              "New intercepts UNRECOGNIZED event -> key:{} - value:{}",
              event.getKeyValue().getKey().toString(),
              event.getKeyValue().getValue().toString());
      }
    }
  }

  public PutResponse registerIntercept(InterceptRequest interceptRequest)
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
    final PutResponse putResponse =
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
    return putResponse;
  }

  public CompletableFuture<PutResponse> registerInterceptAsync(InterceptRequest interceptRequest)
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

  public Set<InterceptRequest> getPeerInterceptRequests(UUID peerUuid)
      throws ExecutionException, InterruptedException {
    final Set<InterceptRequest> interceptRequests = new HashSet<>();
    final String peerInterceptsPath = getInterceptsPathForPeer(peerUuid);
    final ByteSequence peerInterceptsPathKey =
        ByteSequence.from(peerInterceptsPath.getBytes(getEncodingCharset()));
    final GetResponse response =
        kvClient.get(peerInterceptsPathKey, GetOption.newBuilder().isPrefix(true).build()).get();
    for (KeyValue kv : response.getKvs()) {
      final String interceptPath = kv.getKey().toString(getEncodingCharset());
      interceptRequests.add(getInterceptRequest(interceptPath));
    }
    return interceptRequests;
  }

  public InterceptRequest getInterceptRequest(String interceptPath)
      throws ExecutionException, InterruptedException {
    final byte[] data;
    List<KeyValue> kvs =
        kvClient
            .get(ByteSequence.from(interceptPath.getBytes(getEncodingCharset())))
            .get()
            .getKvs();
    if (kvs.size() == 0) {
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

  public DeleteResponse unregisterPeerInterceptRequests(UUID peerUuid)
      throws ExecutionException, InterruptedException {
    final String peerInterceptsPath = getInterceptsPathForPeer(peerUuid);
    final DeleteResponse deleteResponse =
        kvClient
            .delete(
                ByteSequence.from(peerInterceptsPath.getBytes(getEncodingCharset())),
                DeleteOption.newBuilder().isPrefix(true).build())
            .get();
    logger.info(
        "Unregistered {} intercept request(s) for peer w/uuid: {}",
        deleteResponse.getDeleted(),
        peerUuid);
    return deleteResponse;
  }

  public DeleteResponse unregisterPeerInterceptRequest(UUID peerUuid, UUID interceptRequestUuid)
      throws ExecutionException, InterruptedException {
    final String peerInterceptsPath = getInterceptsPathForPeer(peerUuid);
    final DeleteResponse deleteResponse =
        kvClient
            .delete(
                ByteSequence.from(
                    format("%s/%s", peerInterceptsPath, interceptRequestUuid.toString())
                        .getBytes(getEncodingCharset())))
            .get();
    logger.info(
        "Unregistered intercept request w/uuid: {} for peer w/uuid: {}",
        interceptRequestUuid,
        peerUuid);
    return deleteResponse;
  }

  // </editor-fold>

  // <editor-fold desc="Log methods">
  public LogInfo registerLog(String logName, String logServers)
      throws ExecutionException, InterruptedException {
    Objects.requireNonNull(logName, logServers);
    if (!logExists(logName)) {
      UUID newLogUuid = UUID.randomUUID();
      final Properties logProperties = new Properties();
      logProperties.setProperty("uuid", newLogUuid.toString());
      logProperties.setProperty("servers", logServers);
      final Instant now = Instant.now();
      logProperties.setProperty("ctime", String.valueOf(now.toEpochMilli()));
      logProperties.setProperty("mtime", String.valueOf(now.toEpochMilli()));
      final ByteSequence logKey =
          ByteSequence.from(getLogPath(logName).getBytes(getEncodingCharset()));
      kvClient.put(logKey, propertiesToByteSequence(logProperties)).get();
      logger.info("Registered given log node: {} with uuid: {}", logName, newLogUuid);
    } else {
      logger.info("Skipping registration of existing log with name: {}", logName);
    }
    return getLogInfo(logName);
  }

  public LogInfo newLog(String logNamePrefix, String logServers)
      throws ExecutionException, InterruptedException {
    Objects.requireNonNull(logNamePrefix, logServers);
    final UUID newLogUuid = UUID.randomUUID();
    final LogInfo lastLogWithPrefix = getLastLogWithPrefix(logNamePrefix);
    final String newLogName;

    // generate name of new log with given prefix and monotonically increasing counter
    if (lastLogWithPrefix == null) {
      newLogName = String.format("%s%010d", logNamePrefix, 1);
    } else {
      String lastLogIdxStr = Strings.stringAfter(lastLogWithPrefix.getName(), logNamePrefix);
      newLogName = String.format("%s%010d", logNamePrefix, Long.parseLong(lastLogIdxStr) + 1);
    }

    // create the KV entry
    final ByteSequence newLogKey =
        ByteSequence.from(getLogPath(newLogName).getBytes(getEncodingCharset()));
    final Properties logProperties = new Properties();
    logProperties.setProperty("uuid", newLogUuid.toString());
    logProperties.setProperty("servers", logServers);
    final Instant now = Instant.now();
    logProperties.setProperty("ctime", String.valueOf(now.toEpochMilli()));
    logProperties.setProperty("mtime", String.valueOf(now.toEpochMilli()));
    kvClient.put(newLogKey, propertiesToByteSequence(logProperties)).get();
    LogInfo newLogInfo = getLogInfo(newLogName);
    logger.info("Created new log: {} with uuid: {}", newLogName, newLogUuid);
    return newLogInfo;
  }

  public LogInfo getLogInfo(String logName) throws ExecutionException, InterruptedException {
    if (!logExists(logName)) {
      return null;
    }
    final Properties props = getProperties(getLogPath(logName));
    final UUID uuid = UUID.fromString(props.getProperty("uuid"));
    final String logServers = props.getProperty("servers");

    final LogInfo logInfo = new LogInfo(logName, uuid, logServers);
    fillTimeValuesFromProperties(logInfo, props);
    return logInfo;
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
                GetOption.newBuilder()
                    .withSortField(GetOption.SortTarget.CREATE)
                    .withSortOrder(GetOption.SortOrder.ASCEND)
                    .isPrefix(true)
                    .build())
            .get();
    final Set<LogInfo> logs = new TreeSet<>();
    for (KeyValue kv : getResponse.getKvs()) {
      final String logName =
          Strings.stringAfterLast(kv.getKey().toString(getEncodingCharset()), "/");
      logs.add(getLogInfo(logName));
    }
    if (logger.isDebugEnabled()) {
      logger.debug("returning from getAllLogsWithPrefix: {}", logs);
    }
    return logs;
  }

  public Set<LogInfo> getAllLogs() throws ExecutionException, InterruptedException {
    final GetResponse getResponse =
        kvClient.get(getLogsPathKey(), GetOption.newBuilder().isPrefix(true).build()).get();
    final Set<LogInfo> allLogs = new TreeSet<>();
    for (KeyValue kv : getResponse.getKvs()) {
      // skip root logs path
      if (kv.getKey().equals(getLogsPathKey())) {
        continue;
      }
      final String logName =
          Strings.stringAfterLast(kv.getKey().toString(getEncodingCharset()), "/");
      allLogs.add(getLogInfo(logName));
    }
    if (logger.isDebugEnabled()) {
      logger.debug("returning from getAllLogs: {}", allLogs);
    }
    return allLogs;
  }

  private Set<String> getAllLogNames() throws ExecutionException, InterruptedException {
    return getAllLogs().stream().map(LogInfo::getName).collect(Collectors.toSet());
  }

  public LogInfo getLastLogWithPrefix(String logNamePrefix)
      throws ExecutionException, InterruptedException {
    final List<String> logNames =
        getAllLogsWithPrefix(logNamePrefix).stream()
            .map(LogInfo::getName)
            .collect(Collectors.toList());
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

  public DeleteResponse unregisterLog(String logName)
      throws ExecutionException, InterruptedException {
    DeleteResponse deleteResp =
        kvClient
            .delete(ByteSequence.from(getLogPath(logName).getBytes(getEncodingCharset())))
            .get();
    if (deleteResp.getDeleted() == 1) {
      logger.info("Unregistered (i.e. deleted) log: {}", logName);
    } else {
      logger.info("Could not unregister log with name: {}, peer does not exist!", logName);
    }
    return deleteResp;
  }

  public long unregisterLogs(String logNamePrefix) throws ExecutionException, InterruptedException {
    final DeleteResponse deleteResponse =
        kvClient
            .delete(
                ByteSequence.from(
                    format("%s/%s", getLogsPath(), logNamePrefix).getBytes(getEncodingCharset())),
                DeleteOption.newBuilder().isPrefix(true).build())
            .get();

    long deleted = deleteResponse.getDeleted();
    logger.info("Unregistered {} logs with prefix: {}", deleted, logNamePrefix);
    return deleted;
  }

  public long unregisterAllLogsWithExcludes(@Nullable Set<UUID> excludeLogs)
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
          kvClient.delete(getLogsPathKey(), DeleteOption.newBuilder().isPrefix(true).build()).get();
      deleted = deleteResponse.getDeleted();
    }
    logger.info("Unregistered {} logs", deleted);
    return deleted;
  }

  public long unregisterAllLogs() throws ExecutionException, InterruptedException {
    return unregisterAllLogsWithExcludes(null);
  }
  // </editor-fold>

  // <editor-fold desc="Misc methods">
  public void close() {
    logger.info("Closing etcd client to {}", directoryUrl);
    kvClient.close();
    client.close();
  }
  // </editor-fold>

  // <editor-fold desc="private helpers">
  private void fillTimeValuesFromProperties(InfoNode infoNode, Properties properties) {
    if (properties.containsKey("ctime")) {
      infoNode.setCtime(Instant.ofEpochMilli(Long.parseLong(properties.getProperty("ctime"))));
    }
    if (properties.containsKey("mtime")) {
      infoNode.setMtime(Instant.ofEpochMilli(Long.parseLong(properties.getProperty("mtime"))));
    }
  }

  private Properties getProperties(String node) throws ExecutionException, InterruptedException {
    final byte[] data;
    final ByteSequence dataKey = ByteSequence.from(node.getBytes(getEncodingCharset()));
    data = kvClient.get(dataKey).get().getKvs().get(0).getValue().getBytes();
    Properties properties = new Properties();
    if (data != null) {
      String nodeData = new String(data, getEncodingCharset());
      String[] lines = nodeData.split("\n");
      for (String line : lines) {
        String key = Strings.stringBefore(line, KEYVALUE_SEP);
        String value = Strings.stringAfter(line, KEYVALUE_SEP);
        properties.setProperty(key, value);
      }
    }
    if (logger.isDebugEnabled()) {
      logger.debug("Returning loaded properties for node: {}, \n{}", node, properties);
    }
    return properties;
  }

  private ByteSequence propertiesToByteSequence(Properties properties) {
    byte[] data = null;
    if (properties != null && !properties.isEmpty()) {
      StringBuilder sb = new StringBuilder();
      int propIdx = 1;
      for (String propKey : properties.stringPropertyNames()) {
        sb.append(propKey.trim())
            .append(KEYVALUE_SEP)
            .append(properties.getProperty(propKey).trim());
        if (propIdx++ < properties.size()) {
          sb.append('\n');
        }
      }
      data = sb.toString().getBytes(getEncodingCharset());
    }
    if (data != null) {
      return ByteSequence.from(data);
    }
    return null;
  }

  private static Charset getEncodingCharset() {
    if (loadedCharset == null) {
      try {
        loadedCharset = StandardCharsets.UTF_8;
      } catch (UnsupportedCharsetException e) {
        logger.warn("No UTF-8 available for encoding/decoding. Falling back to default charset", e);
        loadedCharset = Charset.defaultCharset();
      }
    }
    return loadedCharset;
  }

  private String getPeerPath(UUID peerUuid) {
    return format("%s/%s", getPeersPath(), peerUuid);
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

  private void createSubPaths() {
    Stream.of(getLogsPath(), getPeersPath(), getInterceptsPath())
        .forEach(
            (path) -> {
              final ByteSequence key = ByteSequence.from(path.getBytes(getEncodingCharset()));
              try {
                if (kvClient.get(key).get().getCount() == 0) {
                  final ByteSequence value = ByteSequence.from("".getBytes(getEncodingCharset()));
                  kvClient.put(key, value).get();
                }
              } catch (Exception e) {
                throw new RuntimeException(e);
              }
            });
  }
  // </editor-fold>
}
