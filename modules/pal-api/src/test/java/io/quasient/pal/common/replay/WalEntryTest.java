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

import static org.junit.Assert.fail;

import org.junit.Ignore;
import org.junit.Test;

/**
 * Unit tests for {@code WalEntry} — the model that wraps a deserialized {@code ExecMessage} with
 * indexed metadata (offset, messageType, threadName, builderSeq, className, executableName,
 * paramTypes, objectRef, kind).
 *
 * <p>Each test constructs a synthetic {@code ExecMessage} via {@code MessageBuilder}, passes it to
 * {@code WalEntry.fromExecMessage(long, ExecMessage)}, and verifies the extracted fields.
 */
public class WalEntryTest {

  /**
   * Verifies that an instance method ExecMessage is correctly parsed into a WalEntry with all
   * fields extracted.
   */
  @Test
  @Ignore("Awaiting implementation in #799")
  public void fromExecMessage_instanceMethod() {
    // Given: An ExecMessage with instanceMethodCall set
    //        (threadName='self-caller', builderSeq=42)
    // When: WalEntry.fromExecMessage(0L, msg) is called
    // Then: offset=0, kind=OPERATION, className and executableName extracted correctly
    //       via ExecMessageUtils, threadName='self-caller', builderSeq=42

    // TODO(#799): Implement test logic
    fail("Not yet implemented");
  }

  /** Verifies that a return value ExecMessage is classified as COMPLETION with correct offset. */
  @Test
  @Ignore("Awaiting implementation in #799")
  public void fromExecMessage_returnValue() {
    // Given: An ExecMessage with returnValue set
    // When: WalEntry.fromExecMessage(5L, msg) is called
    // Then: offset=5, kind=COMPLETION, rawMessage preserved

    // TODO(#799): Implement test logic
    fail("Not yet implemented");
  }

  /** Verifies that a constructor ExecMessage yields kind=OPERATION and executableName='new'. */
  @Test
  @Ignore("Awaiting implementation in #799")
  public void fromExecMessage_constructor() {
    // Given: An ExecMessage with constructorCall set
    // When: WalEntry.fromExecMessage() is called
    // Then: kind=OPERATION, executableName='new'

    // TODO(#799): Implement test logic
    fail("Not yet implemented");
  }

  /** Verifies that a throwable ExecMessage is classified as COMPLETION. */
  @Test
  @Ignore("Awaiting implementation in #799")
  public void fromExecMessage_throwable() {
    // Given: An ExecMessage with raisedThrowable set
    // When: WalEntry.fromExecMessage() is called
    // Then: kind=COMPLETION

    // TODO(#799): Implement test logic
    fail("Not yet implemented");
  }

  /** Verifies that a putStaticDone ExecMessage is classified as COMPLETION. */
  @Test
  @Ignore("Awaiting implementation in #799")
  public void fromExecMessage_putStaticDone() {
    // Given: An ExecMessage with staticFieldPutDone set
    // When: WalEntry.fromExecMessage() is called
    // Then: kind=COMPLETION

    // TODO(#799): Implement test logic
    fail("Not yet implemented");
  }
}
