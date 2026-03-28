/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.quasient.pal.common.directory.nodes;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * Represents an abstract information node within the Pal directory structure.
 *
 * <p>This class maintains creation and modification timestamps for a node and provides
 * functionality to serialize the node's information into JSON format. It is intended to be extended
 * by concrete subclasses that represent specific types of directory nodes.
 *
 * <p>The creation and modification times are managed as {@link OffsetDateTime} instances in UTC.
 */
public abstract class InfoNode {

  /**
   * Shared Jackson {@link ObjectMapper} used by {@link #toJson()} and subclass {@code fromJson}
   * methods. Configured to ignore unknown properties during deserialization so that read-only
   * computed fields (such as {@code humanReadableByteSize}) do not cause errors.
   */
  static final ObjectMapper MAPPER =
      new ObjectMapper()
          .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
          .setSerializationInclusion(JsonInclude.Include.NON_NULL);

  /**
   * The creation time of the node, stored as an {@link OffsetDateTime} in UTC.
   *
   * <p>This field represents the timestamp when the node was created. It is mutable, although
   * perhaps it should not be.
   */
  private OffsetDateTime ctime;

  /** The modification time of the node, stored as an {@link OffsetDateTime} in UTC. */
  private OffsetDateTime mtime;

  /**
   * Sets the creation time of the node using epoch milliseconds.
   *
   * <p>Converts the provided epoch milliseconds to an {@link Instant} and then to an {@link
   * OffsetDateTime} in UTC before setting the creation time.
   *
   * @param ctime the creation time in epoch milliseconds. Must be a non-negative value representing
   *     milliseconds since the Unix epoch.
   */
  @JsonProperty("ctime")
  public final void setCtime(long ctime) {
    Instant instant = Instant.ofEpochMilli(ctime);
    this.ctime = OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
  }

  /**
   * Sets the creation time of the node using an {@link Instant}.
   *
   * <p>Converts the provided {@link Instant} to an {@link OffsetDateTime} in UTC before setting the
   * creation time.
   *
   * @param time the creation time as an {@link Instant}. Must not be null.
   */
  public final void setCtime(Instant time) {
    this.ctime = OffsetDateTime.ofInstant(time, ZoneOffset.UTC);
  }

  /**
   * Sets the modification time of the node using epoch milliseconds.
   *
   * <p>Converts the provided epoch milliseconds to an {@link Instant} and then to an {@link
   * OffsetDateTime} in UTC before setting the modification time.
   *
   * @param mtime the modification time in epoch milliseconds. Must be a non-negative value
   *     representing milliseconds since the Unix epoch.
   */
  @JsonProperty("mtime")
  public final void setMtime(long mtime) {
    Instant instant = Instant.ofEpochMilli(mtime);
    this.mtime = OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
  }

  /**
   * Retrieves the creation time of the node.
   *
   * @return the creation time as an {@link OffsetDateTime} in UTC.
   */
  @JsonIgnore
  public final OffsetDateTime getCTime() {
    return ctime;
  }

  /**
   * Retrieves the modification time of the node.
   *
   * @return the modification time as an {@link OffsetDateTime} in UTC.
   */
  @JsonIgnore
  public final OffsetDateTime getMTime() {
    return mtime;
  }

  /**
   * Returns the creation time as epoch milliseconds for JSON serialization.
   *
   * @return the creation time in epoch milliseconds, or {@code null} if not set.
   */
  @JsonProperty("ctime")
  final Long getCtimeMillis() {
    return ctime != null ? ctime.toInstant().toEpochMilli() : null;
  }

  /**
   * Returns the modification time as epoch milliseconds for JSON serialization.
   *
   * @return the modification time in epoch milliseconds, or {@code null} if not set.
   */
  @JsonProperty("mtime")
  final Long getMtimeMillis() {
    return mtime != null ? mtime.toInstant().toEpochMilli() : null;
  }

  /**
   * Serializes the node's information to a JSON string.
   *
   * @return a JSON representation of the node.
   * @throws UncheckedIOException if serialization fails.
   */
  public final String toJson() {
    try {
      return MAPPER.writeValueAsString(this);
    } catch (JsonProcessingException e) {
      throw new UncheckedIOException(e);
    }
  }
}
