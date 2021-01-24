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

package net.ittera.pal.tools.stats;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicLong;
import javax.annotation.Nullable;
import net.ittera.pal.messages.protobuf.Exec.ExecMessageType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ContinuousPrinter implements Runnable {

  private final Logger logger = LoggerFactory.getLogger(ContinuousPrinter.class);
  private static int DEFAULT_SECS_TO_SLEEP = 2;
  private final int secsToSleep;
  private boolean done;
  private boolean asJson;
  private final Counters counters;
  private Gson gson;

  public ContinuousPrinter(Counters counters) {
    this(counters, false, null);
  }

  public ContinuousPrinter(Counters counters, boolean asJson) {
    this(counters, asJson, null);
  }

  public ContinuousPrinter(Counters counters, boolean asJson, @Nullable Integer secsToSleep) {
    this.counters = counters;
    this.asJson = asJson;
    if (asJson) {
      gson = new GsonBuilder().setPrettyPrinting().create();
    }
    this.secsToSleep = secsToSleep == null ? DEFAULT_SECS_TO_SLEEP : secsToSleep;
    logger.info("Created new printer");
  }

  @Override
  public void run() {

    logger.debug("Starting to print");
    while (!done) {
      clearScreen();
      if (asJson) {
        System.out.println(gson.toJson(counters));
      } else {
        Arrays.stream(ExecMessageType.values())
            .forEach(
                msgType -> {
                  AtomicLong cntr = counters.getMessagesByType().get(msgType);
                  System.out.printf(
                      "# msgs of type: %16s : %d%n", msgType, cntr == null ? 0 : cntr.longValue());
                });
        printSeparator();
        counters.getMessagesFromPeer().entrySet().stream()
            .forEach(
                entry -> {
                  System.out.printf(
                      "# msgs by peer: %40s : %d%n",
                      entry.getKey(), entry.getValue() == null ? 0 : entry.getValue().longValue());
                });
        printSeparator();
        counters.getMessagesByThread().entrySet().stream()
            .forEach(
                entry -> {
                  System.out.printf(
                      "# msgs by thread: %40s : %d%n",
                      entry.getKey(), entry.getValue() == null ? 0 : entry.getValue().longValue());
                });
        printSeparator();
        counters.getObjectsCreated().entrySet().stream()
            .forEach(
                entry -> {
                  System.out.printf(
                      "# created objects of class: %40s = %d%n",
                      entry.getKey(), entry.getValue() == null ? 0 : entry.getValue().longValue());
                });
        printSeparator();
        counters.getMethodsCalled().entrySet().stream()
            .forEach(
                entry -> {
                  System.out.printf(
                      "# calls to <class>.<method>: %40s = %d%n",
                      entry.getKey(), entry.getValue() == null ? 0 : entry.getValue().longValue());
                });
        printSeparator();
        counters.getFieldReads().entrySet().stream()
            .forEach(
                entry -> {
                  System.out.printf(
                      "# reads from <class>.<field>: %40s = %d%n",
                      entry.getKey(), entry.getValue() == null ? 0 : entry.getValue().longValue());
                });
        printSeparator();
        counters.getFieldWrites().entrySet().stream()
            .forEach(
                entry -> {
                  System.out.printf(
                      "# writes to <class>.<field>: %40s = %d%n",
                      entry.getKey(), entry.getValue() == null ? 0 : entry.getValue().longValue());
                });
      }
      sleep();
    }
  }

  private void printSeparator() {
    System.out.printf("===============================================================%n");
  }

  private void sleep() {
    try {
      Thread.sleep(secsToSleep * 1000);
    } catch (InterruptedException e) {
      logger.warn("thread interrupted", e);
    }
  }

  public void setDone(boolean done) {
    this.done = done;
  }

  private static void clearScreen() {
    System.out.print("\033[H\033[2J");
    System.out.flush();
  }
}
