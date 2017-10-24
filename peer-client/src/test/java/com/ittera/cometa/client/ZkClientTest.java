package com.ittera.cometa.client;

import com.ittera.cometa.LogInfo;
import com.ittera.cometa.PeerInfo;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.After;
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

    private PeerLogDirectory zkCli ;

    @Before
    public void setup() throws Exception {
        zkCli = new ZkClient(zookeeperUrl);
    }
    
    @After
    public void cleanup() throws Exception {
        zkCli.close();
    }

    @Test
    public void registerPeer_newPeer_peerCreated() throws Exception {

        UUID peerUuid = UUID.randomUUID();
        Properties peerProps = new Properties();
        peerProps.put("peerAddr", "tcp://127.0.0.1:5671");

        zkCli.registerPeer(peerUuid, peerProps);
        assertTrue(zkCli.peerExists(peerUuid));
        createdPeers.add(peerUuid);
    }


    @Test
    public void peerExists_existingPeer_true() throws Exception {

        UUID peerUuid = UUID.randomUUID();
        Properties peerProps = new Properties();
        peerProps.put("peerAddr", "tcp://127.0.0.1:5671");

        zkCli.registerPeer(peerUuid, peerProps);

        assertTrue(zkCli.peerExists(peerUuid));
        createdPeers.add(peerUuid);
    }

    @Test
    public void peerExists_nonExistingPeer_false() throws Exception {
      
        UUID peerUuid = UUID.randomUUID();
        
        assertFalse(zkCli.peerExists(peerUuid));
    }

    @Test
    public void unregisterPeer_existingPeer_peerDeleted() throws Exception {

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
    }

    @Test
    public void addLog_newLog_logCreated() throws Exception {

        String logName = "test.topic";

        LogInfo newLogInfo = zkCli.addLog(logName, "localhost:9092");
        String createdLogName = newLogInfo.getName();

        assertTrue(zkCli.logExists(createdLogName));
        assertNotNull(newLogInfo.getUuid());
        createdLogs.add(createdLogName);
    }


    @Test
    public void logExists_existingLog_true() throws Exception {

        String logName = "test.topic";

        LogInfo newLogInfo = zkCli.addLog(logName, "localhost:9092");
        String createdLogName = newLogInfo.getName();

        assertTrue(zkCli.logExists(createdLogName));
        createdLogs.add(createdLogName);
    }

    @Test
    public void getLastLog_someLogsMatch_last() throws Exception {

        String logNamePrefix = "test.topic";

        // make sure no logs around
        zkCli.deleteAllLogs(logNamePrefix);
        assertEquals(0, zkCli.getAllLogs().size());

        // create  a few
        String lastCreated = null;
        for (int i = 6; i > 0; i--) {
            LogInfo newLogInfo = zkCli.addLog(logNamePrefix, "localhost:9092");
            lastCreated = newLogInfo.getName();
            createdLogs.add(lastCreated);
        }

        assertEquals(lastCreated, zkCli.getLastLog(logNamePrefix).getName());
    }

    @Test
    public void getAllLogs_someLogsExist_all() throws Exception {

        String logNamePrefix = "test.topic";

        // make sure no logs around
        zkCli.deleteAllLogs(logNamePrefix);
        assertEquals(0, zkCli.getAllLogs().size());

        // create N nodes
        String lastCreated = null;
        int N = 30;
        for (int i = N; i > 0; i--) {
            LogInfo newLogInfo = zkCli.addLog(logNamePrefix, "localhost:9092");
            createdLogs.add(newLogInfo.getName());
        }

        assertEquals(N, zkCli.getAllLogs().size());
    }


    @Test
    public void logExists_nonExistingLog_false() throws Exception {
        String logName = "test.topic";

        assertFalse(zkCli.logExists(logName));
    }

    @Test
    public void deleteLog_existingLog_logDeleted() throws Exception {

        String logName = "test.topic";

        LogInfo newLogInfo = zkCli.addLog(logName, "localhost:9092");
        String createdLogName = newLogInfo.getName();

        assertTrue(zkCli.logExists(createdLogName));

        zkCli.deleteLogNamed(createdLogName);
        assertFalse(zkCli.logExists(createdLogName));
    }

    @Test
    public void deleteAllLogs_matchingLogs_allMatchingDeleted() throws Exception {

        String logNamePrefix = "test.topic";

        // create  a few
        String lastCreated = null;
        for (int i = 0; i > 10; i--) {
            LogInfo newLogInfo = zkCli.addLog(logNamePrefix, "localhost:9092");
            createdLogs.add(newLogInfo.getName());
        }

        assertNotEquals(0, zkCli.getLogCount(logNamePrefix));
        zkCli.deleteAllLogs(logNamePrefix);
        assertEquals(0, zkCli.getLogCount(logNamePrefix));
    }

    @Test
    public void getLogProperties_existingLog_logInfo() throws Exception {

        String logName = "test.topic";
        String bootstrapServers = "localhost:9092";

        LogInfo newLogInfo = zkCli.addLog(logName, bootstrapServers);
        createdLogs.add(newLogInfo.getName());

        // now compare
        assertEquals(bootstrapServers, newLogInfo.getBootstrapServers());
    }

    @Test
    public void getPeerProperties_existingPeer_peerProperties() throws Exception {

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

    @Test
    public void isConnectionEstablished_notConnected_false() throws Exception {

        PeerLogDirectory detachedZkCli = new ZkClient();
        assertFalse(detachedZkCli.isConnectionEstablished());
    }

    @Test
    public void isConnectionEstablished_connected_true() throws Exception {

        assertTrue(zkCli.isConnectionEstablished());
    }

    @Test
    public void getAllPeers_noPeers_emptySet() throws Exception {

        // first make sure we have no peers
        deleteCreatedPeers();

        Set<PeerInfo> allPeers = zkCli.getAllPeers();
        assertTrue(allPeers.isEmpty());
    }

    @Test
    public void getAllPeers_somePeers_nonEmptySet() throws Exception {

        // create a peer
        UUID peerUuid = UUID.randomUUID();
        Properties peerProps = new Properties();
        peerProps.put("peerAddr", "tcp://127.0.0.1:5671");

        zkCli.registerPeer(peerUuid, peerProps);
        createdPeers.add(peerUuid);

        // create a second peer
        peerUuid = UUID.randomUUID();
        peerProps = new Properties();
        peerProps.put("peerAddr", "tcp://127.0.0.1:5671");

        zkCli.registerPeer(peerUuid, peerProps);
        createdPeers.add(peerUuid);

        // now check
        Set<PeerInfo> allPeers = zkCli.getAllPeers();
        assertEquals(2, allPeers.size());

    }

    private static void deleteCreatedPeers() throws Exception {
        PeerLogDirectory zkCli = new ZkClient(zookeeperUrl);

        for (UUID peer : createdPeers) {
            zkCli.unregisterPeer(peer);
            logger.info("Cleaned up left over peer: {}", peer);
        }

        zkCli.close();
    }

    private static void deleteCreatedLogs() throws Exception {
        PeerLogDirectory zkCli = new ZkClient(zookeeperUrl);

        for (String log : createdLogs) {
            zkCli.deleteLogNamed(log);
            logger.info("Cleaned up left over log: {}", log);
        }

        zkCli.close();
    }

    @AfterClass
    public static void deleteCreatedPeersAndLogs() throws Exception {

        deleteCreatedPeers();
        deleteCreatedLogs();
    }
}
