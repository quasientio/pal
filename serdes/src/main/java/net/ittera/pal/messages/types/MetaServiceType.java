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

import java.util.Locale;

public enum MetaServiceType {
  FETCH_CLASSES_INFO((byte) 1);

  private static final MetaServiceType[] LOOKUP = new MetaServiceType[256];

  static {
    for (MetaServiceType type : values()) {
      LOOKUP[type.id & 0xFF] = type;
    }
  }

  private final byte id;

  MetaServiceType(byte id) {
    this.id = id;
  }

  public byte getId() {
    return id;
  }

  public static MetaServiceType fromId(byte id) {
    return LOOKUP[id & 0xFF];
  }

  public String getJsonName() {
    return name().toLowerCase(Locale.ROOT);
  }

  public static MetaServiceType fromJsonName(String jsonName) {
    for (MetaServiceType type : values()) {
      if (type.getJsonName().equalsIgnoreCase(jsonName)) {
        return type;
      }
    }
    return null;
  }
}
