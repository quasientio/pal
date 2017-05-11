package com.ittera.cometa.concentrator;

public interface KafkaMessageWriter {
    void openConnections();
    void writeToLastLog(String logNamePrefix) throws Exception;
}
