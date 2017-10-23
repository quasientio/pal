package com.ittera.cometa;

public class LogInfo implements Comparable {

  private String name;
  private String bootstrapServers;
  private String uuid;
  private long startOffset, endOffset;
  private int logSegments;
  private int bytes;
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

  // log names are unique in zookeeper, so no need to compare anything else if sorting by name
  @Override
  public int compareTo(Object o) {
    return getName().compareTo(((LogInfo)o).getName());
  }
}
