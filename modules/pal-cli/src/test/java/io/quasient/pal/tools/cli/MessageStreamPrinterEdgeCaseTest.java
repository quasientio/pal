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
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.Test;

/**
 * Edge case tests for MessageStreamPrinter filtering and offset handling.
 *
 * <p>Tests multiple simultaneous filters, conflicting filters, boundary offset values, and filter
 * combinations to ensure robust message filtering behavior.
 */
public class MessageStreamPrinterEdgeCaseTest {

  private static LogMessage<?> logOf(Message m) {
    return new LogMessage<>("topic", 1L, Map.of(), m);
  }

  /**
   * Tests that multiple filters work correctly together (peer + thread + type).
   *
   * @throws Exception if reflection fails
   */
  @Test
  public void shouldPrint_multipleFiltersAllMatch() throws Exception {
    UUID peer = UUID.randomUUID();
    MessageBuilder b = new MessageBuilder(peer, Boolean.toString(false));
    var em = b.buildEmptyConstructor(peer, "java.lang.String");
    var m = b.wrap(em);
    LogMessage<?> lm = logOf(m);

    MessageStreamPrinter p = new MessageStreamPrinter();

    // Set multiple filters that all match
    var fTypes = MessageStreamPrinter.class.getDeclaredField("msgTypes");
    fTypes.setAccessible(true);
    fTypes.set(p, List.of("EXEC_CONSTRUCTOR"));

    var fPeer = MessageStreamPrinter.class.getDeclaredField("fromPeer");
    fPeer.setAccessible(true);
    fPeer.set(p, peer.toString());

    var fThread = MessageStreamPrinter.class.getDeclaredField("threadName");
    fThread.setAccessible(true);
    fThread.set(p, em.getThreadName());

    Method should =
        MessageStreamPrinter.class.getDeclaredMethod(
            "shouldPrint", Long.class, String.class, LogMessage.class);
    should.setAccessible(true);

    boolean ok = (boolean) should.invoke(p, 5L, peer.toString(), lm);
    assertThat("All filters match - should print", ok, is(true));
  }

  /**
   * Tests that when one filter doesn't match, message is not printed.
   *
   * @throws Exception if reflection fails
   */
  @Test
  public void shouldPrint_multipleFiltersOneDoesNotMatch() throws Exception {
    UUID peer = UUID.randomUUID();
    MessageBuilder b = new MessageBuilder(peer, Boolean.toString(false));
    var em = b.buildEmptyConstructor(peer, "java.lang.String");
    var m = b.wrap(em);
    LogMessage<?> lm = logOf(m);

    MessageStreamPrinter p = new MessageStreamPrinter();

    // Set multiple filters where one doesn't match
    var fTypes = MessageStreamPrinter.class.getDeclaredField("msgTypes");
    fTypes.setAccessible(true);
    fTypes.set(p, List.of("EXEC_CONSTRUCTOR"));

    var fPeer = MessageStreamPrinter.class.getDeclaredField("fromPeer");
    fPeer.setAccessible(true);
    fPeer.set(p, UUID.randomUUID().toString()); // Different peer - won't match

    Method should =
        MessageStreamPrinter.class.getDeclaredMethod(
            "shouldPrint", Long.class, String.class, LogMessage.class);
    should.setAccessible(true);

    boolean ok = (boolean) should.invoke(p, 5L, peer.toString(), lm);
    assertThat("One filter doesn't match - should not print", ok, is(false));
  }

  /**
   * Tests filtering with empty filter lists (should match all).
   *
   * @throws Exception if reflection fails
   */
  @Test
  public void shouldPrint_emptyFilterLists() throws Exception {
    UUID peer = UUID.randomUUID();
    MessageBuilder b = new MessageBuilder(peer, Boolean.toString(false));
    var em = b.buildEmptyConstructor(peer, "java.lang.String");
    var m = b.wrap(em);
    LogMessage<?> lm = logOf(m);

    MessageStreamPrinter p = new MessageStreamPrinter();

    // Don't set any filters (or set to empty)
    var fTypes = MessageStreamPrinter.class.getDeclaredField("msgTypes");
    fTypes.setAccessible(true);
    fTypes.set(p, List.of()); // Empty list

    Method should =
        MessageStreamPrinter.class.getDeclaredMethod(
            "shouldPrint", Long.class, String.class, LogMessage.class);
    should.setAccessible(true);

    boolean ok = (boolean) should.invoke(p, 5L, peer.toString(), lm);
    assertThat("Empty filters - should match all", ok, is(true));
  }

