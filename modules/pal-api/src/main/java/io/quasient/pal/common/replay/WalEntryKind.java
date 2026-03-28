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
