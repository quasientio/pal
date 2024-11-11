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

package net.ittera.pal.messages;

/** Used by ContextFillingFixedKeyProcessor to encapsulate context of a kafka log message. */
public class MessageContext {
  private final long offset;
  private final long timestamp;
  private final int partition;
  private final String topic;

  public MessageContext(long offset, int partition, long timestamp, String topic) {
    this.offset = offset;
    this.partition = partition;
    this.timestamp = timestamp;
    this.topic = topic;
  }

  public long getOffset() {
    return offset;
  }

  public long getTimestamp() {
    return timestamp;
  }

  public int getPartition() {
    return partition;
  }

  @SuppressWarnings("unused")
  public String getTopic() {
    return topic;
  }

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
        + '}';
  }
}
