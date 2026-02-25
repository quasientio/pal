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

import io.quasient.pal.messages.types.MessageType;

/**
 * Classifies each EXEC {@link MessageType} as either an {@code OPERATION} (a request that initiates
 * work) or a {@code COMPLETION} (a response that signals work is done).
 *
 * <p>The seven EXEC types that initiate work (constructor, method call, field get/put) are
 * classified as {@link #OPERATION}. The four EXEC types that signal completion (return value,
 * throwable, put-done) are classified as {@link #COMPLETION}.
 */
public enum WalEntryKind {

  /**
   * An operation that initiates work: constructor invocation, method call, or field access
   * (get/put).
   */
  OPERATION,

  /**
   * A completion that signals work is done: return value, thrown exception, or field put
   * acknowledgement.
   */
  COMPLETION;

  /**
   * Maps a {@link MessageType} to the corresponding {@link WalEntryKind}.
   *
   * @param messageType the EXEC message type to classify
   * @return {@link #OPERATION} for the seven operation types, {@link #COMPLETION} for the four
   *     completion types
   * @throws IllegalArgumentException if the message type is not an EXEC family type
   */
  public static WalEntryKind fromMessageType(MessageType messageType) {
    return switch (messageType) {
      case EXEC_CONSTRUCTOR,
              EXEC_INSTANCE_METHOD,
              EXEC_CLASS_METHOD,
              EXEC_GET_STATIC,
              EXEC_GET_FIELD,
              EXEC_PUT_STATIC,
              EXEC_PUT_FIELD ->
          OPERATION;
      case EXEC_PUT_STATIC_DONE, EXEC_PUT_FIELD_DONE, EXEC_THROWABLE, EXEC_RETURN_VALUE ->
          COMPLETION;
      default ->
          throw new IllegalArgumentException(
              String.format("Non-EXEC MessageType: %s", messageType));
    };
  }
}
