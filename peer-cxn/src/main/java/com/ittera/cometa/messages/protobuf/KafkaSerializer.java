package com.ittera.cometa.messages.protobuf;

import com.ittera.cometa.messages.protobuf.data.Wrappers.ExecMessage;
import org.apache.kafka.common.serialization.Serializer;

import java.util.Map;

public class KafkaSerializer implements Serializer<ExecMessage> {

	@Override
	public void configure(Map map, boolean b) {

	}

	@Override
	public byte[] serialize(String s, ExecMessage execMessage) {
		return execMessage.toByteArray();
	}

	@Override
	public void close() {

	}
}
