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

import java.util.concurrent.ThreadLocalRandom;

/**
 * Fast ID generator implementation. For a cryptographically strong/predictable id that uses
 * UUID.randomUUID() (and CSPRNG) use {@link Base62UuidGenerator}
 */
public final class FastIdGeneratorNonCrypto implements IdGenerator {

  /**
   * The character set used for encoding, consisting of digits, uppercase, and lowercase letters.
   */
  private static final char[] A =
      "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz".toCharArray();

  /**
   * Encodes an <strong>unsigned</strong> 64-bit value into exactly 11 Base-62 characters.
   *
   * <p>The encoding is written in-place into the provided {@code out} buffer starting at {@code
   * off}. The output is left-padded with {@code '0'} (the first symbol in the alphabet) to
   * guarantee a fixed width of 11 characters. Because {@code 62^11 > 2^64}, every unsigned 64-bit
   * value fits in 11 Base-62 digits.
   *
   * <p>This method performs no allocations and uses {@link Long#divideUnsigned(long, long)} and
   * {@link Long#remainderUnsigned(long, long)} to ensure unsigned semantics.
   *
   * @param out the destination buffer; must have capacity for at least {@code off + 11} chars
   * @param off the starting index in {@code out} at which to write the first character
   * @param v the value to encode, interpreted as an <em>unsigned</em> 64-bit integer
   * @throws IndexOutOfBoundsException if {@code off < 0} or {@code out.length < off + 11}
   * @implNote Thread-safe as long as callers provide distinct buffers; {@code A} is immutable.
   */
  private static void enc64(char[] out, int off, long v) {
    int i = 10 + off;
    long x = v;
    do {
      long q = Long.divideUnsigned(x, 62);
      out[i--] = A[(int) Long.remainderUnsigned(x, 62)];
      x = q;
    } while (x != 0L);
    while (i >= off) out[i--] = A[0];
  }

  /** {@inheritDoc} */
  @Override
  public String nextId() {
    long a = ThreadLocalRandom.current().nextLong();
    long b = ThreadLocalRandom.current().nextLong();
    char[] buf = new char[22];
    enc64(buf, 0, a);
    enc64(buf, 11, b);
    return new String(buf);
  }
}
