package net.ittera.pal.apps;

import static java.lang.String.format;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

public class HelloWorldLoop {
  private static final String NAMES_FILE = "/names";
  private static List<String> names;
  private static boolean PRINT_STATS = false;

  static {
    try (BufferedReader br =
        new BufferedReader(
            new InputStreamReader(HelloWorldLoop.class.getResourceAsStream(NAMES_FILE)))) {
      names = br.lines().collect(Collectors.toList());
    } catch (IOException e) {
      System.err.println("Error opening names file: " + e.getMessage());
      System.exit(1);
    }
  }

  public static void main(String[] args) {
    final DateTimeFormatter formatter = DateTimeFormatter.ISO_TIME;
    long minuteStart = Instant.now().getEpochSecond();
    long hellosPerMinute = 0;
    while (true) {
      LocalDateTime currentTime = LocalDateTime.now();
      final String salute =
          format(
              "Hello %-15s hey! it's %s here.",
              names.get(ThreadLocalRandom.current().nextInt(0, names.size())) + "!",
              formatter.format(currentTime));
      System.out.println(salute);
      if (PRINT_STATS) {
        hellosPerMinute++;
        long now = Instant.now().getEpochSecond();
        if (now - minuteStart >= 60) {
          System.out.printf("%d hello's in 1 minute%n", hellosPerMinute);
          hellosPerMinute = 0;
          minuteStart = now;
        }
      }
    }
  }
}
