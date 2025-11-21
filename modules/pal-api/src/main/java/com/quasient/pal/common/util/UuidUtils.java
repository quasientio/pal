/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
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
