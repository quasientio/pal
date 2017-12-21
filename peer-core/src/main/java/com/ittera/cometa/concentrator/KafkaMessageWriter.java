package com.ittera.cometa.concentrator;

public interface KafkaMessageWriter {
	void openConnections();

	void writeToLog(String logName) throws Exception;
}
