package com.ittera.cometa.concentrator;

import com.ittera.cometa.LogInfo;

public interface KafkaMessageWriter {
	void openConnections();

	void writeToLog(LogInfo outLog, LogInfo inLog, boolean publishOffsets) throws Exception;
}
