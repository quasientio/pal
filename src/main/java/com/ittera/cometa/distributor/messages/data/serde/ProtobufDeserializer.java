package com.ittera.cometa.distributor.messages.data.serde;

import com.google.protobuf.InvalidProtocolBufferException;
import org.apache.kafka.common.serialization.Deserializer;

import com.ittera.cometa.distributor.messages.data.Wrappers;

import java.util.Map;

public class ProtobufDeserializer implements Deserializer {

  @Override
  public void configure(Map map, boolean b) {

  }

  @Override
  public Object deserialize(String s, byte[] bytes) {
    Object obj = null;
    try {
      obj = Wrappers.DataMessage.parseFrom(bytes);
    } catch (InvalidProtocolBufferException e) {
      obj = e;
    }
    return obj;
  }

  @Override
  public void close() {

  }
}
