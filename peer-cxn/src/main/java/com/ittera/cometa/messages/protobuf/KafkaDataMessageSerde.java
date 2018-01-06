package com.ittera.cometa.messages.protobuf;

import com.ittera.cometa.messages.protobuf.data.Wrappers.DataMessage;

import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.serialization.Serializer;

import java.util.Map;

public class KafkaDataMessageSerde implements Serde<DataMessage> {

	private final Serde<DataMessage> inner;

	public KafkaDataMessageSerde() {
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
	public Serializer<DataMessage> serializer() {
		return inner.serializer();
	}

	@Override
	public Deserializer<DataMessage> deserializer() {
		return inner.deserializer();
	}
}
