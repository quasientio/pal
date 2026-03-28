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
package io.quasient.pal.messages;

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
