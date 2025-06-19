package com.quasient.pal.common.util;

import static org.junit.Assert.assertEquals;

import java.util.Random;
import org.junit.Test;

public class GzipBase64UtilsTest {

  public static String generateRandomText(int length) {
    // Define the characters allowed in the random text
    String characters = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789 ";
    StringBuilder randomText = new StringBuilder();
    Random random = new Random();

    for (int i = 0; i < length; i++) {
      int index = random.nextInt(characters.length());
      randomText.append(characters.charAt(index));
    }

    return randomText.toString();
  }

  @Test
  public void encode() {
    String text = generateRandomText(1000);
    String encoded = GzipBase64Utils.encode(text);
    String decoded = GzipBase64Utils.decode(encoded);
    assertEquals(text, decoded);
  }
}
