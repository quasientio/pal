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

package net.ittera.pal.common.directory.events;

import java.util.Objects;
import java.util.UUID;
import javax.annotation.Nonnull;
import net.ittera.pal.common.directory.nodes.InterceptRequest;

public final class InterceptEvent {

  public enum Type {
    INTERCEPT_ADDED,
    INTERCEPT_REMOVED,
  }

  @Nonnull private final Type type;
  @Nonnull private final String interceptPath;
  @Nonnull private final UUID peerUUID;
  @Nonnull private final UUID interceptUUID;
  @Nonnull private final InterceptRequest interceptRequest;

  public InterceptEvent(
      @Nonnull Type type,
      @Nonnull String interceptPath,
      @Nonnull UUID peerUUID,
      @Nonnull UUID interceptUUID,
      @Nonnull InterceptRequest interceptRequest) {
    this.type = Objects.requireNonNull(type);
    this.interceptPath = Objects.requireNonNull(interceptPath);
    this.peerUUID = Objects.requireNonNull(peerUUID);
    this.interceptUUID = Objects.requireNonNull(interceptUUID);
    this.interceptRequest = Objects.requireNonNull(interceptRequest);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    InterceptEvent that = (InterceptEvent) o;
    return type == that.type
        && interceptPath.equals(that.interceptPath)
        && peerUUID.equals(that.peerUUID)
        && interceptUUID.equals(that.interceptUUID)
        && interceptRequest.equals(that.interceptRequest);
  }

  @Override
  public int hashCode() {
    return Objects.hash(type, interceptPath, peerUUID, interceptUUID, interceptRequest);
  }

  @Override
  public String toString() {
    return "InterceptEvent{"
        + "type="
        + type
        + ", interceptPath='"
        + interceptPath
        + '\''
        + ", peerUUID="
        + peerUUID
        + ", interceptUUID="
        + interceptUUID
        + ", interceptRequest='"
        + interceptRequest
        + '\''
        + '}';
  }

  @Nonnull
  public Type getType() {
    return type;
  }

  @Nonnull
  public String getInterceptPath() {
    return interceptPath;
  }

  @Nonnull
  public UUID getPeerUUID() {
    return peerUUID;
  }

  @Nonnull
  public UUID getInterceptUUID() {
    return interceptUUID;
  }

  @Nonnull
  public InterceptRequest getInterceptRequest() {
    return interceptRequest;
  }
}
