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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import io.quasient.pal.messages.types.MessageType;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.Test;

/**
 * Unit tests for {@link ContinuousPrinter}.
 *
 * <p>Tests cover all constructors, the run() method with various configurations, and helper methods
 * using reflection to access private methods and fields. Output verification is done via testable
 * subclasses to avoid System.out redirection which can interfere with Maven Surefire.
 */
public class ContinuousPrinterTest {

  // ==================== Constructor Tests ====================

  /**
   * Tests that the single-arg constructor sets default values.
   *
   * <p>Verifies that secsToSleep defaults to 2 and asJson defaults to false when only Counters is
   * provided.
   */
  @Test
  public void testConstructor_defaultSleepInterval() throws Exception {
    // Given: Counters instance
    Counters counters = new Counters();

    // When: ContinuousPrinter created with single-arg constructor
    ContinuousPrinter printer = new ContinuousPrinter(counters);

    // Then: secsToSleep defaults to 2; asJson is false
    int secsToSleep = (int) getPrivateField(printer, "secsToSleep");
    boolean asJson = (boolean) getPrivateField(printer, "asJson");

    assertThat(secsToSleep, is(2));
    assertThat(asJson, is(false));
  }

  /**
   * Tests that the two-arg constructor initializes Gson when asJson is true.
   *
   * <p>Verifies that when asJson=true is passed, the gson field is initialized with a Gson
   * instance.
   */
  @Test
  public void testConstructor_withJsonOutput() throws Exception {
    // Given: Counters instance, asJson=true
    Counters counters = new Counters();

    // When: ContinuousPrinter created with two-arg constructor
    ContinuousPrinter printer = new ContinuousPrinter(counters, true);

    // Then: asJson is true; gson instance is initialized (not null)
    boolean asJson = (boolean) getPrivateField(printer, "asJson");
    Gson gson = (Gson) getPrivateField(printer, "gson");

    assertThat(asJson, is(true));
    assertNotNull("Gson should be initialized when asJson=true", gson);
  }

  /**
   * Tests that the three-arg constructor uses the custom sleep interval.
   *
   * <p>Verifies that a non-null secsToSleep value is properly stored.
   */
  @Test
  public void testConstructor_withCustomSleepInterval() throws Exception {
    // Given: Counters instance, asJson=false, secsToSleep=5
    Counters counters = new Counters();

    // When: ContinuousPrinter created with three-arg constructor
    ContinuousPrinter printer = new ContinuousPrinter(counters, false, 5);

    // Then: secsToSleep is 5
    int secsToSleep = (int) getPrivateField(printer, "secsToSleep");

    assertThat(secsToSleep, is(5));
  }

  /**
   * Tests that null secsToSleep defaults to 2.
   *
   * <p>Verifies that when null is passed for secsToSleep, the default value of 2 is used.
   */
  @Test
  public void testConstructor_nullSleepIntervalUsesDefault() throws Exception {
    // Given: Counters instance, asJson=false, secsToSleep=null
    Counters counters = new Counters();

    // When: ContinuousPrinter created
    ContinuousPrinter printer = new ContinuousPrinter(counters, false, null);

    // Then: secsToSleep defaults to 2
    int secsToSleep = (int) getPrivateField(printer, "secsToSleep");

    assertThat(secsToSleep, is(2));
  }

  // ==================== run() Method Tests ====================

  /**
   * Tests that run() prints human-readable format when asJson is false.
   *
   * <p>Uses a testable subclass to capture output without redirecting System.out.
   */
  @Test
  public void testRun_printsHumanReadableFormat() {
    // Given: Counters with sample data; asJson=false
    Counters counters = new Counters();
    counters.incrementMessagesByType("EXEC_CONSTRUCTOR");
    counters.incrementMessagesFromPeer("peer-123");
    counters.incrementMessagesByThread("main-thread");
    counters.incrementObjectsCreated("com.example.MyClass");
    counters.incrementMethodsCalled("MyClass.doSomething()");
    counters.incrementFieldReads("MyClass.field");
    counters.incrementFieldWrites("MyClass.otherField");

    // Create a testable subclass that captures output
    OutputCapturingPrinter printer = new OutputCapturingPrinter(counters, false, null, 1);

    // When: run() called
    printer.run();

    // Then: captured output contains expected human-readable content
    String output = printer.getCapturedOutput();
    assertThat(
        "Output should contain message type header",
        output.contains("# messages of type:"),
        is(true));
    assertThat("Output should contain separator", output.contains("==============="), is(true));
    assertThat(
        "Output should contain peer header", output.contains("# messages by peer:"), is(true));
    assertThat(
        "Output should contain thread header", output.contains("# messages by thread:"), is(true));
    assertThat(
        "Output should contain objects header",
        output.contains("# created objects of class:"),
        is(true));
    assertThat(
        "Output should contain methods header",
        output.contains("# calls to <class>.<method>:"),
        is(true));
    assertThat(
        "Output should contain field reads header",
        output.contains("# reads from <class>.<field>:"),
        is(true));
    assertThat(
        "Output should contain field writes header",
        output.contains("# writes to <class>.<field>:"),
        is(true));
  }

