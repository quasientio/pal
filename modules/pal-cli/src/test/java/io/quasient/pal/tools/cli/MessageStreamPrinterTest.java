/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.tools.cli;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.fail;

import io.quasient.pal.messages.LogMessage;
import io.quasient.pal.messages.colfer.Message;
import io.quasient.pal.serdes.colfer.MessageBuilder;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.Ignore;
import org.junit.Test;

public class MessageStreamPrinterTest {

  private static LogMessage<?> logOf(Message m) {
    return new LogMessage<>("topic", 1L, Map.of(), m);
  }

  // MessageStreamPrinter does not expose getShortClassname; tested in MessageStreamStats suite.

  @Test
  public void shouldPrint_appliesAllFilters() throws Exception {
    UUID peer = UUID.randomUUID();
    MessageBuilder b = new MessageBuilder(peer, Boolean.toString(false));
    var em = b.buildEmptyConstructor(peer, "java.lang.String");
    var m = b.wrap(em);
    LogMessage<?> lm = logOf(m);

    MessageStreamPrinter p = new MessageStreamPrinter();
    var fFormats = MessageStreamPrinter.class.getDeclaredField("msgFormats");
    fFormats.setAccessible(true);
    fFormats.set(p, List.of("BINARY"));
    var fTypes = MessageStreamPrinter.class.getDeclaredField("msgTypes");
    fTypes.setAccessible(true);
    fTypes.set(p, List.of("CONSTRUCTOR"));
    var fPeer = MessageStreamPrinter.class.getDeclaredField("fromPeer");
    fPeer.setAccessible(true);
    fPeer.set(p, peer.toString());
    var fThread = MessageStreamPrinter.class.getDeclaredField("threadName");
    fThread.setAccessible(true);
    fThread.set(p, em.getThreadName());
    var fId = MessageStreamPrinter.class.getDeclaredField("id");
    fId.setAccessible(true);
    fId.set(p, em.getMessageId());

    Method should =
        MessageStreamPrinter.class.getDeclaredMethod(
            "shouldPrint", Long.class, String.class, LogMessage.class);
    should.setAccessible(true);
    boolean ok = (boolean) should.invoke(p, 5L, peer.toString(), lm);
    assertThat(ok, is(true));
  }

  @Test
  public void printRecord_formatsOutputVariants() throws Exception {
    UUID peer = UUID.randomUUID();
    MessageBuilder b = new MessageBuilder(peer, Boolean.toString(false));
    var em = b.buildEmptyConstructor(peer, "java.lang.String");
    var m = b.wrap(em);
    LogMessage<?> lm = logOf(m);

    MessageStreamPrinter p = new MessageStreamPrinter();
    ByteArrayOutputStream bout = new ByteArrayOutputStream();
    var outField = AbstractPalSubcommand.class.getDeclaredField("out");
    outField.setAccessible(true);
    outField.set(p, new PrintStream(bout));

    // Access the formatOptions field and FormatOptions inner class
    var fmtOptsField = MessageStreamPrinter.class.getDeclaredField("formatOptions");
    fmtOptsField.setAccessible(true);
    Class<?> formatOptionsClass =
        Class.forName("io.quasient.pal.tools.cli.MessageStreamPrinter$FormatOptions");

    // Test each format by setting the appropriate flag
    for (var v : MessageStreamPrinter.OutputFormat.values()) {
      // Create a new FormatOptions instance
      Object formatOptions = formatOptionsClass.getDeclaredConstructor().newInstance();

      // Set the appropriate flag based on the format
      switch (v) {
        case COMPACT -> {
          var compactField = formatOptionsClass.getDeclaredField("compact");
          compactField.setAccessible(true);
          compactField.set(formatOptions, true);
        }
        case JSON -> {
          var jsonField = formatOptionsClass.getDeclaredField("json");
          jsonField.setAccessible(true);
          jsonField.set(formatOptions, true);
        }
        case FULL -> {
          var fullField = formatOptionsClass.getDeclaredField("full");
          fullField.setAccessible(true);
          fullField.set(formatOptions, true);
        }
      }

      fmtOptsField.set(p, formatOptions);

      Method print =
          MessageStreamPrinter.class.getDeclaredMethod(
              "printRecord", String.class, LogMessage.class, long.class);
      print.setAccessible(true);
      print.invoke(p, peer.toString(), lm, 10L);
    }
  }

