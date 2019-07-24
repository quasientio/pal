package com.ittera.cometa.cxn;

import com.ittera.cometa.*;

import java.nio.charset.Charset;
import java.nio.charset.UnsupportedCharsetException;
import java.util.*;
import java.util.stream.Collectors;

import org.apache.zookeeper.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.zookeeper.data.Stat;
import org.apache.zookeeper.AsyncCallback.StringCallback;
import org.apache.zookeeper.AsyncCallback.StatCallback;
import org.apache.zookeeper.AsyncCallback.ChildrenCallback;

import org.apache.commons.lang3.StringUtils;

public class ZkClient implements Watcher, PeerLogDirectory {

	protected static final Logger logger = LoggerFactory.getLogger(ZkClient.class);

	protected static final String PROPERTIES_SEP = "|";
	static Charset loadedCharset;

	// root paths
	protected static final String DEFAULT_ROOT_PATH = "/cometa";
	protected static final String PEERS_SUBPATH = "/peers";
	protected static final String LOGS_SUBPATH = "/logs";
	protected static final String BROKERS_PATH = "/brokers/ids";

	public static final int SESSION_TIMEOUT = 10000;

	private boolean brokersInfoLoaded;
	private ZooKeeper zk;
	private Watcher watcher;
	private String customRootPath;
	private String zkAddress;
	private Set<KafkaBrokerInfo> brokerInfoSet;

	public static ZkClient getConnectedClient(String zkAddress, String customRootPath, Watcher watcher)
		throws Exception {

		ZkClient cli = new ZkClient();

		if (watcher != null) {
			cli.watcher = watcher;
		}

		if (customRootPath != null) {
			cli.setCustomRootPath(customRootPath);
		}

		cli.connect(zkAddress);

		return cli;
	}

	public static ZkClient getConnectedClient(String zkAddress, String customRootPath)
		throws Exception {
		return getConnectedClient(zkAddress, customRootPath, null);
	}

	public static ZkClient getConnectedClient(String zkAddress, Watcher watcher)
		throws Exception {
		return getConnectedClient(zkAddress, null, watcher);
	}

	public static ZkClient getConnectedClient(String zkAddress)
		throws Exception {
		return getConnectedClient(zkAddress, null, null);
	}

	public static ZkClient getDisconnectedClient(String customRootPath, Watcher watcher) {

		ZkClient cli = new ZkClient();

		if (watcher != null) {
			cli.watcher = watcher;
		}

		if (customRootPath != null) {
			cli.setCustomRootPath(customRootPath);
		}

		return cli;
	}

	public static ZkClient getDisconnectedClient(Watcher watcher) {
		return getDisconnectedClient(null, watcher);
	}

	public static ZkClient getDisconnectedClient(String customRootPath) {
		return getDisconnectedClient(customRootPath, null);
	}

	public static ZkClient getDisconnectedClient() {
		return getDisconnectedClient(null, null);
	}

