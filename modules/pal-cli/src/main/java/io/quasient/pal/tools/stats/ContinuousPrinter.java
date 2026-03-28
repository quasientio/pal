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
package io.quasient.pal.tools.stats;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.quasient.pal.messages.types.MessageType;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicLong;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Continuously prints statistical data from counters at regular intervals.
 *
 * <p>This class implements {@link Runnable} and periodically outputs the current state of various
 * counters to the standard output. It supports both human-readable and JSON formats, configurable
 * sleep intervals between prints, and can be gracefully stopped.
 */
public class ContinuousPrinter implements Runnable {

  /** Logger instance for recording runtime information and debug messages. */
  private final Logger logger = LoggerFactory.getLogger(ContinuousPrinter.class);

  /** Default number of seconds the printer sleeps between prints when not specified otherwise. */
  private static final int DEFAULT_SECS_TO_SLEEP = 2;

  /** Number of seconds to sleep between each print cycle. */
  private final int secsToSleep;

  /** Flag indicating whether the printer should stop running. */
  private boolean done;

  /** Flag indicating whether the output should be in JSON format. */
  private final boolean asJson;

  /** Instance of {@link Counters} containing the statistical data to be printed. */
  private final Counters counters;

  /**
   * Gson instance used for serializing counters to JSON format when {@code asJson} is {@code true}.
   */
  private Gson gson;

  /**
   * Constructs a {@code ContinuousPrinter} with default settings.
   *
   * @param counters the {@link Counters} instance containing statistical data to print
   */
  public ContinuousPrinter(Counters counters) {
    this(counters, false, null);
  }

  /**
   * Constructs a {@code ContinuousPrinter} with specified output format.
   *
   * @param counters the {@link Counters} instance containing statistical data to print
   * @param asJson if {@code true}, outputs the data in JSON format; otherwise, uses a
   *     human-readable format
   */
  public ContinuousPrinter(Counters counters, boolean asJson) {
    this(counters, asJson, null);
  }

  /**
   * Constructs a {@code ContinuousPrinter} with specified output format and sleep interval.
   *
   * @param counters the {@link Counters} instance containing statistical data to print
   * @param asJson if {@code true}, outputs the data in JSON format; otherwise, uses a
   *     human-readable format
   * @param secsToSleep the number of seconds to sleep between each print cycle. If {@code null},
   *     defaults to {@link #DEFAULT_SECS_TO_SLEEP}
   */
  public ContinuousPrinter(Counters counters, boolean asJson, @Nullable Integer secsToSleep) {
    this.counters = counters;
    this.asJson = asJson;
    if (asJson) {
      gson = new GsonBuilder().setPrettyPrinting().create();
    }
    this.secsToSleep = secsToSleep == null ? DEFAULT_SECS_TO_SLEEP : secsToSleep;
    logger.info("Created new printer");
  }

  /**
   * Executes the continuous printing process.
   *
   * <p>This method is called when the printer is run in a separate thread. It continuously prints
   * the statistical data at intervals defined by {@code secsToSleep} until the {@code done} flag is
   * set.
   */
  @Override
  public void run() {

    logger.debug("Starting to print");
    while (!done) {
      clearScreen();
      if (asJson) {
        System.out.println(gson.toJson(counters));
      } else {
        Arrays.stream(MessageType.values())
            .forEach(
                msgType -> {
                  AtomicLong messageCounter = counters.getMessagesByType().get(msgType.name());
                  System.out.printf(
                      "# messages of type: %16s : %d%n",
                      msgType, messageCounter == null ? 0 : messageCounter.longValue());
                });
        printSeparator();
        counters
            .getMessagesFromPeer()
            .forEach(
                (key, value) ->
                    System.out.printf(
                        "# messages by peer: %40s : %d%n",
                        key, value == null ? 0 : value.longValue()));
        printSeparator();
        counters
            .getMessagesByThread()
            .forEach(
                (key, value) ->
                    System.out.printf(
                        "# messages by thread: %40s : %d%n",
                        key, value == null ? 0 : value.longValue()));
        printSeparator();
        counters
            .getObjectsCreated()
            .forEach(
                (key, value) ->
                    System.out.printf(
                        "# created objects of class: %40s = %d%n",
                        key, value == null ? 0 : value.longValue()));
        printSeparator();
        counters
            .getMethodsCalled()
            .forEach(
                (key, value) ->
                    System.out.printf(
                        "# calls to <class>.<method>: %40s = %d%n",
                        key, value == null ? 0 : value.longValue()));
        printSeparator();
        counters
            .getFieldReads()
            .forEach(
                (key, value) ->
                    System.out.printf(
                        "# reads from <class>.<field>: %40s = %d%n",
                        key, value == null ? 0 : value.longValue()));
        printSeparator();
        counters
            .getFieldWrites()
            .forEach(
                (key, value) ->
                    System.out.printf(
                        "# writes to <class>.<field>: %40s = %d%n",
                        key, value == null ? 0 : value.longValue()));
      }
      sleep();
    }
  }

  /**
   * Prints a separator line to the standard output.
   *
   * <p>Used to visually separate different sections of the printed statistical data.
   */
  private void printSeparator() {
    System.out.printf("===============================================================%n");
  }

  /**
   * Pauses the printing thread for the configured number of seconds.
   *
   * <p>If the thread is interrupted during sleep, a warning is logged.
   */
  private void sleep() {
    try {
      Thread.sleep(secsToSleep * 1000L);
    } catch (InterruptedException e) {
      logger.warn("thread interrupted", e);
    }
  }

  /**
   * Signals the printer to stop running.
   *
   * @param done {@code true} to terminate the printing loop; {@code false} to continue running
   */
  public void setDone(boolean done) {
    this.done = done;
  }

  /**
   * Clears the console screen.
   *
   * <p>Uses ANSI escape codes to clear the terminal display before printing new statistical data.
   */
  private static void clearScreen() {
    System.out.print("\033[H\033[2J");
    System.out.flush();
  }
}
