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

import java.util.UUID;

public class InterceptEvent {

  public enum Type {
    INTERCEPT_ADDED,
    INTERCEPT_REMOVED;
  }

  private final Type type;
  private final String interceptPath;
  private final UUID peerUUID;
  private final UUID interceptUUID;

  public InterceptEvent(Type type, String interceptPath, UUID peerUUID, UUID interceptUUID) {
    this.type = type;
    this.interceptPath = interceptPath;
    this.peerUUID = peerUUID;
    this.interceptUUID = interceptUUID;
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
        + '}';
  }

  public Type getType() {
    return type;
  }

  public String getInterceptPath() {
    return interceptPath;
  }

  public UUID getPeerUUID() {
    return peerUUID;
  }

  public UUID getInterceptUUID() {
    return interceptUUID;
  }
}
