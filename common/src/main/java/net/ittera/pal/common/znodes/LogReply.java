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

package net.ittera.pal.common.znodes;

import java.util.Objects;
import java.util.UUID;

public class LogReply extends UTCTimestampedInfo implements Comparable {

  private final UUID uuid;
  private final UUID peerUuid;
  private final UUID isReplyTo;
  private final long offset;

  public LogReply(UUID uuid, UUID peerUuid, UUID isReplyTo, long offset) {
    this.uuid = uuid;
    this.peerUuid = peerUuid;
    this.isReplyTo = isReplyTo;
    this.offset = offset;
  }

  public UUID getUuid() {
    return uuid;
  }

  public long getOffset() {
    return offset;
  }

  public UUID getIsReplyTo() {
    return isReplyTo;
  }

  public UUID getPeerUuid() {
    return peerUuid;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    LogReply that = (LogReply) o;
    return offset == that.offset
        && uuid.equals(that.uuid)
        && peerUuid.equals(that.peerUuid)
        && isReplyTo.equals(that.isReplyTo);
  }

  @Override
  public int hashCode() {
    return Objects.hash(uuid, peerUuid, isReplyTo, offset);
  }

  @Override
  public int compareTo(Object o) {
    return Long.compare(getOffset(), ((LogReply) o).getOffset());
  }

  @Override
  public String toString() {
    return "LogReply {uuid: "
        + getUuid()
        + ", offset: "
        + getOffset()
        + ", from-peer: "
        + getPeerUuid()
        + ", isReplyTo: "
        + getIsReplyTo()
        + '}';
  }
}
