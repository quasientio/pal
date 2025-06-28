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
 * Represents the context of a Log message within the PAL runtime, encapsulating Kafka metadata such
 * as offset, partition, timestamp, topic, and log identifier.
 */
public record MessageContext(

    /*
     The offset of the message within the partition, indicating its position.
    */
    long offset,

    /*
     The partition number where the message resides.
    */
    int partition,

    /*
     The timestamp associated with the message, typically representing the time of production.
    */
    long timestamp,

    /*
     The topic name to which the message belongs.
    */
    String topic,

    /*
     The unique identifier for the log containing the message.
    */
    String logId) {

  /**
   * Retrieves the topic associated with this message context.
   *
   * @return the topic name.
   */
  @Override
  @SuppressWarnings("unused")
  public String topic() {
    return topic;
  }

  /** {@inheritDoc} */
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
        + ", logId="
        + logId
        + '}';
  }
}