  // ==========================================================================
  // Test specifications for getFormat(), shouldPrint() branches, and printRecord()
  // Issue #366 - Awaiting implementation in #367
  // ==========================================================================

  @Test
  public void testGetFormat_returnsCompact_whenNoFormatSpecified() throws Exception {
    // Given: No format flags set (formatOptions is null)
    MessageStreamPrinter p = new MessageStreamPrinter();
    // formatOptions is null by default

    // When: getFormat() called via reflection
    Method getFormat = MessageStreamPrinter.class.getDeclaredMethod("getFormat");
    getFormat.setAccessible(true);
    Object result = getFormat.invoke(p);

    // Then: Returns COMPACT
    assertThat(result, is(MessageStreamPrinter.OutputFormat.COMPACT));
  }

  @Test
  public void testGetFormat_returnsFull_whenFullFlagSet() throws Exception {
    // Given: fullOutput=true in FormatOptions
    MessageStreamPrinter p = new MessageStreamPrinter();

    // Create FormatOptions instance with full=true
    Class<?> formatOptionsClass =
        Class.forName("io.quasient.pal.tools.cli.MessageStreamPrinter$FormatOptions");
    Object formatOptions = formatOptionsClass.getDeclaredConstructor().newInstance();
    var fullField = formatOptionsClass.getDeclaredField("full");
    fullField.setAccessible(true);
    fullField.set(formatOptions, true);

    // Set formatOptions field via reflection
    var fmtOptsField = MessageStreamPrinter.class.getDeclaredField("formatOptions");
    fmtOptsField.setAccessible(true);
    fmtOptsField.set(p, formatOptions);

    // When: getFormat() called via reflection
    Method getFormat = MessageStreamPrinter.class.getDeclaredMethod("getFormat");
    getFormat.setAccessible(true);
    Object result = getFormat.invoke(p);

    // Then: Returns FULL
    assertThat(result, is(MessageStreamPrinter.OutputFormat.FULL));
  }

  @Test
  public void testGetFormat_returnsJson_whenJsonFlagSet() throws Exception {
    // Given: jsonOutput=true in FormatOptions
    MessageStreamPrinter p = new MessageStreamPrinter();

    // Create FormatOptions instance with json=true
    Class<?> formatOptionsClass =
        Class.forName("io.quasient.pal.tools.cli.MessageStreamPrinter$FormatOptions");
    Object formatOptions = formatOptionsClass.getDeclaredConstructor().newInstance();
    var jsonField = formatOptionsClass.getDeclaredField("json");
    jsonField.setAccessible(true);
    jsonField.set(formatOptions, true);

    // Set formatOptions field via reflection
    var fmtOptsField = MessageStreamPrinter.class.getDeclaredField("formatOptions");
    fmtOptsField.setAccessible(true);
    fmtOptsField.set(p, formatOptions);

    // When: getFormat() called via reflection
    Method getFormat = MessageStreamPrinter.class.getDeclaredMethod("getFormat");
    getFormat.setAccessible(true);
    Object result = getFormat.invoke(p);

    // Then: Returns JSON
    assertThat(result, is(MessageStreamPrinter.OutputFormat.JSON));
  }

