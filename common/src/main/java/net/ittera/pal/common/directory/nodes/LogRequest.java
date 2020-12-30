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

package net.ittera.pal.common.directory.nodes;

import java.util.Objects;
import java.util.UUID;
import javax.annotation.Nonnull;

public final class LogRequest extends InfoNode implements Comparable<LogRequest> {

  @Nonnull private final UUID uuid;
  private final LogInfo outputLog;

  public LogRequest(@Nonnull UUID uuid, LogInfo outputLog) {
    this.uuid = Objects.requireNonNull(uuid);
    this.outputLog = outputLog;
  }

  public LogRequest(@Nonnull UUID uuid) {
    this(uuid, null);
  }

  @Nonnull
  public UUID getUuid() {
    return uuid;
  }

  public LogInfo getOutputLog() {
    return outputLog;
  }

  @Override
  public int compareTo(LogRequest o) {
    return getUuid().compareTo(o.getUuid());
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    LogRequest that = (LogRequest) o;
    return uuid.equals(that.uuid) && Objects.equals(outputLog, that.outputLog);
  }

  @Override
  public int hashCode() {
    return Objects.hash(uuid, outputLog);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("LogRequest {uuid=").append(getUuid());
    if (getOutputLog() != null) {
      sb.append(", outputLog=").append(getOutputLog().getName());
    }
    sb.append(", ctime=").append(getCTime());
    sb.append(", mtime=").append(getMTime());
    sb.append('}');
    return sb.toString();
  }
}
