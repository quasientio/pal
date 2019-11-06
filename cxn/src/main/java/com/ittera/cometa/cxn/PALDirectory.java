package com.ittera.cometa.cxn;

import com.ittera.cometa.KafkaBrokerInfo;
import com.ittera.cometa.LogInfo;
import com.ittera.cometa.LogReply;
import com.ittera.cometa.LogRequest;
import com.ittera.cometa.PeerInfo;
import com.ittera.cometa.common.util.Strings;
import java.lang.reflect.Field;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.charset.UnsupportedCharsetException;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.inject.Inject;
import javax.inject.Named;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.framework.api.BackgroundCallback;
import org.apache.curator.framework.api.CuratorWatcher;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.data.Stat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PALDirectory implements AutoCloseable {

  private static final Logger logger = LoggerFactory.getLogger(PALDirectory.class);

  // root paths
  private static final String DEFAULT_PAL_NAMESPACE = "cometa";
  private static final String PEERS_DIR = "peers";
  private static final String LOGS_DIR = "logs";

  // absolute path of kafka broker nodes
  private static final String BROKERS_PATH = "/brokers/ids";

  // TODO load from config
  private static final int BASE_SLEEP_TIME_MS = 1000;
  private static final int MAX_RETRIES = 3;
  private static final int CONNECTION_TIMEOUT_MS = 6000;
  private static final int SESSION_TIMEOUT_MS = 15000;

  private static final String KEYVALUE_SEP = "|";
  private static Charset loadedCharset;

  private final String directoryUrl;
  private final CuratorFramework curator;
  private final String namespace;
  private Set<KafkaBrokerInfo> brokerInfoSet;
  private boolean brokersInfoLoaded;

  @Inject
  public PALDirectory(@Named("paldir_url") String connectionString) {
    this(connectionString, null);
  }

  public PALDirectory(String connectionString, String namespace) {
    this.curator = newCuratorInstance(connectionString);
    logger.info("Will connect to zookeeper@{}", connectionString);
    /* we can't set the namespace on the Curator instance, as we need access to
      kafka's namespace (for /brokers), so we will handle it manually
    */
    this.namespace = namespace != null ? namespace : DEFAULT_PAL_NAMESPACE;
    this.directoryUrl = connectionString;
    curator.start();
    try {
      createSubPaths();
    } catch (Exception e) {
      logger.error("Error trying to create subpaths", e);
    }
  }

  // <editor-fold desc="Peer methods">
  public boolean peerExists(UUID peerUuid) throws Exception {
    return curator.checkExists().forPath(getPeerPath(peerUuid)) != null;
  }

  public void registerPeer(UUID peerUuid, Properties peerProperties) throws Exception {
    byte[] peerData = peerPropsToData(peerProperties);

    if (curator.checkExists().forPath(getPeerPath(peerUuid)) == null) {
      curator.create().creatingParentsIfNeeded().forPath(getPeerPath(peerUuid), peerData);
      logger.info("Registered peer with uuid: {} and properties: {}", peerUuid, peerProperties);
    } else {
      logger.info(
          "Skipping registration of existing peer with uuid: {} and properties: {}",
          peerUuid,
          peerProperties);
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

  public PeerInfo getPeerInfo(UUID peerUuid) throws Exception {
    if (!peerExists(peerUuid)) {
      throw new NoPeerInfoNodeException(
          String.format("Node for peer: %s does not exist", peerUuid));
    }
    final Properties props = getProperties(getPeerPath(peerUuid));
    final PeerInfo peerInfo = new PeerInfo(peerUuid);
    final Stat stat = curator.checkExists().forPath(getPeerPath(peerUuid));
    // set bean fields reflectively
    Stream.of("name", "reqAddress", "pubAddress", "jmxAddress")
        .forEach(
            fldName -> {
              setFieldValueUnlessNull(peerInfo, fldName, props.getProperty(fldName));
            });
    peerInfo.setCtime(stat.getCtime());
    peerInfo.setMtime(stat.getMtime());
    return peerInfo;
  }

  public Set<PeerInfo> getAllPeers() throws Exception {
    final Set<PeerInfo> allPeers = new TreeSet<>();
    for (String uuid : curator.getChildren().forPath(getPeersPath())) {
      allPeers.add(getPeerInfo(UUID.fromString(uuid)));
    }
    return allPeers;
  }

  public void unregisterAllPeers() throws Exception {
    for (String uuid : curator.getChildren().forPath(getPeersPath())) {
      unregisterPeer(UUID.fromString(uuid));
    }
  }

  public void unregisterPeer(UUID peerUuid) throws Exception {
    if (peerExists(peerUuid)) {
      curator.delete().forPath(getPeerPath(peerUuid));
      logger.info("Unregistered peer with uuid: {}", peerUuid);
    }
  }
  // </editor-fold>

  // <editor-fold desc="Log methods">
  public LogInfo registerLog(String logName) throws Exception {
    UUID newLogUuid = UUID.randomUUID();
    String createdNode =
        curator
            .create()
            .creatingParentsIfNeeded()
            .withMode(CreateMode.PERSISTENT)
            .forPath(getLogPath(logName), logPropsToData(newLogUuid));

    String registeredLogName = Strings.stringAfterLast(createdNode, "/");
    // assert registeredLogName == logName
    LogInfo newLogInfo = getLogInfo(registeredLogName);
    logger.info("Registered given log node: {} with uuid: {}", registeredLogName, newLogUuid);
    return newLogInfo;
  }

  public LogInfo newLog(String logNamePrefix) throws Exception {
    UUID newLogUuid = UUID.randomUUID();
    String createdNode =
        curator
            .create()
            .creatingParentsIfNeeded()
            .withMode(CreateMode.PERSISTENT_SEQUENTIAL)
            .forPath(getLogPath(logNamePrefix), logPropsToData(newLogUuid));

    String createdLogName = Strings.stringAfterLast(createdNode, "/");
    LogInfo newLogInfo = getLogInfo(createdLogName);
    logger.info("Created new log node: {} with uuid: {}", createdLogName, newLogUuid);
    return newLogInfo;
  }

  public LogInfo getLogInfo(String logName) throws Exception {
    if (!logExists(logName)) {
      throw new NoLogInfoNodeException(String.format("Node for log: %s does not exist", logName));
    }
    final Properties props = getProperties(getLogPath(logName));
    final UUID uuid = UUID.fromString(props.getProperty("uuid"));
    final Stat stat = curator.checkExists().forPath(getLogPath(logName));
    final LogInfo logInfo = new LogInfo(logName, getKafkaBrokers(), uuid);
    logInfo.setCtime(stat.getCtime());
    logInfo.setMtime(stat.getMtime());
    return logInfo;
  }

  public boolean logExists(String logName) throws Exception {
    return curator.checkExists().forPath(getLogPath(logName)) != null;
  }

  public Set<LogInfo> getAllLogs() throws Exception {
    final Set<LogInfo> allLogs = new TreeSet<>();
    for (String logName : curator.getChildren().forPath(getLogsPath())) {
      allLogs.add(getLogInfo(logName));
    }
    return allLogs;
  }

  public LogInfo getLastLog(String logNamePrefix) throws Exception {
    List<String> logs =
        curator.getChildren().forPath(getLogsPath()).stream()
            .filter(l -> l.startsWith(logNamePrefix))
            .collect(Collectors.toList());

    long maxLogIndex = -1;
    String lastLog = null;
    for (String log : logs) {
      // parse index in log names and set max
      String logIdxStr = Strings.stringAfter(log, logNamePrefix);
      Long logIdx = Long.valueOf(logIdxStr);
      if (logIdx > maxLogIndex) {
        maxLogIndex = logIdx;
        lastLog = log;
      }
    }
    logger.info("With prefix '{}' got last log = {}", logNamePrefix, lastLog);
    return getLogInfo(lastLog);
  }

  public int getLogCount(String logNamePrefix) throws Exception {
    int count = 0;
    for (String log : curator.getChildren().forPath(getLogsPath())) {
      if (log.startsWith(logNamePrefix)) {
        count++;
      }
    }
    return count;
  }

  public void unregisterLog(String logName) throws Exception {
    if (logExists(logName)) {
      curator.delete().deletingChildrenIfNeeded().forPath(getLogPath(logName));
      logger.info("Unregistered (i.e. deleted) log node: {}", logName);
    }
  }

  public void unregisterLogs(String logNamePrefix) throws Exception {
    List<String> logs =
        curator.getChildren().forPath(getLogsPath()).stream()
            .filter(l -> l.startsWith(logNamePrefix))
            .collect(Collectors.toList());
    int deleted = 0;
    for (String logName : logs) {
      unregisterLog(logName);
      deleted++;
    }
    logger.info("Unregistered {} logs with prefix: {}", deleted, logNamePrefix);
  }

  public void unregisterAllLogs() throws Exception {
    for (String logName : curator.getChildren().forPath(getLogsPath())) {
      unregisterLog(logName);
    }
  }
  // </editor-fold>

  // <editor-fold desc="Log requests">
  public boolean logRequestExistsAsync(
      String logName, UUID requestUuid, BackgroundCallback callback, CuratorWatcher watcher)
      throws Exception {
    String requestNode = String.format("%s/%s", getLogPath(logName), requestUuid);
    return curator.checkExists().usingWatcher(watcher).inBackground(callback).forPath(requestNode)
        != null;
  }

  public void addLogRequestAsync(String logName, LogRequest logRequest, BackgroundCallback callback)
      throws Exception {
    String newRequestNode = String.format("%s/%s", getLogPath(logName), logRequest.getUuid());
    if (!logExists(logName)) {
      throw new NoLogInfoNodeException(String.format("Node for log: %s does not exist", logName));
    }

    curator.create().inBackground(callback).forPath(newRequestNode);
    if (logger.isDebugEnabled()) {
      logger.debug(
          "Async-created new request node uuid: {} for log: {}", logRequest.getUuid(), logName);
    }
  }

  /**
   * NOTE: background operation has no callback
   *
   * @param logName
   * @param logRequest
   * @throws Exception
   */
  public void deleteLogRequestAsync(String logName, LogRequest logRequest) throws Exception {
    String requestNode = String.format("%s/%s", getLogPath(logName), logRequest.getUuid());
    int replyNodes = 0;
    // delete all reply nodes
    for (String replyNode : curator.getChildren().forPath(requestNode)) {
      curator.delete().inBackground().forPath(requestNode + "/" + replyNode);
      replyNodes++;
    }

    curator.delete().inBackground().forPath(requestNode);
    logger.info(
        "Async-deleted w/o guarantee request node {} and its {} reply nodes, for log: {}",
        logRequest.getUuid(),
        replyNodes,
        logName);
  }

  public int getLogRequestsCount(String logName) throws Exception {
    if (!logExists(logName)) {
      throw new NoLogInfoNodeException(String.format("Node for log: %s does not exist", logName));
    }
    return curator.getChildren().forPath(getLogPath(logName)).size();
  }

  public List<String> getRequestNodeChildrenAsyncWithWatch(
      String logName,
      UUID requestUuid,
      BackgroundCallback backgroundCallback,
      CuratorWatcher curatorWatcher)
      throws Exception {
    String requestNode = String.format("%s/%s", getLogPath(logName), requestUuid);
    if (logger.isDebugEnabled()) {
      logger.debug("Setting watch on children of request node: {}", requestNode);
    }
    return curator
        .getChildren()
        .usingWatcher(curatorWatcher)
        .inBackground(backgroundCallback)
        .forPath(requestNode);
  }

  // </editor-fold>

  // <editor-fold desc="Log replies">
  public void addLogReplyAsync(String logName, LogReply logReply, BackgroundCallback callback)
      throws Exception {
    UUID requestUuid = logReply.getIsReplyTo();
    String requestNode = String.format("%s/%s", getLogPath(logName), requestUuid);
    String newReplyNode = String.format("%s/%s", requestNode, logReply.getUuid());
    if (curator.checkExists().forPath(requestNode) != null) {
      String sb =
          "from"
              + KEYVALUE_SEP
              + logReply.getPeerUuid()
              + '\n'
              + "offset"
              + KEYVALUE_SEP
              + logReply.getOffset()
              + '\n';
      curator
          .create()
          .inBackground(callback)
          .forPath(newReplyNode, sb.getBytes(getEncodingCharset()));
      if (logger.isDebugEnabled()) {
        logger.debug(
            "Async-created new reply node uuid: {} for request: {}, log: {}",
            logReply.getUuid(),
            requestUuid,
            logName);
      }
    } else {
      if (!logExists(logName)) {
        throw new NoLogInfoNodeException(String.format("Node for log: %s does not exist", logName));
      } else {
        throw new NoLogRequestNodeException(
            String.format("Request node %s for log: %s does not exist", requestUuid, logName));
      }
    }
  }

  public Set<LogReply> getRepliesTo(String logName, LogRequest logRequest) throws Exception {
    if (!logExists(logName)) {
      throw new NoLogInfoNodeException(String.format("Node for log: %s does not exist", logName));
    }

    String requestNode = String.format("%s/%s", getLogPath(logName), logRequest.getUuid());
    if (curator.checkExists().forPath(requestNode) == null) {
      throw new NoLogRequestNodeException(
          String.format("Request node %s for log: %s does not exist", logRequest, logName));
    }

    Set<LogReply> replies = new TreeSet<>();
    // get all reply nodes
    for (String replyNode : curator.getChildren().forPath(requestNode)) {
      Properties props = getProperties(requestNode + "/" + replyNode);
      replies.add(
          new LogReply(
              UUID.fromString(replyNode),
              props.getProperty("from") == null ? null : UUID.fromString(props.getProperty("from")),
              logRequest.getUuid(),
              Long.parseLong(props.getProperty("offset"))));
    }
    return replies;
  }
  // </editor-fold>

  // <editor-fold desc="Misc methods">

  public void close() {
    logger.info("Closing zookeeper connection to {}", directoryUrl);
    curator.close();
  }
  // </editor-fold>

  // <editor-fold desc="private helpers">
  private Properties getProperties(String node) throws Exception {
    byte[] data = curator.getData().forPath(node);
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

  private byte[] logPropsToData(UUID logUuid) {
    StringBuilder sb = new StringBuilder();
    sb.append("uuid").append(KEYVALUE_SEP).append(logUuid).append('\n');
    return sb.toString().getBytes(getEncodingCharset());
  }

  private byte[] peerPropsToData(Properties peerProperties) {
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
    return data;
  }

  private Charset getEncodingCharset() {
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

  // TODO SHOLD NOT BE PUBLIC / INTERFACE
  public Set<KafkaBrokerInfo> getKafkaBrokers() {
    if (!brokersInfoLoaded) {
      loadBrokerInfoSet();
    }
    return brokerInfoSet;
  }

  /**
   * Since we use the same Zookeeper node as Kafka does, we can directly peek into Kafka brokers
   * info
   */
  private void loadBrokerInfoSet() {
    String brokerInfoPath;
    if (brokerInfoSet == null) {
      brokerInfoSet = new HashSet<>();
    } else {
      brokerInfoSet.clear();
    }
    try {
      for (String brokerId : curator.getChildren().forPath(BROKERS_PATH)) {
        brokerInfoPath = BROKERS_PATH + "/" + brokerId;
        String nodeData =
            new String(curator.getData().forPath(brokerInfoPath), getEncodingCharset());
        if (logger.isDebugEnabled()) {
          logger.debug("Read registered broker info data: {}", nodeData);
        }
        brokerInfoSet.add(KafkaBrokerInfo.parseFromJSON(nodeData));
      }
    } catch (Exception e) {
      logger.error("Error reading registered kafka brokers info", e);
    } finally {
      brokersInfoLoaded = true;
    }
  }

  private String getPeerPath(UUID peerUuid) {
    return String.format("%s/%s", getPeersPath(), peerUuid);
  }

  private String getLogPath(String logName) {
    return String.format("%s/%s", getLogsPath(), logName);
  }

  private String getPeersPath() {
    return String.format("/%s/%s", namespace, PEERS_DIR);
  }

  private String getLogsPath() {
    return String.format("/%s/%s", namespace, LOGS_DIR);
  }

  private void createSubPaths() throws Exception {
    if (curator.checkExists().forPath(getLogsPath()) == null) {
      curator.create().creatingParentsIfNeeded().forPath(getLogsPath());
    }
    if (curator.checkExists().forPath(getPeersPath()) == null) {
      curator.create().creatingParentsIfNeeded().forPath(getPeersPath());
    }
  }

  private static CuratorFramework newCuratorInstance(String connectionString) {
    ExponentialBackoffRetry retryPolicy =
        new ExponentialBackoffRetry(BASE_SLEEP_TIME_MS, MAX_RETRIES);
    return CuratorFrameworkFactory.builder()
        .connectString(connectionString)
        .retryPolicy(retryPolicy)
        .connectionTimeoutMs(CONNECTION_TIMEOUT_MS)
        .sessionTimeoutMs(SESSION_TIMEOUT_MS)
        .build();
  }
  // </editor-fold>

  public String getDirectoryUrl() {
    return directoryUrl;
  }
}
