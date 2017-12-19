package com.ittera.cometa.cxn;

import com.ittera.cometa.LogInfo;
import com.ittera.cometa.LogReply;
import com.ittera.cometa.PeerInfo;

import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.Properties;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.zookeeper.ZooDefs;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.data.Stat;
import org.apache.zookeeper.AsyncCallback.StringCallback;
import org.apache.zookeeper.AsyncCallback.StatCallback;
import org.apache.zookeeper.AsyncCallback.ChildrenCallback;

import org.apache.commons.lang3.StringUtils;

public class ZkClient implements Watcher, PeerLogDirectory {

	protected static final Logger logger = LoggerFactory.getLogger(ZkClient.class);

	protected static final String PROPERTIES_SEP = "|";

	// root paths
	public static final String ROOT_PATH = "/cometa";
	public static final String PEERS_PATH = ROOT_PATH + "/peers";
	public static final String LOGS_PATH = ROOT_PATH + "/logs";

	public static final int SESSION_TIMEOUT = 10000;

	private ZooKeeper zk;
	private Watcher watcher;

	private String zookeeperUrl;

	/**
	 * Provides a disconnected ZkClient
	 * NOTE: when using this constructor, be sure to call connect() before anything else
	 */
	public ZkClient() {
	}

	/**
	 * Provides a connected ZkClient
	 *
	 * @param zookeeperUrl
	 * @throws Exception
	 */
	public ZkClient(String zookeeperUrl) throws Exception {
		this.zookeeperUrl = zookeeperUrl;
		connect(zookeeperUrl);
	}

	public ZkClient(String zookeeperUrl, Watcher watcher) throws Exception {
		this.zookeeperUrl = zookeeperUrl;
		this.watcher = watcher;
		connect(zookeeperUrl);
	}

