package com.ittera.cometa.messages.protobuf;

import com.google.protobuf.InvalidProtocolBufferException;
import org.apache.kafka.common.serialization.Deserializer;

import com.ittera.cometa.messages.protobuf.data.Wrappers.DataMessage;

import java.util.Map;

public class KafkaDeserializer implements Deserializer<DataMessage> {

	@Override
	public void configure(Map map, boolean b) {

	}

	@Override
	public DataMessage deserialize(String s, byte[] bytes) {
		DataMessage message;
		try {
			message = DataMessage.parseFrom(bytes);
		} catch (InvalidProtocolBufferException e) {
			throw new RuntimeException(e);
		}
		return message;
	}

	@Override
	public void close() {

	}
}
