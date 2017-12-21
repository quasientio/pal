package com.ittera.cometa.concentrator.messages;

public interface IncomingMessageDispatcher {

	void acceptConnections(boolean acceptConnections);

	void readFromLog(String logName) throws Exception;

	void readFromLog(String logName, Long initialOffset) throws Exception;
}
