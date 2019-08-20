package com.ittera.cometa.messages;

import org.apache.kafka.common.header.Header;

import java.io.UnsupportedEncodingException;

public class LogMessageHeader implements Header {
	private final String key;
	private final String value;

	public LogMessageHeader(String key, String value) {
		this.key = key;
		this.value = value;
	}

	public String key() {
		return key;
	}

	public byte[] value() {
		try {
			return value.getBytes("UTF-8");
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
			return null;
		}
	}
}
