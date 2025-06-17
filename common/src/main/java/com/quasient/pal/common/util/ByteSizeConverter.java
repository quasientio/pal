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

import java.text.CharacterIterator;
import java.text.StringCharacterIterator;

/**
 * Utility class for converting byte counts into human-readable string formats.
 *
 * <p>This class provides methods to format byte values into more easily readable strings, using
 * either SI (decimal) or binary prefixes.
 *
 * <p>Credits: <a
 * href="https://stackoverflow.com/questions/3758606/how-to-convert-byte-size-into-human-readable-format-in-java">...</a>
 */
public final class ByteSizeConverter {

  /** Private constructor to prevent instantiation of this utility class. */
  private ByteSizeConverter() {}

  /**
   * Converts the given byte count to a human-readable string using SI (decimal) units.
   *
   * <p>Formats the byte value into a string with SI prefixes (k, M, G, etc.) based on factors of
   * 1000. For byte counts between -1000 and 1000, it returns the value in bytes.
   *
   * @param bytes the number of bytes to convert; can be negative
   * @return a formatted string representing the byte count in SI units
   */
  private static String humanReadableByteCountSi(long bytes) {
    if (-1000 < bytes && bytes < 1000) {
      return bytes + " B";
    }
    CharacterIterator ci = new StringCharacterIterator("kMGTPE");
    while (bytes <= -999_950 || bytes >= 999_950) {
      bytes /= 1000;
      ci.next();
    }
    return String.format("%.1f %cB", bytes / 1000.0, ci.current());
  }

  /**
   * Converts the given byte count to a human-readable string using binary units.
   *
   * <p>Formats the byte value into a string with binary prefixes (Ki, Mi, Gi, etc.) based on
   * factors of 1024. For byte counts less than 1024, it returns the value in bytes.
   *
   * @param bytes the number of bytes to convert; can be negative
   * @return a formatted string representing the byte count in binary units
   */
  private static String humanReadableByteCountBin(long bytes) {
    long absB = bytes == Long.MIN_VALUE ? Long.MAX_VALUE : Math.abs(bytes);
    if (absB < 1024) {
      return bytes + " B";
    }
    long value = absB;
    CharacterIterator ci = new StringCharacterIterator("KMGTPE");
    for (int i = 40; i >= 0 && absB > 0xfffccccccccccccL >> i; i -= 10) {
      value >>= 10;
      ci.next();
    }
    value *= Long.signum(bytes);
    return String.format("%.1f %ciB", value / 1024.0, ci.current());
  }

  /**
   * Converts the given byte count to a human-readable string using either SI or binary units.
   *
   * <p>Selects the formatting method based on the {@code si} parameter. If {@code true}, it uses SI
   * units (decimal prefixes); otherwise, it uses binary units.
   *
   * @param bytes the number of bytes to convert; can be negative
   * @param si {@code true} to use SI (decimal) units, {@code false} to use binary units
   * @return a formatted string representing the byte count in the chosen units
   */
  public static String humanReadableByteCount(long bytes, boolean si) {
    return si ? humanReadableByteCountSi(bytes) : humanReadableByteCountBin(bytes);
  }
}
