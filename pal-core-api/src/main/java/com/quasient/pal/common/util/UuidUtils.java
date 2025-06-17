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

package com.quasient.pal.common.util;

import com.google.common.primitives.Longs;
import java.util.Objects;
import java.util.UUID;

/**
 * Provides utility methods for converting between UUIDs and their byte array representations.
 *
 * <p>This class offers methods to serialize and deserialize UUIDs to and from byte arrays, enabling
 * a more efficient storage and transmission in binary form.
 */
public final class UuidUtils {

  /** Private constructor to prevent instantiation of this utility class. */
  private UuidUtils() {}

  /**
   * Converts a UUID string to its byte array representation.
   *
   * <p>Parses the given UUID string and serializes it into a 16-byte array, with the least
   * significant bits first.
   *
   * @param uuid the UUID string to convert, in standard string format
   * @return the byte array representation of the UUID
   */
  public static byte[] toBytes(String uuid) {
    return toBytes(UUID.fromString(uuid));
  }

  /**
   * Serializes a UUID into a 16-byte array.
   *
   * <p>The byte array contains the most significant bits followed by the least significant bits of
   * the UUID. This method provides a compact binary representation compared to string
   * serialization, which uses 36 bytes (e.g., {@code
   * uuid.toString().getBytes(StandardCharsets.UTF_8)}).
   *
   * @param uuid the UUID to convert
   * @return a 16-byte array representation of the UUID
   * @throws NullPointerException if {@code uuid} is null
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

  /**
   * Deserializes a 16-byte array into a UUID.
   *
   * <p>The byte array must contain the UUID's most significant bits followed by the least
   * significant bits, in the same order as produced by {@link #toBytes(UUID)}. The array must be
   * exactly 16 bytes in length.
   *
   * @param bytes the 16-byte array to convert into a UUID
   * @return the UUID represented by the byte array
   */
  public static UUID fromBytes(byte[] bytes) {
    byte[] lsbB = new byte[8];
    byte[] msbB = new byte[8];
    System.arraycopy(bytes, 0, lsbB, 0, 8);
    System.arraycopy(bytes, 8, msbB, 0, 8);

    return new UUID(Longs.fromByteArray(msbB), Longs.fromByteArray(lsbB));
  }
}
