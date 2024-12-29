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

public enum MetaServiceType {
  FETCH_CLASSES_INFO((byte) 1, "fetch_classes_info");

  private static final MetaServiceType[] LOOKUP = new MetaServiceType[256];

  static {
    for (MetaServiceType type : values()) {
      LOOKUP[type.id & 0xFF] = type;
    }
  }

  private final byte id;
  private final String jsonName;

  MetaServiceType(byte id, String jsonName) {
    this.id = id;
    this.jsonName = jsonName;
  }

  public byte getId() {
    return id;
  }

  public String getJsonName() {
    return jsonName;
  }

  public static MetaServiceType fromId(byte id) {
    return LOOKUP[id & 0xFF];
  }

  public static MetaServiceType fromJsonName(String jsonName) {
    for (MetaServiceType type : values()) {
      if (type.jsonName.equals(jsonName)) {
        return type;
      }
    }
    return null;
  }
}
