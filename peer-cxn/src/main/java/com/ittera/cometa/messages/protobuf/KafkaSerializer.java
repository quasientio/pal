package com.ittera.cometa.messages.protobuf;

import com.ittera.cometa.messages.protobuf.data.Wrappers;
import org.apache.kafka.common.serialization.Serializer;

import java.util.Map;

public class KafkaSerializer implements Serializer {

    @Override
    public void configure(Map map, boolean b) {

    }

    @Override
    public byte[] serialize(String s, Object o) {
        return ((Wrappers.DataMessage) o).toByteArray();
    }

    @Override
    public void close() {

    }
}
