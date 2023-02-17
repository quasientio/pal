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
import io.etcd.jetcd.kv.DeleteResponse;
import io.etcd.jetcd.kv.GetResponse;
import io.etcd.jetcd.kv.PutResponse;
import io.etcd.jetcd.options.DeleteOption;
import io.etcd.jetcd.options.GetOption;
import java.lang.reflect.Field;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnsupportedCharsetException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.ittera.pal.common.directory.events.InterceptNodeListener;
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
  private final KV kvClient;
  private final String namespace;
  private Map<Object, Object> peersCache;
  private Map<Object, Object> interceptsCache;
  private ExecutorService cachingExecutor;
  private final List<InterceptNodeListener> interceptListeners = new ArrayList<>();

  public PALDirectory(String connectionString) {
    this(connectionString, null, false);
  }

  public PALDirectory(List<URI> endpoints) {
    this(endpoints.stream().map(URI::toString).collect(Collectors.joining(",")), null, false);
  }

  public PALDirectory(String endpoints, String namespace, boolean withCaching) {
    this.directoryUrl = endpoints;
    logger.info("Will connect to etcd endpoints: {}", endpoints);
    this.client = Client.builder().target(endpoints).build();
    this.kvClient = client.getKVClient();
    this.namespace = namespace != null ? namespace : DEFAULT_PAL_NAMESPACE;

    // TODO is this required now with etcd??
    try {
      createSubPaths();
    } catch (Exception e) {
      logger.error("Error creating subpaths", e);
    }
    // build caches
    if (withCaching) {
      logger.warn("Caching is disabled (missing etcd implementation)");
      throw new UnsupportedOperationException("Caching not implemented");
    }
  }

  public String getDirectoryUrl() {
    return directoryUrl;
  }

  // <editor-fold desc="Peer methods">
  public boolean peerExists(UUID peerUuid) throws ExecutionException, InterruptedException {
    if (peersCache == null) {
      return kvClient
              .get(ByteSequence.from(getPeerPath(peerUuid).getBytes(getEncodingCharset())))
              .get()
              .getCount()
          != 0;
    } else {
      // TODO
      throw new UnsupportedOperationException("Caching not implemented");
    }
  }

  public PutResponse registerPeer(UUID peerUuid, Properties peerProperties)
      throws ExecutionException, InterruptedException {
    final ByteSequence peerData = peerPropsToByteSequence(peerProperties);
    if (peerExists(peerUuid)) {
      logger.info(
          "Skipping registration of existing peer with uuid: {} and properties: {}",
          peerUuid,
          peerProperties);
      return null;
    } else {
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

    final Properties props = getProperties(getPeerPath(peerUuid), peersCache);
    final PeerInfo peerInfo = new PeerInfo(peerUuid);
    // set bean fields reflectively
    Stream.of("name", "reqAddress", "pubAddress", "jmxAddress")
        .forEach(fldName -> setFieldValueUnlessNull(peerInfo, fldName, props.getProperty(fldName)));

    // TODO fill time values
    return peerInfo;
  }

  public Set<PeerInfo> getAllPeers() throws ExecutionException, InterruptedException {
    final GetResponse response =
        kvClient
            .get(
                getPeersPathKey(),
                GetOption.newBuilder()
                    .withSortField(GetOption.SortTarget.CREATE)
                    .withSortOrder(GetOption.SortOrder.ASCEND)
                    .isPrefix(true)
                    .build())
            .get();
    final Set<PeerInfo> allPeers = new TreeSet<>();
    for (KeyValue kv : response.getKvs()) {
      final String peerUuid = Strings.stringAfterLast(kv.getValue().toString(), "/");
      // TODO allPeers.add(PeerInfo.fromJSON(kv.getValue().toString()));
      PeerInfo peerInfo = getPeerInfo(UUID.fromString(peerUuid));
      if (peerInfo != null) {
        allPeers.add(peerInfo);
      }
    }
    return allPeers;
  }

  public DeleteResponse unregisterAllPeers() throws ExecutionException, InterruptedException {
    final DeleteResponse deleteResponse =
        kvClient.delete(getPeersPathKey(), DeleteOption.newBuilder().isPrefix(true).build()).get();
    logger.info("Unregistered {} peers", deleteResponse.getDeleted());
    return deleteResponse;
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
    if (interceptsCache == null) {
      final GetResponse response =
          kvClient.get(peerInterceptsPathKey, GetOption.newBuilder().isPrefix(true).build()).get();
      for (KeyValue kv : response.getKvs()) {
        final String interceptPath =
            format("%s/%s", peerInterceptsPath, kv.getKey().toString(getEncodingCharset()));
        interceptRequests.add(getInterceptRequest(interceptPath));
      }
    } else {
      throw new UnsupportedOperationException("Caching not implemented");
    }
    return interceptRequests;
  }

  public InterceptRequest getInterceptRequest(String interceptPath)
      throws ExecutionException, InterruptedException {
    final byte[] data;
    if (interceptsCache == null) {
      data =
          kvClient
              .get(ByteSequence.from(interceptPath.getBytes(getEncodingCharset())))
              .get()
              .getKvs()
              .get(0)
              .getValue()
              .getBytes();
    } else {
      throw new UnsupportedOperationException("Caching not implemented");
    }
    if (data != null) {
      return InterceptRequest.fromBytes(data, getEncodingCharset());
    } else {
      return null;
    }
  }

  public void addInterceptNodeListener(InterceptNodeListener listener) {
    interceptListeners.add(listener);
  }

  public DeleteResponse unregisterPeerInterceptRequests(UUID peerUuid) throws Exception {
    final String peerInterceptsPath = getInterceptsPathForPeer(peerUuid);
    if (interceptsCache == null) {
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
    } else {
      throw new UnsupportedOperationException("Caching not implemented");
    }
  }

  // </editor-fold>

  // <editor-fold desc="Log methods">
  public LogInfo registerLog(String logName) throws Exception {
    UUID newLogUuid = UUID.randomUUID();
    if (!logExists(logName)) {
      final ByteSequence logKey =
          ByteSequence.from(getLogPath(logName).getBytes(getEncodingCharset()));
      kvClient.put(logKey, logPropsToByteSequence(newLogUuid)).get();
      logger.info("Registered given log node: {} with uuid: {}", logName, newLogUuid);
    } else {
      logger.info(
          "Skipping registration of existing log with name: {} and uuid: {}", logName, newLogUuid);
    }
    return getLogInfo(logName);
  }

  public LogInfo newLog(String logNamePrefix) throws Exception {
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
    kvClient.put(newLogKey, logPropsToByteSequence(newLogUuid)).get();
    LogInfo newLogInfo = getLogInfo(newLogName);
    logger.info("Created new log node: {} with uuid: {}", newLogName, newLogUuid);
    return newLogInfo;
  }

  public LogInfo getLogInfo(String logName) throws ExecutionException, InterruptedException {
    if (!logExists(logName)) {
      return null;
    }
    final Properties props = getProperties(getLogPath(logName), null);
    final UUID uuid = UUID.fromString(props.getProperty("uuid"));

    // TODO fill time values
    return new LogInfo(logName, uuid);
  }

  public boolean logExists(String logName) throws ExecutionException, InterruptedException {
    return kvClient
            .get(ByteSequence.from(getLogPath(logName).getBytes(getEncodingCharset())))
            .get()
            .getCount()
        != 0;
  }

  public Set<LogInfo> getAllLogs() throws Exception {
    final GetResponse getResponse =
        kvClient
            .get(
                getLogsPathKey(),
                GetOption.newBuilder()
                    .withSortField(GetOption.SortTarget.CREATE)
                    .withSortOrder(GetOption.SortOrder.ASCEND)
                    .isPrefix(true)
                    .build())
            .get();
    final Set<LogInfo> allLogs = new TreeSet<>();
    for (KeyValue kv : getResponse.getKvs()) {
      final String logName = Strings.stringAfterLast(kv.getValue().toString(), "/");
      // TODO allPeers.add(PeerInfo.fromJSON(kv.getValue().toString()));
      allLogs.add(getLogInfo(logName));
    }
    return allLogs;
  }

  private Set<String> getAllLogNames() throws Exception {
    final GetResponse getResponse =
        kvClient
            .get(
                getLogsPathKey(),
                GetOption.newBuilder()
                    .withSortField(GetOption.SortTarget.CREATE)
                    .withSortOrder(GetOption.SortOrder.ASCEND)
                    .isPrefix(true)
                    .build())
            .get();
    final Set<String> allLogNames = new TreeSet<>();
    for (KeyValue kv : getResponse.getKvs()) {
      final String logName = Strings.stringAfterLast(kv.getValue().toString(), "/");
      allLogNames.add(logName);
    }
    return allLogNames;
  }

  public LogInfo getLastLogWithPrefix(String logNamePrefix) throws Exception {
    final List<String> logNames =
        getAllLogNames().stream()
            .filter(l -> l.startsWith(logNamePrefix))
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

  public int getLogCount(String logNamePrefix) throws Exception {
    return (int) getAllLogNames().stream().filter(l -> l.startsWith(logNamePrefix)).count();
  }

  public DeleteResponse unregisterLog(String logName) throws Exception {
    DeleteResponse deleteResp =
        kvClient
            .delete(ByteSequence.from(getLogPath(logName).getBytes(getEncodingCharset())))
            .get();
    if (deleteResp.getDeleted() == 1) {
      logger.info("Unregistered (i.e. deleted) log node: {}", logName);
    } else {
      logger.info("Could not unregister log with name: {}, peer does not exist!", logName);
    }
    return deleteResp;
  }

  public int unregisterLogs(String logNamePrefix) throws Exception {
    final List<String> logNames =
        getAllLogNames().stream()
            .filter(l -> l.startsWith(logNamePrefix))
            .collect(Collectors.toList());

    int deleted = 0;
    for (String logName : logNames) {
      unregisterLog(logName);
      deleted++;
    }
    logger.info("Unregistered {} logs with prefix: {}", deleted, logNamePrefix);
    return deleted;
  }

  public DeleteResponse unregisterAllLogs() throws Exception {
    DeleteResponse deleteResponse =
        kvClient.delete(getLogsPathKey(), DeleteOption.newBuilder().isPrefix(true).build()).get();
    logger.info("Unregistered {} logs", deleteResponse.getDeleted());
    return deleteResponse;
  }
  // </editor-fold>

  // <editor-fold desc="Misc methods">
  public void close() {
    if (cachingExecutor != null) {
      cachingExecutor.shutdown();
      try {
        cachingExecutor.awaitTermination(500, TimeUnit.MILLISECONDS);
      } catch (InterruptedException e) {
        logger.warn("Error waiting for caching executor service to terminate", e);
      }
    }
    if (peersCache != null) {
      peersCache.clear();
    }
    if (interceptsCache != null) {
      interceptsCache.clear();
    }
    logger.info("Closing etcd client to {}", directoryUrl);
    kvClient.close();
    client.close();
  }
  // </editor-fold>

  // <editor-fold desc="private helpers">
  private Properties getProperties(String node, Map<Object, Object> cache)
      throws ExecutionException, InterruptedException {
    final byte[] data;
    if (cache != null) {
      throw new UnsupportedOperationException("Caching not implemented");
    } else {
      final ByteSequence dataKey = ByteSequence.from(node.getBytes(getEncodingCharset()));
      data = kvClient.get(dataKey).get().getKvs().get(0).getValue().getBytes();
    }
    Properties properties = new Properties();
    if (data != null) {
      String nodeData = new String(data, getEncodingCharset());
      String[] lines = nodeData.split("\n");
      for (String line : lines) {
        String key = Strings.stringBefore(line, KEYVALUE_SEP);
        String value = Strings.stringAfter(line, KEYVALUE_SEP);
        properties.put(key, value);
      }
    }
    return properties;
  }

  private ByteSequence logPropsToByteSequence(UUID logUuid) {
    return ByteSequence.from(
        ("uuid" + KEYVALUE_SEP + logUuid + '\n').getBytes(getEncodingCharset()));
  }

  private ByteSequence peerPropsToByteSequence(Properties peerProperties) {
    byte[] data = null;
    if (peerProperties != null && !peerProperties.isEmpty()) {
      StringBuilder sb = new StringBuilder();
      int propIdx = 1;
      for (String propKey : peerProperties.stringPropertyNames()) {
        sb.append(propKey.trim())
            .append(KEYVALUE_SEP)
            .append(peerProperties.getProperty(propKey).trim());
        if (propIdx++ < peerProperties.size()) {
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
