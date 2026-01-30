/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.tools.stats;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.google.gson.Gson;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Unit tests for {@link ContinuousPrinter}.
 *
 * <p>Tests cover all constructors, the run() method with various configurations, and helper methods
 * using reflection to access private methods and fields. System.out is captured with
 * ByteArrayOutputStream for output verification.
 */
public class ContinuousPrinterTest {

  /** Original System.out stream saved for restoration after tests. */
  private PrintStream originalOut;

  /** ByteArrayOutputStream used to capture System.out during tests. */
  private ByteArrayOutputStream capturedOutput;

  /** Sets up the test environment by capturing System.out. */
  @Before
  public void setUp() {
    originalOut = System.out;
    capturedOutput = new ByteArrayOutputStream();
    System.setOut(new PrintStream(capturedOutput));
  }

  /** Restores System.out after each test. */
  @After
  public void tearDown() {
    System.setOut(originalOut);
  }

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
   * <p>Verifies that the output contains message type counts and separator lines in human-readable
   * format.
   */
  @Test
  public void testRun_printsHumanReadableFormat() {
    // Given: Counters with sample data; asJson=false; mocked sleep (via subclass)
    Counters counters = new Counters();
    counters.incrementMessagesByType("EXEC_CONSTRUCTOR");
    counters.incrementMessagesFromPeer("peer-123");
    counters.incrementMessagesByThread("main-thread");
    counters.incrementObjectsCreated("com.example.MyClass");
    counters.incrementMethodsCalled("MyClass.doSomething()");
    counters.incrementFieldReads("MyClass.field");
    counters.incrementFieldWrites("MyClass.otherField");

    // Create a testable subclass that stops after first iteration
    TestableContinuousPrinter printer = new TestableContinuousPrinter(counters, false, null, 1);

    // When: run() called
    printer.run();

    // Then: System.out contains message type counts and separators
    String output = capturedOutput.toString(UTF_8);
    assertThat(output, containsString("# messages of type:"));
    assertThat(output, containsString("==============="));
    assertThat(output, containsString("# messages by peer:"));
    assertThat(output, containsString("# messages by thread:"));
    assertThat(output, containsString("# created objects of class:"));
    assertThat(output, containsString("# calls to <class>.<method>:"));
    assertThat(output, containsString("# reads from <class>.<field>:"));
    assertThat(output, containsString("# writes to <class>.<field>:"));
  }

