/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.serdes.jsonrpc;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.quasient.pal.messages.jsonrpc.JsonRpcMessage;
import com.quasient.pal.messages.jsonrpc.Params;

/**
 * Provides functionality to serialize and deserialize JSON-RPC messages using Gson. It offers
 * methods to convert JsonRpcMessage objects to JSON strings and vice versa, with support for both
 * compact and pretty-printed JSON formats.
 */
public class JsonRpcSerializer {
  /** Gson instance configured with custom deserializers for JSON-RPC message processing. */
  private static final Gson gson;

  /**
   * Gson instance configured with custom deserializers and pretty printing enabled for JSON-RPC
   * message processing.
   */
  private static final Gson prettyGson;

  static {
    // initialize Gson with custom deserializer(s)
    gson = new GsonBuilder().registerTypeAdapter(Params.class, new ParamsDeserializer()).create();
    prettyGson =
        new GsonBuilder()
            .registerTypeAdapter(Params.class, new ParamsDeserializer())
            .setPrettyPrinting()
            .create();
  }

  /**
   * Serializes a JsonRpcMessage object into its JSON string representation.
   *
   * @param message the JsonRpcMessage to serialize
   * @return the JSON string representation of the message
   * @throws JsonSerializationException if serialization fails
   */
  public static String toJson(JsonRpcMessage message) throws JsonSerializationException {
    try {
      return gson.toJson(message);
    } catch (Exception e) {
      throw new JsonSerializationException("Failed to serialize JSON-RPC message", e);
    }
  }

  /**
   * Serializes a JsonRpcMessage object into a pretty-printed JSON string representation.
   *
   * @param message the JsonRpcMessage to serialize
   * @return the pretty-printed JSON string representation of the message
   * @throws JsonSerializationException if serialization fails
   */
  public static String toPrettyJson(JsonRpcMessage message) throws JsonSerializationException {
    try {
      return prettyGson.toJson(message);
    } catch (Exception e) {
      throw new JsonSerializationException("Failed to serialize JSON-RPC message", e);
    }
  }

  /**
   * Deserializes a JSON string into a JsonRpcMessage object of the specified type.
   *
   * @param json the JSON string to deserialize
   * @param clazz the class of the JsonRpcMessage to deserialize into
   * @param <T> the type of JsonRpcMessage
   * @return the deserialized JsonRpcMessage object
   * @throws JsonSerializationException if deserialization fails
   */
  public static <T extends JsonRpcMessage> T fromJson(String json, Class<T> clazz)
      throws JsonSerializationException {
    try {
      return gson.fromJson(json, clazz);
    } catch (Exception e) {
      throw new JsonSerializationException("Failed to deserialize JSON-RPC message", e);
    }
  }
}
