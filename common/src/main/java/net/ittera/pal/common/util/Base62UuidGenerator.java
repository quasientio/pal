package net.ittera.pal.common.util;

import java.math.BigInteger;
import java.util.UUID;

public class Base62UuidGenerator implements IdGenerator {
  private static final char[] BASE62_CHARACTERS =
      "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz".toCharArray();

  /**
   * Encodes a 128-bit UUID value into a Base62 string using BigInteger.
   *
   * @param bytes The 16-byte array representing the UUID.
   * @return The Base62-encoded string.
   */
  private static String encodeBase62(byte[] bytes) {
    // Use BigInteger to handle the full 128-bit number positively
    BigInteger value = new BigInteger(1, bytes);

    // If value is zero (extremely unlikely for a random UUID, but possible if a UUID was all zeros)
    if (value.equals(BigInteger.ZERO)) {
      return String.valueOf(BASE62_CHARACTERS[0]);
    }

    StringBuilder base62 = new StringBuilder();
    BigInteger base = BigInteger.valueOf(62);

    // Convert to Base62 by repeatedly taking remainder and dividing
    while (value.compareTo(BigInteger.ZERO) > 0) {
      BigInteger[] divRem = value.divideAndRemainder(base);
      value = divRem[0];
      int index = divRem[1].intValue();
      base62.append(BASE62_CHARACTERS[index]);
    }

    // The digits are appended in reverse order, so reverse at the end
    return base62.reverse().toString();
  }

  /**
   * Generates a Base62-encoded UUID.
   *
   * @return A Base62-encoded UUID string.
   */
  @Override
  public String nextId() {
    UUID uuid = UUID.randomUUID();
    byte[] bytes = new byte[16];
    long msb = uuid.getMostSignificantBits();
    long lsb = uuid.getLeastSignificantBits();

    for (int i = 0; i < 8; i++) {
      bytes[i] = (byte) ((msb >>> (8 * (7 - i))) & 0xFF);
      bytes[8 + i] = (byte) ((lsb >>> (8 * (7 - i))) & 0xFF);
    }

    return encodeBase62(bytes);
  }

  public static void main(String[] args) {
    Base62UuidGenerator generator = new Base62UuidGenerator();

    // Generate and print 5 Base62-encoded UUIDs
    for (int i = 0; i < 25; i++) {
      System.out.println(generator.nextId());
    }
  }
}