	private void ensureRootAndSubdirsExist() throws Exception {

		// make sure root node exists
		if (zk.exists(ROOT_PATH, null) == null) {
			zk.create(ROOT_PATH, new byte[0], ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
			logger.info("Created persistent root node: {}", ROOT_PATH);
		}


		// make sure peers node exists
		if (zk.exists(PEERS_PATH, null) == null) {
			zk.create(PEERS_PATH, new byte[0], ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
			logger.info("Created persistent peers node: {}", PEERS_PATH);
		}

		// make sure logs node exists
		if (zk.exists(LOGS_PATH, null) == null) {
			zk.create(LOGS_PATH, new byte[0], ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
			logger.info("Created persistent logs node: {}", LOGS_PATH);
		}

	}

	@Override
	public void connect(String zookeeperUrl) throws Exception {
		zk = new ZooKeeper(zookeeperUrl, SESSION_TIMEOUT, watcher == null ? this : watcher);
		logger.info("Connected to zookeeper at {}", zookeeperUrl);
		ensureRootAndSubdirsExist();
	}

	@Override
	public void registerPeer(UUID peerUuid, Properties peerProperties) throws Exception {

		String peerNode = PEERS_PATH + "/" + peerUuid;
		byte[] data = null;

		if (peerProperties != null && !peerProperties.isEmpty()) {
			StringBuffer sb = new StringBuffer();
			int propIdx = 1;
			for (String propKey : peerProperties.stringPropertyNames()) {
				sb.append(propKey.trim()).append(PROPERTIES_SEP).append(peerProperties.getProperty(propKey).trim());
				if (propIdx++ < peerProperties.size()) {
					sb.append('\n');
				}
			}
			data = sb.toString().getBytes();
		}

		if (zk.exists(peerNode, null) == null) {
			zk.create(peerNode, data, ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
			logger.info("Registered peer node with uuid: {} and properties: {}", peerUuid, peerProperties);
		}
	}

	@Override
	public void unregisterPeer(UUID peerUuid) throws Exception {
		String peerNode = PEERS_PATH + "/" + peerUuid;
		if (peerExists(peerUuid)) {
			zk.delete(peerNode, -1);
			logger.info("Unregistered (i.e. deleted) peer node with uuid: {}", peerUuid);
		}
	}

	@Override
	public void unregisterAllPeers() throws Exception {

		List<String> peerUuids = zk.getChildren(PEERS_PATH, false);
		// loop through all peers
		int deleted = 0;
		for (String peerUuid : peerUuids) {
			unregisterPeer(UUID.fromString(peerUuid));
			deleted++;
		}

		logger.debug("Deleted {} peers", deleted);
	}

	@Override
	public boolean peerExists(UUID peerUuid) throws Exception {
		String peerNode = PEERS_PATH + "/" + peerUuid;
		return zk.exists(peerNode, null) != null;
	}

	@Override
	public LogInfo addLog(String logNamePrefix, String bootstrapServers) throws Exception {
		String logNodePrefix = LOGS_PATH + "/" + logNamePrefix;

		byte[] data = null;

		// create new node
		StringBuffer sb = new StringBuffer();
		String newLogUuid = UUID.randomUUID().toString();
		sb.append("bootstrap.servers").append(PROPERTIES_SEP).append(bootstrapServers.trim()).append('\n');
		sb.append("uuid").append(PROPERTIES_SEP).append(newLogUuid).append('\n');
		data = sb.toString().getBytes();
		String createdNode = zk.create(logNodePrefix, data, ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT_SEQUENTIAL);

		String createdLogName = StringUtils.substringAfterLast(createdNode, "/");
		LogInfo newLogInfo = getLogInfo(createdLogName);
		logger.info("Created new log node: {} with bootstrapServers: {} and uuid: {}", createdLogName, bootstrapServers,
			newLogUuid);
		return newLogInfo;
	}

	/**
	 * Asynchronous version
	 *
	 * @param logName
	 * @param requestUuid
	 * @param cb
	 * @param ctx
	 * @throws Exception
	 */
	public void addLogRequest(String logName, String requestUuid, StringCallback cb, Object ctx)
		throws Exception {

		String newRequestNode = String.format("%s/%s/%s", LOGS_PATH, logName, requestUuid);
		if (!logExists(logName)) {
			throw new IllegalArgumentException(String.format("Node for log: %s does not exist", logName));
		}

		zk.create(newRequestNode, null, ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT, cb, ctx);
		logger.debug("Async-created new request node uuid: {} for log: {}", requestUuid, logName);
	}

	@Override
	public String addLogRequest(String logName, String requestUuid) throws Exception {

		String newRequestNode = String.format("%s/%s/%s", LOGS_PATH, logName, requestUuid);
		if (!logExists(logName)) {
			throw new IllegalArgumentException(String.format("Node for log: %s does not exist", logName));
		}

		String createdNode = zk.create(newRequestNode, null, ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
		logger.debug("Created new request node uuid: {} for log: {}", requestUuid, logName);
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

		String requestUuid = logReply.getReplyTo();
		String requestNode = String.format("%s/%s/%s", LOGS_PATH, logName, requestUuid);

		String newReplyNode = String.format("%s/%s/%s/%s", LOGS_PATH, logName, requestUuid, logReply.getUuid());
		if (zk.exists(requestNode, false) != null) {
			// create reply node with message uuid as name and offset as data
			StringBuffer sb = new StringBuffer();
			sb.append("from").append(PROPERTIES_SEP).append(logReply.getPeerUuid()).append('\n');
			sb.append("offset").append(PROPERTIES_SEP).append(logReply.getOffset()).append('\n');
			byte[] data = sb.toString().getBytes();
			zk.create(newReplyNode, data, ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT, callback, null);
			logger.debug("Async-created new reply node uuid: {} for request: {}, log: {}", logReply.getUuid(), requestUuid,
				logName);
		} else {
			if (!logExists(logName)) {
				throw new IllegalArgumentException(String.format("Node for log: %s does not exist", logName));
			} else {
				throw new IllegalArgumentException(String.format("Request node %s for log: %s does not exist", requestUuid,
					logName));
			}
		}
	}

	@Override
	public void addLogReply(String logName, LogReply logReply) throws Exception {

		String requestUuid = logReply.getReplyTo();
		String requestNode = String.format("%s/%s/%s", LOGS_PATH, logName, requestUuid);

		String newReplyNode = String.format("%s/%s/%s/%s", LOGS_PATH, logName, requestUuid, logReply.getUuid());
		if (zk.exists(requestNode, false) != null) {
			// create reply node with message uuid as name and offset as data
			StringBuffer sb = new StringBuffer();
			sb.append("from").append(PROPERTIES_SEP).append(logReply.getPeerUuid()).append('\n');
			sb.append("offset").append(PROPERTIES_SEP).append(logReply.getOffset()).append('\n');
			byte[] data = sb.toString().getBytes();
			zk.create(newReplyNode, data, ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
		} else {
			if (!logExists(logName)) {
				throw new IllegalArgumentException(String.format("Node for log: %s does not exist", logName));
			} else {
				throw new IllegalArgumentException(String.format("Request node %s for log: %s does not exist", requestUuid,
					logName));
			}
		}
	}

	@Override
	public void deleteLogRequest(String logName, String requestUuid) throws Exception {

		String requestNode = String.format("%s/%s/%s", LOGS_PATH, logName, requestUuid);
		int deleted = 0;

		// delete all reply nodes
		for (String replyNode : zk.getChildren(requestNode, false)) {
			zk.delete(requestNode + "/" + replyNode, -1);
			deleted++;
		}

		zk.delete(requestNode, -1);
		logger.info("Deleted request node {} and its {} reply nodes, for log: {}", requestUuid, deleted, logName);
	}

	@Override
	public void deleteLogRequests(String logName) throws Exception {

		String logNode = LOGS_PATH + "/" + logName;
		int deleted = 0;
		for (String reqNode : zk.getChildren(logNode, false)) {
			deleteLogRequest(logName, reqNode);
			deleted++;
		}

		logger.info("Deleted {} request nodes for log: {}", deleted, logName);
	}

	@Override
	public Set<LogReply> getRepliesTo(String logName, String requestUuid) throws Exception {

		// check log exists
		if (!logExists(logName)) {
			throw new IllegalArgumentException(String.format("Node for log: %s does not exist", logName));
		}

		String requestNode = String.format("%s/%s/%s", LOGS_PATH, logName, requestUuid);
		Stat nodeStat;
		String replyNodePath;
		Properties props;
		Set<LogReply> replies = new TreeSet<>();

		// check req node exists
		if (zk.exists(requestNode, false) == null) {
			throw new IllegalArgumentException(String.format("Request node %s for log: %s does not exist", requestUuid,
				logName));
		}

		// get all reply nodes
		for (String replyNode : zk.getChildren(requestNode, false)) {

			nodeStat = zk.exists(requestNode + "/" + replyNode, null);
			replyNodePath = requestNode + "/" + replyNode;
			props = getProperties(replyNodePath, nodeStat);
			replies.add(new LogReply(replyNode, props.getProperty("from"),
				requestUuid, Long.valueOf(props.getProperty("offset"))));
		}

		return replies;
	}

	@Override
	public LogReply getLogReply(String logName, String requestUuid, String replyUuid) throws Exception {

		String replyNode = String.format("%s/%s/%s/%s", LOGS_PATH, logName, requestUuid, replyUuid);
		Stat nodeStat = zk.exists(replyNode, null);
		Properties props = getProperties(replyNode, nodeStat);
		return new LogReply(replyUuid, props.getProperty("from"), requestUuid, Long.valueOf(props.getProperty("offset")));
	}

	public void getChildren(String logName, String requestUuid, Watcher watcher, ChildrenCallback cb, Object ctx) {

		String requestNode = String.format("%s/%s/%s", LOGS_PATH, logName, requestUuid);

		logger.debug("Setting watch on getChildren for new request node: {}", requestNode);
		zk.getChildren(requestNode, watcher, cb, ctx);
	}

	public void requestExists(String logName, String requestUuid, Watcher watcher, StatCallback cb) {

		String requestNode = String.format("%s/%s/%s", LOGS_PATH, logName, requestUuid);
		zk.exists(requestNode, watcher, cb, null);
	}

	@Override
	public LogInfo getLastLog(String logNamePrefix) throws Exception {
		if (!zk.getState().equals(ZooKeeper.States.CONNECTED)) {
			connect(zookeeperUrl);
		}

		// find last
		List<String> logs = zk.getChildren(LOGS_PATH, false);
		long maxLogIndex = 0;
		String lastLog = null;
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
		List<String> logs = zk.getChildren(LOGS_PATH, false);
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
		List<String> logs = zk.getChildren(LOGS_PATH, false);
		for (String logName : logs) {
			allLogs.add(getLogInfo(logName));
		}

		return allLogs;
	}


	private Properties getProperties(String node, Stat nodeStat) throws Exception {

		byte[] data = zk.getData(node, false, nodeStat);

		Properties properties = new Properties();

		if (data != null) {
			String nodeData = new String(zk.getData(node, false, nodeStat));
			String[] lines = nodeData.split("\n");
			for (int i = 0; i < lines.length; i++) {
				String key = StringUtils.substringBefore(lines[i], PROPERTIES_SEP);
				String value = StringUtils.substringAfter(lines[i], PROPERTIES_SEP);
				properties.put(key, value);
			}
		}

		return properties;
	}

	@Override
	public LogInfo getLogInfo(String logName) throws Exception {
		if (!zk.getState().equals(ZooKeeper.States.CONNECTED)) {
			connect(zookeeperUrl);
		}

		Stat nodeStat = getLogNodeStat(logName);
		String logNode = LOGS_PATH + "/" + logName;

		Properties props = getProperties(logNode, nodeStat);
		String servers = props.getProperty("bootstrap.servers");
		String uuid = props.getProperty("uuid");

		// fill stat info
		LogInfo logInfo = new LogInfo(logName, servers, uuid);
		logInfo.setZk_ctime(nodeStat.getCtime());

		return logInfo;
	}

	@Override
	public LogInfo getLogInfo(UUID uuid) throws Exception {
		Set<LogInfo> allLogs = getAllLogs();
		for (LogInfo logInfo : allLogs) {
			if (logInfo.getUuid().equalsIgnoreCase(uuid.toString())) {
				return logInfo;
			}
		}

		return null;
	}

	@Override
	public Properties getPeerProperties(UUID peerUuid) throws Exception {
		if (!zk.getState().equals(ZooKeeper.States.CONNECTED)) {
			connect(zookeeperUrl);
		}

		Stat nodeStat = getPeerNodeStat(peerUuid);
		String peerNode = PEERS_PATH + "/" + peerUuid;

		return getProperties(peerNode, nodeStat);
	}

	@Override
	public int getPeerCount() throws Exception {
		int count = 0;
		// find last
		List<String> peers = zk.getChildren(PEERS_PATH, false);
		return peers.size();

	}

	@Override
	public PeerInfo getPeerInfo(UUID peerUuid) throws Exception {
		if (!zk.getState().equals(ZooKeeper.States.CONNECTED)) {
			connect(zookeeperUrl);
		}

		PeerInfo peerInfo = new PeerInfo(peerUuid);

		Stat nodeStat = getPeerNodeStat(peerUuid);
		String peerNode = PEERS_PATH + "/" + peerUuid;

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
	public PeerInfo getPeerInfo(String peerUuid) throws Exception {
		return getPeerInfo(UUID.fromString(peerUuid));
	}

	@Override
	public Set<PeerInfo> getAllPeers() throws Exception {

		Set<PeerInfo> allPeers = new TreeSet();
		List<String> peers = zk.getChildren(PEERS_PATH, false);
		for (String uuid : peers) {
			allPeers.add(getPeerInfo(uuid));
		}

		return allPeers;
	}

	@Override
	public void deleteLogNamed(String logName) throws Exception {
		if (logExists(logName)) {
			// first delete any children request nodes
			deleteLogRequests(logName);

			String logNode = LOGS_PATH + "/" + logName;
			zk.delete(logNode, -1);
			logger.info("Unregistered (i.e. deleted) log node: {}", logName);
		}
	}

	@Override
	public void deleteAllLogs(String logNamePrefix) throws Exception {

		List<String> logs = zk.getChildren(LOGS_PATH, false);
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

	protected Stat getLogNodeStat(String logName) throws Exception {
		String logNode = LOGS_PATH + "/" + logName;
		return zk.exists(logNode, null);
	}

	protected Stat getPeerNodeStat(UUID peerUuid) throws Exception {
		String peerNode = PEERS_PATH + "/" + peerUuid;
		return zk.exists(peerNode, null);
	}

	@Override
	public boolean logExists(String logName) throws Exception {
		return getLogNodeStat(logName) != null;
	}

	@Override
	public boolean isConnectionEstablished() throws Exception {
		return zk != null && zk.getState().equals(ZooKeeper.States.CONNECTED);
	}

	@Override
	public String getUrl() {
		return zookeeperUrl;
	}

	@Override
	public void process(WatchedEvent watchedEvent) {
		logger.debug("Ignoring received event: {}", watchedEvent);
	}

	@Override
	public void close() {
		logger.info("Closing zookeeper connection to {}", zookeeperUrl);
		try {
			zk.close();
		} catch (InterruptedException ex) {
			logger.error("Error while closing down zookeeper connection", ex);
		}
	}
}
