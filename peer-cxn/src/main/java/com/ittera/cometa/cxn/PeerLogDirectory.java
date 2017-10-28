package com.ittera.cometa.cxn;

import com.ittera.cometa.LogInfo;
import com.ittera.cometa.PeerInfo;

import java.util.Properties;
import java.util.UUID;
import java.util.Set;

public interface PeerLogDirectory {
    void connect(String zookeeperUrl) throws Exception;

    void registerPeer(UUID peerUuid, Properties peerProperties) throws Exception;

    void unregisterPeer(UUID peerUuid) throws Exception;

    void unregisterAllPeers() throws Exception;

    boolean peerExists(UUID peerUuid) throws Exception;

    Properties getPeerProperties(UUID peerUuid) throws Exception;

    int getPeerCount() throws Exception;

    PeerInfo getPeerInfo(UUID peerUuid) throws Exception;

    PeerInfo getPeerInfo(String peerUuid) throws Exception;

    Set<PeerInfo> getAllPeers() throws Exception;

    LogInfo addLog(String logNamePrefix, String bootstrapServers) throws Exception;

    LogInfo getLastLog(String logNamePrefix) throws Exception;

    int getLogCount(String logNamePrefix) throws Exception;

    Set<LogInfo> getAllLogs() throws Exception;

    LogInfo getLogInfo(String logName) throws Exception;

    void deleteLogNamed(String logName) throws Exception;

    void deleteAllLogs(String logNamePrefix) throws Exception;

    boolean logExists(String logName) throws Exception;

    boolean isConnectionEstablished() throws Exception;

    String getUrl();

    void close();
}
