/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.messages;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import io.quasient.pal.messages.colfer.Message;

/**
 * Defines the contract for objects that can be marshaled into a byte buffer and deserialized from a
 * JSON representation.
 *
 * <p>This interface is implemented by all Colfer-generated messages, such as {@link Message} and
 * all classes in its hierarchy (eg: ExecMessage, MetaMessage, ControlMessage, etc.)
 */
public interface Marshallable {

  /**
   * Constructs a Marshallable instance from the specified JSON object.
   *
   * @param json the JSON object containing the data to deserialize
   * @return a Marshallable instance represented by the provided JSON
   * @throws JsonParseException if the JSON is invalid or cannot be parsed into a Marshallable
   *     instance
   */
  Marshallable fromJson(JsonObject json) throws JsonParseException;

  /**
   * Serializes the object into the given byte buffer starting at the specified offset.
   *
   * @param buf the byte array buffer where the object should be serialized
   * @param offset the starting index in the buffer for serialization
   * @return the number of bytes written to the buffer
   */
  int marshal(byte[] buf, int offset);

  /**
   * Determines the number of bytes required to serialize the object.
   *
   * @return the required byte size for marshaling the object
   */
  int marshalFit();
}
