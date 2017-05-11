package com.ittera.cometa.concentrator;

import java.util.Properties;
import java.util.UUID;

public interface PeerLogDirectory {
    void registerPeer(UUID peerUuid, Properties peerProperties) throws Exception;

    void unregisterPeer(UUID peerUuid) throws Exception;

    boolean peerExists(UUID peerUuid) throws Exception;

    String addLog(String logNamePrefix, Properties logProperties) throws Exception;

    String getLastLog(String logNamePrefix) throws Exception;

    Properties getLogProperties(String logName) throws Exception;

    Properties getPeerProperties(UUID peerUuid) throws Exception;

    void deleteLog(String logName) throws Exception;

    void deleteAllLogs(String logNamePrefix) throws Exception;

    boolean logExists(String logName) throws Exception;

    void close();
}