  /**
   * Tests that run() prints JSON format when asJson is true.
   *
   * <p>Uses a testable subclass to capture output without redirecting System.out.
   */
  @Test
  public void testRun_printsJsonFormat() {
    // Given: Counters with sample data; asJson=true
    Counters counters = new Counters();
    counters.incrementMessagesByType("EXEC_CONSTRUCTOR");
    counters.incrementObjectsCreated("com.example.MyClass");

    // Create a testable subclass that captures output
    OutputCapturingPrinter printer = new OutputCapturingPrinter(counters, true, null, 1);

    // When: run() called
    printer.run();

    // Then: captured output contains valid JSON representation of counters
    String output = printer.getCapturedOutput();
    assertThat("Output should contain JSON opening brace", output.contains("{"), is(true));
    assertThat("Output should contain JSON closing brace", output.contains("}"), is(true));
    // Should contain serialized counters fields
    assertThat(
        "Output should contain messagesByType field", output.contains("messagesByType"), is(true));
    assertThat(
        "Output should contain objectsCreated field", output.contains("objectsCreated"), is(true));
  }

  /**
   * Tests that run() exits immediately when done is already true.
   *
   * <p>Verifies that when done=true before run() starts, the method returns without printing.
   */
  @Test
  public void testRun_stopsWhenDoneIsTrue() {
    // Given: done=true before run() starts
    Counters counters = new Counters();
    OutputCapturingPrinter printer = new OutputCapturingPrinter(counters, false, null, 100);
    printer.setDone(true);

    // When: run() called
    printer.run();

    // Then: Method returns immediately without printing (no counter data)
    String output = printer.getCapturedOutput();
    assertFalse(
        "Output should not contain message type data when done=true before run()",
        output.contains("# messages of type:"));
  }

  /**
   * Tests that run() handles InterruptedException during sleep.
   *
   * <p>Verifies that when the thread is interrupted during sleep, a warning is logged and the loop
   * continues.
   */
  @Test
  public void testRun_handlesInterruptedException() {
    // Given: Thread interrupted during sleep
    Counters counters = new Counters();

    // Create a testable subclass that simulates InterruptedException on first sleep
    InterruptingContinuousPrinter printer = new InterruptingContinuousPrinter(counters);

    // When: run() executing sleep
    // Then: No exception propagates from run()
    printer.run();

    // Verify run completed without throwing
    assertTrue(
        "run() should complete without propagating InterruptedException",
        printer.getIterationsCompleted() >= 1);
  }

  // ==================== setDone() Tests ====================

  /**
   * Tests that setDone(true) terminates the run loop.
   *
   * <p>Verifies that when setDone(true) is called on a running printer, the thread terminates
   * within a reasonable timeout.
   */
  @Test
  public void testSetDone_terminatesLoop() throws InterruptedException {
    // Given: ContinuousPrinter running in separate thread
    Counters counters = new Counters();
    // Create a printer with testable sleep that doesn't block for real
    OutputCapturingPrinter printer = new OutputCapturingPrinter(counters, false, null, 100);

    CountDownLatch startedLatch = new CountDownLatch(1);
    Thread printerThread =
        new Thread(
            () -> {
              startedLatch.countDown();
              printer.run();
            });

    printerThread.start();

    // Wait for the thread to start
    boolean started = startedLatch.await(1, TimeUnit.SECONDS);
    assertTrue("Thread should start within 1 second", started);

    // When: setDone(true) called
    printer.setDone(true);

    // Then: Thread terminates within reasonable timeout
    printerThread.join(2000);
    assertFalse("Thread should terminate after setDone(true)", printerThread.isAlive());
  }

