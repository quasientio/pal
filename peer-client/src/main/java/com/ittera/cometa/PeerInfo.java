package com.ittera.cometa;

import java.util.UUID;

public class PeerInfo implements Comparable {

  // name of node in zk
  private UUID uuid;

  // in zk node stat
  private long zk_ctime;

  // in zk node data
  private String listenAddress;

  public PeerInfo(UUID uuid) {
    setUuid(uuid);
  }

  public PeerInfo(String uuid) {
    this(UUID.fromString(uuid));
  }

  public UUID getUuid() {
    return uuid;
  }

  public void setUuid(UUID uuid) {
    this.uuid = uuid;
  }

  public String getListenAddress() {
    return listenAddress;
  }

  public void setListenAddress(String listenAddress) {
    this.listenAddress = listenAddress;
  }

  public void setZk_ctime(long ctime) {
    this.zk_ctime = ctime;
  }

  public long getZk_ctime() {
    return zk_ctime;
  }

  @Override
  public int compareTo(Object o) {
    return getUuid().compareTo(((PeerInfo)o).getUuid());
  }
}
