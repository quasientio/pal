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
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.quasient.pal.common.directory.nodes.LogInfo;
import io.quasient.pal.common.objects.ObjectRef;
import io.quasient.pal.common.runtime.ExecPhase;
import io.quasient.pal.cxn.directory.PalDirectory;
import io.quasient.pal.messages.LogMessage;
import io.quasient.pal.messages.OutboundMsg;
import io.quasient.pal.messages.colfer.ExecMessage;
import io.quasient.pal.messages.colfer.Message;
import io.quasient.pal.messages.colfer.ReturnValue;
import io.quasient.pal.messages.types.MessageType;
import io.quasient.pal.serdes.colfer.MessageBuilder;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
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
        case TREE -> {
          var treeField = formatOptionsClass.getDeclaredField("tree");
          treeField.setAccessible(true);
          treeField.set(formatOptions, true);
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
            ObjectRef.randomRef(),
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
  public void testPerformSocketPrinterShutdown_countsDownLatch() {
    // Given: latch with count of 1
    MessageStreamPrinter printer = new MessageStreamPrinter();
    printer.socketPrinterLatch = new CountDownLatch(1);

    // Assert socketPrinterLatch.getCount() == 1 before call
    assertThat(printer.socketPrinterLatch.getCount(), is(1L));

    // When: performSocketPrinterShutdown() called
    printer.performSocketPrinterShutdown();

    // Then: latch.getCount() returns 0
    assertThat(printer.socketPrinterLatch.getCount(), is(0L));
  }

  // ==========================================================================
  // Test specifications for printVerboseFilters, resolveLogInfo, getLogMessage,
  // getFormat, and shouldPrint (offset filtering)
  // Issue #629 - Awaiting implementation in #630
  // ==========================================================================

  /**
   * Tests that printVerboseFilters prints all filter details when every filter field is set.
   *
   * @throws Exception if reflection fails
   */
  @Test
  public void printVerboseFilters_allFiltersSet_printsAll() throws Exception {
    // Given
    MessageStreamPrinter p = new MessageStreamPrinter();
    setField(p, "msgFormats", List.of("BINARY"));
    setField(p, "msgTypes", List.of("CONSTRUCTOR"));
    setField(p, "fromPeer", "some-peer-uuid");
    setField(p, "threadName", "main");
    setField(p, "id", "msg-1");
    setField(p, "offset", 42L);

    // Capture System.out
    PrintStream originalOut = System.out;
    ByteArrayOutputStream bout = new ByteArrayOutputStream();
    System.setOut(new PrintStream(bout, true, UTF_8));

    try {
      // When
      Method m =
          MessageStreamPrinter.class.getDeclaredMethod(
              "printVerboseFilters", String.class, String.class);
      m.setAccessible(true);
      m.invoke(p, "Header line", "offset id");

      // Then
      String output = bout.toString(UTF_8);
      assertThat(output, containsString("Header line"));
      assertThat(output, containsString("Filtering by format(s): BINARY"));
      assertThat(output, containsString("Filtering by type(s): CONSTRUCTOR"));
      assertThat(output, containsString("Filtering by peer: some-peer-uuid"));
      assertThat(output, containsString("Filtering by thread: main"));
      assertThat(output, containsString("Filtering by message id: msg-1"));
      assertThat(output, containsString("Will print message with offset id: 42 and then exit"));
    } finally {
      System.setOut(originalOut);
    }
  }

  /**
   * Tests that printVerboseFilters prints only the header line when no filters are set.
   *
   * @throws Exception if reflection fails
   */
  @Test
  public void printVerboseFilters_noFilters_printsHeaderOnly() throws Exception {
    // Given: all filter fields are null/default
    MessageStreamPrinter p = new MessageStreamPrinter();

    PrintStream originalOut = System.out;
    ByteArrayOutputStream bout = new ByteArrayOutputStream();
    System.setOut(new PrintStream(bout, true, UTF_8));

    try {
      // When
      Method m =
          MessageStreamPrinter.class.getDeclaredMethod(
              "printVerboseFilters", String.class, String.class);
      m.setAccessible(true);
      m.invoke(p, "Header line", "offset id");

      // Then
      String output = bout.toString(UTF_8);
      assertThat(output, containsString("Header line"));
      assertThat(output, not(containsString("Filtering by")));
      assertThat(output, not(containsString("Will print message with")));
    } finally {
      System.setOut(originalOut);
    }
  }

  /**
   * Tests that printVerboseFilters prints only the format filter line when only msgFormats is set.
   *
   * @throws Exception if reflection fails
   */
  @Test
  public void printVerboseFilters_formatFilterOnly_printsFormat() throws Exception {
    // Given
    MessageStreamPrinter p = new MessageStreamPrinter();
    setField(p, "msgFormats", List.of("BINARY", "JSON"));

    PrintStream originalOut = System.out;
    ByteArrayOutputStream bout = new ByteArrayOutputStream();
    System.setOut(new PrintStream(bout, true, UTF_8));

    try {
      // When
      Method m =
          MessageStreamPrinter.class.getDeclaredMethod(
              "printVerboseFilters", String.class, String.class);
      m.setAccessible(true);
      m.invoke(p, "Header", "offset id");

      // Then
      String output = bout.toString(UTF_8);
      assertThat(output, containsString("Header"));
      assertThat(output, containsString("Filtering by format(s): BINARY,JSON"));
      assertThat(output, not(containsString("Filtering by type(s)")));
      assertThat(output, not(containsString("Filtering by peer")));
      assertThat(output, not(containsString("Filtering by thread")));
      assertThat(output, not(containsString("Filtering by message id")));
      assertThat(output, not(containsString("Will print message with")));
    } finally {
      System.setOut(originalOut);
    }
  }

  /**
   * Tests that printVerboseFilters prints only the type filter line when only msgTypes is set.
   *
   * @throws Exception if reflection fails
   */
  @Test
  public void printVerboseFilters_typeFilterOnly_printsType() throws Exception {
    // Given
    MessageStreamPrinter p = new MessageStreamPrinter();
    setField(p, "msgTypes", List.of("CONSTRUCTOR", "INSTANCE_METHOD"));

    PrintStream originalOut = System.out;
    ByteArrayOutputStream bout = new ByteArrayOutputStream();
    System.setOut(new PrintStream(bout, true, UTF_8));

    try {
      // When
      Method m =
          MessageStreamPrinter.class.getDeclaredMethod(
              "printVerboseFilters", String.class, String.class);
      m.setAccessible(true);
      m.invoke(p, "Header", "offset id");

      // Then
      String output = bout.toString(UTF_8);
      assertThat(output, containsString("Header"));
      assertThat(output, containsString("Filtering by type(s): CONSTRUCTOR,INSTANCE_METHOD"));
      assertThat(output, not(containsString("Filtering by format(s)")));
      assertThat(output, not(containsString("Filtering by peer")));
      assertThat(output, not(containsString("Filtering by thread")));
      assertThat(output, not(containsString("Filtering by message id")));
      assertThat(output, not(containsString("Will print message with")));
    } finally {
      System.setOut(originalOut);
    }
  }

  /**
   * Tests that printVerboseFilters prints only the peer filter line when only fromPeer is set.
   *
   * @throws Exception if reflection fails
   */
  @Test
  public void printVerboseFilters_peerFilterOnly_printsPeer() throws Exception {
    // Given
    MessageStreamPrinter p = new MessageStreamPrinter();
    setField(p, "fromPeer", "peer-uuid-123");

    PrintStream originalOut = System.out;
    ByteArrayOutputStream bout = new ByteArrayOutputStream();
    System.setOut(new PrintStream(bout, true, UTF_8));

    try {
      // When
      Method m =
          MessageStreamPrinter.class.getDeclaredMethod(
              "printVerboseFilters", String.class, String.class);
      m.setAccessible(true);
      m.invoke(p, "Header", "offset id");

      // Then
      String output = bout.toString(UTF_8);
      assertThat(output, containsString("Header"));
      assertThat(output, containsString("Filtering by peer: peer-uuid-123"));
      assertThat(output, not(containsString("Filtering by format(s)")));
      assertThat(output, not(containsString("Filtering by type(s)")));
      assertThat(output, not(containsString("Filtering by thread")));
      assertThat(output, not(containsString("Filtering by message id")));
      assertThat(output, not(containsString("Will print message with")));
    } finally {
      System.setOut(originalOut);
    }
  }

  /**
   * Tests that printVerboseFilters prints only the thread filter line when only threadName is set.
   *
   * @throws Exception if reflection fails
   */
  @Test
  public void printVerboseFilters_threadFilterOnly_printsThread() throws Exception {
    // Given
    MessageStreamPrinter p = new MessageStreamPrinter();
    setField(p, "threadName", "worker-1");

    PrintStream originalOut = System.out;
    ByteArrayOutputStream bout = new ByteArrayOutputStream();
    System.setOut(new PrintStream(bout, true, UTF_8));

    try {
      // When
      Method m =
          MessageStreamPrinter.class.getDeclaredMethod(
              "printVerboseFilters", String.class, String.class);
      m.setAccessible(true);
      m.invoke(p, "Header", "offset id");

      // Then
      String output = bout.toString(UTF_8);
      assertThat(output, containsString("Header"));
      assertThat(output, containsString("Filtering by thread: worker-1"));
      assertThat(output, not(containsString("Filtering by format(s)")));
      assertThat(output, not(containsString("Filtering by type(s)")));
      assertThat(output, not(containsString("Filtering by peer")));
      assertThat(output, not(containsString("Filtering by message id")));
      assertThat(output, not(containsString("Will print message with")));
    } finally {
      System.setOut(originalOut);
    }
  }

  /**
   * Tests that printVerboseFilters prints only the id filter line when only id is set.
   *
   * @throws Exception if reflection fails
   */
  @Test
  public void printVerboseFilters_idFilterOnly_printsId() throws Exception {
    // Given
    MessageStreamPrinter p = new MessageStreamPrinter();
    setField(p, "id", "msg-42");

    PrintStream originalOut = System.out;
    ByteArrayOutputStream bout = new ByteArrayOutputStream();
    System.setOut(new PrintStream(bout, true, UTF_8));

    try {
      // When
      Method m =
          MessageStreamPrinter.class.getDeclaredMethod(
              "printVerboseFilters", String.class, String.class);
      m.setAccessible(true);
      m.invoke(p, "Header", "offset id");

      // Then
      String output = bout.toString(UTF_8);
      assertThat(output, containsString("Header"));
      assertThat(output, containsString("Filtering by message id: msg-42"));
      assertThat(output, not(containsString("Filtering by format(s)")));
      assertThat(output, not(containsString("Filtering by type(s)")));
      assertThat(output, not(containsString("Filtering by peer")));
      assertThat(output, not(containsString("Filtering by thread")));
      assertThat(output, not(containsString("Will print message with")));
    } finally {
      System.setOut(originalOut);
    }
  }

  /**
   * Tests that printVerboseFilters prints only the offset line when only offset is set.
   *
   * @throws Exception if reflection fails
   */
  @Test
  public void printVerboseFilters_offsetSet_printsOffset() throws Exception {
    // Given
    MessageStreamPrinter p = new MessageStreamPrinter();
    setField(p, "offset", 99L);

    PrintStream originalOut = System.out;
    ByteArrayOutputStream bout = new ByteArrayOutputStream();
    System.setOut(new PrintStream(bout, true, UTF_8));

    try {
      // When
      Method m =
          MessageStreamPrinter.class.getDeclaredMethod(
              "printVerboseFilters", String.class, String.class);
      m.setAccessible(true);
      m.invoke(p, "Header", "offset id");

      // Then
      String output = bout.toString(UTF_8);
      assertThat(output, containsString("Header"));
      assertThat(output, containsString("Will print message with offset id: 99 and then exit"));
      assertThat(output, not(containsString("Filtering by")));
    } finally {
      System.setOut(originalOut);
    }
  }

  /**
   * Tests that resolveLogInfo returns LogInfo when the log is found by name in the directory.
   *
   * @throws Exception if reflection fails
   */
  @Test
  public void resolveLogInfo_foundByName_returnsLogInfo() throws Exception {
    // Given
    LogInfo expected = new LogInfo("my-log");
    PalDirectory palDirectory = mock(PalDirectory.class);
    when(palDirectory.getLogInfo("my-log")).thenReturn(expected);

    MessageStreamPrinter p = new MessageStreamPrinter();
    Method m =
        MessageStreamPrinter.class.getDeclaredMethod(
            "resolveLogInfo", PalDirectory.class, String.class);
    m.setAccessible(true);

    // When
    LogInfo result = (LogInfo) m.invoke(p, palDirectory, "my-log");

    // Then
    assertThat(result, is(notNullValue()));
    assertThat(result.getName(), is("my-log"));
  }

  /**
   * Tests that resolveLogInfo tries UUID lookup when name lookup fails, and returns LogInfo if UUID
   * lookup succeeds.
   *
   * @throws Exception if reflection fails
   */
  @Test
  public void resolveLogInfo_notFoundByName_triesUuid() throws Exception {
    // Given: getLogInfo(name) throws RuntimeException, listAllLogs returns matching UUID
    UUID logUuid = UUID.randomUUID();
    LogInfo expected = new LogInfo("some-log", logUuid);
    PalDirectory palDirectory = mock(PalDirectory.class);
    when(palDirectory.getLogInfo(logUuid.toString())).thenThrow(new RuntimeException("not found"));
    when(palDirectory.listAllLogs()).thenReturn(Set.of(expected));

    MessageStreamPrinter p = new MessageStreamPrinter();
    Method m =
        MessageStreamPrinter.class.getDeclaredMethod(
            "resolveLogInfo", PalDirectory.class, String.class);
    m.setAccessible(true);

    // When
    LogInfo result = (LogInfo) m.invoke(p, palDirectory, logUuid.toString());

    // Then
    assertThat(result, is(notNullValue()));
    assertThat(result.getUuid(), is(logUuid));
  }

  /**
   * Tests that resolveLogInfo returns null when both name and UUID lookups fail.
   *
   * @throws Exception if reflection fails
   */
  @Test
  public void resolveLogInfo_invalidUuid_returnsNull() throws Exception {
    // Given: getLogInfo(name) returns null, identifier is not a valid UUID
    PalDirectory palDirectory = mock(PalDirectory.class);
    when(palDirectory.getLogInfo("not-a-uuid")).thenReturn(null);

    MessageStreamPrinter p = new MessageStreamPrinter();
    Method m =
        MessageStreamPrinter.class.getDeclaredMethod(
            "resolveLogInfo", PalDirectory.class, String.class);
    m.setAccessible(true);

    // When
    LogInfo result = (LogInfo) m.invoke(p, palDirectory, "not-a-uuid");

    // Then
    assertThat(result, is(nullValue()));
  }

  /**
   * Tests that getLogMessage creates a LogMessage from an OutboundMsg with correct headers and
   * content.
   *
   * @throws Exception if reflection fails
   */
  @Test
  public void getLogMessage_createsLogMessageFromOutboundMsg() throws Exception {
    // Given: Build a Message and wrap it in an OutboundMsg
    UUID peer = UUID.randomUUID();
    MessageBuilder b = new MessageBuilder(peer, Boolean.toString(false));
    var em = b.buildEmptyConstructor(peer, "java.lang.String");
    Message message = b.wrap(em);

    OutboundMsg outboundMsg =
        new OutboundMsg(
            MessageType.EXEC_CONSTRUCTOR, ExecPhase.BEFORE, null, "msg-123", "resp-456", message);

    // When: getLogMessage called via reflection
    Method m =
        MessageStreamPrinter.class.getDeclaredMethod(
            "getLogMessage", OutboundMsg.class, long.class, Message.class);
    m.setAccessible(true);

    @SuppressWarnings("unchecked")
    LogMessage<Message> result = (LogMessage<Message>) m.invoke(null, outboundMsg, 5L, message);

    // Then: headers and content are correct
    assertThat(result, is(notNullValue()));
    assertThat(result.getHeaders().get("message-type"), is("EXEC_CONSTRUCTOR"));
    assertThat(result.getHeaders().get("message-format"), is("BINARY"));
    assertThat(result.getHeaders().get("message-id"), is("msg-123"));
    assertThat(result.getHeaders().get("response-to-id"), is("resp-456"));
    assertThat(result.getContent(), is(message));
  }

  /**
   * Tests that getFormat returns the explicitly specified format when set.
   *
   * @throws Exception if reflection fails
   */
  @Test
  public void getFormat_explicitFormat_returnsSpecified() throws Exception {
    // Given: formatOptions.json = true
    MessageStreamPrinter p = new MessageStreamPrinter();

    Class<?> formatOptionsClass =
        Class.forName("io.quasient.pal.tools.cli.MessageStreamPrinter$FormatOptions");
    Object formatOptions = formatOptionsClass.getDeclaredConstructor().newInstance();
    var jsonField = formatOptionsClass.getDeclaredField("json");
    jsonField.setAccessible(true);
    jsonField.set(formatOptions, true);

    var fmtOptsField = MessageStreamPrinter.class.getDeclaredField("formatOptions");
    fmtOptsField.setAccessible(true);
    fmtOptsField.set(p, formatOptions);

    // When
    Method getFormat = MessageStreamPrinter.class.getDeclaredMethod("getFormat");
    getFormat.setAccessible(true);
    Object result = getFormat.invoke(p);

    // Then
    assertThat(result, is(MessageStreamPrinter.OutputFormat.JSON));
  }

  /**
   * Tests that getFormat returns COMPACT (the default) when no format is explicitly set.
   *
   * @throws Exception if reflection fails
   */
  @Test
  public void getFormat_defaultFormat_returnsFull() throws Exception {
    // Given: formatOptions = null (no format specified)
    MessageStreamPrinter p = new MessageStreamPrinter();

    // When
    Method getFormat = MessageStreamPrinter.class.getDeclaredMethod("getFormat");
    getFormat.setAccessible(true);
    Object result = getFormat.invoke(p);

    // Then: Returns COMPACT (the default)
    assertThat(result, is(MessageStreamPrinter.OutputFormat.COMPACT));
  }

  /**
   * Tests that shouldPrint correctly filters by offset when offset filter is set.
   *
   * @throws Exception if reflection fails
   */
  @Test
  public void shouldPrint_withOffset_filtersByOffset() throws Exception {
    // Given
    UUID peer = UUID.randomUUID();
    MessageBuilder b = new MessageBuilder(peer, Boolean.toString(false));
    var em = b.buildEmptyConstructor(peer, "java.lang.String");
    var m = b.wrap(em);
    LogMessage<?> lm = logOf(m);

    MessageStreamPrinter p = new MessageStreamPrinter();
    setField(p, "offset", 10L);

    Method shouldPrint =
        MessageStreamPrinter.class.getDeclaredMethod(
            "shouldPrint", Long.class, String.class, LogMessage.class);
    shouldPrint.setAccessible(true);

    // When: matching offset
    boolean matchResult = (boolean) shouldPrint.invoke(p, 10L, peer.toString(), lm);

    // Then: true (offset matches, short-circuits)
    assertThat(matchResult, is(true));

    // When: non-matching offset, no other filters set
    boolean noMatchResult = (boolean) shouldPrint.invoke(p, 5L, peer.toString(), lm);

    // Then: true because remaining filters (all null) pass through
    assertThat(noMatchResult, is(true));
  }

  // ==========================================================================
  // Tests for TREE output format
  // ==========================================================================

  /**
   * Tests that getFormat returns TREE when tree flag is set.
   *
   * @throws Exception if reflection fails
   */
  @Test
  public void getFormat_returnsTree_whenTreeFlagSet() throws Exception {
    MessageStreamPrinter p = new MessageStreamPrinter();

    Class<?> formatOptionsClass =
        Class.forName("io.quasient.pal.tools.cli.MessageStreamPrinter$FormatOptions");
    Object formatOptions = formatOptionsClass.getDeclaredConstructor().newInstance();
    var treeField = formatOptionsClass.getDeclaredField("tree");
    treeField.setAccessible(true);
    treeField.set(formatOptions, true);

    var fmtOptsField = MessageStreamPrinter.class.getDeclaredField("formatOptions");
    fmtOptsField.setAccessible(true);
    fmtOptsField.set(p, formatOptions);

    Method getFormat = MessageStreamPrinter.class.getDeclaredMethod("getFormat");
    getFormat.setAccessible(true);
    Object result = getFormat.invoke(p);

    assertThat(result, is(MessageStreamPrinter.OutputFormat.TREE));
  }

  /**
   * Tests that TREE format produces indented output with offset markers.
   *
   * @throws Exception if reflection fails
   */
  @Test
  public void printRecord_treeFormat_producesIndentedOutput() throws Exception {
    UUID peer = UUID.randomUUID();
    MessageBuilder b = new MessageBuilder(peer, Boolean.toString(false));
    var em = b.buildEmptyConstructor(peer, "java.lang.String");
    var m = b.wrap(em);
    LogMessage<?> lm = logOf(m);

    MessageStreamPrinter p = new MessageStreamPrinter();

    // Set TREE format
    Class<?> formatOptionsClass =
        Class.forName("io.quasient.pal.tools.cli.MessageStreamPrinter$FormatOptions");
    Object formatOptions = formatOptionsClass.getDeclaredConstructor().newInstance();
    var treeField = formatOptionsClass.getDeclaredField("tree");
    treeField.setAccessible(true);
    treeField.set(formatOptions, true);
    var fmtOptsField = MessageStreamPrinter.class.getDeclaredField("formatOptions");
    fmtOptsField.setAccessible(true);
    fmtOptsField.set(p, formatOptions);

    // Capture output
    PrintStream originalOut = System.out;
    ByteArrayOutputStream bout = new ByteArrayOutputStream();
    System.setOut(new PrintStream(bout, true, UTF_8));

    try {
      Method printRecord =
          MessageStreamPrinter.class.getDeclaredMethod(
              "printRecord", String.class, LogMessage.class, long.class);
      printRecord.setAccessible(true);
      printRecord.invoke(p, peer.toString(), lm, 5L);

      String output = bout.toString(UTF_8);
      // TREE format should contain [offset] and the summary
      assertThat(output, containsString("[5]"));
      assertThat(output, containsString("new String"));
    } finally {
      System.setOut(originalOut);
    }
  }

  /**
   * Tests that TREE format increases depth after operations and decreases after returns.
   *
   * @throws Exception if reflection fails
   */
  @Test
  public void printRecord_treeFormat_nestingIncreasesAndDecreases() throws Exception {
    UUID peer = UUID.randomUUID();
    MessageBuilder b = new MessageBuilder(peer, Boolean.toString(false));

    // Constructor call - should increase depth
    var constructorMsg = b.buildEmptyConstructor(peer, "java.lang.String");
    var m1 = b.wrap(constructorMsg);
    LogMessage<?> lm1 = logOf(m1);

    // Return value - should decrease depth
    var returnMsg = new ExecMessage();
    returnMsg.setPeerUuid(peer.toString());
    returnMsg.setMessageId("ret-1");
    returnMsg.setThreadName("main");
    returnMsg.setCurrentTime("2026-01-01T00:00:00Z");
    var rv = new ReturnValue();
    rv.setIsVoid(true);
    returnMsg.setReturnValue(rv);
    Message m2 = new Message();
    m2.setMessageType(MessageType.EXEC_RETURN_VALUE.getId());
    m2.setExecMessage(returnMsg);
    LogMessage<?> lm2 = logOf(m2);

    MessageStreamPrinter p = new MessageStreamPrinter();

    // Set TREE format
    Class<?> formatOptionsClass =
        Class.forName("io.quasient.pal.tools.cli.MessageStreamPrinter$FormatOptions");
    Object formatOptions = formatOptionsClass.getDeclaredConstructor().newInstance();
    var treeField = formatOptionsClass.getDeclaredField("tree");
    treeField.setAccessible(true);
    treeField.set(formatOptions, true);
    var fmtOptsField = MessageStreamPrinter.class.getDeclaredField("formatOptions");
    fmtOptsField.setAccessible(true);
    fmtOptsField.set(p, formatOptions);

    // Capture output
    PrintStream originalOut = System.out;
    ByteArrayOutputStream bout = new ByteArrayOutputStream();
    System.setOut(new PrintStream(bout, true, UTF_8));

    try {
      Method printRecord =
          MessageStreamPrinter.class.getDeclaredMethod(
              "printRecord", String.class, LogMessage.class, long.class);
      printRecord.setAccessible(true);

      // Print constructor (depth 0 -> 1)
      printRecord.invoke(p, peer.toString(), lm1, 0L);
      // Print return (depth 1 -> 0)
      printRecord.invoke(p, peer.toString(), lm2, 1L);

      String output = bout.toString(UTF_8);
      String[] lines = output.split("\n", -1);
      // First line (constructor) should not be indented
      assertThat(lines[0], containsString("[0]"));
      assertThat(lines[0], containsString("new String"));
      // Second line (return) should not be indented either (depth decreases before print)
      assertThat(lines[1], containsString("[1]"));
      assertThat(lines[1], containsString("return void"));
    } finally {
      System.setOut(originalOut);
    }
  }

  // ==========================================================================
  // Tests for --with-return (extractMessageId, isResponseTo)
  // ==========================================================================

  /**
   * Tests that extractMessageId returns the message ID from a Colfer message.
   *
   * @throws Exception if reflection fails
   */
  @Test
  public void extractMessageId_colferMessage_returnsId() throws Exception {
    UUID peer = UUID.randomUUID();
    MessageBuilder b = new MessageBuilder(peer, Boolean.toString(false));
    var em = b.buildEmptyConstructor(peer, "java.lang.String");
    var m = b.wrap(em);
    LogMessage<?> lm = logOf(m);

    Method extractId =
        MessageStreamPrinter.class.getDeclaredMethod("extractMessageId", LogMessage.class);
    extractId.setAccessible(true);
    String result = (String) extractId.invoke(null, lm);

    assertThat(result, is(notNullValue()));
    assertThat(result, is(em.getMessageId()));
  }

  /**
   * Tests that isResponseTo returns true when a RETURN_VALUE message has a matching responseToId.
   *
   * @throws Exception if reflection fails
   */
  @Test
  public void isResponseTo_matchingReturn_returnsTrue() throws Exception {
    UUID peer = UUID.randomUUID();
    String originalMsgId = "original-msg-123";

    // Build a return value message with responseToId set
    var returnMsg = new ExecMessage();
    returnMsg.setPeerUuid(peer.toString());
    returnMsg.setMessageId("ret-1");
    returnMsg.setThreadName("main");
    returnMsg.setCurrentTime("2026-01-01T00:00:00Z");
    returnMsg.setResponseToId(originalMsgId);
    var rv = new ReturnValue();
    rv.setIsVoid(true);
    returnMsg.setReturnValue(rv);
    Message m = new Message();
    m.setMessageType(MessageType.EXEC_RETURN_VALUE.getId());
    m.setExecMessage(returnMsg);
    LogMessage<?> lm = logOf(m);

    Method isResp =
        MessageStreamPrinter.class.getDeclaredMethod(
            "isResponseTo", LogMessage.class, String.class);
    isResp.setAccessible(true);
    boolean result = (boolean) isResp.invoke(null, lm, originalMsgId);

    assertThat(result, is(true));
  }

  /**
   * Tests that isResponseTo returns false when the responseToId does not match.
   *
   * @throws Exception if reflection fails
   */
  @Test
  public void isResponseTo_mismatchingReturn_returnsFalse() throws Exception {
    UUID peer = UUID.randomUUID();

    var returnMsg = new ExecMessage();
    returnMsg.setPeerUuid(peer.toString());
    returnMsg.setMessageId("ret-1");
    returnMsg.setThreadName("main");
    returnMsg.setCurrentTime("2026-01-01T00:00:00Z");
    returnMsg.setResponseToId("different-msg-id");
    var rv = new ReturnValue();
    rv.setIsVoid(true);
    returnMsg.setReturnValue(rv);
    Message m = new Message();
    m.setMessageType(MessageType.EXEC_RETURN_VALUE.getId());
    m.setExecMessage(returnMsg);
    LogMessage<?> lm = logOf(m);

    Method isResp =
        MessageStreamPrinter.class.getDeclaredMethod(
            "isResponseTo", LogMessage.class, String.class);
    isResp.setAccessible(true);
    boolean result = (boolean) isResp.invoke(null, lm, "original-msg-123");

    assertThat(result, is(false));
  }

  /**
   * Tests that isResponseTo returns false for non-return messages (e.g., CONSTRUCTOR).
   *
   * @throws Exception if reflection fails
   */
  @Test
  public void isResponseTo_nonReturnMessage_returnsFalse() throws Exception {
    UUID peer = UUID.randomUUID();
    MessageBuilder b = new MessageBuilder(peer, Boolean.toString(false));
    var em = b.buildEmptyConstructor(peer, "java.lang.String");
    var m = b.wrap(em);
    LogMessage<?> lm = logOf(m);

    Method isResp =
        MessageStreamPrinter.class.getDeclaredMethod(
            "isResponseTo", LogMessage.class, String.class);
    isResp.setAccessible(true);
    boolean result = (boolean) isResp.invoke(null, lm, "any-id");

    assertThat(result, is(false));
  }

  // ==========================================================================
  // Tests for --filter (matchesFilters, shouldPrint with filters)
  // ==========================================================================

  /**
   * Tests that shouldPrint accepts messages matching a class filter.
   *
   * @throws Exception if reflection fails
   */
  @Test
  public void shouldPrint_withClassFilter_matchesClass() throws Exception {
    UUID peer = UUID.randomUUID();
    MessageBuilder b = new MessageBuilder(peer, Boolean.toString(false));
    var em = b.buildEmptyConstructor(peer, "java.lang.String");
    var m = b.wrap(em);
    LogMessage<?> lm = logOf(m);

    MessageStreamPrinter p = new MessageStreamPrinter();
    setField(p, "filters", List.of("class=java.lang.String"));

    Method shouldPrint =
        MessageStreamPrinter.class.getDeclaredMethod(
            "shouldPrint", Long.class, String.class, LogMessage.class);
    shouldPrint.setAccessible(true);
    boolean result = (boolean) shouldPrint.invoke(p, 5L, peer.toString(), lm);

    assertThat(result, is(true));
  }

  /**
   * Tests that shouldPrint rejects messages not matching a class filter.
   *
   * @throws Exception if reflection fails
   */
  @Test
  public void shouldPrint_withClassFilter_rejectsMismatch() throws Exception {
    UUID peer = UUID.randomUUID();
    MessageBuilder b = new MessageBuilder(peer, Boolean.toString(false));
    var em = b.buildEmptyConstructor(peer, "java.lang.String");
    var m = b.wrap(em);
    LogMessage<?> lm = logOf(m);

    MessageStreamPrinter p = new MessageStreamPrinter();
    setField(p, "filters", List.of("class=com.example.OrderService"));

    Method shouldPrint =
        MessageStreamPrinter.class.getDeclaredMethod(
            "shouldPrint", Long.class, String.class, LogMessage.class);
    shouldPrint.setAccessible(true);
    boolean result = (boolean) shouldPrint.invoke(p, 5L, peer.toString(), lm);

    assertThat(result, is(false));
  }

  /**
   * Tests that shouldPrint accepts messages matching a method filter.
   *
   * @throws Exception if reflection fails
   */
  @Test
  public void shouldPrint_withMethodFilter_matchesMethod() throws Exception {
    UUID peer = UUID.randomUUID();
    MessageBuilder b = new MessageBuilder(peer, Boolean.toString(false));
    var em =
        b.buildInstanceMethod(
            peer,
            "java.util.ArrayList",
            "add",
            ObjectRef.randomRef(),
            new String[] {"int"},
            new Object[] {1});
    var m = b.wrap(em);
    LogMessage<?> lm = logOf(m);

    MessageStreamPrinter p = new MessageStreamPrinter();
    setField(p, "filters", List.of("method=add"));

    Method shouldPrint =
        MessageStreamPrinter.class.getDeclaredMethod(
            "shouldPrint", Long.class, String.class, LogMessage.class);
    shouldPrint.setAccessible(true);
    boolean result = (boolean) shouldPrint.invoke(p, 5L, peer.toString(), lm);

    assertThat(result, is(true));
  }

  /**
   * Tests that shouldPrint rejects messages not matching a method filter.
   *
   * @throws Exception if reflection fails
   */
  @Test
  public void shouldPrint_withMethodFilter_rejectsMismatch() throws Exception {
    UUID peer = UUID.randomUUID();
    MessageBuilder b = new MessageBuilder(peer, Boolean.toString(false));
    var em =
        b.buildInstanceMethod(
            peer,
            "java.util.ArrayList",
            "add",
            ObjectRef.randomRef(),
            new String[] {"int"},
            new Object[] {1});
    var m = b.wrap(em);
    LogMessage<?> lm = logOf(m);

    MessageStreamPrinter p = new MessageStreamPrinter();
    setField(p, "filters", List.of("method=remove"));

    Method shouldPrint =
        MessageStreamPrinter.class.getDeclaredMethod(
            "shouldPrint", Long.class, String.class, LogMessage.class);
    shouldPrint.setAccessible(true);
    boolean result = (boolean) shouldPrint.invoke(p, 5L, peer.toString(), lm);

    assertThat(result, is(false));
  }

  /**
   * Tests that shouldPrint applies AND logic when both class and method filters are set.
   *
   * @throws Exception if reflection fails
   */
  @Test
  public void shouldPrint_withMultipleFilters_appliesAndLogic() throws Exception {
    UUID peer = UUID.randomUUID();
    MessageBuilder b = new MessageBuilder(peer, Boolean.toString(false));
    var em =
        b.buildInstanceMethod(
            peer,
            "java.util.ArrayList",
            "add",
            ObjectRef.randomRef(),
            new String[] {"int"},
            new Object[] {1});
    var m = b.wrap(em);
    LogMessage<?> lm = logOf(m);

    MessageStreamPrinter p = new MessageStreamPrinter();
    setField(p, "filters", List.of("class=java.util.ArrayList", "method=add"));

    Method shouldPrint =
        MessageStreamPrinter.class.getDeclaredMethod(
            "shouldPrint", Long.class, String.class, LogMessage.class);
    shouldPrint.setAccessible(true);
    boolean result = (boolean) shouldPrint.invoke(p, 5L, peer.toString(), lm);

    assertThat(result, is(true));
  }

  /**
   * Tests that filter supports partial class name matching (contains, not exact match).
   *
   * @throws Exception if reflection fails
   */
  @Test
  public void shouldPrint_withClassFilter_supportsPartialMatch() throws Exception {
    UUID peer = UUID.randomUUID();
    MessageBuilder b = new MessageBuilder(peer, Boolean.toString(false));
    var em = b.buildEmptyConstructor(peer, "java.lang.String");
    var m = b.wrap(em);
    LogMessage<?> lm = logOf(m);

    MessageStreamPrinter p = new MessageStreamPrinter();
    setField(p, "filters", List.of("class=String"));

    Method shouldPrint =
        MessageStreamPrinter.class.getDeclaredMethod(
            "shouldPrint", Long.class, String.class, LogMessage.class);
    shouldPrint.setAccessible(true);
    boolean result = (boolean) shouldPrint.invoke(p, 5L, peer.toString(), lm);

    assertThat(result, is(true));
  }

  /**
   * Tests that printVerboseFilters outputs filter patterns when set.
   *
   * @throws Exception if reflection fails
   */
  @Test
  public void printVerboseFilters_withFilters_printsPatterns() throws Exception {
    MessageStreamPrinter p = new MessageStreamPrinter();
    setField(p, "filters", List.of("class=com.example.OrderService"));

    PrintStream originalOut = System.out;
    ByteArrayOutputStream bout = new ByteArrayOutputStream();
    System.setOut(new PrintStream(bout, true, UTF_8));

    try {
      Method m =
          MessageStreamPrinter.class.getDeclaredMethod(
              "printVerboseFilters", String.class, String.class);
      m.setAccessible(true);
      m.invoke(p, "Header", "offset id");

      String output = bout.toString(UTF_8);
      assertThat(output, containsString("Filtering by pattern(s):"));
      assertThat(output, containsString("class=com.example.OrderService"));
    } finally {
      System.setOut(originalOut);
    }
  }

  /**
   * Tests that printVerboseFilters shows --with-return indication when offset and withReturn are
   * set.
   *
   * @throws Exception if reflection fails
   */
  @Test
  public void printVerboseFilters_withReturn_showsReturnMessage() throws Exception {
    MessageStreamPrinter p = new MessageStreamPrinter();
    setField(p, "offset", 10L);
    setField(p, "withReturn", true);

    PrintStream originalOut = System.out;
    ByteArrayOutputStream bout = new ByteArrayOutputStream();
    System.setOut(new PrintStream(bout, true, UTF_8));

    try {
      Method m =
          MessageStreamPrinter.class.getDeclaredMethod(
              "printVerboseFilters", String.class, String.class);
      m.setAccessible(true);
      m.invoke(p, "Header", "offset id");

      String output = bout.toString(UTF_8);
      assertThat(output, containsString("Will print message with offset id: 10"));
      assertThat(output, containsString("Will also show return value"));
    } finally {
      System.setOut(originalOut);
    }
  }

  /**
   * Sets a field value on the target object or its superclass hierarchy via reflection.
   *
   * @param target the target object
   * @param fieldName the field name
   * @param value the value to set
   * @throws Exception if reflection fails
   */
  private static void setField(Object target, String fieldName, Object value) throws Exception {
    Field f = findField(target.getClass(), fieldName);
    f.setAccessible(true);
    f.set(target, value);
  }

  /**
   * Finds a declared field in the class hierarchy.
   *
   * @param clazz the class to start searching from
   * @param name the field name
   * @return the field
   * @throws NoSuchFieldException if not found in any superclass
   */
  private static Field findField(Class<?> clazz, String name) throws NoSuchFieldException {
    Class<?> current = clazz;
    while (current != null) {
      try {
        return current.getDeclaredField(name);
      } catch (NoSuchFieldException e) {
        current = current.getSuperclass();
      }
    }
    throw new NoSuchFieldException(name);
  }
}
