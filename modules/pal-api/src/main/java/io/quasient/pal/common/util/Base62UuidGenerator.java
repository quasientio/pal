/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.quasient.pal.common.util;

import java.math.BigInteger;
import java.util.UUID;

/**
 * This implementation of the {@link IdGenerator} interface provides a standardized method for
 * generating Base62-encoded unique identifiers, utilizing {@link UUID} and {@link BigInteger} for
 * encoding operations.
 */
public class Base62UuidGenerator implements IdGenerator {

  /**
   * The character set used for encoding, consisting of digits, uppercase, and lowercase letters.
   */
  private static final char[] ALPHABET =
      "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz".toCharArray();

  /**
   * Encodes an unsigned 64-bit value to exactly 11 base62 chars (left-padded with '0')
   *
   * @param out the destination buffer
   * @param offset the starting index in {@code out} at which to write the first character
   * @param v the value to encode
   */
  private static void encodeUnsignedLongTo(char[] out, int offset, long v) {
    int i = 10 + offset;
    long x = v;
    do {
      long q = Long.divideUnsigned(x, 62);
      int r = (int) Long.remainderUnsigned(x, 62);
      out[i--] = ALPHABET[r];
      x = q;
    } while (x != 0L);
    while (i >= offset) out[i--] = ALPHABET[0];
  }

  /**
   * {@inheritDoc}
   *
   * <p>This implementation generates a random UUID and encodes it into a Base62 string.
   *
   * @return A Base62-encoded UUID string.
   */
  @Override
  public String nextId() {
    UUID u = UUID.randomUUID(); // CSPRNG-backed in OpenJDK
    char[] buf = new char[22];
    encodeUnsignedLongTo(buf, 0, u.getMostSignificantBits());
    encodeUnsignedLongTo(buf, 11, u.getLeastSignificantBits());
    return new String(buf);
  }
}
