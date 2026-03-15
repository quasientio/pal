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

import io.quasient.pal.common.objects.ObjectRef;
import io.quasient.pal.messages.LogMessage;
import io.quasient.pal.messages.colfer.Message;
import io.quasient.pal.serdes.colfer.MessageBuilder;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.Test;

/**
 * Unit tests for {@code AbstractPrintCommand}.
 *
 * <p>AbstractPrintCommand is the shared base class for {@code LogPrint} and {@code PeerPrint},
 * containing the shared formatting logic including output format selection, message filtering
 * ({@code shouldPrint}), and record formatting ({@code printRecord}, {@code printTreeRecord}) in
 * compact, full, JSON, and tree formats.
 *
 * @see AbstractPrintCommand
 */
public class AbstractPrintCommandTest {

  /**
   * Creates a LogMessage wrapping the given Message for testing.
   *
   * @param m the Message to wrap
   * @return a LogMessage with standard test metadata
   */
  private static LogMessage<?> logOf(Message m) {
    return new LogMessage<>("topic", 1L, Map.of(), m);
  }

  /**
   * Creates a concrete test instance of AbstractPrintCommand. Uses LogPrint as the concrete
   * subclass since AbstractPrintCommand is abstract.
   *
   * @return a new LogPrint instance for testing base class behavior
   */
  private static LogPrint createTestInstance() {
    return new LogPrint();
  }

  // ==================== getFormat() Tests ====================

  /**
   * Tests that the default output format is COMPACT when no format flag is specified.
   *
   * <p>Verifies that when none of {@code --full}, {@code --json}, or {@code --tree} flags are set,
   * the format defaults to COMPACT.
   */
  @Test
  public void getFormat_returnsCompact_whenNoFormatSpecified() {
    // Given: no format flag specified (formatOptions is null by default)
    LogPrint cmd = createTestInstance();

    // When: getFormat() is called
    AbstractPrintCommand.OutputFormat result = cmd.getFormat();

    // Then: returns COMPACT format
    assertThat(result, is(AbstractPrintCommand.OutputFormat.COMPACT));
  }

  /**
   * Tests that the --full flag sets the output format to FULL.
   *
   * <p>Verifies that when the {@code --full} flag is set, getFormat() returns FULL.
   */
  @Test
  public void getFormat_returnsFull_whenFullFlagSet() throws Exception {
    // Given: --full flag is set
    LogPrint cmd = createTestInstance();
    AbstractPrintCommand.FormatOptions opts = new AbstractPrintCommand.FormatOptions();
    opts.full = true;
    var fmtField = AbstractPrintCommand.class.getDeclaredField("formatOptions");
    fmtField.setAccessible(true);
    fmtField.set(cmd, opts);

    // When: getFormat() is called
    AbstractPrintCommand.OutputFormat result = cmd.getFormat();

    // Then: returns FULL format
    assertThat(result, is(AbstractPrintCommand.OutputFormat.FULL));
  }

  /**
   * Tests that the --json flag sets the output format to JSON.
   *
   * <p>Verifies that when the {@code --json} flag is set, getFormat() returns JSON.
   */
  @Test
  public void getFormat_returnsJson_whenJsonFlagSet() throws Exception {
    // Given: --json flag is set
    LogPrint cmd = createTestInstance();
    AbstractPrintCommand.FormatOptions opts = new AbstractPrintCommand.FormatOptions();
    opts.json = true;
    var fmtField = AbstractPrintCommand.class.getDeclaredField("formatOptions");
    fmtField.setAccessible(true);
    fmtField.set(cmd, opts);

    // When: getFormat() is called
    AbstractPrintCommand.OutputFormat result = cmd.getFormat();

    // Then: returns JSON format
    assertThat(result, is(AbstractPrintCommand.OutputFormat.JSON));
  }

  /**
   * Tests that the --tree flag sets the output format to TREE.
   *
   * <p>Verifies that when the {@code --tree} flag is set, getFormat() returns TREE.
   */
  @Test
  public void getFormat_returnsTree_whenTreeFlagSet() throws Exception {
    // Given: --tree flag is set
    LogPrint cmd = createTestInstance();
    AbstractPrintCommand.FormatOptions opts = new AbstractPrintCommand.FormatOptions();
    opts.tree = true;
    var fmtField = AbstractPrintCommand.class.getDeclaredField("formatOptions");
    fmtField.setAccessible(true);
    fmtField.set(cmd, opts);

    // When: getFormat() is called
    AbstractPrintCommand.OutputFormat result = cmd.getFormat();

    // Then: returns TREE format
    assertThat(result, is(AbstractPrintCommand.OutputFormat.TREE));
  }

