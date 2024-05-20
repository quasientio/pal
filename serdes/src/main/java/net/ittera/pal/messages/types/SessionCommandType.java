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

package net.ittera.pal.messages.types;

public enum SessionCommandType {
  STORE_OBJECT((byte) 1), // only internal
  DELETE_OBJECT((byte) 2),
  DELETE_SESSION((byte) 3),
  CLEAR_SESSIONS((byte) 4); // only internal

  private final byte idx;

  SessionCommandType(byte idx) {
    this.idx = idx;
  }

  public static SessionCommandType fromByte(byte messageTypeAsByte) {
    return switch (messageTypeAsByte) {
      case 1 -> STORE_OBJECT;
      case 2 -> DELETE_OBJECT;
      case 3 -> DELETE_SESSION;
      case 4 -> CLEAR_SESSIONS;
      default ->
          throw new IllegalArgumentException("Unknown session command type: " + messageTypeAsByte);
    };
  }

  public byte toByte() {
    return idx;
  }
}
