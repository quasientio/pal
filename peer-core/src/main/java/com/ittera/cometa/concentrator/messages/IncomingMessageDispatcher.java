package com.ittera.cometa.concentrator.messages;

public interface IncomingMessageDispatcher {

	void acceptConnections(boolean acceptConnections);

	void readFromLog(String logName) throws Exception;

	void readFromLastLog(String logNamePrefix) throws Exception;
}
