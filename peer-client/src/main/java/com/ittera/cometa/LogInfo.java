package com.ittera.cometa;

public class LogInfo implements Comparable {

  // name of node in zk
  private String name;

  // in zk node stat
  private long zk_ctime;

  // in zk node data
  private String bootstrapServers;
  private String uuid;

  // to be filled from (kafka) mbeans via jmx
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

  public void setZk_ctime(long ctime) {
    this.zk_ctime = ctime;
  }

  public long getZk_ctime() {
    return zk_ctime;
  }

  // log names are unique in zookeeper, so no need to compare anything else if sorting by name
  @Override
  public int compareTo(Object o) {
    return getName().compareTo(((LogInfo)o).getName());
  }
}
