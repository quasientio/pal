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
