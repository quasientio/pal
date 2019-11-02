package com.ittera.cometa.messages.protobuf;

import com.ittera.cometa.messages.protobuf.Wrappers.ExecMessage;
import java.util.Map;
import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.serialization.Serializer;

public class KafkaExecMessageSerde implements Serde<ExecMessage> {

  private final Serde inner;

  public KafkaExecMessageSerde() {
    inner = Serdes.serdeFrom(new KafkaSerializer(), new KafkaDeserializer());
  }

  @Override
  public void configure(Map map, boolean b) {
    inner.serializer().configure(map, b);
    inner.deserializer().configure(map, b);
  }

  @Override
  public void close() {
    inner.serializer().close();
    inner.deserializer().close();
  }

  @Override
  public Serializer<ExecMessage> serializer() {
    return inner.serializer();
  }

  @Override
  public Deserializer<ExecMessage> deserializer() {
    return inner.deserializer();
  }
}
