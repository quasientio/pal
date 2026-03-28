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

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Provides utility methods for compressing strings using GZIP and encoding them in Base64, as well
 * as decoding Base64-encoded strings and decompressing them back to their original form.
 */
public class GzipBase64Utils {

  /**
   * Compresses the specified text using GZIP and encodes the compressed data in Base64.
   *
   * @param text the input string to encode; must not be {@code null} or empty
   * @return a Base64-encoded string representing the compressed input
   * @throws IllegalArgumentException if {@code text} is {@code null} or empty
   * @throws RuntimeException if an I/O error occurs during encoding
   */
  public static String encode(String text) {
    if (text == null || text.isEmpty()) {
      throw new IllegalArgumentException("Input text cannot be null or empty");
    }

    try {
      // Convert the text to bytes
      byte[] inputBytes = text.getBytes(StandardCharsets.UTF_8);

      // GZIP the input bytes
      ByteArrayOutputStream gzipOutputStream = new ByteArrayOutputStream();
      try (GZIPOutputStream gzipStream = new GZIPOutputStream(gzipOutputStream)) {
        gzipStream.write(inputBytes);
      }
      byte[] gzippedBytes = gzipOutputStream.toByteArray();
      gzipOutputStream.close();

      // Encode the gzipped bytes to Base64
      return Base64.getEncoder().encodeToString(gzippedBytes);
    } catch (IOException e) {
      throw new RuntimeException("Error during encoding", e);
    }
  }

  /**
   * Decodes the specified Base64-encoded string and decompresses the resulting data using GZIP to
   * retrieve the original text.
   *
   * @param base64Encoded the Base64-encoded string to decode; must not be {@code null} or empty
   * @return the original uncompressed string
   * @throws IllegalArgumentException if {@code base64Encoded} is {@code null} or empty
   * @throws RuntimeException if an I/O error occurs during decoding
   */
  public static String decode(String base64Encoded) {
    if (base64Encoded == null || base64Encoded.isEmpty()) {
      throw new IllegalArgumentException("Encoded text cannot be null or empty");
    }

    try {
      // Decode Base64 to gzipped bytes
      byte[] gzippedBytes = Base64.getDecoder().decode(base64Encoded);

      // Decompress the gzipped bytes
      try (ByteArrayInputStream gzipInputStream = new ByteArrayInputStream(gzippedBytes);
          GZIPInputStream gzipStream = new GZIPInputStream(gzipInputStream);
          ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
        byte[] buffer = new byte[1024];
        int bytesRead;
        while ((bytesRead = gzipStream.read(buffer)) != -1) {
          outputStream.write(buffer, 0, bytesRead);
        }
        return outputStream.toString(StandardCharsets.UTF_8);
      }
    } catch (IOException e) {
      throw new RuntimeException("Error during decoding", e);
    }
  }
}
