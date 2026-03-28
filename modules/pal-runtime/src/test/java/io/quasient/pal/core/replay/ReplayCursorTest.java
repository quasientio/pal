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
package io.quasient.pal.core.replay;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.hamcrest.Matchers.sameInstance;

import io.quasient.pal.common.replay.WalEntry;
import io.quasient.pal.messages.colfer.Class;
import io.quasient.pal.messages.colfer.ExecMessage;
import io.quasient.pal.messages.colfer.InstanceMethodCall;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

/**
 * Unit tests for {@code ReplayCursor} — the thread-local WAL position tracker that the dispatch
 * replay path uses to walk through WAL entries in order.
 *
 * <p>Tests cover cursor navigation (peek, advance, advancePast), exhaustion detection, and thread
 * name accessibility. All tests use synthetic {@code WalEntry} instances with controlled offsets.
 */
public class ReplayCursorTest {

  /** Verifies that peeking returns the first entry without advancing the cursor position. */
  @Test
  public void peekNextReturnsFirstEntry() {
    List<WalEntry> entries = makeEntries("t1", 10, 20, 30);
    ReplayCursor cursor = new ReplayCursor("t1", entries);

    WalEntry peeked = cursor.peekNext();
    assertThat(peeked, is(sameInstance(entries.get(0))));
    assertThat(cursor.getPosition(), is(0));

    WalEntry peekedAgain = cursor.peekNext();
    assertThat(peekedAgain, is(sameInstance(entries.get(0))));
    assertThat(cursor.getPosition(), is(0));
  }

  /** Verifies that advance returns the current entry and moves the cursor forward by one. */
  @Test
  public void advanceMovesForward() {
    List<WalEntry> entries = makeEntries("t1", 10, 20, 30);
    ReplayCursor cursor = new ReplayCursor("t1", entries);

    WalEntry advanced = cursor.advance();
    assertThat(advanced, is(sameInstance(entries.get(0))));
    assertThat(cursor.peekNext(), is(sameInstance(entries.get(1))));
    assertThat(cursor.getPosition(), is(1));
  }

  /** Verifies that multiple advances traverse all entries in order. */
  @Test
  public void advanceMultipleTimes() {
    List<WalEntry> entries = makeEntries("t1", 10, 20, 30);
    ReplayCursor cursor = new ReplayCursor("t1", entries);

    assertThat(cursor.advance(), is(sameInstance(entries.get(0))));
    assertThat(cursor.advance(), is(sameInstance(entries.get(1))));
    assertThat(cursor.advance(), is(sameInstance(entries.get(2))));
  }

  /** Verifies that the cursor reports exhaustion after all entries are consumed. */
  @Test
  public void isExhaustedWhenAllConsumed() {
    List<WalEntry> entries = makeEntries("t1", 10, 20);
    ReplayCursor cursor = new ReplayCursor("t1", entries);

    cursor.advance();
    cursor.advance();
    assertThat(cursor.isExhausted(), is(true));
    assertThat(cursor.peekNext(), is(nullValue()));
  }

  /** Verifies that an empty cursor is immediately exhausted. */
  @Test
  public void isExhaustedOnEmpty() {
    ReplayCursor cursor = new ReplayCursor("t1", Collections.emptyList());
    assertThat(cursor.isExhausted(), is(true));
    assertThat(cursor.peekNext(), is(nullValue()));
    assertThat(cursor.advance(), is(nullValue()));
  }

  /** Verifies that advancePast skips forward to the correct offset. */
  @Test
  public void advancePastSkipsToOffset() {
    List<WalEntry> entries = makeEntries("t1", 10, 20, 30, 40);
    ReplayCursor cursor = new ReplayCursor("t1", entries);

    cursor.advancePast(20);
    assertThat(cursor.peekNext().getOffset(), is(30L));
  }

  /** Verifies that advancePast beyond all entries exhausts the cursor. */
  @Test
  public void advancePastBeyondAllEntries() {
    List<WalEntry> entries = makeEntries("t1", 10, 20);
    ReplayCursor cursor = new ReplayCursor("t1", entries);

    cursor.advancePast(100);
    assertThat(cursor.isExhausted(), is(true));
  }

  /** Verifies that the thread name is accessible via the getter. */
  @Test
  public void threadNameAccessor() {
    ReplayCursor cursor = new ReplayCursor("self-caller", Collections.emptyList());
    assertThat(cursor.getThreadName(), is("self-caller"));
  }

  /**
   * Creates a list of synthetic OPERATION {@link WalEntry} instances with the given offsets.
   *
   * @param threadName the thread name for all entries
   * @param offsets the WAL offsets for each entry
   * @return a mutable list of entries
   */
  private static List<WalEntry> makeEntries(String threadName, long... offsets) {
    List<WalEntry> entries = new ArrayList<>();
    int seq = 0;
    for (long offset : offsets) {
      ExecMessage msg = new ExecMessage();
      msg.setThreadName(threadName);
      msg.setBuilderSeq(seq++);

      InstanceMethodCall imc = new InstanceMethodCall();
      imc.setName("op" + offset);
      Class clazz = new Class();
      clazz.setName("com.example.Test");
      imc.setClazz(clazz);
      msg.setInstanceMethodCall(imc);

      entries.add(WalEntry.fromExecMessage(offset, msg));
    }
    return entries;
  }
}
