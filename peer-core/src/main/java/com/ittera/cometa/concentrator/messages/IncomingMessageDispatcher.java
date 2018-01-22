package com.ittera.cometa.concentrator.messages;

public interface IncomingMessageDispatcher {

	void acceptConnections(boolean acceptConnections);

	void readFromLog(String logName, boolean skipWrittenOffsets) throws Exception;

	void readFromLog(String logName, boolean skipWrittenOffsets, Long initialOffset) throws Exception;
}
