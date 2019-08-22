package com.ittera.cometa.messages;

import org.apache.kafka.common.header.Header;

public class LogMessageHeader implements Header {
	private final String key;
	private final byte[] value;

	public LogMessageHeader(String key, byte[] value) {
		this.key = key;
		this.value = value;
	}

	public String key() {
		return key;
	}

	public byte[] value() {
		return value;
	}
}
