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
  public final void setMtime(long mtime) {
    Instant instant = Instant.ofEpochMilli(mtime);
    this.mtime = OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
  }

  /**
   * Retrieves the creation time of the node.
   *
   * @return the creation time as an {@link OffsetDateTime} in UTC.
   */
  public final OffsetDateTime getCTime() {
    return ctime;
  }

  /**
   * Retrieves the modification time of the node.
   *
   * @return the modification time as an {@link OffsetDateTime} in UTC.
   */
  public final OffsetDateTime getMTime() {
    return mtime;
  }

  /**
   * Serializes the node's information to a JSON string.
   *
   * @return a JSON representation of the node.
   * @throws com.alibaba.fastjson.JSONException if serialization fails.
   */
  public final String toJson() {
    return JSON.toJSONString(this);
  }
}