  // ==================== shouldPrint() Tests ====================

  /**
   * Tests that a null offset is filtered correctly by shouldPrint.
   *
   * <p>Verifies that when the offset parameter is null, shouldPrint handles it gracefully and does
   * not throw a NullPointerException.
   */
  @Test
  public void shouldPrint_filtersNullOffset() throws Exception {
    // Given: a valid LogMessage and a type filter that won't match
    UUID peer = UUID.randomUUID();
    MessageBuilder b = new MessageBuilder(peer, Boolean.toString(false));
    var em = b.buildEmptyConstructor(peer, "java.lang.String");
    var m = b.wrap(em);
    LogMessage<?> lm = logOf(m);

    LogPrint cmd = createTestInstance();
    // Set a type filter that won't match (message is CONSTRUCTOR, filter is INSTANCE_METHOD)
    cmd.msgTypes = List.of("INSTANCE_METHOD");

    // When: shouldPrint() called with null recOffset
    boolean result = cmd.shouldPrint(null, peer.toString(), lm);

    // Then: returns false because type filter rejects it, but no NPE
    assertThat(result, is(false));
  }

  /**
   * Tests that multiple filters (type + peer + thread) are applied together.
   *
   * <p>Verifies that when type, peer, and thread filters are all set and all match the message,
   * shouldPrint returns true.
   */
  @Test
  public void shouldPrint_combinesMultipleFilters() {
    // Given: type filter set to "CONSTRUCTOR", peer filter set to message's peer UUID,
    //        and thread filter set to message's thread name (all matching)
    UUID peer = UUID.randomUUID();
    MessageBuilder b = new MessageBuilder(peer, Boolean.toString(false));
    var em = b.buildEmptyConstructor(peer, "java.lang.String");
    var m = b.wrap(em);
    LogMessage<?> lm = logOf(m);

    LogPrint cmd = createTestInstance();
    cmd.msgTypes = List.of("CONSTRUCTOR");
    cmd.fromPeer = peer.toString();
    cmd.threadName = em.getThreadName();

    // When: shouldPrint() called with message matching both filters
    boolean result = cmd.shouldPrint(5L, peer.toString(), lm);

    // Then: returns true because all filters match
    assertThat(result, is(true));
  }

  /**
   * Tests that a mismatched filter causes shouldPrint to reject the message.
   *
   * <p>Verifies that when one filter does not match (e.g., peer filter set to a different UUID),
   * shouldPrint returns false even if other filters match.
   */
  @Test
  public void shouldPrint_rejectsMismatchedFilter() {
    // Given: type filter matching the message, but peer filter set to a different UUID
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

    LogPrint cmd = createTestInstance();
    cmd.msgTypes = List.of("CONSTRUCTOR");

    // When: shouldPrint() called with message of type INSTANCE_METHOD
    boolean result = cmd.shouldPrint(5L, peer.toString(), lm);

    // Then: returns false because type filter doesn't match
    assertThat(result, is(false));
  }

  // ==================== printRecord() Tests ====================

  /**
   * Tests that printRecord produces correct output in FULL format.
   *
   * <p>Verifies that when the output format is FULL, printRecord outputs the complete message
   * details including offset, peer, thread, class, method, arguments, and return value.
   */
  @Test
  public void printRecord_handlesFullFormat() throws Exception {
    // Given: output format set to FULL, a valid LogMessage with ExecMessage
    UUID peer = UUID.randomUUID();
    MessageBuilder b = new MessageBuilder(peer, Boolean.toString(false));
    var em = b.buildEmptyConstructor(peer, "java.lang.String");
    var m = b.wrap(em);
    LogMessage<?> lm = logOf(m);

    LogPrint cmd = createTestInstance();
    AbstractPrintCommand.FormatOptions opts = new AbstractPrintCommand.FormatOptions();
    opts.full = true;
    var fmtField = AbstractPrintCommand.class.getDeclaredField("formatOptions");
    fmtField.setAccessible(true);
    fmtField.set(cmd, opts);

    // Redirect System.out to capture output
    PrintStream originalOut = System.out;
    ByteArrayOutputStream bout = new ByteArrayOutputStream();
    System.setOut(new PrintStream(bout, true, UTF_8));

    try {
      // When: printRecord() called
      cmd.printRecord(peer.toString(), lm, 10L);

      // Then: Output contains "CONTEXT:", "HEADERS:", and offset info
      String output = bout.toString(UTF_8);
      assertThat(output, containsString("CONTEXT:"));
      assertThat(output, containsString("HEADERS:"));
      assertThat(output, containsString("offset: 10"));
    } finally {
      System.setOut(originalOut);
    }
  }

