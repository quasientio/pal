package com.ittera.cometa.concentrator;

import org.junit.AfterClass;
import org.junit.Test;

import static org.junit.Assert.*;

import java.util.Properties;
import java.util.UUID;
import java.util.Set;
import java.util.HashSet;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

/**
 * Naming convention to use: MethodName_StateUnderTest_ExpectedBehavior
 */
public class ZkClientTest {

    protected final static Logger logger = LogManager.getLogger("tests");

    private static final String zookeeperUrl = "localhost:2181";
    private static final Set<UUID> createdPeers = new HashSet<>();
    private static final Set<String> createdLogs = new HashSet<>();

    @Test
    public void registerPeer_newPeer_peerCreated() throws Exception {
        PeerLogDirectory zkCli = new ZkClient(zookeeperUrl);

        UUID peerUuid = UUID.randomUUID();
        Properties peerProps = new Properties();
        peerProps.put("peerAddr", "tcp://127.0.0.1:5671");

        zkCli.registerPeer(peerUuid, peerProps);
        assertTrue(zkCli.peerExists(peerUuid));
        createdPeers.add(peerUuid);

        zkCli.close();
    }


    @Test
    public void peerExists_existingPeer_true() throws Exception {
        PeerLogDirectory zkCli = new ZkClient(zookeeperUrl);

        UUID peerUuid = UUID.randomUUID();
        Properties peerProps = new Properties();
        peerProps.put("peerAddr", "tcp://127.0.0.1:5671");

        zkCli.registerPeer(peerUuid, peerProps);

        assertTrue(zkCli.peerExists(peerUuid));
        createdPeers.add(peerUuid);

        zkCli.close();
    }

    @Test
    public void peerExists_nonExistingPeer_false() throws Exception {
        PeerLogDirectory zkCli = new ZkClient(zookeeperUrl);
        UUID peerUuid = UUID.randomUUID();

        assertFalse(zkCli.peerExists(peerUuid));
        zkCli.close();
    }

    @Test
    public void unregisterPeer_existingPeer_peerDeleted() throws Exception {

        PeerLogDirectory zkCli = new ZkClient(zookeeperUrl);

        Properties peerProps = new Properties();
        peerProps.put("peerAddr", "tcp://127.0.0.1:5671");

        // create
        UUID peerUuid = UUID.randomUUID();
        zkCli.registerPeer(peerUuid, peerProps);

        assertTrue(zkCli.peerExists(peerUuid));
        createdPeers.add(peerUuid);

        // delete
        zkCli.unregisterPeer(peerUuid);
        assertFalse(zkCli.peerExists(peerUuid));
        createdPeers.remove(peerUuid);

        zkCli.close();
    }

    @Test
    public void addLog_newLog_logCreated() throws Exception {
        PeerLogDirectory zkCli = new ZkClient(zookeeperUrl);

        String logName = "test.topic";
        Properties logProps = new Properties();
        logProps.put("bootstrap.servers", "localhost:9092");

        String newLogPath = zkCli.addLog(logName, logProps);
        String createdLogName = newLogPath;

        assertTrue(zkCli.logExists(createdLogName));
        createdLogs.add(createdLogName);

        zkCli.close();
    }


    @Test
    public void logExists_existingLog_true() throws Exception {
        PeerLogDirectory zkCli = new ZkClient(zookeeperUrl);

        String logName = "test.topic";
        Properties logProps = new Properties();
        logProps.put("bootstrap.servers", "localhost:9092");

        String newLogPath = zkCli.addLog(logName, logProps);
        String createdLogName = newLogPath;

        assertTrue(zkCli.logExists(createdLogName));
        createdLogs.add(createdLogName);

        zkCli.close();
    }

    @Test
    public void getLastLog_someLogsMatch_last() throws Exception {
        PeerLogDirectory zkCli = new ZkClient(zookeeperUrl);

        // make sure no logs around
        this.deleteLog_existingLog_logDeleted();

        String logNamePrefix = "test.topic";
        Properties logProps = new Properties();
        logProps.put("bootstrap.servers", "localhost:9092");

        // create  a few
        String lastCreated = null;
        for (int i = 6; i > 0; i--) {
            lastCreated = zkCli.addLog(logNamePrefix, logProps);
            createdLogs.add(lastCreated);
        }

        assertEquals(lastCreated, zkCli.getLastLog(logNamePrefix));

        zkCli.close();
    }


    @Test
    public void logExists_nonExistingLog_false() throws Exception {
        PeerLogDirectory zkCli = new ZkClient(zookeeperUrl);
        String logName = "test.topic";

        assertFalse(zkCli.logExists(logName));
        zkCli.close();
    }

    @Test
    public void deleteLog_existingLog_logDeleted() throws Exception {
        PeerLogDirectory zkCli = new ZkClient(zookeeperUrl);

        String logName = "test.topic";
        Properties logProps = new Properties();
        logProps.put("bootstrap.servers", "localhost:9092");

        String newLogPath = zkCli.addLog(logName, logProps);
        String createdLogName = newLogPath;

        assertTrue(zkCli.logExists(createdLogName));

        zkCli.deleteLog(createdLogName);
        assertFalse(zkCli.logExists(createdLogName));

        zkCli.close();
    }

    @Test
    public void getLogProperties_existingLog_logProperties() throws Exception {
        PeerLogDirectory zkCli = new ZkClient(zookeeperUrl);

        String logName = "test.topic";
        Properties logProps = new Properties();
        logProps.put("bootstrap.servers", "localhost:9092");

        String createdLogName = zkCli.addLog(logName, logProps);
        createdLogs.add(createdLogName);

        // now load and compare
        Properties propsLoaded = zkCli.getLogProperties(createdLogName);
        assertEquals(logProps, propsLoaded);
    }

    @Test
    public void getPeerProperties_existingPeer_peerProperties() throws Exception {
        PeerLogDirectory zkCli = new ZkClient(zookeeperUrl);

        Properties peerProps = new Properties();
        peerProps.put("peerAddr", "tcp://127.0.0.1:5671");

        // create
        UUID peerUuid = UUID.randomUUID();
        zkCli.registerPeer(peerUuid, peerProps);
        createdPeers.add(peerUuid);

        // now load and compare
        Properties propsLoaded = zkCli.getPeerProperties(peerUuid);
        assertEquals(peerProps, propsLoaded);
    }


    // TODO
    // get peer node's address


    @AfterClass
    public static void deleteCreatedPeersAndLogs() throws Exception {
        PeerLogDirectory zkCli = new ZkClient(zookeeperUrl);

        for (UUID peer : createdPeers) {
            zkCli.unregisterPeer(peer);
            logger.info("Cleaned up left over peer: {}", peer);
        }

        for (String log : createdLogs) {
            zkCli.deleteLog(log);
            logger.info("Cleaned up left over peer: {}", log);
        }

        zkCli.close();
    }

}
