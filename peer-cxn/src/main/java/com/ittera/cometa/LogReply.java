package com.ittera.cometa;

public class LogReply implements Comparable {

	private String uuid;
	private String peerUuid;
	private String replyTo;
	private long offset;

	public LogReply(String uuid, String peerUuid, String replyTo, long offset) {
		this.uuid = uuid;
		this.peerUuid = peerUuid;
		this.replyTo = replyTo;
		this.offset = offset;
	}

	public String getUuid() {
		return uuid;
	}

	public long getOffset() {
		return offset;
	}

	public String getReplyTo() {
		return replyTo;
	}

	public String getPeerUuid() {
		return peerUuid;
	}

	@Override
	public int compareTo(Object o) {
		return Long.valueOf(getOffset()).compareTo(Long.valueOf(((LogReply) o).getOffset()));
	}
}
