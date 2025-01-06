package net.ittera.pal.serdes.colfer;

import com.fasterxml.jackson.databind.ObjectMapper;

public final class JsonUtil {
  public static final ObjectMapper MAPPER = new ObjectMapper();

  private JsonUtil() {}

  public static String toJson(Object obj) {
    try {
      return MAPPER.writeValueAsString(obj);
    } catch (Exception e) {
      throw new RuntimeException("Error serializing to JSON", e);
    }
  }

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
