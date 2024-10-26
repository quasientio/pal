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

import com.alibaba.fastjson.JSON;
import java.util.Objects;
import java.util.UUID;
import javax.annotation.Nonnull;
import net.ittera.pal.common.util.ByteSizeConverter;

public final class LogInfo extends InfoNode implements Comparable<LogInfo> {

  // acts as key in etcd
  @Nonnull private final String name;

  private UUID uuid;

  // to be filled from (kafka) mbeans via jmx
  private Long startOffset;
  private Long endOffset;
  private long bytes;
  private boolean exists;

  // computed fields
  private String humanReadableByteSize;
  private String bootstrapServers;

  public LogInfo(@Nonnull String name) {
    this.name = Objects.requireNonNull(name);
  }

  public LogInfo(@Nonnull String name, String bootstrapServers) {
    this(name);
    setBootstrapServers(bootstrapServers);
  }

  public LogInfo(@Nonnull String name, UUID uuid) {
    this(name);
    this.uuid = uuid;
  }

  public LogInfo(@Nonnull String name, UUID uuid, String bootstrapServers) {
    this(name, uuid);
    this.bootstrapServers = bootstrapServers;
  }

  @Nonnull
  public String getName() {
    return name;
  }

  public void setUuid(UUID uuid) {
    this.uuid = uuid;
  }

  public UUID getUuid() {
    return uuid;
  }

  public String getBootstrapServers() {
    return bootstrapServers;
  }

  public void setBootstrapServers(String bootstrapServers) {
    this.bootstrapServers = bootstrapServers;
  }

  public String getHumanReadableByteSize() {
    return humanReadableByteSize;
  }

  public Long getStartOffset() {
    return startOffset;
  }

  public void setStartOffset(long startOffset) {
    this.startOffset = startOffset;
  }

  public Long getEndOffset() {
    return endOffset;
  }

  public void setEndOffset(long endOffset) {
    this.endOffset = endOffset;
  }

  public long getBytes() {
    return bytes;
  }

  public void setBytes(long bytes) {
    this.bytes = bytes;
    humanReadableByteSize = ByteSizeConverter.humanReadableByteCount(getBytes(), false);
  }

  public boolean isExists() {
    return exists;
  }

  public void setExists(boolean exists) {
    this.exists = exists;
  }

  // log names are unique in etcd, so no need to compare anything else if sorting by name
  @Override
  public int compareTo(LogInfo o) {
    return getName().compareTo(o.getName());
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    LogInfo logInfo = (LogInfo) o;
    return name.equals(logInfo.name);
  }

  @Override
  public int hashCode() {
    return Objects.hash(name);
  }

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

  public static LogInfo fromJson(String repr) {
    return JSON.parseObject(repr, LogInfo.class);
  }
}
