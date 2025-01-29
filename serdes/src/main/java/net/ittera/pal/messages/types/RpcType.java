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

public enum RpcType {
  BIN_RPC((byte) 1),
  JSON_RPC((byte) 2);

  private final byte idx;

  RpcType(byte idx) {
    this.idx = idx;
  }

  public static RpcType fromByte(byte typeAsByte) {
    return switch (typeAsByte) {
      case 1 -> BIN_RPC;
      case 2 -> JSON_RPC;
      default -> throw new IllegalArgumentException("Unknown RPC type: " + typeAsByte);
    };
  }

  public byte toByte() {
    return idx;
  }
}
