/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.common.replay;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import io.quasient.pal.messages.types.MessageType;
import org.junit.Test;

/**
 * Unit tests for {@code WalEntryKind} — the enum that classifies each EXEC {@code MessageType} as
 * either {@code OPERATION} (request) or {@code COMPLETION} (response/done).
 *
 * <p>Each test verifies a single {@code MessageType} → {@code WalEntryKind} mapping. Operations are
 * the seven EXEC types that initiate work (constructor, method call, field get/put). Completions
 * are the four EXEC types that signal work is done (return value, throwable, put-done).
 */
public class WalEntryKindTest {

  /** Verifies that EXEC_CONSTRUCTOR maps to OPERATION. */
  @Test
  public void classifiesConstructorAsOperation() {
    assertThat(
        WalEntryKind.fromMessageType(MessageType.EXEC_CONSTRUCTOR), is(WalEntryKind.OPERATION));
  }

  /** Verifies that EXEC_INSTANCE_METHOD maps to OPERATION. */
  @Test
  public void classifiesInstanceMethodAsOperation() {
    assertThat(
        WalEntryKind.fromMessageType(MessageType.EXEC_INSTANCE_METHOD), is(WalEntryKind.OPERATION));
  }

  /** Verifies that EXEC_CLASS_METHOD maps to OPERATION. */
  @Test
  public void classifiesClassMethodAsOperation() {
    assertThat(
        WalEntryKind.fromMessageType(MessageType.EXEC_CLASS_METHOD), is(WalEntryKind.OPERATION));
  }

  /** Verifies that EXEC_GET_STATIC maps to OPERATION. */
  @Test
  public void classifiesGetStaticAsOperation() {
    assertThat(
        WalEntryKind.fromMessageType(MessageType.EXEC_GET_STATIC), is(WalEntryKind.OPERATION));
  }

  /** Verifies that EXEC_GET_FIELD maps to OPERATION. */
  @Test
  public void classifiesGetFieldAsOperation() {
    assertThat(
        WalEntryKind.fromMessageType(MessageType.EXEC_GET_FIELD), is(WalEntryKind.OPERATION));
  }

  /** Verifies that EXEC_PUT_STATIC maps to OPERATION. */
  @Test
  public void classifiesPutStaticAsOperation() {
    assertThat(
        WalEntryKind.fromMessageType(MessageType.EXEC_PUT_STATIC), is(WalEntryKind.OPERATION));
  }

  /** Verifies that EXEC_PUT_FIELD maps to OPERATION. */
  @Test
  public void classifiesPutFieldAsOperation() {
    assertThat(
        WalEntryKind.fromMessageType(MessageType.EXEC_PUT_FIELD), is(WalEntryKind.OPERATION));
  }

  /** Verifies that EXEC_PUT_STATIC_DONE maps to COMPLETION. */
  @Test
  public void classifiesPutStaticDoneAsCompletion() {
    assertThat(
        WalEntryKind.fromMessageType(MessageType.EXEC_PUT_STATIC_DONE),
        is(WalEntryKind.COMPLETION));
  }

  /** Verifies that EXEC_PUT_FIELD_DONE maps to COMPLETION. */
  @Test
  public void classifiesPutFieldDoneAsCompletion() {
    assertThat(
        WalEntryKind.fromMessageType(MessageType.EXEC_PUT_FIELD_DONE), is(WalEntryKind.COMPLETION));
  }

  /** Verifies that EXEC_THROWABLE maps to COMPLETION. */
  @Test
  public void classifiesThrowableAsCompletion() {
    assertThat(
        WalEntryKind.fromMessageType(MessageType.EXEC_THROWABLE), is(WalEntryKind.COMPLETION));
  }

  /** Verifies that EXEC_RETURN_VALUE maps to COMPLETION. */
  @Test
  public void classifiesReturnValueAsCompletion() {
    assertThat(
        WalEntryKind.fromMessageType(MessageType.EXEC_RETURN_VALUE), is(WalEntryKind.COMPLETION));
  }

  /** Verifies that non-EXEC MessageTypes throw IllegalArgumentException. */
  @Test(expected = IllegalArgumentException.class)
  public void throwsForNonExecType() {
    WalEntryKind.fromMessageType(MessageType.CONTROL_MESSAGE_REQUEST);
  }
}