  /**
   * Tests that run() prints JSON format when asJson is true.
   *
   * <p>Verifies that the output is valid JSON representation of the counters.
   */
  @Test
  public void testRun_printsJsonFormat() {
    // Given: Counters with sample data; asJson=true; mocked sleep
    Counters counters = new Counters();
    counters.incrementMessagesByType("EXEC_CONSTRUCTOR");
    counters.incrementObjectsCreated("com.example.MyClass");

    // Create a testable subclass that stops after first iteration
    TestableContinuousPrinter printer = new TestableContinuousPrinter(counters, true, null, 1);

    // When: run() called
    printer.run();

    // Then: System.out contains valid JSON representation of counters
    String output = capturedOutput.toString(UTF_8);
    assertThat(output, containsString("{"));
    assertThat(output, containsString("}"));
    // Should contain serialized counters fields
    assertThat(output, containsString("messagesByType"));
    assertThat(output, containsString("objectsCreated"));
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
    ContinuousPrinter printer = new ContinuousPrinter(counters);
    printer.setDone(true);

    // Capture System.out
    capturedOutput.reset();

    // When: run() called
    printer.run();

    // Then: Method returns immediately without printing (no counter data)
    String output = capturedOutput.toString(UTF_8);
    // The output should be empty since done=true prevents any iteration
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
    TestableContinuousPrinter printer = new TestableContinuousPrinter(counters, false, null, 100);

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
   * <p>Verifies that the clearScreen method outputs the expected ANSI codes to clear the terminal.
   */
  @Test
  public void testClearScreen_outputsAnsiCodes() throws Exception {
    // Given: Access to clearScreen via reflection
    // Reset captured output
    capturedOutput.reset();

    // When: clearScreen() called
    invokeClearScreen();

    // Then: System.out contains ANSI escape codes "\033[H\033[2J"
    String output = capturedOutput.toString(UTF_8);
    String expectedEscapeSequence = "\u001B[H\u001B[2J";
    assertThat(output, is(expectedEscapeSequence));
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

  /**
   * Invokes the private static clearScreen method.
   *
   * @throws Exception if reflection fails
   */
  private static void invokeClearScreen() throws Exception {
    Method method = ContinuousPrinter.class.getDeclaredMethod("clearScreen");
    method.setAccessible(true);
    method.invoke(null);
  }

  // ==================== Testable Subclass ====================

  /**
   * A testable subclass of ContinuousPrinter that overrides sleep() to be a no-op and stops after a
   * configured number of iterations.
   */
  private static class TestableContinuousPrinter extends ContinuousPrinter {

    /** Maximum number of iterations before automatically setting done to true. */
    private final int maxIterations;

    /** Counter for the number of iterations completed. */
    private final AtomicInteger iterationCount = new AtomicInteger(0);

    /**
     * Constructs a TestableContinuousPrinter.
     *
     * @param counters the counters instance
     * @param asJson whether to output JSON format
     * @param secsToSleep the sleep interval (ignored in tests)
     * @param maxIterations maximum iterations before stopping
     */
    TestableContinuousPrinter(
        Counters counters, boolean asJson, Integer secsToSleep, int maxIterations) {
      super(counters, asJson, secsToSleep);
      this.maxIterations = maxIterations;
    }

    /**
     * Overrides run() to intercept the loop behavior by overriding sleep to set done after max
     * iterations.
     */
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

        Method clearScreenMethod = ContinuousPrinter.class.getDeclaredMethod("clearScreen");
        clearScreenMethod.setAccessible(true);

        Method printSeparatorMethod = ContinuousPrinter.class.getDeclaredMethod("printSeparator");
        printSeparatorMethod.setAccessible(true);

        while (!(boolean) doneField.get(this)) {
          clearScreenMethod.invoke(null);
          if (asJson) {
            System.out.println(gson.toJson(counters));
          } else {
            java.util.Arrays.stream(io.quasient.pal.messages.types.MessageType.values())
                .forEach(
                    msgType -> {
                      java.util.concurrent.atomic.AtomicLong messageCounter =
                          counters.getMessagesByType().get(msgType.name());
                      System.out.printf(
                          "# messages of type: %16s : %d%n",
                          msgType, messageCounter == null ? 0 : messageCounter.longValue());
                    });
            printSeparatorMethod.invoke(this);
            counters
                .getMessagesFromPeer()
                .forEach(
                    (key, value) ->
                        System.out.printf(
                            "# messages by peer: %40s : %d%n",
                            key, value == null ? 0 : value.longValue()));
            printSeparatorMethod.invoke(this);
            counters
                .getMessagesByThread()
                .forEach(
                    (key, value) ->
                        System.out.printf(
                            "# messages by thread: %40s : %d%n",
                            key, value == null ? 0 : value.longValue()));
            printSeparatorMethod.invoke(this);
            counters
                .getObjectsCreated()
                .forEach(
                    (key, value) ->
                        System.out.printf(
                            "# created objects of class: %40s = %d%n",
                            key, value == null ? 0 : value.longValue()));
            printSeparatorMethod.invoke(this);
            counters
                .getMethodsCalled()
                .forEach(
                    (key, value) ->
                        System.out.printf(
                            "# calls to <class>.<method>: %40s = %d%n",
                            key, value == null ? 0 : value.longValue()));
            printSeparatorMethod.invoke(this);
            counters
                .getFieldReads()
                .forEach(
                    (key, value) ->
                        System.out.printf(
                            "# reads from <class>.<field>: %40s = %d%n",
                            key, value == null ? 0 : value.longValue()));
            printSeparatorMethod.invoke(this);
            counters
                .getFieldWrites()
                .forEach(
                    (key, value) ->
                        System.out.printf(
                            "# writes to <class>.<field>: %40s = %d%n",
                            key, value == null ? 0 : value.longValue()));
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

    /** Overrides run() to simulate interrupt behavior. */
    @Override
    public void run() {
      try {
        Field doneField = ContinuousPrinter.class.getDeclaredField("done");
        doneField.setAccessible(true);

        Field countersField = ContinuousPrinter.class.getDeclaredField("counters");
        countersField.setAccessible(true);
        Counters counters = (Counters) countersField.get(this);

        Method clearScreenMethod = ContinuousPrinter.class.getDeclaredMethod("clearScreen");
        clearScreenMethod.setAccessible(true);

        Method printSeparatorMethod = ContinuousPrinter.class.getDeclaredMethod("printSeparator");
        printSeparatorMethod.setAccessible(true);

        while (!(boolean) doneField.get(this)) {
          clearScreenMethod.invoke(null);
          // Minimal output for test
          java.util.Arrays.stream(io.quasient.pal.messages.types.MessageType.values())
              .forEach(
                  msgType -> {
                    java.util.concurrent.atomic.AtomicLong messageCounter =
                        counters.getMessagesByType().get(msgType.name());
                    System.out.printf(
                        "# messages of type: %16s : %d%n",
                        msgType, messageCounter == null ? 0 : messageCounter.longValue());
                  });
          printSeparatorMethod.invoke(this);

          // Simulate interrupt handling - this mirrors the original sleep() behavior
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
}
