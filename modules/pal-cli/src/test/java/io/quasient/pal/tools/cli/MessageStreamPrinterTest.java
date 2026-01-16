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

import io.quasient.pal.messages.LogMessage;
import io.quasient.pal.messages.colfer.Message;
import io.quasient.pal.serdes.colfer.MessageBuilder;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
}