  /**
   * Tests filtering with multiple message types in filter list.
   *
   * @throws Exception if reflection fails
   */
  @Test
  public void shouldPrint_multipleTypesInFilter() throws Exception {
    UUID peer = UUID.randomUUID();
    MessageBuilder b = new MessageBuilder(peer, Boolean.toString(false));
    var em = b.buildEmptyConstructor(peer, "java.lang.String");
    var m = b.wrap(em);
    LogMessage<?> lm = logOf(m);

    MessageStreamPrinter p = new MessageStreamPrinter();

    // Set multiple message types in filter
    var fTypes = MessageStreamPrinter.class.getDeclaredField("msgTypes");
    fTypes.setAccessible(true);
    fTypes.set(p, List.of("EXEC_CONSTRUCTOR", "EXEC_INSTANCE_METHOD", "EXEC_CLASS_METHOD"));

    Method should =
        MessageStreamPrinter.class.getDeclaredMethod(
            "shouldPrint", Long.class, String.class, LogMessage.class);
    should.setAccessible(true);

    boolean ok = (boolean) should.invoke(p, 5L, peer.toString(), lm);
    assertThat("Message type in list - should match", ok, is(true));
  }

  /**
   * Tests filtering with message type not in filter list.
   *
   * @throws Exception if reflection fails
   */
  @Test
  public void shouldPrint_typeNotInFilter() throws Exception {
    UUID peer = UUID.randomUUID();
    MessageBuilder b = new MessageBuilder(peer, Boolean.toString(false));
    var em = b.buildEmptyConstructor(peer, "java.lang.String");
    var m = b.wrap(em);
    LogMessage<?> lm = logOf(m);

    MessageStreamPrinter p = new MessageStreamPrinter();

    // Set filter for types that don't match
    var fTypes = MessageStreamPrinter.class.getDeclaredField("msgTypes");
    fTypes.setAccessible(true);
    fTypes.set(p, List.of("EXEC_INSTANCE_METHOD", "EXEC_CLASS_METHOD")); // Not CONSTRUCTOR

    Method should =
        MessageStreamPrinter.class.getDeclaredMethod(
            "shouldPrint", Long.class, String.class, LogMessage.class);
    should.setAccessible(true);

    boolean ok = (boolean) should.invoke(p, 5L, peer.toString(), lm);
    assertThat("Message type not in filter list - should not match", ok, is(false));
  }

  /**
   * Tests filtering with peer UUID that doesn't match.
   *
   * @throws Exception if reflection fails
   */
  @Test
  public void shouldPrint_differentPeerUuid() throws Exception {
    UUID peer = UUID.randomUUID();
    UUID differentPeer = UUID.randomUUID();
    MessageBuilder b = new MessageBuilder(peer, Boolean.toString(false));
    var em = b.buildEmptyConstructor(peer, "java.lang.String");
    var m = b.wrap(em);
    LogMessage<?> lm = logOf(m);

    MessageStreamPrinter p = new MessageStreamPrinter();

    // Filter for different peer
    var fPeer = MessageStreamPrinter.class.getDeclaredField("fromPeer");
    fPeer.setAccessible(true);
    fPeer.set(p, differentPeer.toString());

    Method should =
        MessageStreamPrinter.class.getDeclaredMethod(
            "shouldPrint", Long.class, String.class, LogMessage.class);
    should.setAccessible(true);

    boolean ok = (boolean) should.invoke(p, 5L, peer.toString(), lm);
    assertThat("Different peer UUID - should not match", ok, is(false));
  }

