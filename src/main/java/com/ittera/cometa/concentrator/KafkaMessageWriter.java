package com.ittera.cometa.concentrator;

import org.zeromq.ZContext;

public interface KafkaMessageWriter {
    void openConnections(ZContext zmqContext);
}
