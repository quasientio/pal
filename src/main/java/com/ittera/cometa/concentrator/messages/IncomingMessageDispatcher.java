package com.ittera.cometa.concentrator.messages;

public interface IncomingMessageDispatcher {

    void acceptConnections(boolean acceptConnections);

    void readFromLastLog(String logNamePrefix) throws Exception;
}