  /**
   * Tests that printRecord produces correct output in COMPACT format.
   *
   * <p>Verifies that when the output format is COMPACT, printRecord outputs a concise single-line
   * summary of the message.
   */
  @Test
  public void printRecord_handlesCompactFormat() throws Exception {
    // Given: output format set to COMPACT (default - formatOptions is null)
    UUID peer = UUID.randomUUID();
    MessageBuilder b = new MessageBuilder(peer, Boolean.toString(false));
    var em = b.buildEmptyConstructor(peer, "java.lang.String");
    var m = b.wrap(em);
    LogMessage<?> lm = logOf(m);

    LogPrint cmd = createTestInstance();
    // formatOptions is null by default, which means COMPACT format

    // Redirect System.out to capture output
    PrintStream originalOut = System.out;
    ByteArrayOutputStream bout = new ByteArrayOutputStream();
    System.setOut(new PrintStream(bout, true, UTF_8));

    try {
      // When: printRecord() called
      cmd.printRecord(peer.toString(), lm, 10L);

      // Then: Output is single line containing "offset=", "id=", "message="
      String output = bout.toString(UTF_8);
      assertThat(output, containsString("offset=10"));
      assertThat(output, containsString("id="));
      assertThat(output, containsString("message="));
    } finally {
      System.setOut(originalOut);
    }
  }

  /**
   * Tests that printRecord produces correct output in JSON format.
   *
   * <p>Verifies that when the output format is JSON, printRecord outputs a valid JSON
   * representation of the message.
   */
  @Test
  public void printRecord_handlesJsonFormat() throws Exception {
    // Given: output format set to JSON
    UUID peer = UUID.randomUUID();
    MessageBuilder b = new MessageBuilder(peer, Boolean.toString(false));
    var em = b.buildEmptyConstructor(peer, "java.lang.String");
    var m = b.wrap(em);
    LogMessage<?> lm = logOf(m);

    LogPrint cmd = createTestInstance();
    AbstractPrintCommand.FormatOptions opts = new AbstractPrintCommand.FormatOptions();
    opts.json = true;
    var fmtField = AbstractPrintCommand.class.getDeclaredField("formatOptions");
    fmtField.setAccessible(true);
    fmtField.set(cmd, opts);

    // Redirect System.out to capture output
    PrintStream originalOut = System.out;
    ByteArrayOutputStream bout = new ByteArrayOutputStream();
    System.setOut(new PrintStream(bout, true, UTF_8));

    try {
      // When: printRecord() called
      cmd.printRecord(peer.toString(), lm, 10L);

      // Then: Output contains JSON content and offset
      String output = bout.toString(UTF_8);
      assertThat(output, containsString("offset: 10"));
      // JSON output contains curly braces
      assertThat(output, containsString("{"));
    } finally {
      System.setOut(originalOut);
    }
  }

  // ==================== printTreeRecord() Tests ====================

  /**
   * Tests that printTreeRecord produces correct tree-formatted output.
   *
   * <p>Verifies that when the output format is TREE, printTreeRecord outputs a hierarchical
   * tree-style representation of the message with indentation reflecting call depth.
   */
  @Test
  public void printTreeRecord_formatsTreeOutput() throws Exception {
    // Given: a valid LogMessage with ExecMessage
    UUID peer = UUID.randomUUID();
    MessageBuilder b = new MessageBuilder(peer, Boolean.toString(false));
    var em = b.buildEmptyConstructor(peer, "java.lang.String");
    var m = b.wrap(em);
    LogMessage<?> lm = logOf(m);

    LogPrint cmd = createTestInstance();

    // Redirect System.out to capture output
    PrintStream originalOut = System.out;
    ByteArrayOutputStream bout = new ByteArrayOutputStream();
    System.setOut(new PrintStream(bout, true, UTF_8));

    try {
      // When: printTreeRecord() called
      cmd.printTreeRecord(lm, 10L);

      // Then: Output contains tree-formatted representation with offset
      String output = bout.toString(UTF_8);
      assertThat(output, containsString("[10]"));
    } finally {
      System.setOut(originalOut);
    }
  }
}
