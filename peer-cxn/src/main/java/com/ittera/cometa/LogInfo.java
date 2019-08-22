package com.ittera.cometa;

import com.ittera.cometa.util.ByteSizeConverter;

import java.util.*;

public class LogInfo implements Comparable {

	// name of node in zk
	private final String name;

	// in zk node stat
	private long zk_ctime;

	// in zk node data
	private UUID uuid;

	// to be filled from (kafka) mbeans via jmx
	private long startOffset;
	private long endOffset;
	private long bytes;
	private boolean exists;

	// computed fields
	private String humanReadableByteSize;
	private String bootstrapServers;

	public LogInfo(String name) {
		this.name = name;
	}

	public LogInfo(String name, Set<KafkaBrokerInfo> brokerInfoSet) {
		this(name);
		setBrokerInfoSet(brokerInfoSet);
	}

	public LogInfo(String name, Set<KafkaBrokerInfo> brokerInfoSet, UUID uuid) {
		this(name, brokerInfoSet);
		this.uuid = uuid;
	}

	public String getName() {
		return name;
	}

	public void setUuid(UUID uuid) {
		this.uuid = uuid;
	}

	public UUID getUuid() {
		return uuid;
	}

	public String getBootstrapServers() {
		return bootstrapServers;
	}

	public void setBrokerInfoSet(Set<KafkaBrokerInfo> brokerInfoSet) {

		// assign bootstrap servers
		if (brokerInfoSet == null) {
			this.bootstrapServers = null;
		} else {
			List<String> urlList = new ArrayList<>();
			for (KafkaBrokerInfo brokerInfo : brokerInfoSet) {
				Arrays.stream(brokerInfo.getEndpoints()).map(KafkaBrokerEndpoint::toURL).forEach(urlList::add);
			}
			this.bootstrapServers = String.join(",", urlList);
		}
	}

	public void setZk_ctime(long ctime) {
		this.zk_ctime = ctime;
	}

	public long getZk_ctime() {
		return zk_ctime;
	}

	public String getHumanReadableByteSize() {
		return humanReadableByteSize;
	}

	public long getStartOffset() {
		return startOffset;
	}

	public void setStartOffset(long startOffset) {
		this.startOffset = startOffset;
	}

	public long getEndOffset() {
		return endOffset;
	}

	public void setEndOffset(long endOffset) {
		this.endOffset = endOffset;
	}

	public long getBytes() {
		return bytes;
	}

	public void setBytes(long bytes) {
		this.bytes = bytes;
		humanReadableByteSize = ByteSizeConverter.humanReadableByteCount(getBytes(), false);
	}

	public boolean isExists() {
		return exists;
	}

	public void setExists(boolean exists) {
		this.exists = exists;
	}

	// log names are unique in zookeeper, so no need to compare anything else if sorting by name
	@Override
	public int compareTo(Object o) {
		return getName().compareTo(((LogInfo) o).getName());
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) return true;
		if (o == null || getClass() != o.getClass()) return false;
		LogInfo logInfo = (LogInfo) o;
		return name.equals(logInfo.name) &&
			Objects.equals(uuid, logInfo.uuid) &&
			Objects.equals(bootstrapServers, logInfo.bootstrapServers);
	}

	@Override
	public int hashCode() {
		return Objects.hash(name, uuid, bootstrapServers);
	}

	@Override
	public String toString() {
		return "Log {name: " + getName() + ", bootstrapServers: " + getBootstrapServers() + "}";
	}

	public String toFullString() {
		return "LogInfo{" +
			"name='" + name + '\'' +
			", zk_ctime=" + zk_ctime +
			", uuid=" + uuid +
			", startOffset=" + startOffset +
			", endOffset=" + endOffset +
			", bytes=" + bytes +
			", exists=" + exists +
			", humanReadableByteSize='" + humanReadableByteSize + '\'' +
			", bootstrapServers='" + bootstrapServers + '\'' +
			'}';
	}
}
