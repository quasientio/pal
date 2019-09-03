package com.ittera.cometa.messages.protobuf;

import com.google.protobuf.InvalidProtocolBufferException;
import org.apache.kafka.common.serialization.Deserializer;

import com.ittera.cometa.messages.protobuf.data.Wrappers.ExecMessage;

import java.util.Map;

public class KafkaDeserializer implements Deserializer<ExecMessage> {

	@Override
	public void configure(Map map, boolean b) {

	}

	@Override
	public ExecMessage deserialize(String s, byte[] bytes) {
		ExecMessage message;
		try {
			message = ExecMessage.parseFrom(bytes);
		} catch (InvalidProtocolBufferException e) {
			throw new RuntimeException(e);
		}
		return message;
	}

	@Override
	public void close() {

	}
}
