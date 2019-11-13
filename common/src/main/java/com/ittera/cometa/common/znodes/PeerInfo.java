package com.ittera.cometa.common.znodes;

import java.util.Objects;
import java.util.UUID;

public class PeerInfo extends UTCTimestampedInfo implements Comparable {

  // name of node in zk
  private UUID uuid;

  // BEWARE bean-like fields are set reflectively by PALDirectory: DO NOT include any logic in
  // setters

  // in zk node data
  private String reqAddress;
  private String pubAddress;
  private String jmxAddress;

  private String name;

  public PeerInfo(UUID uuid) {
    setUuid(uuid);
  }

  public PeerInfo(UUID uuid, String name) {
    setUuid(uuid);
    setName(name);
  }

  public PeerInfo(String reqAddress) {
    setReqAddress(reqAddress);
  }

  public UUID getUuid() {
    return uuid;
  }

  public void setUuid(UUID uuid) {
    this.uuid = uuid;
  }

  public String getReqAddress() {
    return reqAddress;
  }

  public void setReqAddress(String reqAddress) {
    this.reqAddress = reqAddress;
  }

  public String getPubAddress() {
    return pubAddress;
  }

  public void setPubAddress(String pubAddress) {
    this.pubAddress = pubAddress;
  }

  public String getJmxAddress() {
    return jmxAddress;
  }

  public void setJmxAddress(String jmxAddress) {
    this.jmxAddress = jmxAddress;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  @Override
  public int compareTo(Object o) {
    return getUuid().compareTo(((PeerInfo) o).getUuid());
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    PeerInfo peerInfo = (PeerInfo) o;
    return Objects.equals(uuid, peerInfo.uuid) && Objects.equals(reqAddress, peerInfo.reqAddress);
  }

  @Override
  public int hashCode() {
    return Objects.hash(uuid, reqAddress);
  }

  @Override
  public String toString() {
    return "Peer{"
        + "uuid="
        + uuid
        + ", name='"
        + name
        + '\''
        + ", reqAddress='"
        + reqAddress
        + '\''
        + ", pubAddress='"
        + pubAddress
        + '\''
        + ", jmxAddress='"
        + jmxAddress
        + '\''
        + '}';
  }
}
