package com.ittera.cometa.messages.protobuf;

import com.ittera.cometa.messages.protobuf.data.Wrappers.DataMessage;
import org.apache.kafka.common.serialization.Serializer;

import java.util.Map;

public class KafkaSerializer implements Serializer<DataMessage> {

	@Override
	public void configure(Map map, boolean b) {

	}

	@Override
	public byte[] serialize(String s, DataMessage dataMessage) {
		return dataMessage.toByteArray();
	}

	@Override
	public void close() {

	}
}
