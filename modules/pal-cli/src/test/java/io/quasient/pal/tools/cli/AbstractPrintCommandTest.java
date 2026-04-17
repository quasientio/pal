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
package io.quasient.pal.tools.cli;

import static io.quasient.pal.serdes.colfer.ExecMessageUtils.getMessageTypeOf;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

import com.google.common.base.Splitter;
import io.quasient.pal.common.objects.ObjectRef;
import io.quasient.pal.messages.LogMessage;
import io.quasient.pal.messages.colfer.Class;
import io.quasient.pal.messages.colfer.ExecMessage;
import io.quasient.pal.messages.colfer.Field;
import io.quasient.pal.messages.colfer.InstanceFieldPutDone;
import io.quasient.pal.messages.colfer.Message;
import io.quasient.pal.messages.colfer.ReturnValue;
import io.quasient.pal.messages.colfer.StaticFieldPutDone;
import io.quasient.pal.messages.types.MessageType;
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

      // Then: Output is valid JSON with offset embedded as a field
      String output = bout.toString(UTF_8);
      assertThat(output, containsString("\"offset\": 10"));
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

  /**
   * Tests that static field put_done messages are printed at the same indentation level as their
   * corresponding put messages, not nested underneath them.
   *
   * <p>Verifies the sequence: new → put_static → put_static_done → return produces correct nesting
   * where put and put_done are siblings (same depth), not parent-child.
   */
  @Test
  public void printTreeRecord_putStaticDoneAtSameDepthAsPut() throws Exception {
    // Given: a sequence of messages: new, put_static, put_static_done, return
    UUID peer = UUID.randomUUID();
    MessageBuilder b = new MessageBuilder(peer, Boolean.toString(false));

    LogMessage<?> newMsg = logOf(b.wrap(b.buildEmptyConstructor(peer, "com.example.MyClass")));
    LogMessage<?> putStaticMsg =
        logOf(b.wrap(b.buildPutStatic(peer, "com.example.MyClass", "count", "int", 42)));
    LogMessage<?> putStaticDoneMsg =
        logOf(buildStaticFieldPutDoneMessage("com.example.MyClass", "count"));
    LogMessage<?> returnMsg = logOf(buildVoidReturnMessage());

    LogPrint cmd = createTestInstance();

    PrintStream originalOut = System.out;
    ByteArrayOutputStream bout = new ByteArrayOutputStream();
    System.setOut(new PrintStream(bout, true, UTF_8));

    try {
      // When: printTreeRecord() called for each message in sequence
      cmd.printTreeRecord(newMsg, 0L);
      cmd.printTreeRecord(putStaticMsg, 1L);
      cmd.printTreeRecord(putStaticDoneMsg, 2L);
      cmd.printTreeRecord(returnMsg, 3L);

      // Then: put and put_done are at the same indentation (depth 1),
      //       not put_done nested under put (depth 2)
      String output = bout.toString(UTF_8);
      List<String> lines = Splitter.on('\n').omitEmptyStrings().splitToList(output);
      assertThat("expected 4 output lines", lines.size(), is(4));

      String newLine = lines.get(0);
      String putLine = lines.get(1);
      String putDoneLine = lines.get(2);
      String returnLine = lines.get(3);

      // new at depth 0 (no indent)
      assertThat(newLine, containsString("[0]"));
      assertThat("new should have no indent", indentOf(newLine), is(0));

      // put at depth 1 (2 spaces)
      assertThat(putLine, containsString("[1]"));
      assertThat("put should be indented under new", indentOf(putLine), is(2));

      // put_done at depth 1 (same as put, not nested under it)
      assertThat(putDoneLine, containsString("[2]"));
      assertThat(putDoneLine, containsString("put_done"));
      assertThat("put_done should be at same depth as put", indentOf(putDoneLine), is(2));

      // return at depth 0 (closing the constructor)
      assertThat(returnLine, containsString("[3]"));
      assertThat("return should be at depth 0", indentOf(returnLine), is(0));
    } finally {
      System.setOut(originalOut);
    }
  }

  /**
   * Tests that instance field put_done messages are printed at the same indentation level as their
   * corresponding put messages.
   *
   * <p>Verifies that instance field put/put_done pairs are treated the same as static field
   * put/put_done pairs for tree indentation purposes.
   */
  @Test
  public void printTreeRecord_putInstanceFieldDoneAtSameDepthAsPut() throws Exception {
    // Given: a sequence of messages: new, put_field, put_field_done, return
    UUID peer = UUID.randomUUID();
    MessageBuilder b = new MessageBuilder(peer, Boolean.toString(false));
    ObjectRef targetRef = ObjectRef.randomRef();

    LogMessage<?> newMsg = logOf(b.wrap(b.buildEmptyConstructor(peer, "com.example.Order")));
    LogMessage<?> putFieldMsg =
        logOf(
            b.wrap(
                b.buildPutObject(peer, "com.example.Order", "total", targetRef, "double", 99.95)));
    LogMessage<?> putFieldDoneMsg =
        logOf(buildInstanceFieldPutDoneMessage("com.example.Order", "total"));
    LogMessage<?> returnMsg = logOf(buildVoidReturnMessage());

    LogPrint cmd = createTestInstance();

    PrintStream originalOut = System.out;
    ByteArrayOutputStream bout = new ByteArrayOutputStream();
    System.setOut(new PrintStream(bout, true, UTF_8));

    try {
      // When: printTreeRecord() called for each message in sequence
      cmd.printTreeRecord(newMsg, 0L);
      cmd.printTreeRecord(putFieldMsg, 1L);
      cmd.printTreeRecord(putFieldDoneMsg, 2L);
      cmd.printTreeRecord(returnMsg, 3L);

      // Then: put_field and put_field_done are at the same depth
      String output = bout.toString(UTF_8);
      List<String> lines = Splitter.on('\n').omitEmptyStrings().splitToList(output);
      assertThat("expected 4 output lines", lines.size(), is(4));

      assertThat("put should be indented", indentOf(lines.get(1)), is(2));
      assertThat("put_done should be at same depth as put", indentOf(lines.get(2)), is(2));
      assertThat(lines.get(2), containsString("put_done"));
      assertThat("return at depth 0", indentOf(lines.get(3)), is(0));
    } finally {
      System.setOut(originalOut);
    }
  }

  /**
   * Tests that multiple put/put_done pairs within a constructor are all at the correct nesting
   * level, and that subsequent operations after a put_done are not erroneously nested deeper.
   *
   * <p>Reproduces the scenario from the bug report where operations following a put_done were
   * incorrectly nested under it.
   */
  @Test
  public void printTreeRecord_multipleFieldPutsInConstructor() throws Exception {
    // Given: new → put → put_done → put → put_done → return
    UUID peer = UUID.randomUUID();
    MessageBuilder b = new MessageBuilder(peer, Boolean.toString(false));
    ObjectRef targetRef = ObjectRef.randomRef();

    LogMessage<?> newMsg = logOf(b.wrap(b.buildEmptyConstructor(peer, "com.example.Service")));
    LogMessage<?> put1 =
        logOf(b.wrap(b.buildPutObject(peer, "com.example.Service", "fieldA", targetRef, "int", 1)));
    LogMessage<?> putDone1 =
        logOf(buildInstanceFieldPutDoneMessage("com.example.Service", "fieldA"));
    LogMessage<?> put2 =
        logOf(b.wrap(b.buildPutObject(peer, "com.example.Service", "fieldB", targetRef, "int", 2)));
    LogMessage<?> putDone2 =
        logOf(buildInstanceFieldPutDoneMessage("com.example.Service", "fieldB"));
    LogMessage<?> returnMsg = logOf(buildVoidReturnMessage());

    LogPrint cmd = createTestInstance();

    PrintStream originalOut = System.out;
    ByteArrayOutputStream bout = new ByteArrayOutputStream();
    System.setOut(new PrintStream(bout, true, UTF_8));

    try {
      // When: all messages printed in sequence
      cmd.printTreeRecord(newMsg, 0L);
      cmd.printTreeRecord(put1, 1L);
      cmd.printTreeRecord(putDone1, 2L);
      cmd.printTreeRecord(put2, 3L);
      cmd.printTreeRecord(putDone2, 4L);
      cmd.printTreeRecord(returnMsg, 5L);

      // Then: both put/put_done pairs at depth 1, return at depth 0
      String output = bout.toString(UTF_8);
      List<String> lines = Splitter.on('\n').omitEmptyStrings().splitToList(output);
      assertThat("expected 6 output lines", lines.size(), is(6));

      // [0] new at depth 0
      assertThat("new at depth 0", indentOf(lines.get(0)), is(0));
      // [1] first put at depth 1
      assertThat("first put at depth 1", indentOf(lines.get(1)), is(2));
      // [2] first put_done at depth 1 (same as put)
      assertThat("first put_done at depth 1", indentOf(lines.get(2)), is(2));
      // [3] second put at depth 1 (not nested under first put_done)
      assertThat("second put at depth 1", indentOf(lines.get(3)), is(2));
      // [4] second put_done at depth 1
      assertThat("second put_done at depth 1", indentOf(lines.get(4)), is(2));
      // [5] return at depth 0
      assertThat("return at depth 0", indentOf(lines.get(5)), is(0));
    } finally {
      System.setOut(originalOut);
    }
  }

  /**
   * Tests that return and throwable messages still correctly decrease depth (regression check).
   *
   * <p>Ensures that the put_done fix did not break the existing behavior for EXEC_RETURN_VALUE and
   * EXEC_THROWABLE message types.
   */
  @Test
  public void printTreeRecord_returnAndThrowableStillDecreaseDepth() throws Exception {
    // Given: new → call → return (inner) → return (outer)
    UUID peer = UUID.randomUUID();
    MessageBuilder b = new MessageBuilder(peer, Boolean.toString(false));
    ObjectRef objRef = ObjectRef.randomRef();

    LogMessage<?> newMsg = logOf(b.wrap(b.buildEmptyConstructor(peer, "com.example.Outer")));
    LogMessage<?> callMsg =
        logOf(
            b.wrap(
                b.buildInstanceMethod(
                    peer,
                    "com.example.Outer",
                    "doWork",
                    objRef,
                    new String[] {},
                    new Object[] {})));
    LogMessage<?> innerReturn = logOf(buildVoidReturnMessage());
    LogMessage<?> outerReturn = logOf(buildVoidReturnMessage());

    LogPrint cmd = createTestInstance();

    PrintStream originalOut = System.out;
    ByteArrayOutputStream bout = new ByteArrayOutputStream();
    System.setOut(new PrintStream(bout, true, UTF_8));

    try {
      cmd.printTreeRecord(newMsg, 0L);
      cmd.printTreeRecord(callMsg, 1L);
      cmd.printTreeRecord(innerReturn, 2L);
      cmd.printTreeRecord(outerReturn, 3L);

      String output = bout.toString(UTF_8);
      List<String> lines = Splitter.on('\n').omitEmptyStrings().splitToList(output);
      assertThat("expected 4 output lines", lines.size(), is(4));

      assertThat("new at depth 0", indentOf(lines.get(0)), is(0));
      assertThat("call at depth 1", indentOf(lines.get(1)), is(2));
      assertThat("inner return at depth 1", indentOf(lines.get(2)), is(2));
      assertThat("outer return at depth 0", indentOf(lines.get(3)), is(0));
    } finally {
      System.setOut(originalOut);
    }
  }

  // ==================== field= filter Tests ====================

  /**
   * Tests that the {@code field=} filter matches a GET_STATIC message by field name.
   *
   * <p>Verifies that when {@code --filter field=count} is set, a static field get message for a
   * field named "count" passes the filter.
   */
  @Test
  public void shouldPrint_matchesFieldFilter_forGetStatic() {
    UUID peer = UUID.randomUUID();
    MessageBuilder b = new MessageBuilder(peer, Boolean.toString(false));
    ExecMessage em = b.buildGetStatic(peer, "com.example.Counter", "count");
    LogMessage<?> lm = logOf(b.wrap(em));

    LogPrint cmd = createTestInstance();
    cmd.filters = List.of("field=count");

    assertThat(cmd.shouldPrint(0L, peer.toString(), lm), is(true));
  }

  /**
   * Tests that the {@code field=} filter matches a GET_FIELD message by field name.
   *
   * <p>Verifies that when {@code --filter field=total} is set, an instance field get message for a
   * field named "total" passes the filter.
   */
  @Test
  public void shouldPrint_matchesFieldFilter_forGetField() {
    UUID peer = UUID.randomUUID();
    MessageBuilder b = new MessageBuilder(peer, Boolean.toString(false));
    ExecMessage em = b.buildGetObject(peer, "com.example.Order", "total", ObjectRef.randomRef());
    LogMessage<?> lm = logOf(b.wrap(em));

    LogPrint cmd = createTestInstance();
    cmd.filters = List.of("field=total");

    assertThat(cmd.shouldPrint(0L, peer.toString(), lm), is(true));
  }

  /**
   * Tests that the {@code field=} filter matches a PUT_STATIC message by field name.
   *
   * <p>Verifies that when {@code --filter field=count} is set, a static field put message for a
   * field named "count" passes the filter.
   */
  @Test
  public void shouldPrint_matchesFieldFilter_forPutStatic() {
    UUID peer = UUID.randomUUID();
    MessageBuilder b = new MessageBuilder(peer, Boolean.toString(false));
    ExecMessage em = b.buildPutStatic(peer, "com.example.Counter", "count", "int", 42);
    LogMessage<?> lm = logOf(b.wrap(em));

    LogPrint cmd = createTestInstance();
    cmd.filters = List.of("field=count");

    assertThat(cmd.shouldPrint(0L, peer.toString(), lm), is(true));
  }

  /**
   * Tests that the {@code field=} filter matches a PUT_FIELD message by field name.
   *
   * <p>Verifies that when {@code --filter field=total} is set, an instance field put message for a
   * field named "total" passes the filter.
   */
  @Test
  public void shouldPrint_matchesFieldFilter_forPutField() {
    UUID peer = UUID.randomUUID();
    MessageBuilder b = new MessageBuilder(peer, Boolean.toString(false));
    ExecMessage em =
        b.buildPutObject(
            peer, "com.example.Order", "total", ObjectRef.randomRef(), "double", 99.95);
    LogMessage<?> lm = logOf(b.wrap(em));

    LogPrint cmd = createTestInstance();
    cmd.filters = List.of("field=total");

    assertThat(cmd.shouldPrint(0L, peer.toString(), lm), is(true));
  }

  /**
   * Tests that the {@code field=} filter rejects a method call message.
   *
   * <p>Verifies that when {@code --filter field=add} is set, an INSTANCE_METHOD message named "add"
   * is rejected because it is not a field operation.
   */
  @Test
  public void shouldPrint_fieldFilterRejectsMethodCall() {
    UUID peer = UUID.randomUUID();
    MessageBuilder b = new MessageBuilder(peer, Boolean.toString(false));
    ExecMessage em =
        b.buildInstanceMethod(
            peer,
            "com.example.Calculator",
            "add",
            ObjectRef.randomRef(),
            new String[] {"int"},
            new Object[] {1});
    LogMessage<?> lm = logOf(b.wrap(em));

    LogPrint cmd = createTestInstance();
    cmd.filters = List.of("field=add");

    assertThat(cmd.shouldPrint(0L, peer.toString(), lm), is(false));
  }

  /**
   * Tests that the {@code field=} filter rejects a constructor message.
   *
   * <p>Verifies that when {@code --filter field=new} is set, a CONSTRUCTOR message is rejected
   * because constructors are not field operations.
   */
  @Test
  public void shouldPrint_fieldFilterRejectsConstructor() {
    UUID peer = UUID.randomUUID();
    MessageBuilder b = new MessageBuilder(peer, Boolean.toString(false));
    ExecMessage em = b.buildEmptyConstructor(peer, "com.example.MyClass");
    LogMessage<?> lm = logOf(b.wrap(em));

    LogPrint cmd = createTestInstance();
    cmd.filters = List.of("field=new");

    assertThat(cmd.shouldPrint(0L, peer.toString(), lm), is(false));
  }

  /**
   * Tests that the {@code field=} filter rejects a field op when the field name doesn't match.
   *
   * <p>Verifies that when {@code --filter field=total} is set, a GET_STATIC for field "count" is
   * rejected.
   */
  @Test
  public void shouldPrint_fieldFilterRejectsMismatchedFieldName() {
    UUID peer = UUID.randomUUID();
    MessageBuilder b = new MessageBuilder(peer, Boolean.toString(false));
    ExecMessage em = b.buildGetStatic(peer, "com.example.Counter", "count");
    LogMessage<?> lm = logOf(b.wrap(em));

    LogPrint cmd = createTestInstance();
    cmd.filters = List.of("field=total");

    assertThat(cmd.shouldPrint(0L, peer.toString(), lm), is(false));
  }

  /**
   * Tests that the {@code field=} filter matches PUT_STATIC_DONE messages.
   *
   * <p>Verifies that field done messages are also matched by the field filter, allowing users to
   * see both the put and the corresponding done when filtering by field name.
   */
  @Test
  public void shouldPrint_matchesFieldFilter_forPutStaticDone() {
    LogMessage<?> lm = logOf(buildStaticFieldPutDoneMessage("com.example.Counter", "count"));

    LogPrint cmd = createTestInstance();
    cmd.filters = List.of("field=count");

    assertThat(cmd.shouldPrint(0L, null, lm), is(true));
  }

  /**
   * Tests that the {@code field=} filter matches PUT_FIELD_DONE messages.
   *
   * <p>Verifies that instance field done messages are also matched by the field filter.
   */
  @Test
  public void shouldPrint_matchesFieldFilter_forPutFieldDone() {
    LogMessage<?> lm = logOf(buildInstanceFieldPutDoneMessage("com.example.Order", "total"));

    LogPrint cmd = createTestInstance();
    cmd.filters = List.of("field=total");

    assertThat(cmd.shouldPrint(0L, null, lm), is(true));
  }

  // ==================== class= filter with field ops Tests ====================

  /**
   * Tests that the {@code class=} filter matches a GET_STATIC message by class name.
   *
   * <p>Verifies that field operations are correctly filtered by class name.
   */
  @Test
  public void shouldPrint_classFilterMatchesGetStatic() {
    UUID peer = UUID.randomUUID();
    MessageBuilder b = new MessageBuilder(peer, Boolean.toString(false));
    ExecMessage em = b.buildGetStatic(peer, "com.example.Counter", "count");
    LogMessage<?> lm = logOf(b.wrap(em));

    LogPrint cmd = createTestInstance();
    cmd.filters = List.of("class=Counter");

    assertThat(cmd.shouldPrint(0L, peer.toString(), lm), is(true));
  }

  /**
   * Tests that the {@code class=} filter matches a PUT_FIELD message by class name.
   *
   * <p>Verifies that instance field put operations are correctly filtered by class name.
   */
  @Test
  public void shouldPrint_classFilterMatchesPutField() {
    UUID peer = UUID.randomUUID();
    MessageBuilder b = new MessageBuilder(peer, Boolean.toString(false));
    ExecMessage em =
        b.buildPutObject(
            peer, "com.example.Order", "total", ObjectRef.randomRef(), "double", 99.95);
    LogMessage<?> lm = logOf(b.wrap(em));

    LogPrint cmd = createTestInstance();
    cmd.filters = List.of("class=Order");

    assertThat(cmd.shouldPrint(0L, peer.toString(), lm), is(true));
  }

  /**
   * Tests that the {@code class=} filter rejects a field operation with a non-matching class name.
   */
  @Test
  public void shouldPrint_classFilterRejectsMismatchedClassOnFieldOp() {
    UUID peer = UUID.randomUUID();
    MessageBuilder b = new MessageBuilder(peer, Boolean.toString(false));
    ExecMessage em = b.buildGetStatic(peer, "com.example.Counter", "count");
    LogMessage<?> lm = logOf(b.wrap(em));

    LogPrint cmd = createTestInstance();
    cmd.filters = List.of("class=Order");

    assertThat(cmd.shouldPrint(0L, peer.toString(), lm), is(false));
  }

  // ==================== method= filter with field ops Tests ====================

  /**
   * Tests that the {@code method=} filter matches field operations by field name (backward
   * compatibility).
   *
   * <p>The {@code method=} filter matches both method and field names. This verifies the
   * pre-existing behavior where field names are returned by {@code getExecMethodName} for field
   * operations.
   */
  @Test
  public void shouldPrint_methodFilterMatchesFieldName() {
    UUID peer = UUID.randomUUID();
    MessageBuilder b = new MessageBuilder(peer, Boolean.toString(false));
    ExecMessage em = b.buildGetStatic(peer, "com.example.Counter", "count");
    LogMessage<?> lm = logOf(b.wrap(em));

    LogPrint cmd = createTestInstance();
    cmd.filters = List.of("method=count");

    assertThat(cmd.shouldPrint(0L, peer.toString(), lm), is(true));
  }

  // ==================== Combined field + class filter Tests ====================

  /**
   * Tests that combining {@code class=} and {@code field=} filters works correctly.
   *
   * <p>Verifies AND logic: both filters must match for the message to pass.
   */
  @Test
  public void shouldPrint_combinedClassAndFieldFilter() {
    UUID peer = UUID.randomUUID();
    MessageBuilder b = new MessageBuilder(peer, Boolean.toString(false));
    ExecMessage em =
        b.buildPutObject(
            peer, "com.example.Order", "total", ObjectRef.randomRef(), "double", 99.95);
    LogMessage<?> lm = logOf(b.wrap(em));

    LogPrint cmd = createTestInstance();
    cmd.filters = List.of("class=Order", "field=total");

    assertThat(cmd.shouldPrint(0L, peer.toString(), lm), is(true));
  }

  /**
   * Tests that combined {@code class=} and {@code field=} filters reject when field doesn't match.
   */
  @Test
  public void shouldPrint_combinedClassAndFieldFilter_rejectsWhenFieldMismatches() {
    UUID peer = UUID.randomUUID();
    MessageBuilder b = new MessageBuilder(peer, Boolean.toString(false));
    ExecMessage em =
        b.buildPutObject(
            peer, "com.example.Order", "total", ObjectRef.randomRef(), "double", 99.95);
    LogMessage<?> lm = logOf(b.wrap(em));

    LogPrint cmd = createTestInstance();
    cmd.filters = List.of("class=Order", "field=count");

    assertThat(cmd.shouldPrint(0L, peer.toString(), lm), is(false));
  }

  // ==================== isReturnType() for field done messages Tests ====================

  /**
   * Tests that {@code isReturnType} returns true for PUT_STATIC_DONE messages.
   *
   * <p>This is essential for the {@code --with-return} option to work with static field put
   * operations, where the "return" is signaled by a PUT_STATIC_DONE message.
   */
  @Test
  public void isReturnType_returnsTrueForPutStaticDone() {
    LogMessage<?> lm = logOf(buildStaticFieldPutDoneMessage("com.example.Counter", "count"));

    assertThat(AbstractPrintCommand.isReturnType(lm), is(true));
  }

  /**
   * Tests that {@code isReturnType} returns true for PUT_FIELD_DONE messages.
   *
   * <p>This is essential for the {@code --with-return} option to work with instance field put
   * operations, where the "return" is signaled by a PUT_FIELD_DONE message.
   */
  @Test
  public void isReturnType_returnsTrueForPutFieldDone() {
    LogMessage<?> lm = logOf(buildInstanceFieldPutDoneMessage("com.example.Order", "total"));

    assertThat(AbstractPrintCommand.isReturnType(lm), is(true));
  }

  /**
   * Tests that {@code isReturnType} returns false for GET_FIELD messages.
   *
   * <p>Field get operations are not "return" types. Their corresponding return is a RETURN_VALUE
   * message.
   */
  @Test
  public void isReturnType_returnsFalseForGetField() {
    UUID peer = UUID.randomUUID();
    MessageBuilder b = new MessageBuilder(peer, Boolean.toString(false));
    ExecMessage em = b.buildGetObject(peer, "com.example.Order", "total", ObjectRef.randomRef());
    LogMessage<?> lm = logOf(b.wrap(em));

    assertThat(AbstractPrintCommand.isReturnType(lm), is(false));
  }

  /**
   * Tests that {@code isReturnType} returns false for PUT_FIELD messages.
   *
   * <p>PUT_FIELD is the operation, not the completion. The completion is PUT_FIELD_DONE.
   */
  @Test
  public void isReturnType_returnsFalseForPutField() {
    UUID peer = UUID.randomUUID();
    MessageBuilder b = new MessageBuilder(peer, Boolean.toString(false));
    ExecMessage em =
        b.buildPutObject(
            peer, "com.example.Order", "total", ObjectRef.randomRef(), "double", 99.95);
    LogMessage<?> lm = logOf(b.wrap(em));

    assertThat(AbstractPrintCommand.isReturnType(lm), is(false));
  }

  // ==================== getExecFieldName() Tests ====================

  /** Tests that {@code getExecFieldName} returns the field name for GET_STATIC messages. */
  @Test
  public void getExecFieldName_returnsFieldName_forGetStatic() {
    UUID peer = UUID.randomUUID();
    MessageBuilder b = new MessageBuilder(peer, Boolean.toString(false));
    ExecMessage em = b.buildGetStatic(peer, "com.example.Counter", "count");
    MessageType msgType = getMessageTypeOf(em);

    assertThat(AbstractPrintCommand.getExecFieldName(em, msgType), is("count"));
  }

  /** Tests that {@code getExecFieldName} returns the field name for GET_FIELD messages. */
  @Test
  public void getExecFieldName_returnsFieldName_forGetField() {
    UUID peer = UUID.randomUUID();
    MessageBuilder b = new MessageBuilder(peer, Boolean.toString(false));
    ExecMessage em = b.buildGetObject(peer, "com.example.Order", "total", ObjectRef.randomRef());
    MessageType msgType = getMessageTypeOf(em);

    assertThat(AbstractPrintCommand.getExecFieldName(em, msgType), is("total"));
  }

  /** Tests that {@code getExecFieldName} returns the field name for PUT_STATIC messages. */
  @Test
  public void getExecFieldName_returnsFieldName_forPutStatic() {
    UUID peer = UUID.randomUUID();
    MessageBuilder b = new MessageBuilder(peer, Boolean.toString(false));
    ExecMessage em = b.buildPutStatic(peer, "com.example.Counter", "count", "int", 42);
    MessageType msgType = getMessageTypeOf(em);

    assertThat(AbstractPrintCommand.getExecFieldName(em, msgType), is("count"));
  }

  /** Tests that {@code getExecFieldName} returns the field name for PUT_FIELD messages. */
  @Test
  public void getExecFieldName_returnsFieldName_forPutField() {
    UUID peer = UUID.randomUUID();
    MessageBuilder b = new MessageBuilder(peer, Boolean.toString(false));
    ExecMessage em =
        b.buildPutObject(
            peer, "com.example.Order", "total", ObjectRef.randomRef(), "double", 99.95);
    MessageType msgType = getMessageTypeOf(em);

    assertThat(AbstractPrintCommand.getExecFieldName(em, msgType), is("total"));
  }

  /**
   * Tests that {@code getExecFieldName} returns null for INSTANCE_METHOD messages.
   *
   * <p>Method calls are not field operations, so the field name extractor should return null.
   */
  @Test
  public void getExecFieldName_returnsNull_forInstanceMethod() {
    UUID peer = UUID.randomUUID();
    MessageBuilder b = new MessageBuilder(peer, Boolean.toString(false));
    ExecMessage em =
        b.buildInstanceMethod(
            peer,
            "com.example.Calculator",
            "add",
            ObjectRef.randomRef(),
            new String[] {"int"},
            new Object[] {1});
    MessageType msgType = getMessageTypeOf(em);

    assertThat(AbstractPrintCommand.getExecFieldName(em, msgType), is(nullValue()));
  }

  /** Tests that {@code getExecFieldName} returns null for CONSTRUCTOR messages. */
  @Test
  public void getExecFieldName_returnsNull_forConstructor() {
    UUID peer = UUID.randomUUID();
    MessageBuilder b = new MessageBuilder(peer, Boolean.toString(false));
    ExecMessage em = b.buildEmptyConstructor(peer, "com.example.MyClass");
    MessageType msgType = getMessageTypeOf(em);

    assertThat(AbstractPrintCommand.getExecFieldName(em, msgType), is(nullValue()));
  }

  /** Tests that {@code getExecFieldName} returns null for RETURN_VALUE messages. */
  @Test
  public void getExecFieldName_returnsNull_forReturnValue() {
    ExecMessage em = new ExecMessage();
    em.setReturnValue(new ReturnValue().withIsVoid(true));
    MessageType msgType = getMessageTypeOf(em);

    assertThat(AbstractPrintCommand.getExecFieldName(em, msgType), is(nullValue()));
  }

  // ==================== getExecMethodName() with field ops Tests ====================

  /**
   * Tests that {@code getExecMethodName} returns the field name for GET_STATIC messages.
   *
   * <p>This verifies backward compatibility: the method= filter uses getExecMethodName, which
   * returns field names for field operations.
   */
  @Test
  public void getExecMethodName_returnsFieldName_forGetStatic() {
    UUID peer = UUID.randomUUID();
    MessageBuilder b = new MessageBuilder(peer, Boolean.toString(false));
    ExecMessage em = b.buildGetStatic(peer, "com.example.Counter", "count");
    MessageType msgType = getMessageTypeOf(em);

    assertThat(AbstractPrintCommand.getExecMethodName(em, msgType), is("count"));
  }

  /** Tests that {@code getExecMethodName} returns the field name for PUT_FIELD messages. */
  @Test
  public void getExecMethodName_returnsFieldName_forPutField() {
    UUID peer = UUID.randomUUID();
    MessageBuilder b = new MessageBuilder(peer, Boolean.toString(false));
    ExecMessage em =
        b.buildPutObject(
            peer, "com.example.Order", "total", ObjectRef.randomRef(), "double", 99.95);
    MessageType msgType = getMessageTypeOf(em);

    assertThat(AbstractPrintCommand.getExecMethodName(em, msgType), is("total"));
  }

  // ==================== Helper Methods ====================

  /**
   * Builds a Message containing a static field put done ExecMessage.
   *
   * @param className the class name for the field
   * @param fieldName the field name
   * @return a Message wrapping the put_done ExecMessage
   */
  private static Message buildStaticFieldPutDoneMessage(String className, String fieldName) {
    ExecMessage em = new ExecMessage();
    em.setStaticFieldPutDone(
        new StaticFieldPutDone()
            .withField(new Field().withName(fieldName).withClazz(new Class().withName(className))));
    return new Message()
        .withMessageType(MessageType.EXEC_PUT_STATIC_DONE.getId())
        .withExecMessage(em);
  }

  /**
   * Builds a Message containing an instance field put done ExecMessage.
   *
   * @param className the class name for the field
   * @param fieldName the field name
   * @return a Message wrapping the put_done ExecMessage
   */
  private static Message buildInstanceFieldPutDoneMessage(String className, String fieldName) {
    ExecMessage em = new ExecMessage();
    em.setInstanceFieldPutDone(
        new InstanceFieldPutDone()
            .withField(new Field().withName(fieldName).withClazz(new Class().withName(className))));
    return new Message()
        .withMessageType(MessageType.EXEC_PUT_FIELD_DONE.getId())
        .withExecMessage(em);
  }

  /**
   * Builds a Message containing a void return value ExecMessage.
   *
   * @return a Message wrapping the void return ExecMessage
   */
  private static Message buildVoidReturnMessage() {
    ExecMessage em = new ExecMessage();
    em.setReturnValue(new ReturnValue().withIsVoid(true));
    return new Message().withMessageType(MessageType.EXEC_RETURN_VALUE.getId()).withExecMessage(em);
  }

  /**
   * Counts the leading spaces in a string.
   *
   * @param line the line to measure
   * @return the number of leading space characters
   */
  private static int indentOf(String line) {
    int count = 0;
    for (int i = 0; i < line.length(); i++) {
      if (line.charAt(i) == ' ') {
        count++;
      } else {
        break;
      }
    }
    return count;
  }
}
