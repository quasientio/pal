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
import static org.junit.Assert.assertNotNull;

import io.quasient.pal.common.objects.ObjectRef;
import io.quasient.pal.messages.colfer.ExecMessage;
import io.quasient.pal.messages.colfer.Message;
import io.quasient.pal.serdes.colfer.MessageBuilder;
import io.quasient.pal.tools.stats.Counters;
import java.util.UUID;
import org.junit.Test;

/**
 * Tests that {@link LogStats#updateCounters} correctly increments all counter maps for each message
 * type.
 */
public class MessageStreamStatsCountersTest {

  /**
   * Tests that updateCounters correctly increments all counter maps for each message type.
   *
   * @throws Exception if an error occurs
   */
  @Test
  public void updateCounters_increments_all_maps_by_type() throws Exception {
    UUID peerId = UUID.randomUUID();
    MessageBuilder b = new MessageBuilder(peerId, Boolean.toString(false));
    LogStats stats = new LogStats("localhost:9092", "log", null, null, null);

    // Constructor
    ExecMessage ctor = b.buildEmptyConstructor(peerId, "java.lang.String");
    Message mCtor = b.wrap(ctor);
    stats.updateCounters(mCtor);

    Counters c = stats.getCounters();
    assertThat(c.getNumberOfMessages().get(), is(1L));
    assertNotNull(c.getMessagesByType().get("EXEC_CONSTRUCTOR"));
    assertNotNull(c.getMessagesFromPeer().get(peerId.toString()));
    assertNotNull(c.getMessagesByThread().get(ctor.getThreadName()));
    assertNotNull(c.getObjectsCreated().get("java.lang.String"));

    // Instance Method
    ExecMessage im =
        b.buildInstanceMethod(
            peerId,
            "java.util.ArrayList",
            "add",
            ObjectRef.randomRef(),
            new String[] {"int"},
            new Object[] {1});
    Message mIm = b.wrap(im);
    stats.updateCounters(mIm);
    assertNotNull(c.getMethodsCalled().get("ArrayList.add()"));

    // Class Method
    ExecMessage cm =
        b.buildClassMethod(
            peerId,
            "java.util.Collections",
            "emptyList",
            new String[] {},
            this,
            ObjectRef.randomRef(),
            new Object[] {});
    Message mCm = b.wrap(cm);
    stats.updateCounters(mCm);
    assertNotNull(c.getMethodsCalled().get("Collections.emptyList()"));

    // Static Field Get
    ExecMessage sfg = b.buildGetStatic(peerId, "java.lang.System", "out");
    stats.updateCounters(b.wrap(sfg));
    assertNotNull(c.getFieldReads().get("System.out"));

    // Instance Field Get
    ExecMessage ifg = b.buildGetObject(peerId, "java.lang.Thread", "name", ObjectRef.randomRef());
    stats.updateCounters(b.wrap(ifg));
    assertNotNull(c.getFieldReads().get("Thread.name"));

    // Static Field Put
    ExecMessage sfp = b.buildPutStatic(peerId, "java.lang.Integer", "value", ObjectRef.randomRef());
    stats.updateCounters(b.wrap(sfp));
    assertNotNull(c.getFieldWrites().get("Integer.value"));

    // Instance Field Put
    ExecMessage ifp =
        b.buildPutObject(
            peerId, "java.lang.Thread", "priority", ObjectRef.randomRef(), ObjectRef.randomRef());
    stats.updateCounters(b.wrap(ifp));
    assertNotNull(c.getFieldWrites().get("Thread.priority"));

    // Final sanity on total count
    assertThat(c.getNumberOfMessages().get(), is(7L));
  }
}
