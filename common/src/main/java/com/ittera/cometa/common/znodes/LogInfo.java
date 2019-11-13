package com.ittera.cometa.common.znodes;

import com.ittera.cometa.common.KafkaBrokerEndpoint;
import com.ittera.cometa.common.KafkaBrokerInfo;
import com.ittera.cometa.common.util.ByteSizeConverter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public class LogInfo extends UTCTimestampedInfo implements Comparable {

  // name of node in zk
  private final String name;

  // in zk node data
  private UUID uuid;

  // to be filled from (kafka) mbeans via jmx
  private Long startOffset;
  private Long endOffset;
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
        Arrays.stream(brokerInfo.getEndpoints())
            .map(KafkaBrokerEndpoint::toURL)
            .forEach(urlList::add);
      }
      this.bootstrapServers = String.join(",", urlList);
    }
  }

  public String getHumanReadableByteSize() {
    return humanReadableByteSize;
  }

  public Long getStartOffset() {
    return startOffset;
  }

  public void setStartOffset(long startOffset) {
    this.startOffset = startOffset;
  }

  public Long getEndOffset() {
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
    return name.equals(logInfo.name);
  }

  @Override
  public int hashCode() {
    return Objects.hash(name);
  }

  @Override
  public String toString() {
    return "Log {name: " + getName() + ", bootstrapServers: " + getBootstrapServers() + "}";
  }

  public String toFullString() {
    return "Log{"
        + "name='"
        + name
        + '\''
        + ", uuid="
        + uuid
        + ", startOffset="
        + startOffset
        + ", endOffset="
        + endOffset
        + ", bytes="
        + bytes
        + ", exists="
        + exists
        + ", humanReadableByteSize='"
        + humanReadableByteSize
        + '\''
        + ", bootstrapServers='"
        + bootstrapServers
        + '\''
        + '}';
  }
}
