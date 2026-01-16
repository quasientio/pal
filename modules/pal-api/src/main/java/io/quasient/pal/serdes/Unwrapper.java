/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.serdes;

import com.fasterxml.jackson.core.type.TypeReference;
import io.quasient.pal.common.util.Classes;
import io.quasient.pal.messages.colfer.Obj;
import io.quasient.pal.serdes.colfer.JsonUtil;
import io.quasient.pal.serdes.colfer.ObjUnwrappableAdapter;
import java.util.ArrayList;
import java.util.HashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Utility class providing static methods for unwrapping serialized (i.e. wrapped) objects back into
 * their original Java types.
 */
public class Unwrapper {

  /** Logger instance for logging within the Unwrapper class. */
  private static final Logger logger = LoggerFactory.getLogger(Unwrapper.class);

  /** Private constructor to prevent instantiation of the Unwrapper utility class. */
  private Unwrapper() {}

  /**
   * Unwraps the given wrapped object into an instance of the specified class. This method reverses
   * the wrapping performed by Wrapper.getWrappedObject().
   *
   * @param wrappedObject the object to unwrap
   * @param clazz the target class to which the object should be unwrapped
   * @return the unwrapped object
   * @throws UnsupportedOperationException if attempting to unwrap a reference-only object
   * @throws IllegalArgumentException if JSON value is missing or type information is unavailable
   * @throws RuntimeException if an error occurs during deserialization
   */
  public static Object unwrapObject(Unwrappable wrappedObject, Class<?> clazz) {
    if (logger.isTraceEnabled()) {
      logger.trace("in with unwrappable:\n{}, clazz:\n{}", wrappedObject.asString(), clazz);
    }

    if (wrappedObject.isNull()) {
      return null;
    }

    // if there's a ref but empty value => reference-only scenario
    Integer ref = wrappedObject.getRef();
    String valueAsJson = wrappedObject.getValue();
    if (ref != null && (valueAsJson == null || valueAsJson.isEmpty())) {
      throw new UnsupportedOperationException("Cannot unwrap reference-only object");
    }

    // we do have actual JSON in 'value'
    if (valueAsJson == null || valueAsJson.isEmpty()) {
      // might be a corner case with inconsistent data
      throw new IllegalArgumentException("No JSON value found but object is not null");
    }

    if (clazz == null) {
      String typeName = wrappedObject.getType();
      if (typeName == null || typeName.isEmpty()) {
        throw new IllegalArgumentException("No type info available for non-null object");
      }
      try {
        clazz = Class.forName(typeName);
      } catch (ClassNotFoundException e) {
        throw new IllegalArgumentException("Cannot find class for: " + typeName, e);
      }
    }

    // specialized list/map logic
    String className = clazz.getName(); // e.g. "java.util.ArrayList"
    try {
      if ("java.util.ArrayList".equals(className)) {
        // parse as ArrayList of ? (raw type)
        return JsonUtil.MAPPER.readValue(valueAsJson, new TypeReference<ArrayList<?>>() {});
      } else if ("java.util.HashMap".equals(className)) { // parse as HashMap of ? to ?
        return JsonUtil.MAPPER.readValue(valueAsJson, new TypeReference<HashMap<?, ?>>() {});
      }

      // we can add more specialized checks for LinkedList, TreeMap, etc.

      // Otherwise, parse with the given clazz
      return JsonUtil.MAPPER.readValue(valueAsJson, clazz);

    } catch (Exception e) {
      throw new RuntimeException("Error deserializing JSON into: " + className, e);
    }
  }

  /**
   * Unwraps the given Obj instance into a corresponding Java object. This method infers the target
   * class from the Obj type information.
   *
   * @param object the Obj instance to unwrap
   * @return the unwrapped Java object
   * @throws ClassNotFoundException if the class specified in the Obj type cannot be found
   */
  public static Object unwrapObject(Obj object) throws ClassNotFoundException {
    return unwrapObject(new ObjUnwrappableAdapter(object));
  }

  /**
   * Unwraps the given Obj instance into an object of the specified class.
   *
   * @param object the Obj instance to unwrap
   * @param clazz the target class to which the object should be unwrapped
   * @return the unwrapped object
   */
  public static Object unwrapObject(Obj object, Class<?> clazz) {
    return unwrapObject(new ObjUnwrappableAdapter(object), clazz);
  }

  /**
   * Unwraps the given Unwrappable object into a corresponding Java object. Infers the target class
   * from the object's type information.
   *
   * @param wrappedObject the Unwrappable object to unwrap
   * @return the unwrapped Java object
   * @throws ClassNotFoundException if the class specified in the object's type information cannot
   *     be found
   */
  public static Object unwrapObject(Unwrappable wrappedObject) throws ClassNotFoundException {
    // Check for null before trying to resolve the type - null values don't have type information
    if (wrappedObject.isNull()) {
      return null;
    }

    String className = wrappedObject.getType();
    Class<?> clazz = Classes.getClassForPrimitive(className);
    if (clazz == null) {
      clazz = Class.forName(className, true, Thread.currentThread().getContextClassLoader());
    }

    return unwrapObject(wrappedObject, clazz);
  }
}
