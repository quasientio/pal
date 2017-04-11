package com.ittera.cometa.concentrator;

import org.zeromq.ZContext;

public interface OutgoingMessageDispatcher {
    ZContext getZContext();
}
