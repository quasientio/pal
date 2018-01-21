package com.ittera.cometa;

public class LogReply implements Comparable {

	private String uuid;
	private String peerUuid;
	private String isReplyTo;
	private long offset;

	public LogReply(String uuid, String peerUuid, String isReplyTo, long offset) {
		this.uuid = uuid;
		this.peerUuid = peerUuid;
		this.isReplyTo = isReplyTo;
		this.offset = offset;
	}

	public String getUuid() {
		return uuid;
	}

	public long getOffset() {
		return offset;
	}

	public String getIsReplyTo() {
		return isReplyTo;
	}

	public String getPeerUuid() {
		return peerUuid;
	}

	@Override
	public int compareTo(Object o) {
		return Long.valueOf(getOffset()).compareTo(Long.valueOf(((LogReply) o).getOffset()));
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("LogReply {uuid: ").append(getUuid());
		sb.append(", offset: ").append(getOffset());
		sb.append(", from-peer: ").append(getPeerUuid());
		sb.append(", isReplyTo: ").append(getIsReplyTo());
		sb.append('}');
		return sb.toString();
	}
}
