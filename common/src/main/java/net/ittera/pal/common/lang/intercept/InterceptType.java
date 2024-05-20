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

package net.ittera.pal.common.lang.intercept;

public enum InterceptType {
  BEFORE((byte) 1),
  AFTER((byte) 2),
  AROUND((byte) 3),
  BEFORE_ASYNC((byte) 4),
  AFTER_ASYNC((byte) 5),
  ;

  private final byte idx;

  InterceptType(byte idx) {
    this.idx = idx;
  }

  public static InterceptType fromByte(byte typeAsByte) {
    return switch (typeAsByte) {
      case 1 -> BEFORE;
      case 2 -> AFTER;
      case 3 -> AROUND;
      case 4 -> BEFORE_ASYNC;
      case 5 -> AFTER_ASYNC;
      default -> throw new IllegalArgumentException("Unknown intercept type: " + typeAsByte);
    };
  }

  public byte toByte() {
    return idx;
  }
}
