package com.ittera.cometa.distributor.messages;

import org.apache.kafka.common.serialization.Serializer;

import java.util.Map;

public class ProtobufSerializer implements Serializer {

  @Override
  public void configure(Map map, boolean b) {

  }

  @Override
  public byte[] serialize(String s, Object o) {
    return ((com.ittera.cometa.distributor.messages.data.Wrappers.DataMessage) o).toByteArray();
  }

  @Override
  public void close() {

  }
}