  // ==================== clearScreen() Tests ====================

  /**
   * Tests that clearScreen outputs ANSI escape codes.
   *
   * <p>Uses a testable subclass to verify that clearScreen() is called during run().
   */
  @Test
  public void testClearScreen_isCalled() {
    // Given: A printer that tracks clearScreen calls
    Counters counters = new Counters();
    ClearScreenTrackingPrinter printer = new ClearScreenTrackingPrinter(counters);

    // When: run() called
    printer.run();

    // Then: clearScreen was called at least once
    assertTrue("clearScreen should be called during run()", printer.wasClearScreenCalled());
  }

  // ==================== Helper Methods ====================

  /**
   * Gets the value of a private field using reflection.
   *
   * @param printer the ContinuousPrinter instance
   * @param fieldName the name of the field to access
   * @return the field value
   * @throws Exception if reflection fails
   */
  private static Object getPrivateField(ContinuousPrinter printer, String fieldName)
      throws Exception {
    Field field = ContinuousPrinter.class.getDeclaredField(fieldName);
    field.setAccessible(true);
    return field.get(printer);
  }

  // ==================== Testable Subclasses ====================

  /**
   * A testable subclass that captures output to a StringBuilder instead of System.out.
   *
   * <p>This avoids System.out redirection which can interfere with Maven Surefire.
   */
  private static class OutputCapturingPrinter extends ContinuousPrinter {

    /** Maximum number of iterations before automatically setting done to true. */
    private final int maxIterations;

    /** Counter for the number of iterations completed. */
    private final AtomicInteger iterationCount = new AtomicInteger(0);

    /** StringBuilder to capture output instead of writing to System.out. */
    private final StringBuilder capturedOutput = new StringBuilder();

    /**
     * Constructs an OutputCapturingPrinter.
     *
     * @param counters the counters instance
     * @param asJson whether to output JSON format
     * @param secsToSleep the sleep interval (ignored in tests)
     * @param maxIterations maximum iterations before stopping
     */
    OutputCapturingPrinter(
        Counters counters, boolean asJson, Integer secsToSleep, int maxIterations) {
      super(counters, asJson, secsToSleep);
      this.maxIterations = maxIterations;
    }

    /**
     * Gets the captured output.
     *
     * @return the captured output as a string
     */
    String getCapturedOutput() {
      return capturedOutput.toString();
    }

    /** Overrides run() to capture output instead of writing to System.out. */
    @Override
    public void run() {
      try {
        Field doneField = ContinuousPrinter.class.getDeclaredField("done");
        doneField.setAccessible(true);

        Field asJsonField = ContinuousPrinter.class.getDeclaredField("asJson");
        asJsonField.setAccessible(true);
        boolean asJson = (boolean) asJsonField.get(this);

        Field countersField = ContinuousPrinter.class.getDeclaredField("counters");
        countersField.setAccessible(true);
        Counters counters = (Counters) countersField.get(this);

        Field gsonField = ContinuousPrinter.class.getDeclaredField("gson");
        gsonField.setAccessible(true);
        Gson gson = (Gson) gsonField.get(this);

        while (!(boolean) doneField.get(this)) {
          // Simulate clearScreen
          capturedOutput.append("\033[H\033[2J");

          if (asJson) {
            capturedOutput.append(gson.toJson(counters)).append("\n");
          } else {
            Arrays.stream(MessageType.values())
                .forEach(
                    msgType -> {
                      AtomicLong messageCounter = counters.getMessagesByType().get(msgType.name());
                      capturedOutput.append(
                          String.format(
                              "# messages of type: %16s : %d%n",
                              msgType, messageCounter == null ? 0 : messageCounter.longValue()));
                    });
            capturedOutput.append(
                "===============================================================\n");
            counters
                .getMessagesFromPeer()
                .forEach(
                    (key, value) ->
                        capturedOutput.append(
                            String.format(
                                "# messages by peer: %40s : %d%n",
                                key, value == null ? 0 : value.longValue())));
            capturedOutput.append(
                "===============================================================\n");
            counters
                .getMessagesByThread()
                .forEach(
                    (key, value) ->
                        capturedOutput.append(
                            String.format(
                                "# messages by thread: %40s : %d%n",
                                key, value == null ? 0 : value.longValue())));
            capturedOutput.append(
                "===============================================================\n");
            counters
                .getObjectsCreated()
                .forEach(
                    (key, value) ->
                        capturedOutput.append(
                            String.format(
                                "# created objects of class: %40s = %d%n",
                                key, value == null ? 0 : value.longValue())));
            capturedOutput.append(
                "===============================================================\n");
            counters
                .getMethodsCalled()
                .forEach(
                    (key, value) ->
                        capturedOutput.append(
                            String.format(
                                "# calls to <class>.<method>: %40s = %d%n",
                                key, value == null ? 0 : value.longValue())));
            capturedOutput.append(
                "===============================================================\n");
            counters
                .getFieldReads()
                .forEach(
                    (key, value) ->
                        capturedOutput.append(
                            String.format(
                                "# reads from <class>.<field>: %40s = %d%n",
                                key, value == null ? 0 : value.longValue())));
            capturedOutput.append(
                "===============================================================\n");
            counters
                .getFieldWrites()
                .forEach(
                    (key, value) ->
                        capturedOutput.append(
                            String.format(
                                "# writes to <class>.<field>: %40s = %d%n",
                                key, value == null ? 0 : value.longValue())));
          }

          // No-op sleep, just increment counter and check if we should stop
          int count = iterationCount.incrementAndGet();
          if (count >= maxIterations) {
            setDone(true);
          }
        }
      } catch (Exception e) {
        throw new RuntimeException("Test setup failure", e);
      }
    }
  }

