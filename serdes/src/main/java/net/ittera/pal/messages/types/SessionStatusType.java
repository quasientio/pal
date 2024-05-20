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

public enum SessionStatusType {
  OK((byte) 1),
  ERROR((byte) 2),
  UNSUPPORTED_SESSION_CMD((byte) 3),
  NO_SUCH_SESSION((byte) 4),
  NO_SUCH_OBJECT((byte) 5);

  private final byte idx;

  SessionStatusType(byte idx) {
    this.idx = idx;
  }

  public static SessionStatusType fromByte(byte typeAsByte) {
    return switch (typeAsByte) {
      case 1 -> OK;
      case 2 -> ERROR;
      case 3 -> UNSUPPORTED_SESSION_CMD;
      case 4 -> NO_SUCH_SESSION;
      case 5 -> NO_SUCH_OBJECT;
      default -> throw new IllegalArgumentException("Unknown session status type: " + typeAsByte);
    };
  }

  public byte toByte() {
    return idx;
  }
}
