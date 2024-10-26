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

public enum JsonRpcType {
  REQUEST((byte) 1),
  RESPONSE((byte) 2);

  private final byte idx;

  JsonRpcType(byte idx) {
    this.idx = idx;
  }

  public static JsonRpcType fromByte(byte typeAsByte) {
    return switch (typeAsByte) {
      case 1 -> REQUEST;
      case 2 -> RESPONSE;
      default -> throw new IllegalArgumentException("Unknown JSON-RPC type: " + typeAsByte);
    };
  }

  public byte toByte() {
    return idx;
  }
}
