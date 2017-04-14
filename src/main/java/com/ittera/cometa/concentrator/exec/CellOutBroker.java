package com.ittera.cometa.concentrator.exec;

import com.ittera.cometa.concentrator.messages.protobuf.data.Wrappers.DataMessage;

public interface CellOutBroker {
    DataMessage sendAndRecv(DataMessage dataMessage);
}
