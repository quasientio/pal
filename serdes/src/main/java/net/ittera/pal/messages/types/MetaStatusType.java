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

public enum MetaStatusType {
  OK((byte) 1),
  ERROR((byte) 2),
  UNAUTHORIZED((byte) 3),
  UNSUPPORTED((byte) 4);

  private static final MetaStatusType[] LOOKUP = new MetaStatusType[256];

  static {
    for (MetaStatusType type : values()) {
      LOOKUP[type.id & 0xFF] = type;
    }
  }

  private final byte id;

  MetaStatusType(byte id) {
    this.id = id;
  }

  public byte getId() {
    return id;
  }

  public static MetaStatusType fromId(byte id) {
    return LOOKUP[id & 0xFF];
  }
}
