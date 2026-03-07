/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.core.rpc.policy;

import io.quasient.pal.messages.types.MessageType;

/**
 * Broad classification of the member being accessed in an RPC operation.
 *
 * <p>Each execution {@link MessageType} maps to exactly one {@code MemberCategory}, enabling policy
 * rules to match on the kind of member (method, constructor, field) independently of the specific
 * message type.
 */
public enum MemberCategory {

  /** An instance method invocation ({@link MessageType#EXEC_INSTANCE_METHOD}). */
  METHOD,

  /** A static (class) method invocation ({@link MessageType#EXEC_CLASS_METHOD}). */
  STATIC_METHOD,

  /** A constructor invocation ({@link MessageType#EXEC_CONSTRUCTOR}). */
  CONSTRUCTOR,

  /**
   * A field or static field read ({@link MessageType#EXEC_GET_FIELD}, {@link
   * MessageType#EXEC_GET_STATIC}).
   */
  FIELD_GET,

  /**
   * A field or static field write ({@link MessageType#EXEC_PUT_FIELD}, {@link
   * MessageType#EXEC_PUT_STATIC}).
   */
  FIELD_SET;

  /**
   * Maps an execution {@link MessageType} to its corresponding {@code MemberCategory}.
   *
   * @param messageType the message type to classify
   * @return the member category for the given message type
   * @throws IllegalArgumentException if the message type does not represent an executable member
   *     operation (e.g. response types like {@link MessageType#EXEC_RETURN_VALUE})
   */
  public static MemberCategory fromMessageType(MessageType messageType) {
    return switch (messageType) {
      case EXEC_CONSTRUCTOR -> CONSTRUCTOR;
      case EXEC_INSTANCE_METHOD -> METHOD;
      case EXEC_CLASS_METHOD -> STATIC_METHOD;
      case EXEC_GET_FIELD, EXEC_GET_STATIC -> FIELD_GET;
      case EXEC_PUT_FIELD, EXEC_PUT_STATIC -> FIELD_SET;
      default ->
          throw new IllegalArgumentException(
              "No MemberCategory mapping for message type: " + messageType);
    };
  }
}
