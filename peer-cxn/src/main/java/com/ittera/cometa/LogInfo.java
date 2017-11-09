package com.ittera.cometa;

import com.ittera.cometa.util.ByteSizeConverter;

public class LogInfo implements Comparable {

  // name of node in zk
  private String name;

  // in zk node stat
  private long zk_ctime;

  // in zk node data
  private String bootstrapServers;
  private String uuid;

  // to be filled from (kafka) mbeans via jmx
  private long startOffset;
  private long endOffset;
  private long bytes;
  private boolean exists;


  public LogInfo(String name) {
    this.name = name;
  }

  public LogInfo(String name, String bootstrapServers) {
    this(name);
    this.bootstrapServers = bootstrapServers;
  }

  public LogInfo(String name, String bootstrapServers, String uuid) {
    this(name, bootstrapServers);
    this.uuid = uuid;
  }

  public String getName() {
    return name;
  }

  public void setUuid(String uuid) {
    this.uuid = uuid;
  }

  public String getUuid() {
    return uuid;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getBootstrapServers() {
    return bootstrapServers;
  }

  public void setBootstrapServers(String bootstrapServers) {
    this.bootstrapServers = bootstrapServers;
  }

  public void setZk_ctime(long ctime) {
    this.zk_ctime = ctime;
  }

  public long getZk_ctime() {
    return zk_ctime;
  }

  public String getHumanReadableByteSize() {
    return ByteSizeConverter.humanReadableByteCount(getBytes(), false);
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
}