  @Test
  public void testShouldPrint_filtersNullOffset() throws Exception {
    // Given: startOffset set; message with null offset and mismatched type filter
    // The offset check in shouldPrint only short-circuits on match (returns true).
    // When offset doesn't match, it continues to other filters.
    // To test that null offset doesn't trigger the early-exit, we add a type filter
    // that will reject the message, proving the offset check didn't short-circuit.
    UUID peer = UUID.randomUUID();
    MessageBuilder b = new MessageBuilder(peer, Boolean.toString(false));
    var em = b.buildEmptyConstructor(peer, "java.lang.String");
    var m = b.wrap(em);
    LogMessage<?> lm = logOf(m);

    MessageStreamPrinter p = new MessageStreamPrinter();
    // Set the offset field to a non-null value (e.g., 10L) via reflection
    var offsetField = MessageStreamPrinter.class.getDeclaredField("offset");
    offsetField.setAccessible(true);
    offsetField.set(p, 10L);
    // Set a type filter that won't match (message is CONSTRUCTOR, filter is INSTANCE_METHOD)
    var fTypes = MessageStreamPrinter.class.getDeclaredField("msgTypes");
    fTypes.setAccessible(true);
    fTypes.set(p, List.of("INSTANCE_METHOD"));

    // When: shouldPrint() called with null recOffset (doesn't match 10L) via reflection
    Method shouldPrint =
        MessageStreamPrinter.class.getDeclaredMethod(
            "shouldPrint", Long.class, String.class, LogMessage.class);
    shouldPrint.setAccessible(true);
    boolean result = (boolean) shouldPrint.invoke(p, null, peer.toString(), lm);

    // Then: Returns false because:
    // 1. offset (10L) != recOffset (null), so no early return
    // 2. Type filter rejects message (CONSTRUCTOR not in [INSTANCE_METHOD])
    assertThat(result, is(false));
  }

  @Test
  public void testShouldPrint_combinesMultipleFilters() throws Exception {
    // Given: Type filter AND peer filter both set
    UUID peer = UUID.randomUUID();
    MessageBuilder b = new MessageBuilder(peer, Boolean.toString(false));
    var em = b.buildEmptyConstructor(peer, "java.lang.String");
    var m = b.wrap(em);
    LogMessage<?> lm = logOf(m);

    MessageStreamPrinter p = new MessageStreamPrinter();
    // Set msgTypes field to List.of("CONSTRUCTOR") via reflection
    var fTypes = MessageStreamPrinter.class.getDeclaredField("msgTypes");
    fTypes.setAccessible(true);
    fTypes.set(p, List.of("CONSTRUCTOR"));
    // Set fromPeer field to a specific UUID string via reflection
    var fPeer = MessageStreamPrinter.class.getDeclaredField("fromPeer");
    fPeer.setAccessible(true);
    fPeer.set(p, peer.toString());

    // When: shouldPrint() called with message matching both filters via reflection
    Method shouldPrint =
        MessageStreamPrinter.class.getDeclaredMethod(
            "shouldPrint", Long.class, String.class, LogMessage.class);
    shouldPrint.setAccessible(true);
    boolean result = (boolean) shouldPrint.invoke(p, 5L, peer.toString(), lm);

    // Then: Returns true
    assertThat(result, is(true));
  }

  @Test
  public void testShouldPrint_rejectsMismatchedFilter() throws Exception {
    // Given: Type filter set to CONSTRUCTOR
    UUID peer = UUID.randomUUID();
    MessageBuilder b = new MessageBuilder(peer, Boolean.toString(false));
    // Create an instance method message instead of constructor
    var em =
        b.buildInstanceMethod(
            peer,
            "java.util.ArrayList",
            "add",
            io.quasient.pal.common.objects.ObjectRef.randomRef(),
            new String[] {"int"},
            new Object[] {1});
    var m = b.wrap(em);
    LogMessage<?> lm = logOf(m);

    MessageStreamPrinter p = new MessageStreamPrinter();
    // Set msgTypes field to List.of("CONSTRUCTOR") via reflection
    var fTypes = MessageStreamPrinter.class.getDeclaredField("msgTypes");
    fTypes.setAccessible(true);
    fTypes.set(p, List.of("CONSTRUCTOR"));

    // When: shouldPrint() called with message of type INSTANCE_METHOD via reflection
    Method shouldPrint =
        MessageStreamPrinter.class.getDeclaredMethod(
            "shouldPrint", Long.class, String.class, LogMessage.class);
    shouldPrint.setAccessible(true);
    boolean result = (boolean) shouldPrint.invoke(p, 5L, peer.toString(), lm);

    // Then: Returns false
    assertThat(result, is(false));
  }

