/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.serdes.colfer;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Utility class for JSON serialization and deserialization using Jackson's {@link ObjectMapper}.
 * Provides methods to convert objects to JSON strings and vice versa.
 */
public final class JsonUtil {

  /** Singleton {@link ObjectMapper} instance used for JSON serialization and deserialization. */
  public static final ObjectMapper MAPPER = new ObjectMapper();

  /** Private constructor to prevent instantiation of this utility class. */
  private JsonUtil() {}

  /**
   * Serializes the given object to its JSON string representation.
   *
   * @param obj the object to serialize
   * @return a JSON string representation of the provided object
   * @throws RuntimeException if an error occurs during serialization
   */
  public static String toJson(Object obj) {
    try {
      return MAPPER.writeValueAsString(obj);
    } catch (Exception e) {
      throw new RuntimeException("Error serializing to JSON", e);
    }
  }

  /**
   * Deserializes the given JSON string into an object of the specified class type.
   *
   * @param json the JSON string to deserialize
   * @param clazz the class of type {@code T} to deserialize into
   * @param <T> the type of the desired object
   * @return an object of type {@code T} deserialized from the JSON string
   * @throws RuntimeException if an error occurs during deserialization
   */
  public static <T> T fromJson(String json, Class<T> clazz) {
    try {
      return MAPPER.readValue(json, clazz);
    } catch (Exception e) {
      throw new RuntimeException("Error deserializing from JSON", e);
    }
  }

  // For cases where the type is not known at compile-time, we might
  // use TypeReference in Jackson or fromJson(String, TypeToken) in Gson.
}
