/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.messages;

/**
 * Represents the context of a log message within the Pal runtime, encapsulating Kafka metadata such
 * as offset, partition, timestamp, topic, and the log identifier.
 *
 * @param offset the offset of the message within its partition, indicating its position
 * @param partition the partition number where the message resides
 * @param timestamp the epoch‐millisecond timestamp associated with the message (typically
 *     production time)
 * @param topic the name of the Kafka topic to which the message belongs
 * @param logId the unique (PalDirectory) identifier of the log containing the message
 */
public record MessageContext(
    long offset, int partition, long timestamp, String topic, String logId) {

  /**
   * Retrieves the topic associated with this message context.
   *
   * @return the Kafka topic name
   */
  @Override
  @SuppressWarnings("unused")
  public String topic() {
    return topic;
  }

  /**
   * Returns a string representation of this MessageContext, including all fields and their values.
   *
   * @return a human-readable representation of the context
   */
  @Override
  public String toString() {
    return "MessageContext{"
        + "offset="
        + offset
        + ", timestamp="
        + timestamp
        + ", partition="
        + partition
        + ", topic='"
        + topic
        + '\''
        + ", logId='"
        + logId
        + '\''
        + '}';
  }
}
