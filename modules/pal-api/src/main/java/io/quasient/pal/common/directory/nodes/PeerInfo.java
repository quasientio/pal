/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.common.directory.nodes;

import com.fasterxml.jackson.core.JsonProcessingException;
import java.io.UncheckedIOException;
import java.util.Objects;
import java.util.UUID;

/**
 * Represents information about a peer within the PAL directory.
 *
 * <p>This class holds the various addresses and identifiers associated with a peer, facilitating
 * communication and management within the directory.
 *
 * <p>Instances of {@code PeerInfo} are typically created and managed through the PalDirectory.
 *
 * @see InfoNode
 */
public final class PeerInfo extends InfoNode implements Comparable<PeerInfo> {

  /**
   * The universally unique identifier (UUID) for this peer.
   *
   * <p>This UUID is part of the key/path in the directory, uniquely identifying the peer.
   */
  private UUID uuid;

  /**
   * The ZMQ RPC address for this peer.
   *
   * <p>Used for initiating ZMQ RPC communications with the peer. Should be a valid network address
   * prefixed by "tcp://".
   */
  private String zmqRpcAddress;

  /**
   * The JSON-RPC address for this peer.
   *
   * <p>Facilitates JSON-based RPC communications. Must conform to expected JSON-RPC standards.
   */
  private String jsonrpcAddress;

  /** The ZMQ PUB address for this peer. */
  private String pubAddress;

  /**
   * The JMX (Java Management Extensions) address for this peer.
   *
   * <p>Allows for management and monitoring of the peer via JMX. Must be properly configured for
   * access.
   */
  private String jmxAddress;

  /**
   * The name of this peer.
   *
   * <p>Provides a human-readable identifier for the peer within the directory system.
   */
  private String name;

  /** Constructs a new {@code PeerInfo} instance with no initial values. */
  public PeerInfo() {}

  /**
   * Constructs a new {@code PeerInfo} instance with the specified UUID.
   *
   * @param uuid the universally unique identifier for this peer; must not be {@code null}
   * @throws NullPointerException if {@code uuid} is {@code null}
   */
  public PeerInfo(UUID uuid) {
    setUuid(uuid);
  }

  /**
   * Constructs a new {@code PeerInfo} instance with the specified UUID and name.
   *
   * @param uuid the universally unique identifier for this peer; must not be {@code null}
   * @param name the human-readable name for this peer; must not be {@code null}
   * @throws NullPointerException if {@code uuid} or {@code name} is {@code null}
   */
  public PeerInfo(UUID uuid, String name) {
    setUuid(uuid);
    setName(name);
  }

  /**
   * Retrieves the UUID of this peer.
   *
   * @return the UUID associated with this peer
   */
  public UUID getUuid() {
    return uuid;
  }

  /**
   * Sets the UUID for this peer.
   *
   * <p>This UUID is used as part of the key/path in the directory and must be unique.
   *
   * @param uuid the universally unique identifier to assign; must not be {@code null}
   * @throws NullPointerException if {@code uuid} is {@code null}
   */
  public void setUuid(UUID uuid) {
    Objects.requireNonNull(uuid);
    this.uuid = uuid;
  }

  /**
   * Retrieves the ZMQ-RPC address of this peer.
   *
   * @return the ZMQ-RPC address, or {@code null} if not set
   */
  public String getZmqRpcAddress() {
    return zmqRpcAddress;
  }

  /**
   * Retrieves the JSON-RPC address of this peer.
   *
   * @return the JSON-RPC address, or {@code null} if not set
   */
  public String getJsonrpcAddress() {
    return jsonrpcAddress;
  }

  /**
   * Sets the ZMQ-RPC address for this peer.
   *
   * <p>This address is used for ZMQ-based Remote Procedure Calls and should be a valid network
   * address prefixed by "tcp://".
   *
   * @param zmqRpcAddress the ZMQ-RPC address to assign; can be {@code null} to unset
   */
  public void setZmqRpcAddress(String zmqRpcAddress) {
    this.zmqRpcAddress = zmqRpcAddress;
  }

