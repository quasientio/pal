package com.ittera.cometa;

import java.util.UUID;

public class LogRequest implements Comparable {

	private final UUID uuid;
	private LogInfo outputLog;

	public LogRequest(UUID uuid) {
		this.uuid = uuid;
	}

	public LogRequest(UUID uuid, LogInfo outputLog) {
		this(uuid);
		this.outputLog = outputLog;
	}

	public UUID getUuid() {
		return uuid;
	}

	public LogInfo getOutputLog() {
		return outputLog;
	}

	@Override
	public int compareTo(Object o) {
		return getUuid().compareTo(((LogRequest) o).getUuid());
	}

	@Override
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append("LogRequest {uuid: ").append(getUuid());
		if (getOutputLog() != null) {
			sb.append(", outputLog: ").append(getOutputLog().getName());
		}
		sb.append('}');
		return sb.toString();
	}
}
