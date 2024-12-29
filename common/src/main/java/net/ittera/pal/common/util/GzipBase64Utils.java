package net.ittera.pal.common.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class GzipBase64Utils {

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

      // Encode the gzipped bytes to Base64
      return Base64.getEncoder().encodeToString(gzippedBytes);
    } catch (IOException e) {
      throw new RuntimeException("Error during encoding", e);
    }
  }

  public static String decode(String base64Encoded) {
    if (base64Encoded == null || base64Encoded.isEmpty()) {
      throw new IllegalArgumentException("Encoded text cannot be null or empty");
    }

    try {
      // Decode Base64 to gzipped bytes
      byte[] gzippedBytes = Base64.getDecoder().decode(base64Encoded);

      // Decompress the gzipped bytes
      ByteArrayInputStream gzipInputStream = new ByteArrayInputStream(gzippedBytes);
      try (GZIPInputStream gzipStream = new GZIPInputStream(gzipInputStream);
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
