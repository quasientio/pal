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
