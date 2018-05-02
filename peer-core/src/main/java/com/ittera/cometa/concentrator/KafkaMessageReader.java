package com.ittera.cometa.concentrator;

public interface KafkaMessageReader {

	void acceptConnections(boolean acceptConnections);

	void readFromLog(String logName, boolean skipWrittenOffsets, Long initialOffset) throws Exception;
}
