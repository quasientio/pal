/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.common.directory.nodes;

import com.alibaba.fastjson.JSON;
import com.quasient.pal.common.util.ByteSizeConverter;
import java.util.Objects;
import java.util.UUID;
import javax.annotation.Nonnull;

/**
 * Represents detailed information about a (Kafka) log within Pal. This class includes metadata such
 * as the log's name, UUID, offsets, byte size, existence status, and associated bootstrap servers.
 * It serves as a unique identifier for logs registered in the Pal Directory (etcd store).
 *
 * @see InfoNode
 */
public final class LogInfo extends InfoNode implements Comparable<LogInfo> {

  /**
   * The name of the log, which acts as the unique key in etcd. This field is non-null and serves as
   * the primary identifier for the log.
   */
  @Nonnull private final String name;

  /**
   * Universally unique identifier for the log. This field may be null if the UUID has not been set.
   */
  private UUID uuid;

  /**
   * The starting offset of the log. May be obtained from Kafka MBeans via JMX. Represents the
   * initial position in the log where processing begins.
   */
  private Long startOffset;

  /**
   * The ending offset of the log. May be obtained from Kafka MBeans via JMX. Represents the final
   * position in the log where processing ends.
   */
  private Long endOffset;

  /** The total number of bytes in the log. This field is used to track the size of the log data. */
  private long bytes;

  /** Indicates whether the log exists in the system. */
  private boolean exists;

  /**
   * The size of the log expressed in a human-readable format. Computed from the 'bytes' field
   * whenever the byte size is set.
   */
  private String humanReadableByteSize;

  /**
   * The bootstrap servers associated with the log. Used for connecting to Kafka or possibly other
   * Log services.
   */
  private String bootstrapServers;

  /**
   * Constructs a new LogInfo instance with the specified name.
   *
   * @param name the unique name of the log, used as a key in etcd. Must not be null.
   * @throws NullPointerException if the name is null.
   */
  public LogInfo(@Nonnull String name) {
    this.name = Objects.requireNonNull(name);
  }

  /**
   * Constructs a new LogInfo instance with the specified name and bootstrap servers.
   *
   * @param name the unique name of the log, used as a key in etcd. Must not be null.
   * @param bootstrapServers the bootstrap servers associated with the log. May be null.
   * @throws NullPointerException if the name is null.
   */
  public LogInfo(@Nonnull String name, String bootstrapServers) {
    this(name);
    setBootstrapServers(bootstrapServers);
  }

  /**
   * Constructs a new LogInfo instance with the specified name and UUID.
   *
   * @param name the unique name of the log, used as a key in etcd. Must not be null.
   * @param uuid the universally unique identifier for the log. May be null.
   * @throws NullPointerException if the name is null.
   */
  public LogInfo(@Nonnull String name, UUID uuid) {
    this(name);
    this.uuid = uuid;
  }

  /**
   * Constructs a new LogInfo instance with the specified name, UUID, and bootstrap servers.
   *
   * @param name the unique name of the log, used as a key in etcd. Must not be null.
   * @param uuid the universally unique identifier for the log. May be null.
   * @param bootstrapServers the bootstrap servers associated with the log. May be null.
   * @throws NullPointerException if the name is null.
   */
  public LogInfo(@Nonnull String name, UUID uuid, String bootstrapServers) {
    this(name, uuid);
    this.bootstrapServers = bootstrapServers;
  }

  /**
   * Retrieves the unique name of the log.
   *
   * @return the name of the log, never null.
   */
  @Nonnull
  public String getName() {
    return name;
  }

  /**
   * Sets the universally unique identifier (UUID) for the log.
   *
   * @param uuid the UUID to set for the log. May be null.
   */
  public void setUuid(UUID uuid) {
    this.uuid = uuid;
  }

  /**
   * Retrieves the universally unique identifier (UUID) of the log.
   *
   * @return the UUID of the log, or null if not set.
   */
  public UUID getUuid() {
    return uuid;
  }

  /**
   * Retrieves the bootstrap servers associated with the log.
   *
   * @return the bootstrap servers as a string, or null if not set.
   */
  public String getBootstrapServers() {
    return bootstrapServers;
  }

