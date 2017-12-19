package com.ittera.cometa.cxn;

import com.ittera.cometa.LogInfo;
import com.ittera.cometa.LogReply;
import com.ittera.cometa.PeerInfo;

import java.util.Properties;
import java.util.UUID;
import java.util.Set;

public interface PeerLogDirectory {

	void connect(String url) throws Exception;

	boolean isConnectionEstablished() throws Exception;

	String getUrl();

	void close();

	/**
	 * PEER METHODS
	 */

	void registerPeer(UUID peerUuid, Properties peerProperties) throws Exception;

	void unregisterPeer(UUID peerUuid) throws Exception;

	void unregisterAllPeers() throws Exception;

	boolean peerExists(UUID peerUuid) throws Exception;

	Properties getPeerProperties(UUID peerUuid) throws Exception;

	int getPeerCount() throws Exception;

	PeerInfo getPeerInfo(UUID peerUuid) throws Exception;

	PeerInfo getPeerInfo(String peerUuid) throws Exception;

	Set<PeerInfo> getAllPeers() throws Exception;

	/**
	 * LOG METHODS
	 */

	LogInfo addLog(String logNamePrefix, String bootstrapServers) throws Exception;

	String addLogRequest(String logName, String requestUuid) throws Exception;

	void addLogReply(String logName, LogReply reply) throws Exception;

	void deleteLogRequest(String logName, String requestUuid) throws Exception;

	void deleteLogRequests(String logName) throws Exception;

	Set<LogReply> getRepliesTo(String logName, String requestUuid) throws Exception;

	LogReply getLogReply(String logName, String requestUuid, String replyUuid) throws Exception;

	LogInfo getLastLog(String logNamePrefix) throws Exception;

	int getLogCount(String logNamePrefix) throws Exception;

	Set<LogInfo> getAllLogs() throws Exception;

	LogInfo getLogInfo(String logName) throws Exception;

	LogInfo getLogInfo(UUID uuid) throws Exception;

	void deleteLogNamed(String logName) throws Exception;

	void deleteAllLogs(String logNamePrefix) throws Exception;

	boolean logExists(String logName) throws Exception;

}
