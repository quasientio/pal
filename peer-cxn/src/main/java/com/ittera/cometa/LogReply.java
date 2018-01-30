package com.ittera.cometa;

import java.util.UUID;

public class LogReply implements Comparable {

	private final UUID uuid;
	private final UUID peerUuid;
	private final UUID isReplyTo;
	private final long offset;

	public LogReply(UUID uuid, UUID peerUuid, UUID isReplyTo, long offset) {
		this.uuid = uuid;
		this.peerUuid = peerUuid;
		this.isReplyTo = isReplyTo;
		this.offset = offset;
	}

	public UUID getUuid() {
		return uuid;
	}

	public long getOffset() {
		return offset;
	}

	public UUID getIsReplyTo() {
		return isReplyTo;
	}

	public UUID getPeerUuid() {
		return peerUuid;
	}

	@Override
	public int compareTo(Object o) {
		return Long.compare(getOffset(), ((LogReply) o).getOffset());
	}

	@Override
	public String toString() {
		return "LogReply {uuid: " + getUuid() + ", offset: " + getOffset() + ", from-peer: " + getPeerUuid()
			+ ", isReplyTo: " + getIsReplyTo() + '}';
	}
}
