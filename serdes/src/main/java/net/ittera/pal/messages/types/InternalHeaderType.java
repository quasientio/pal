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

public enum InternalHeaderType {
  /**
   * Sent before dispatching a received message. Value is the UUID of the receiving-dispatching
   * peer, not the producer.
   */
  WRITE_AHEAD;

  public static InternalHeaderType fromByte(byte typeAsByte) {
    return InternalHeaderType.values()[typeAsByte - 1];
  }

  public byte toByte() {
    return (byte) (this.ordinal() + 1);
  }
}
