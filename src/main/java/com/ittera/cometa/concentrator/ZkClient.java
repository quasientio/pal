package com.ittera.cometa.concentrator;

import java.util.UUID;
import java.util.Properties;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.google.inject.name.Named;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import org.apache.zookeeper.*;
import org.apache.zookeeper.data.Stat;

import org.apache.commons.lang3.StringUtils;

@Singleton
public class ZkClient implements Watcher, PeerLogDirectory {

    protected static final Logger logger = LogManager.getLogger(ZkClient.class);

    protected static final String PROPERTIES_SEP = "|";

    // root paths
    public static final String ROOT_PATH = "/cometa";
    public static final String PEERS_PATH = ROOT_PATH + "/peers";
    public static final String LOGS_PATH = ROOT_PATH + "/logs";

    public static final int SESSION_TIMEOUT = 3000;

    private ZooKeeper zk;

    @Inject
    public ZkClient(@Named("zookeeper.url") String zookeeperUrl) throws Exception {
        zk = new ZooKeeper(zookeeperUrl, SESSION_TIMEOUT, this);
        ensureRootAndSubdirsExist();
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
            zk.delete(peerNode, 0);
            logger.info("Unregistered (i.e. deleted) peer node with uuid: {}", peerUuid);
        }
    }

    @Override
    public boolean peerExists(UUID peerUuid) throws Exception {
        String peerNode = PEERS_PATH + "/" + peerUuid;
        return zk.exists(peerNode, null) != null;
    }

    @Override
    public String addLog(String logNamePrefix, Properties logProperties) throws Exception {
        String logNodePrefix = LOGS_PATH + "/" + logNamePrefix;

        byte[] data = null;

        if (logProperties != null && !logProperties.isEmpty()) {
            StringBuffer sb = new StringBuffer();
            int propIdx = 1;
            for (String propKey : logProperties.stringPropertyNames()) {
                sb.append(propKey.trim()).append(PROPERTIES_SEP).append(logProperties.getProperty(propKey).trim());
                if (propIdx++ < logProperties.size()) {
                    sb.append('\n');
                }
            }
            data = sb.toString().getBytes();
        }

        String createdNode = zk.create(logNodePrefix, data, ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT_SEQUENTIAL);
        String createdLogName = StringUtils.substringAfterLast(createdNode, "/");
        logger.info("Created new log node: {} with full path: {}", createdLogName, createdNode);
        return createdLogName;
    }

    @Override
    public String getLastLog(String logNamePrefix) throws Exception {

        //find last
        //String createdLogName = StringUtils.substringAfterLast(newLogPath, "/");
        List<String> logs = zk.getChildren(LOGS_PATH, false);
        long maxLogIndex = 0;
        String lastLog = null;
        // loop through all logs
        for (String log : logs) {
            // filter out those with our prefix
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

        logger.debug("log with max index = {}", lastLog);

        return lastLog;
    }


    private Properties getProperties(String node, Stat nodeStat) throws Exception {
        String nodeData = new String(zk.getData(node, false, nodeStat));
        String[] lines = nodeData.split("\n");
        Properties properties = new Properties();
        for (int i = 0; i < lines.length; i++) {
            String key = StringUtils.substringBefore(lines[i], PROPERTIES_SEP);
            String value = StringUtils.substringAfter(lines[i], PROPERTIES_SEP);
            properties.put(key, value);
        }

        return properties;
    }

    @Override
    public Properties getLogProperties(String logName) throws Exception {
        Stat nodeStat = getLogNodeStat(logName);
        String logNode = LOGS_PATH + "/" + logName;

        return getProperties(logNode, nodeStat);
    }

    @Override
    public Properties getPeerProperties(UUID peerUuid) throws Exception {
        Stat nodeStat = getPeerNodeStat(peerUuid);
        String peerNode = PEERS_PATH + "/" + peerUuid;

        return getProperties(peerNode, nodeStat);
    }

    @Override
    public void deleteLog(String logName) throws Exception {
        if (logExists(logName)) {
            String logNode = LOGS_PATH + "/" + logName;
            zk.delete(logNode, 0);
            logger.info("Unregistered (i.e. deleted) log node: {}", logName);
        }
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
    public void process(WatchedEvent watchedEvent) {
        logger.debug("Got event: {}", watchedEvent);
    }

    @Override
    public void close() {
        try {
            zk.close();
            logger.info("Closed zookeeper connection");
        } catch (InterruptedException ex) {
            logger.error("Error while closing down zookeeper client", ex);
        }
    }
}
