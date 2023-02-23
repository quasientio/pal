/*
   Copyright (c) 2020 Contributors listed in the AUTHORS file

   This file is part of PAL, the friendly java runtime.

   PAL is free software: you can redistribute it and/or modify
   it under the terms of the GNU General Public License as published by
   the Free Software Foundation, either version 3 of the License, or
   (at your option) any later version.

   PAL is distributed in the hope that it will be useful,
   but WITHOUT ANY WARRANTY; without even the implied warranty of
   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
   GNU General Public License for more details.

   You should have received a copy of the GNU General Public License
   along with this program. If not, see <http://www.gnu.org/licenses/>.
*/

package net.ittera.pal.common.directory.nodes;

import com.alibaba.fastjson.JSON;
import java.util.Objects;
import java.util.UUID;

public final class PeerInfo extends InfoNode implements Comparable<PeerInfo> {

  // part of the key/path in directory
  private UUID uuid;

  // BEWARE bean-like fields are set reflectively by PALDirectory: DO NOT include any logic in
  // setters

  // value/data
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
  public int compareTo(PeerInfo o) {
    return getUuid().compareTo(o.getUuid());
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    PeerInfo peerInfo = (PeerInfo) o;
    return Objects.equals(uuid, peerInfo.uuid) && Objects.equals(reqAddress, peerInfo.reqAddress);
  }

  @Override
  public int hashCode() {
    return Objects.hash(uuid, reqAddress);
  }

  @Override
  public String toString() {
    return "Peer {"
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
        + ", ctime="
        + getCTime()
        + ", mtime="
        + getMTime()
        + '}';
  }

  public static PeerInfo fromJSON(String repr) {
    return JSON.parseObject(repr, PeerInfo.class);
  }
}
