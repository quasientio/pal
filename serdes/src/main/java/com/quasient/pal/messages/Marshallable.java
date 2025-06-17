/*
   Copyright (c) 2020 Contributors listed in the AUTHORS file

   This file is part of PAL, the friendly java runtime.

   PAL is free software: you can redistribute it and/or modify
   it under the terms of the GNU General Public License as published by
   the Free Software Foundation, either version 3 of the License, or
   (at your option) any later version.

   PAL is distributed in the hope that it will be useful,
   but WITHOUT ANY WARRANTY; without even the implied warranty of
   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
   GNU General Public License for more details.

   You should have received a copy of the GNU General Public License
   along with this program. If not, see <http://www.gnu.org/licenses/>.
*/

package com.quasient.pal.messages;

import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.quasient.pal.messages.colfer.Message;

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
