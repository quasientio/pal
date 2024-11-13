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

package net.ittera.pal.common.util;

import com.google.common.primitives.Longs;
import java.util.Objects;
import java.util.UUID;

public final class UuidUtils {

  private UuidUtils() {}

  public static byte[] toBytes(String uuid) {
    return toBytes(UUID.fromString(uuid));
  }

  /**
   * Converts a UUID to a byte array. The serialization length is 16 bytes, with the least
   * significant bits first. A string-serialized uuid, as in
   * uuid.toString().getBytes(StandardCharsets.UTF_8) will use 36 bytes.
   *
   * @param uuid the UUID to convert
   * @return the byte array representation of the UUID
   */
  public static byte[] toBytes(UUID uuid) {
    Objects.requireNonNull(uuid);
    byte[] lsbB = Longs.toByteArray(uuid.getLeastSignificantBits());
    byte[] msbB = Longs.toByteArray(uuid.getMostSignificantBits());
    byte[] uuidBytes = new byte[16];
    System.arraycopy(lsbB, 0, uuidBytes, 0, 8);
    System.arraycopy(msbB, 0, uuidBytes, 8, 8);
    return uuidBytes;
  }

  public static UUID fromBytes(byte[] bytes) {
    byte[] lsbB = new byte[8];
    byte[] msbB = new byte[8];
    System.arraycopy(bytes, 0, lsbB, 0, 8);
    System.arraycopy(bytes, 8, msbB, 0, 8);

    return new UUID(Longs.fromByteArray(msbB), Longs.fromByteArray(lsbB));
  }
}
