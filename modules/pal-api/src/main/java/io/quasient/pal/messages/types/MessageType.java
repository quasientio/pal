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
package io.quasient.pal.messages.types;

/**
 * Represents the different types of messages used within the PAL runtime.
 *
 * <p>Each message type is categorized by a {@link MessageFamily} and identified by a unique byte
 * ID.
 */
public enum MessageType {
  /** Represents a constructor execution message. */
  EXEC_CONSTRUCTOR(MessageFamily.EXEC, (byte) 1),

  /** Represents an instance method execution message. */
  EXEC_INSTANCE_METHOD(MessageFamily.EXEC, (byte) 2),

  /** Represents a class method execution message. */
  EXEC_CLASS_METHOD(MessageFamily.EXEC, (byte) 3),

  /** Represents a request to get/read a static value. */
  EXEC_GET_STATIC(MessageFamily.EXEC, (byte) 4),

  /** Represents a request to get/read a field value. */
  EXEC_GET_FIELD(MessageFamily.EXEC, (byte) 5),

  /** Represents a request to put/set a static value. */
  EXEC_PUT_STATIC(MessageFamily.EXEC, (byte) 6),

  /** Represents a request to put/set a field value. */
  EXEC_PUT_FIELD(MessageFamily.EXEC, (byte) 7),

  /** Represents the completion of a static put operation. */
  EXEC_PUT_STATIC_DONE(MessageFamily.EXEC, (byte) 8),

  /** Represents the completion of a field put operation. */
  EXEC_PUT_FIELD_DONE(MessageFamily.EXEC, (byte) 9),

  /** Represents a throwable execution message. */
  EXEC_THROWABLE(MessageFamily.EXEC, (byte) 10),

  /** Represents a return value execution message. */
  EXEC_RETURN_VALUE(MessageFamily.EXEC, (byte) 11),

  /** Represents a control message request. */
  CONTROL_MESSAGE_REQUEST(MessageFamily.CONTROL, (byte) 31),

  /** Represents a control message response. */
  CONTROL_MESSAGE_RESPONSE(MessageFamily.CONTROL, (byte) 32),

  /** Represents an intercept message. */
  INTERCEPT_MESSAGE(MessageFamily.INTERCEPT, (byte) 51),

  /** Represents an intercept key message. */
  INTERCEPT_KEY(MessageFamily.INTERCEPT, (byte) 52),

  /** Represents an intercept response message. */
  INTERCEPT_RESPONSE(MessageFamily.INTERCEPT, (byte) 53),

  /** Represents an intercept callback request message. */
  INTERCEPT_CALLBACK_REQUEST(MessageFamily.INTERCEPT, (byte) 54),

  /** Represents an intercept callback response message. */
  INTERCEPT_CALLBACK_RESPONSE(MessageFamily.INTERCEPT, (byte) 55),

  /** Represents a meta message request. */
  META_MESSAGE_REQUEST(MessageFamily.META, (byte) 60),

  /** Represents a meta message response. */
  META_MESSAGE_RESPONSE(MessageFamily.META, (byte) 61),

  /** Represents an unknown message type. */
  UNKNOWN(null, (byte) 255);

  /** The family to which this message type belongs. */
  private final MessageFamily family;

  /** The unique identifier for this message type. */
  private final byte id;

  /**
   * Constructs a {@code MessageType} with the specified family and identifier.
   *
   * @param family the {@link MessageFamily} categorizing this message type
   * @param id the byte identifier for this message type
   */
  MessageType(MessageFamily family, byte id) {
    this.family = family;
    this.id = id;
  }

  /**
   * Retrieves the message family categorizing this message type.
   *
   * @return the {@link MessageFamily} of this message type
   */
  public MessageFamily getFamily() {
    return family;
  }

  /**
   * Retrieves the byte identifier for this message type.
   *
   * @return the byte ID of this message type
   */
  public byte getId() {
    return id;
  }

  /** Lookup table for retrieving {@code MessageType} by their byte identifier. */
  private static final MessageType[] LOOKUP = new MessageType[256];

  static {
    for (MessageType type : values()) {
      LOOKUP[type.id & 0xFF] = type;
    }
  }

  /**
   * Retrieves the {@code MessageType} corresponding to the given byte identifier.
   *
   * @param id the byte identifier of the desired message type
   * @return the {@code MessageType} associated with the specified id, or {@code null} if no
   *     matching type is found
   */
  public static MessageType fromId(byte id) {
    MessageType t = LOOKUP[id & 0xFF];
    return t != null ? t : UNKNOWN;
  }
}