  /**
   * Sets the JSON-RPC address for this peer.
   *
   * <p>This address facilitates JSON-based RPC communications and should adhere to JSON-RPC
   * specifications.
   *
   * @param jsonrpcAddress the JSON-RPC address to assign; can be {@code null} to unset
   */
  public void setJsonrpcAddress(String jsonrpcAddress) {
    this.jsonrpcAddress = jsonrpcAddress;
  }

  /**
   * Retrieves the PUB address of this peer.
   *
   * @return the PUB address, or {@code null} if not set
   */
  public String getPubAddress() {
    return pubAddress;
  }

  /**
   * Sets the PUB address for this peer.
   *
   * @param pubAddress the PUB address to assign; can be {@code null} to unset
   */
  public void setPubAddress(String pubAddress) {
    this.pubAddress = pubAddress;
  }

  /**
   * Retrieves the JMX address of this peer.
   *
   * @return the JMX address, or {@code null} if not set
   */
  public String getJmxAddress() {
    return jmxAddress;
  }

  /**
   * Sets the JMX address for this peer.
   *
   * <p>This address allows for management and monitoring via Java Management Extensions and should
   * be properly configured.
   *
   * @param jmxAddress the JMX address to assign; can be {@code null} to unset
   */
  public void setJmxAddress(String jmxAddress) {
    this.jmxAddress = jmxAddress;
  }

  /**
   * Retrieves the name of this peer.
   *
   * @return the name of the peer, or {@code null} if not set
   */
  public String getName() {
    return name;
  }

  /**
   * Sets the name for this peer.
   *
   * <p>The name provides a human-readable identifier for the peer within the directory.
   *
   * @param name the name to assign to the peer; must not be {@code null}
   * @throws NullPointerException if {@code name} is {@code null}
   */
  public void setName(String name) {
    Objects.requireNonNull(name);
    this.name = name;
  }

  /**
   * Compares this {@code PeerInfo} with the specified {@code PeerInfo} for order based on UUID.
   *
   * @param o the {@code PeerInfo} to be compared
   * @return a negative integer, zero, or a positive integer as this UUID is less than, equal to, or
   *     greater than the specified UUID
   * @throws NullPointerException if {@code o} is {@code null}
   */
  @Override
  public int compareTo(PeerInfo o) {
    Objects.requireNonNull(o);
    return getUuid().compareTo(o.getUuid());
  }

  /**
   * Indicates whether some other object is "equal to" this one based on UUID and addresses.
   *
   * @param o the reference object with which to compare
   * @return {@code true} if this object is the same as the {@code o} argument; {@code false}
   *     otherwise
   */
  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    PeerInfo peerInfo = (PeerInfo) o;
    return Objects.equals(uuid, peerInfo.uuid)
        && Objects.equals(zmqRpcAddress, peerInfo.zmqRpcAddress)
        && Objects.equals(jsonrpcAddress, peerInfo.jsonrpcAddress);
  }

  /**
   * Returns a hash code value for the object based on UUID and addresses.
   *
   * @return a hash code value for this object
   */
  @Override
  public int hashCode() {
    return Objects.hash(uuid, zmqRpcAddress, jsonrpcAddress);
  }

  /**
   * Returns a string representation of the peer, including UUID, name, and addresses.
   *
   * @return a string representation of this peer
   */
  @Override
  public String toString() {
    return "Peer {"
        + "uuid="
        + uuid
        + ", name='"
        + name
        + '\''
        + ", zmqRpcAddress='"
        + zmqRpcAddress
        + '\''
        + ", jsonRpcAddress='"
        + jsonrpcAddress
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

  /**
   * Creates a {@code PeerInfo} instance from its JSON representation.
   *
   * @param repr the JSON string representing a {@code PeerInfo}; must not be {@code null}
   * @return a {@code PeerInfo} object parsed from the JSON string
   * @throws UncheckedIOException if the JSON parsing fails
   */
  public static PeerInfo fromJson(String repr) {
    try {
      return MAPPER.readValue(repr, PeerInfo.class);
    } catch (JsonProcessingException e) {
      throw new UncheckedIOException(e);
    }
  }
}
