package com.ittera.cometa;

public class LogRequest implements Comparable {

	private String uuid;
	private LogInfo outputLog;

	public LogRequest(String uuid) {
		this.uuid = uuid;
	}

	public LogRequest(String uuid, LogInfo outputLog) {
		this(uuid);
		this.outputLog = outputLog;
	}

	public String getUuid() {
		return uuid;
	}

	public LogInfo getOutputLog() {
		return outputLog;
	}

	@Override
	public int compareTo(Object o) {
		return uuid.compareTo((String) o);
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
