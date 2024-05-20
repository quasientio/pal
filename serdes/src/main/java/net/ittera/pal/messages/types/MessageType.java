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

public enum MessageType {
  CONTROL_MESSAGE((byte) 1),
  EXEC_MESSAGE((byte) 2),
  INTERCEPT_MESSAGE((byte) 3),
  INTERCEPT_KEY((byte) 4),
  INTERCEPT_REPLY((byte) 5);

  private final byte idx;

  MessageType(byte idx) {
    this.idx = idx;
  }

  public static MessageType fromByte(byte messageTypeAsByte) {
    return switch (messageTypeAsByte) {
      case 1 -> CONTROL_MESSAGE;
      case 2 -> EXEC_MESSAGE;
      case 3 -> INTERCEPT_MESSAGE;
      case 4 -> INTERCEPT_KEY;
      case 5 -> INTERCEPT_REPLY;
      default -> throw new IllegalArgumentException("Unknown message type: " + messageTypeAsByte);
    };
  }

  public byte toByte() {
    return idx;
  }
}
