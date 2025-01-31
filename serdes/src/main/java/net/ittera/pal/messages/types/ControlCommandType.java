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

public enum ControlCommandType {
  DELETE_OBJECT((byte) 1),
  DELETE_SESSION((byte) 2),
  GC((byte) 3);

  private final byte id;

  ControlCommandType(byte id) {
    this.id = id;
  }

  public static ControlCommandType fromId(byte messageTypeAsByte) {
    return switch (messageTypeAsByte) {
      case 1 -> DELETE_OBJECT;
      case 2 -> DELETE_SESSION;
      case 3 -> GC;
      default ->
          throw new IllegalArgumentException("Unknown control command type: " + messageTypeAsByte);
    };
  }

  public byte getId() {
    return id;
  }

  public String getJsonName() {
    return name().toLowerCase(Locale.ROOT);
  }

  public static ControlCommandType fromJsonName(String jsonName) {
    for (ControlCommandType type : values()) {
      if (type.getJsonName().equalsIgnoreCase(jsonName)) {
        return type;
      }
    }
    return null;
  }
}
