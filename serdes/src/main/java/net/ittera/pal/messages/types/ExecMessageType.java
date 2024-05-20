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

public enum ExecMessageType {
  STATIC_CONSTRUCTOR((byte) 1),
  RETURN_CLASS((byte) 2),
  CONSTRUCTOR((byte) 3),
  INSTANCE_METHOD((byte) 4),
  CLASS_METHOD((byte) 5),
  GET_STATIC((byte) 6),
  GET_FIELD((byte) 7),
  PUT_STATIC((byte) 8),
  PUT_FIELD((byte) 9),
  PUT_STATIC_DONE((byte) 10),
  PUT_FIELD_DONE((byte) 11),
  THROWABLE((byte) 12),
  RETURN_VALUE((byte) 13);

  private final byte idx;

  ExecMessageType(byte idx) {
    this.idx = idx;
  }

  public static ExecMessageType fromByte(byte typeAsByte) {
    return switch (typeAsByte) {
      case 1 -> STATIC_CONSTRUCTOR;
      case 2 -> RETURN_CLASS;
      case 3 -> CONSTRUCTOR;
      case 4 -> INSTANCE_METHOD;
      case 5 -> CLASS_METHOD;
      case 6 -> GET_STATIC;
      case 7 -> GET_FIELD;
      case 8 -> PUT_STATIC;
      case 9 -> PUT_FIELD;
      case 10 -> PUT_STATIC_DONE;
      case 11 -> PUT_FIELD_DONE;
      case 12 -> THROWABLE;
      case 13 -> RETURN_VALUE;
      default -> throw new IllegalArgumentException("Unknown exec message type: " + typeAsByte);
    };
  }

  public byte toByte() {
    return idx;
  }
}
