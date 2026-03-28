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
package io.quasient.pal.serdes.jsonrpc;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import java.lang.reflect.Type;

/**
 * Adapter for serializing and deserializing JSON-RPC message IDs.
 *
 * <p>This adapter ensures that message IDs are handled as strings during serialization and
 * deserialization, accepting both string and numeric representations in JSON.
 */
public class JsonRpcMessageIdAdapter implements JsonDeserializer<String>, JsonSerializer<String> {

  /**
   * Deserializes a JSON element into a String representation of the message ID.
   *
   * <p>Expects the JSON element to be a primitive, either a string or a number.
   *
   * @param json the JSON element to deserialize
   * @param typeOfT the type of the object to deserialize to
   * @param context the deserialization context
   * @return the deserialized message ID as a String
   * @throws JsonParseException if the JSON element is not a primitive string or number
   */
  @Override
  public String deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context)
      throws JsonParseException {
    if (!json.isJsonPrimitive()
        || !(json.getAsJsonPrimitive().isString() || json.getAsJsonPrimitive().isNumber())) {
      throw new JsonParseException("id must be a number or string");
    }
    return json.getAsString();
  }

  /**
   * Serializes a String message ID into its JSON representation.
   *
   * @param src the message ID to serialize
   * @param typeOfSrc the type of the source object
   * @param context the serialization context
   * @return a JsonPrimitive representing the message ID as a string
   */
  @Override
  public JsonElement serialize(String src, Type typeOfSrc, JsonSerializationContext context) {
    return new JsonPrimitive(src);
  }
}
