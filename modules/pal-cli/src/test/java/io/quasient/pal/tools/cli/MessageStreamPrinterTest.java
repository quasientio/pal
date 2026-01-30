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
  @Ignore("Awaiting implementation in #367")
  public void testGetFormat_returnsCompact_whenNoFormatSpecified() throws Exception {
    // Given: No format flags set (formatOptions is null)
    // When: getFormat() called
    // Then: Returns COMPACT

    // TODO(#367): Implement after #367 provides the implementation
    // Create MessageStreamPrinter instance without setting any format flags
    // Access getFormat() via reflection
    // Assert that the returned format is OutputFormat.COMPACT
    fail("Not yet implemented");
  }

  @Test
  @Ignore("Awaiting implementation in #367")
  public void testGetFormat_returnsFull_whenFullFlagSet() throws Exception {
    // Given: fullOutput=true in FormatOptions
    // When: getFormat() called
    // Then: Returns FULL

    // TODO(#367): Implement after #367 provides the implementation
    // Create MessageStreamPrinter instance
    // Create FormatOptions instance with full=true
    // Set formatOptions field via reflection
    // Access getFormat() via reflection
    // Assert that the returned format is OutputFormat.FULL
    fail("Not yet implemented");
  }

  @Test
  @Ignore("Awaiting implementation in #367")
  public void testGetFormat_returnsJson_whenJsonFlagSet() throws Exception {
    // Given: jsonOutput=true in FormatOptions
    // When: getFormat() called
    // Then: Returns JSON

    // TODO(#367): Implement after #367 provides the implementation
    // Create MessageStreamPrinter instance
    // Create FormatOptions instance with json=true
    // Set formatOptions field via reflection
    // Access getFormat() via reflection
    // Assert that the returned format is OutputFormat.JSON
    fail("Not yet implemented");
  }

  @Test
  @Ignore("Awaiting implementation in #367")
  public void testShouldPrint_filtersNullOffset() throws Exception {
    // Given: startOffset set; message with null offset passed to shouldPrint
    // When: shouldPrint() called with null recOffset
    // Then: Returns false (message filtered out because offset doesn't match)

    // TODO(#367): Implement after #367 provides the implementation
    // Create MessageStreamPrinter instance
    // Set the offset field to a non-null value (e.g., 10L) via reflection
    // Create a LogMessage
    // Call shouldPrint(null, key, logMessage) via reflection
    // Assert that the method returns false since null != 10L
    fail("Not yet implemented");
  }

  @Test
  @Ignore("Awaiting implementation in #367")
  public void testShouldPrint_combinesMultipleFilters() throws Exception {
    // Given: Type filter AND peer filter both set
    // When: shouldPrint() called with message matching both filters
    // Then: Returns true

    // TODO(#367): Implement after #367 provides the implementation
    // Create MessageStreamPrinter instance
    // Set msgTypes field to List.of("CONSTRUCTOR") via reflection
    // Set fromPeer field to a specific UUID string via reflection
    // Create a LogMessage with matching type (EXEC_CONSTRUCTOR) and peer UUID
    // Call shouldPrint(offset, peerUuid, logMessage) via reflection
    // Assert that the method returns true
    fail("Not yet implemented");
  }

  @Test
  @Ignore("Awaiting implementation in #367")
  public void testShouldPrint_rejectsMismatchedFilter() throws Exception {
    // Given: Type filter set to CONSTRUCTOR
    // When: shouldPrint() called with message of type INSTANCE_METHOD
    // Then: Returns false

    // TODO(#367): Implement after #367 provides the implementation
    // Create MessageStreamPrinter instance
    // Set msgTypes field to List.of("CONSTRUCTOR") via reflection
    // Create a LogMessage with type EXEC_INSTANCE_METHOD
    // Call shouldPrint(offset, key, logMessage) via reflection
    // Assert that the method returns false
    fail("Not yet implemented");
  }

  @Test
  @Ignore("Awaiting implementation in #367")
  public void testPrintRecord_handlesFullFormat() throws Exception {
    // Given: OutputFormat.FULL configured
    // When: printRecord() called
    // Then: Outputs detailed multi-line format with CONTEXT, HEADERS, and JSON content

    // TODO(#367): Implement after #367 provides the implementation
    // Create MessageStreamPrinter instance
    // Set formatOptions.full = true via reflection
    // Set up ByteArrayOutputStream to capture output
    // Set out field to PrintStream wrapping the ByteArrayOutputStream
    // Create a LogMessage with test content
    // Call printRecord(key, logMessage, offset) via reflection
    // Assert output contains "CONTEXT:", "HEADERS:", and message content in JSON
    fail("Not yet implemented");
  }

  @Test
  @Ignore("Awaiting implementation in #367")
  public void testPrintRecord_handlesCompactFormat() throws Exception {
    // Given: OutputFormat.COMPACT configured (default or explicit)
    // When: printRecord() called
    // Then: Outputs single-line summary with offset=, id=, message=

    // TODO(#367): Implement after #367 provides the implementation
    // Create MessageStreamPrinter instance
    // Set formatOptions.compact = true via reflection (or leave formatOptions null for default)
    // Set up ByteArrayOutputStream to capture output
    // Set out field to PrintStream wrapping the ByteArrayOutputStream
    // Create a LogMessage with test content
    // Call printRecord(key, logMessage, offset) via reflection
    // Assert output is single line containing "offset=", "id=", "message="
    fail("Not yet implemented");
  }
}