  @Test
  public void testPrintRecord_handlesFullFormat() throws Exception {
    // Given: OutputFormat.FULL configured
    UUID peer = UUID.randomUUID();
    MessageBuilder b = new MessageBuilder(peer, Boolean.toString(false));
    var em = b.buildEmptyConstructor(peer, "java.lang.String");
    var m = b.wrap(em);
    LogMessage<?> lm = logOf(m);

    MessageStreamPrinter p = new MessageStreamPrinter();

    // Set formatOptions.full = true via reflection
    Class<?> formatOptionsClass =
        Class.forName("io.quasient.pal.tools.cli.MessageStreamPrinter$FormatOptions");
    Object formatOptions = formatOptionsClass.getDeclaredConstructor().newInstance();
    var fullField = formatOptionsClass.getDeclaredField("full");
    fullField.setAccessible(true);
    fullField.set(formatOptions, true);
    var fmtOptsField = MessageStreamPrinter.class.getDeclaredField("formatOptions");
    fmtOptsField.setAccessible(true);
    fmtOptsField.set(p, formatOptions);

    // Redirect System.out to capture output (printRecord uses System.out.printf)
    PrintStream originalOut = System.out;
    ByteArrayOutputStream bout = new ByteArrayOutputStream();
    System.setOut(new PrintStream(bout, true, UTF_8));

    try {
      // When: printRecord() called via reflection
      Method printRecord =
          MessageStreamPrinter.class.getDeclaredMethod(
              "printRecord", String.class, LogMessage.class, long.class);
      printRecord.setAccessible(true);
      printRecord.invoke(p, peer.toString(), lm, 10L);

      // Then: Output contains "CONTEXT:", "HEADERS:", and offset info
      String output = bout.toString(UTF_8);
      assertThat(output.contains("CONTEXT:"), is(true));
      assertThat(output.contains("HEADERS:"), is(true));
      assertThat(output.contains("offset: 10"), is(true));
    } finally {
      System.setOut(originalOut);
    }
  }

  @Test
  public void testPrintRecord_handlesCompactFormat() throws Exception {
    // Given: OutputFormat.COMPACT configured (default - formatOptions is null)
    UUID peer = UUID.randomUUID();
    MessageBuilder b = new MessageBuilder(peer, Boolean.toString(false));
    var em = b.buildEmptyConstructor(peer, "java.lang.String");
    var m = b.wrap(em);
    LogMessage<?> lm = logOf(m);

    MessageStreamPrinter p = new MessageStreamPrinter();
    // formatOptions is null by default, which means COMPACT format

    // Redirect System.out to capture output (printRecord uses System.out.printf)
    PrintStream originalOut = System.out;
    ByteArrayOutputStream bout = new ByteArrayOutputStream();
    System.setOut(new PrintStream(bout, true, UTF_8));

    try {
      // When: printRecord() called via reflection
      Method printRecord =
          MessageStreamPrinter.class.getDeclaredMethod(
              "printRecord", String.class, LogMessage.class, long.class);
      printRecord.setAccessible(true);
      printRecord.invoke(p, peer.toString(), lm, 10L);

      // Then: Output is single line containing "offset=", "id=", "message="
      String output = bout.toString(UTF_8);
      assertThat(output.contains("offset=10"), is(true));
      assertThat(output.contains("id="), is(true));
      assertThat(output.contains("message="), is(true));
    } finally {
      System.setOut(originalOut);
    }
  }

  // ==========================================================================
  // Test specifications for performSocketPrinterShutdown()
  // Issue #372 - Awaiting implementation in #373
  // ==========================================================================

  /**
   * Tests that performSocketPrinterShutdown counts down the latch.
   *
   * <p>Verifies that calling performSocketPrinterShutdown() decrements the socketPrinterLatch count
   * to 0.
   */
  @Test
  @Ignore("Awaiting implementation in #373")
  public void testPerformSocketPrinterShutdown_countsDownLatch() {
    // Given: latch with count of 1
    // When: performSocketPrinterShutdown() called
    // Then: latch.getCount() returns 0

    // TODO(#373): Implement test
    // Create MessageStreamPrinter instance
    // Create and set socketPrinterLatch field to new CountDownLatch(1)
    // Assert socketPrinterLatch.getCount() == 1 before call
    // Call performSocketPrinterShutdown()
    // Assert socketPrinterLatch.getCount() == 0 after call
    fail("Not yet implemented");
  }
}
