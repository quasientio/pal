package com.ittera.cometa.messages;

import org.apache.kafka.common.header.Headers;

import java.util.Arrays;
import java.util.stream.Collectors;

/** Used by ContextFillingTransformSupplier to encapsulate context of a kafka log message
 *
 */
public class MessageContext {
	private final long offset;
	private final long timestamp;
	private final Headers headers;
	private final int partition;
	private final String topic;

	MessageContext(long offset, int partition, long timestamp, String topic, Headers headers) {
		this.offset = offset;
		this.partition = partition;
		this.timestamp = timestamp;
		this.topic = topic;
		this.headers = headers;
	}

	public long getOffset() {
		return offset;
	}

	public long getTimestamp() {
		return timestamp;
	}

	public Headers getHeaders() {
		return headers;
	}

	public int getPartition() {
		return partition;
	}

	public String getTopic() {
		return topic;
	}

	public String getHeadersToString() {
		return String.format("[%s]", Arrays.stream(getHeaders().toArray()).map(Object::toString)
			.collect(Collectors.joining(",")));
	}
}
