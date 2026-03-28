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