  /**
   * Sets the bootstrap servers associated with the log.
   *
   * @param bootstrapServers the bootstrap servers to associate with the log. May be null.
   */
  public void setBootstrapServers(String bootstrapServers) {
    this.bootstrapServers = bootstrapServers;
  }

  /**
   * Retrieves the size of the log in a human-readable format.
   *
   * @return the human-readable byte size of the log.
   */
  public String getHumanReadableByteSize() {
    return humanReadableByteSize;
  }

  /**
   * Retrieves the starting offset of the log.
   *
   * @return the start offset of the log, or null if not set.
   */
  public Long getStartOffset() {
    return startOffset;
  }

  /**
   * Sets the starting offset of the log.
   *
   * @param startOffset the starting offset to set. Must be a non-negative value.
   */
  public void setStartOffset(long startOffset) {
    this.startOffset = startOffset;
  }

  /**
   * Retrieves the ending offset of the log.
   *
   * @return the end offset of the log, or null if not set.
   */
  public Long getEndOffset() {
    return endOffset;
  }

  /**
   * Sets the ending offset of the log.
   *
   * @param endOffset the ending offset to set. Must be a non-negative value.
   */
  public void setEndOffset(long endOffset) {
    this.endOffset = endOffset;
  }

  /**
   * Retrieves the total number of bytes in the log.
   *
   * @return the size of the log in bytes.
   */
  public long getBytes() {
    return bytes;
  }

  /**
   * Sets the total number of bytes in the log and updates the human-readable byte size.
   *
   * @param bytes the total number of bytes in the log. Must be a non-negative value.
   */
  public void setBytes(long bytes) {
    this.bytes = bytes;
    humanReadableByteSize = ByteSizeConverter.humanReadableByteCount(getBytes(), false);
  }

  /**
   * Checks whether the log exists in Kafka.
   *
   * @return true if the log exists, false otherwise.
   */
  public boolean isExists() {
    return exists;
  }

  /**
   * Sets the existence status of the log.
   *
   * @param exists true if the log exists, false otherwise.
   */
  public void setExists(boolean exists) {
    this.exists = exists;
  }

  /**
   * {@inheritDoc}
   *
   * <p>Compares this LogInfo with the specified LogInfo for order based on the log name.
   *
   * @param o the LogInfo to be compared.
   * @return a negative integer, zero, or a positive integer as this log name is less than, equal
   *     to, or greater than the specified log name.
   */
  @Override
  public int compareTo(LogInfo o) {
    return getName().compareTo(o.getName());
  }

  /**
   * {@inheritDoc}
   *
   * <p>Logs are considered equal if they have the same name and bootstrap servers.
   *
   * @param o the reference object with which to compare.
   * @return true if this object is the same as the obj argument; false otherwise.
   */
  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    LogInfo logInfo = (LogInfo) o;
    return Objects.equals(name, logInfo.name)
        && Objects.equals(bootstrapServers, logInfo.bootstrapServers);
  }

  /**
   * {@inheritDoc}
   *
   * <p>Returns a hash code value for the log based on its name and bootstrap servers.
   *
   * @return a hash code value for this log.
   */
  @Override
  public int hashCode() {
    return Objects.hash(name, bootstrapServers);
  }

  /**
   * {@inheritDoc}
   *
   * <p>Returns a string representation of the log, including its name, UUID, bootstrap servers,
   * start and end offsets, creation time, and modification time.
   *
   * @return a string representation of the log.
   */
  @Override
  public String toString() {
    return "LogInfo{"
        + "name='"
        + name
        + '\''
        + ", uuid="
        + uuid
        + ", bootstrapServers='"
        + bootstrapServers
        + '\''
        + ", startOffset="
        + startOffset
        + ", endOffset="
        + endOffset
        + ", ctime="
        + getCTime()
        + ", mtime="
        + getMTime()
        + '}';
  }

  /**
   * Creates a new LogInfo instance from its JSON representation.
   *
   * @param repr the JSON string representing a LogInfo object.
   * @return a LogInfo instance parsed from the JSON string.
   * @throws com.alibaba.fastjson.JSONException if the JSON parsing fails.
   * @see com.alibaba.fastjson.JSON#parseObject(String, Class)
   */
  public static LogInfo fromJson(String repr) {
    return JSON.parseObject(repr, LogInfo.class);
  }
}