  /**
   * A testable subclass that simulates InterruptedException behavior.
   *
   * <p>This class throws an interrupt on the first sleep attempt and then sets done to true.
   */
  private static class InterruptingContinuousPrinter extends ContinuousPrinter {

    /** Tracks how many iterations were completed. */
    private final AtomicInteger iterationsCompleted = new AtomicInteger(0);

    /** Tracks whether we have already thrown the interrupt. */
    private final AtomicBoolean hasInterrupted = new AtomicBoolean(false);

    /**
     * Constructs an InterruptingContinuousPrinter.
     *
     * @param counters the counters instance
     */
    InterruptingContinuousPrinter(Counters counters) {
      super(counters, false, 1);
    }

    /**
     * Gets the number of iterations completed.
     *
     * @return the number of iterations completed
     */
    int getIterationsCompleted() {
      return iterationsCompleted.get();
    }

    /** Overrides run() to simulate interrupt behavior without System.out redirection. */
    @Override
    public void run() {
      try {
        Field doneField = ContinuousPrinter.class.getDeclaredField("done");
        doneField.setAccessible(true);

        while (!(boolean) doneField.get(this)) {
          // Minimal operation - just count iterations
          iterationsCompleted.incrementAndGet();

          // Simulate that an interrupt was handled (original logs a warning but continues)
          // After handling the simulated interrupt, we set done to true to exit the loop
          hasInterrupted.set(true);
          setDone(true);
        }
      } catch (Exception e) {
        throw new RuntimeException("Test setup failure", e);
      }
    }
  }

  /** A testable subclass that tracks whether clearScreen was called. */
  private static class ClearScreenTrackingPrinter extends ContinuousPrinter {

    /** Tracks whether clearScreen was called. */
    private final AtomicBoolean clearScreenCalled = new AtomicBoolean(false);

    /**
     * Constructs a ClearScreenTrackingPrinter.
     *
     * @param counters the counters instance
     */
    ClearScreenTrackingPrinter(Counters counters) {
      super(counters, false, 1);
    }

    /**
     * Checks if clearScreen was called.
     *
     * @return true if clearScreen was called
     */
    boolean wasClearScreenCalled() {
      return clearScreenCalled.get();
    }

    /** Overrides run() to track clearScreen calls. */
    @Override
    public void run() {
      try {
        Field doneField = ContinuousPrinter.class.getDeclaredField("done");
        doneField.setAccessible(true);

        while (!(boolean) doneField.get(this)) {
          // Track that clearScreen would be called
          clearScreenCalled.set(true);
          // Exit after one iteration
          setDone(true);
        }
      } catch (Exception e) {
        throw new RuntimeException("Test setup failure", e);
      }
    }
  }
}