  /**
   * Tests filtering with thread name that doesn't match.
   *
   * @throws Exception if reflection fails
   */
  @Test
  public void shouldPrint_differentThreadName() throws Exception {
    UUID peer = UUID.randomUUID();
    MessageBuilder b = new MessageBuilder(peer, Boolean.toString(false));
    var em = b.buildEmptyConstructor(peer, "java.lang.String");
    var m = b.wrap(em);
    LogMessage<?> lm = logOf(m);

    MessageStreamPrinter p = new MessageStreamPrinter();

    // Filter for different thread name
    var fThread = MessageStreamPrinter.class.getDeclaredField("threadName");
    fThread.setAccessible(true);
    fThread.set(p, "non-existent-thread");

    Method should =
        MessageStreamPrinter.class.getDeclaredMethod(
            "shouldPrint", Long.class, String.class, LogMessage.class);
    should.setAccessible(true);

    boolean ok = (boolean) should.invoke(p, 5L, peer.toString(), lm);
    assertThat("Different thread name - should not match", ok, is(false));
  }

  /**
   * Tests filtering with message ID that doesn't match.
   *
   * @throws Exception if reflection fails
   */
  @Test
  public void shouldPrint_differentMessageId() throws Exception {
    UUID peer = UUID.randomUUID();
    MessageBuilder b = new MessageBuilder(peer, Boolean.toString(false));
    var em = b.buildEmptyConstructor(peer, "java.lang.String");
    var m = b.wrap(em);
    LogMessage<?> lm = logOf(m);

    MessageStreamPrinter p = new MessageStreamPrinter();

    // Filter for different message ID
    var fId = MessageStreamPrinter.class.getDeclaredField("id");
    fId.setAccessible(true);
    fId.set(p, 99999L); // Different ID

    Method should =
        MessageStreamPrinter.class.getDeclaredMethod(
            "shouldPrint", Long.class, String.class, LogMessage.class);
    should.setAccessible(true);

    boolean ok = (boolean) should.invoke(p, 5L, peer.toString(), lm);
    assertThat("Different message ID - should not match", ok, is(false));
  }

  /**
   * Tests that null filter values are handled gracefully (should match all).
   *
   * @throws Exception if reflection fails
   */
  @Test
  public void shouldPrint_nullFilterValues() throws Exception {
    UUID peer = UUID.randomUUID();
    MessageBuilder b = new MessageBuilder(peer, Boolean.toString(false));
    var em = b.buildEmptyConstructor(peer, "java.lang.String");
    var m = b.wrap(em);
    LogMessage<?> lm = logOf(m);

    MessageStreamPrinter p = new MessageStreamPrinter();

    // Set all filters to null (no filtering)
    var fTypes = MessageStreamPrinter.class.getDeclaredField("msgTypes");
    fTypes.setAccessible(true);
    fTypes.set(p, null);

    var fPeer = MessageStreamPrinter.class.getDeclaredField("fromPeer");
    fPeer.setAccessible(true);
    fPeer.set(p, null);

    var fThread = MessageStreamPrinter.class.getDeclaredField("threadName");
    fThread.setAccessible(true);
    fThread.set(p, null);

    var fId = MessageStreamPrinter.class.getDeclaredField("id");
    fId.setAccessible(true);
    fId.set(p, null);

    Method should =
        MessageStreamPrinter.class.getDeclaredMethod(
            "shouldPrint", Long.class, String.class, LogMessage.class);
    should.setAccessible(true);

    boolean ok = (boolean) should.invoke(p, 5L, peer.toString(), lm);
    assertThat("Null filters - should match all", ok, is(true));
  }

  /**
   * Tests filtering with message format filter (BINARY format).
   *
   * @throws Exception if reflection fails
   */
  @Test
  public void shouldPrint_formatFilterBinary() throws Exception {
    UUID peer = UUID.randomUUID();
    MessageBuilder b = new MessageBuilder(peer, Boolean.toString(false));
    var em = b.buildEmptyConstructor(peer, "java.lang.String");
    var m = b.wrap(em);
    LogMessage<?> lm = logOf(m);

    MessageStreamPrinter p = new MessageStreamPrinter();

    // Filter for BINARY format only
    var fFormats = MessageStreamPrinter.class.getDeclaredField("msgFormats");
    fFormats.setAccessible(true);
    fFormats.set(p, List.of("BINARY"));

    Method should =
        MessageStreamPrinter.class.getDeclaredMethod(
            "shouldPrint", Long.class, String.class, LogMessage.class);
    should.setAccessible(true);

    boolean ok = (boolean) should.invoke(p, 5L, peer.toString(), lm);
    // Should match based on format
    assertThat("Format filter applied", ok, is(true));
  }
}