	private void ensureRootAndSubdirsExist() throws Exception {

		// make sure root node exists
		if (zk.exists(getRootPath(), null) == null) {
			zk.create(getRootPath(), new byte[0], ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
			logger.info("Created persistent root node: {}", getRootPath());
		}

		// make sure peers node exists
		if (zk.exists(getPeersPath(), null) == null) {
			zk.create(getPeersPath(), new byte[0], ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
			logger.info("Created persistent peers node: {}", getPeersPath());
		}

		// make sure logs node exists
		if (zk.exists(getLogsPath(), null) == null) {
			zk.create(getLogsPath(), new byte[0], ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
			logger.info("Created persistent logs node: {}", getLogsPath());
		}

	}

	private void loadBrokerInfoSet() {

		Stat brokersInfoStat;
		String brokerInfoPath;

		if (brokerInfoSet == null) {
			brokerInfoSet = new HashSet<>();
		} else {
			brokerInfoSet.clear();
		}

		try {
			for (String brokerId : zk.getChildren(BROKERS_PATH, false)) {
				brokerInfoPath = BROKERS_PATH + "/" + brokerId;
				brokersInfoStat = zk.exists(brokerInfoPath, null);
				String nodeData = new String(zk.getData(brokerInfoPath, false, brokersInfoStat), getEncodingCharset());
				logger.debug("Read registered broker info data: {}", nodeData);
				KafkaBrokerInfo kafkaBrokerInfo = KafkaBrokerInfo.parseFromJSON(nodeData);
				brokerInfoSet.add(kafkaBrokerInfo);
			}
		} catch (Exception e) {
			logger.error("Error reading registered kafka brokers info", e);
		} finally {
			brokersInfoLoaded = true;
		}
	}

	@Override
	public void connect(String zkAddress) throws Exception {
		this.zkAddress = zkAddress;
		zk = new ZooKeeper(zkAddress, SESSION_TIMEOUT, watcher == null ? this : watcher);
		logger.info("Connected to zookeeper at {}", zkAddress);
		ensureRootAndSubdirsExist();
	}

	@Override
	public Set<KafkaBrokerInfo> getKafkaBrokers() {
		if (!brokersInfoLoaded) {
			loadBrokerInfoSet();
		}

		return brokerInfoSet;
	}

	@Override
	public void registerPeer(UUID peerUuid, Properties peerProperties) throws Exception {

		String peerNode = getPeersPath() + "/" + peerUuid;
		byte[] data = null;

		if (peerProperties != null && !peerProperties.isEmpty()) {
			StringBuilder sb = new StringBuilder();
			int propIdx = 1;
			for (String propKey : peerProperties.stringPropertyNames()) {
				sb.append(propKey.trim()).append(PROPERTIES_SEP).append(peerProperties.getProperty(propKey).trim());
				if (propIdx++ < peerProperties.size()) {
					sb.append('\n');
				}
			}
			data = sb.toString().getBytes(getEncodingCharset());
		}

		if (zk.exists(peerNode, null) == null) {
			zk.create(peerNode, data, ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
			logger.info("Registered peer node with uuid: {} and properties: {}", peerUuid, peerProperties);
		} else {
			logger.info("Skipping registration of existing peer node with uuid: {} and properties: {}", peerUuid, peerProperties);
		}
	}

	@Override
	public void unregisterPeer(UUID peerUuid) throws Exception {
		String peerNode = getPeersPath() + "/" + peerUuid;
		if (peerExists(peerUuid)) {
			zk.delete(peerNode, -1);
			logger.info("Unregistered (i.e. deleted) peer node with uuid: {}", peerUuid);
		}
	}

	@Override
	public void unregisterAllPeers() throws Exception {

		List<UUID> peerUuids = zk.getChildren(getPeersPath(), false).stream().map(UUID::fromString)
			.collect(Collectors.toList());

		// loop through all peers
		int deleted = 0;
		for (UUID peerUuid : peerUuids) {
			unregisterPeer(peerUuid);
			deleted++;
		}

		logger.debug("Deleted {} peers", deleted);
	}

	@Override
	public boolean peerExists(UUID peerUuid) throws Exception {
		String peerNode = getPeersPath() + "/" + peerUuid;
		return zk.exists(peerNode, null) != null;
	}

	@Override
	public LogInfo createLog(String logNamePrefix) throws Exception {
		String logNodePrefix = getLogsPath() + "/" + logNamePrefix;

		byte[] data;

		// create new node
		StringBuilder sb = new StringBuilder();
		String newLogUuid = UUID.randomUUID().toString();
		sb.append("uuid").append(PROPERTIES_SEP).append(newLogUuid).append('\n');
		data = sb.toString().getBytes(getEncodingCharset());
		String createdNode = zk.create(logNodePrefix, data, ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT_SEQUENTIAL);

		String createdLogName = StringUtils.substringAfterLast(createdNode, "/");
		LogInfo newLogInfo = getLogInfo(createdLogName);
		logger.info("Created new log node: {} with uuid: {}", createdLogName, newLogUuid);
		return newLogInfo;
	}

	@Override
	public LogInfo addGivenLog(String logName) throws Exception {
		String logNode = getLogsPath() + "/" + logName;

		byte[] data;

		// create new node
		StringBuilder sb = new StringBuilder();
		String logUuid = UUID.randomUUID().toString();
		sb.append("uuid").append(PROPERTIES_SEP).append(logUuid).append('\n');
		data = sb.toString().getBytes(getEncodingCharset());
		String createdNode = zk.create(logNode, data, ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);

		String registeredLogName = StringUtils.substringAfterLast(createdNode, "/");
		LogInfo newLogInfo = getLogInfo(registeredLogName);
		logger.info("Registered given log node: {} with uuid: {}", registeredLogName, logUuid);
		return newLogInfo;
	}

	/**
	 * Asynchronous version
	 *
	 * @param logName
	 * @param logRequest
	 * @param cb
	 * @param ctx
	 * @throws Exception
	 */
	public void addLogRequest(String logName, LogRequest logRequest, StringCallback cb, Object ctx)
		throws Exception {

		String newRequestNode = String.format("%s/%s/%s", getLogsPath(), logName, logRequest.getUuid());
		if (!logExists(logName)) {
			throw new NoLogInfoNodeException(String.format("Node for log: %s does not exist", logName));
		}

		zk.create(newRequestNode, null, ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT, cb, ctx);
		logger.debug("Async-created new request node uuid: {} for log: {}", logRequest.getUuid(), logName);
	}

	@Override
	public String addLogRequest(String logName, LogRequest logRequest) throws Exception {

		String newRequestNode = String.format("%s/%s/%s", getLogsPath(), logName, logRequest.getUuid());
		if (!logExists(logName)) {
			throw new NoLogInfoNodeException(String.format("Node for log: %s does not exist", logName));
		}

		String createdNode = zk.create(newRequestNode, null, ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
		logger.debug("Created new request node uuid: {} for log: {}", logRequest.getUuid(), logName);
		return createdNode;
	}

	/**
	 * Asynchronous version
	 *
	 * @param logName
	 * @param logReply
	 * @param callback
	 * @throws Exception
	 */
	public void addLogReply(String logName, LogReply logReply, StringCallback callback) throws Exception {

		UUID requestUuid = logReply.getIsReplyTo();
		String requestNode = String.format("%s/%s/%s", getLogsPath(), logName, requestUuid);

		String newReplyNode = String.format("%s/%s/%s/%s", getLogsPath(), logName, requestUuid, logReply.getUuid());
		if (zk.exists(requestNode, false) != null) {
			// create reply node with message uuid as name and offset as data
			String sb = "from" + PROPERTIES_SEP + logReply.getPeerUuid() +
				'\n' + "offset" + PROPERTIES_SEP + logReply.getOffset() + '\n';
			byte[] data = sb.getBytes(getEncodingCharset());
			zk.create(newReplyNode, data, ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT, callback, null);
			logger.debug("Async-created new reply node uuid: {} for request: {}, log: {}", logReply.getUuid(), requestUuid,
				logName);
		} else {
			if (!logExists(logName)) {
				throw new NoLogInfoNodeException(String.format("Node for log: %s does not exist", logName));
			} else {
				throw new NoLogRequestNodeException(String.format("Request node %s for log: %s does not exist", requestUuid,
					logName));
			}
		}
	}

	@Override
	public void addLogReply(String logName, LogReply logReply) throws Exception {

		UUID requestUuid = logReply.getIsReplyTo();
		String requestNode = String.format("%s/%s/%s", getLogsPath(), logName, requestUuid);

		String newReplyNode = String.format("%s/%s/%s/%s", getLogsPath(), logName, requestUuid, logReply.getUuid());
		if (zk.exists(requestNode, false) != null) {
			// create reply node with message uuid as name and offset as data
			String sb = "from" + PROPERTIES_SEP + logReply.getPeerUuid() + '\n' +
				"offset" + PROPERTIES_SEP + logReply.getOffset() + '\n';
			byte[] data = sb.getBytes(getEncodingCharset());
			zk.create(newReplyNode, data, ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
		} else {
			if (!logExists(logName)) {
				throw new NoLogInfoNodeException(String.format("Node for log: %s does not exist", logName));
			} else {
				throw new NoLogRequestNodeException(String.format("Request node %s for log: %s does not exist", requestUuid,
					logName));
			}
		}
	}

	@Override
	public void deleteLogRequest(String logName, LogRequest logRequest) throws Exception {

		String requestNode = String.format("%s/%s/%s", getLogsPath(), logName, logRequest.getUuid());
		int deleted = 0;

		// delete all reply nodes
		for (String replyNode : zk.getChildren(requestNode, false)) {
			zk.delete(requestNode + "/" + replyNode, -1);
			deleted++;
		}

		zk.delete(requestNode, -1);
		logger.info("Deleted request node {} and its {} reply nodes, for log: {}", logRequest.getUuid(), deleted, logName);
	}

	@Override
	public void deleteLogRequests(String logName) throws Exception {

		String logNode = getLogsPath() + "/" + logName;
		int deleted = 0;
		for (String reqNode : zk.getChildren(logNode, false)) {
			deleteLogRequest(logName, new LogRequest(UUID.fromString(reqNode)));
			deleted++;
		}

		logger.info("Deleted {} request nodes for log: {}", deleted, logName);
	}

	@Override
	public Set<LogReply> getRepliesTo(String logName, LogRequest logRequest) throws Exception {

		// check log exists
		if (!logExists(logName)) {
			throw new NoLogInfoNodeException(String.format("Node for log: %s does not exist", logName));
		}

		String requestNode = String.format("%s/%s/%s", getLogsPath(), logName, logRequest.getUuid());
		Stat nodeStat;
		String replyNodePath;
		Properties props;
		Set<LogReply> replies = new TreeSet<>();

		// check req node exists
		if (zk.exists(requestNode, false) == null) {
			throw new NoLogRequestNodeException(String.format("Request node %s for log: %s does not exist", logRequest,
				logName));
		}

		// get all reply nodes
		for (String replyNode : zk.getChildren(requestNode, false)) {

			nodeStat = zk.exists(requestNode + "/" + replyNode, null);
			replyNodePath = requestNode + "/" + replyNode;
			props = getProperties(replyNodePath, nodeStat);
			replies.add(new LogReply(UUID.fromString(replyNode),
				props.getProperty("from") == null ? null : UUID.fromString(props.getProperty("from")),
				logRequest.getUuid(), Long.parseLong(props.getProperty("offset"))));
		}

		return replies;
	}

	@Override
	public LogReply getLogReply(String logName, UUID requestUuid, UUID replyUuid) throws Exception {

		String replyNode = String.format("%s/%s/%s/%s", getLogsPath(), logName, requestUuid, replyUuid);
		Stat nodeStat = zk.exists(replyNode, null);
		Properties props = getProperties(replyNode, nodeStat);
		return new LogReply(replyUuid, UUID.fromString(props.getProperty("from")), requestUuid,
			Long.parseLong(props.getProperty("offset")));
	}

	public void getChildren(String logName, UUID requestUuid, Watcher watcher, ChildrenCallback cb, Object ctx) {

		String requestNode = String.format("%s/%s/%s", getLogsPath(), logName, requestUuid);

		logger.debug("Setting watch on getChildren for new request node: {}", requestNode);
		zk.getChildren(requestNode, watcher, cb, ctx);
	}

	public void requestExists(String logName, UUID requestUuid, Watcher watcher, StatCallback cb) {

		String requestNode = String.format("%s/%s/%s", getLogsPath(), logName, requestUuid);
		zk.exists(requestNode, watcher, cb, null);
	}

	@Override
	public LogInfo getLastLog(String logNamePrefix) throws Exception {
		if (!zk.getState().equals(ZooKeeper.States.CONNECTED)) {
			connect(zkAddress);
		}

		// find last
		List<String> logs = zk.getChildren(getLogsPath(), false);
		long maxLogIndex = -1;
		String lastLog = null;
		if (logs.size() == 0) {
			logger.debug("No logs found with prefix '{}'", logNamePrefix);
		}
		// loop through all logs
		for (String log : logs) {
			// filter those with our prefix
			if (log.startsWith(logNamePrefix)) {
				// parse index in log names and set max
				String logIdxStr = StringUtils.substringAfter(log, logNamePrefix);
				Long logIdx = Long.valueOf(logIdxStr);
				if (logIdx > maxLogIndex) {
					maxLogIndex = logIdx;
					lastLog = log;
				}
			}
		}

		logger.debug("With prefix '{}' got log = {}", logNamePrefix, lastLog);

		return getLogInfo(lastLog);
	}

	@Override
	public int getLogCount(String logNamePrefix) throws Exception {

		int count = 0;
		List<String> logs = zk.getChildren(getLogsPath(), false);
		for (String log : logs) {
			if (log.startsWith(logNamePrefix)) {
				count++;
			}
		}
		return count;
	}

	@Override
	public Set<LogInfo> getAllLogs() throws Exception {
		Set<LogInfo> allLogs = new TreeSet();
		List<String> logs = zk.getChildren(getLogsPath(), false);
		for (String logName : logs) {
			allLogs.add(getLogInfo(logName));
		}

		return allLogs;
	}


	private Properties getProperties(String node, Stat nodeStat) throws Exception {

		byte[] data = zk.getData(node, false, nodeStat);

		Properties properties = new Properties();

		if (data != null) {
			String nodeData = new String(zk.getData(node, false, nodeStat), getEncodingCharset());
			String[] lines = nodeData.split("\n");
			for (String line : lines) {
				String key = StringUtils.substringBefore(line, PROPERTIES_SEP);
				String value = StringUtils.substringAfter(line, PROPERTIES_SEP);
				properties.put(key, value);
			}
		}

		return properties;
	}

	@Override
	public LogInfo getLogInfo(String logName) throws Exception {
		if (!zk.getState().equals(ZooKeeper.States.CONNECTED)) {
			connect(zkAddress);
		}

		Stat nodeStat = getLogNodeStat(logName);
		String logNode = getLogsPath() + "/" + logName;

		Properties props = getProperties(logNode, nodeStat);
		UUID uuid = UUID.fromString(props.getProperty("uuid"));

		// fill stat info
		LogInfo logInfo = new LogInfo(logName, getKafkaBrokers(), uuid);
		logInfo.setZk_ctime(nodeStat.getCtime());

		return logInfo;
	}

	@Override
	public LogInfo getLogInfo(UUID uuid) throws Exception {
		Set<LogInfo> allLogs = getAllLogs();
		for (LogInfo logInfo : allLogs) {
			if (logInfo.getUuid().equals(uuid)) {
				return logInfo;
			}
		}

		return null;
	}

	@Override
	public Properties getPeerProperties(UUID peerUuid) throws Exception {
		if (!zk.getState().equals(ZooKeeper.States.CONNECTED)) {
			connect(zkAddress);
		}

		Stat nodeStat = getPeerNodeStat(peerUuid);
		String peerNode = getPeersPath() + "/" + peerUuid;

		return getProperties(peerNode, nodeStat);
	}

	@Override
	public int getPeerCount() throws Exception {
		int count = 0;
		// find last
		List<String> peers = zk.getChildren(getPeersPath(), false);
		return peers.size();

	}

	@Override
	public PeerInfo getPeerInfo(UUID peerUuid) throws Exception {
		if (!zk.getState().equals(ZooKeeper.States.CONNECTED)) {
			connect(zkAddress);
		}

		PeerInfo peerInfo = new PeerInfo(peerUuid);

		Stat nodeStat = getPeerNodeStat(peerUuid);
		String peerNode = getPeersPath() + "/" + peerUuid;

		Properties props = getProperties(peerNode, nodeStat);
		String listenAddress = props.getProperty("listenAddress");
		if (listenAddress != null) {
			peerInfo.setListenAddress(listenAddress);
		}

		// fill stat info
		peerInfo.setZk_ctime(nodeStat.getCtime());

		return peerInfo;
	}

	@Override
	public Set<PeerInfo> getAllPeers() throws Exception {

		Set<PeerInfo> allPeers = new TreeSet();
		List<String> peers = zk.getChildren(getPeersPath(), false);
		for (String uuid : peers) {
			allPeers.add(getPeerInfo(UUID.fromString(uuid)));
		}

		return allPeers;
	}

	@Override
	public void deleteLogNamed(String logName) throws Exception {
		if (logExists(logName)) {
			// first delete any children request nodes
			deleteLogRequests(logName);

			String logNode = getLogsPath() + "/" + logName;
			zk.delete(logNode, -1);
			logger.info("Unregistered (i.e. deleted) log node: {}", logName);
		}
	}

	@Override
	public void deleteAllLogs(String logNamePrefix) throws Exception {

		List<String> logs = zk.getChildren(getLogsPath(), false);
		// loop through all logs
		int deleted = 0;
		for (String logName : logs) {
			// filter those with our prefix
			if (logName.startsWith(logNamePrefix)) {
				deleteLogNamed(logName);
				deleted++;
			}
		}

		logger.debug("Deleted {} logs with prefix: {}", deleted, logNamePrefix);

	}

	public void deleteRootPaths() throws Exception {

		zk.delete(getLogsPath(), -1);
		logger.info("Deleted logs path node {}", getLogsPath());

		zk.delete(getPeersPath(), -1);
		logger.info("Deleted peers path node {}", getPeersPath());

		zk.delete(getRootPath(), -1);
		logger.info("Deleted root path node {}", getRootPath());
	}

	protected Stat getLogNodeStat(String logName) throws Exception {
		String logNode = getLogsPath() + "/" + logName;
		return zk.exists(logNode, null);
	}

	protected Stat getPeerNodeStat(UUID peerUuid) throws Exception {
		String peerNode = getPeersPath() + "/" + peerUuid;
		return zk.exists(peerNode, null);
	}

	@Override
	public boolean logExists(String logName) throws Exception {
		return getLogNodeStat(logName) != null;
	}

	@Override
	public boolean isConnectionEstablished() {
		return zk != null && zk.getState().equals(ZooKeeper.States.CONNECTED);
	}

	public String getRootPath() {
		return customRootPath != null ? customRootPath : DEFAULT_ROOT_PATH;
	}

	public String getPeersPath() {
		return getRootPath() + PEERS_SUBPATH;
	}

	public String getLogsPath() {
		return getRootPath() + LOGS_SUBPATH;
	}

	public String getCustomRootPath() {
		return customRootPath;
	}

	public void setCustomRootPath(String customRootPath) {
		this.customRootPath = customRootPath;
	}

	@Override
	public String getAddress() {
		return zkAddress;
	}

	public ZooKeeper.States getZkState() {
		return zk.getState();
	}

	public long getZkSessionId() {
		return zk.getSessionId();
	}

	@Override
	public void process(WatchedEvent watchedEvent) {
		logger.debug("Ignoring received event: {}", watchedEvent);
	}

	@Override
	public void close() {
		logger.info("Closing zookeeper connection to {}", zkAddress);
		try {
			zk.close();
		} catch (InterruptedException ex) {
			logger.error("Error while closing down zookeeper connection", ex);
		}
	}

	private Charset getEncodingCharset() {
		if (loadedCharset == null) {
			try {
				loadedCharset = Charset.forName("UTF-8");
			} catch (UnsupportedCharsetException e) {
				logger.warn("No UTF-8 available for encoding/decoding. Falling back to default charset", e);
				loadedCharset = Charset.defaultCharset();
			}
		}
		return loadedCharset;
	}
}
