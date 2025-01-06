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

package net.ittera.pal.serdes;

import com.fasterxml.jackson.core.type.TypeReference;
import java.util.ArrayList;
import java.util.HashMap;
import net.ittera.pal.common.util.Classes;
import net.ittera.pal.messages.colfer.Obj;
import net.ittera.pal.serdes.colfer.JsonUtil;
import net.ittera.pal.serdes.colfer.ObjUnwrappableAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * On dynamically creating arrays with generics: see Rohit Jain answer. <a
 * href="https://stackoverflow.com/questions/18581002/how-to-create-a-generic-array">...</a>
 */
public class Unwrapper {

  private static final Logger logger = LoggerFactory.getLogger(Unwrapper.class);

  private Unwrapper() {}

  /**
   * Returns objects in objectList as Object array with each object typed as its type in classList
   * This method undoes the wrapping of objects done by Wrapper.getWrappedObject()
   *
   * @param wrappedObject the Obj instance to unwrap
   * @param clazz the class of the object to unwrap
   * @return the unwrapped Object
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

  public static Object unwrapObject(Obj object) throws ClassNotFoundException {
    return unwrapObject(new ObjUnwrappableAdapter(object));
  }

  public static Object unwrapObject(Obj object, Class<?> clazz) {
    return unwrapObject(new ObjUnwrappableAdapter(object), clazz);
  }

  public static Object unwrapObject(Unwrappable wrappedObject) throws ClassNotFoundException {
    String className = wrappedObject.getType();
    Class<?> clazz = Classes.getClassForPrimitive(className);
    if (clazz == null) {
      clazz = Class.forName(className, true, Thread.currentThread().getContextClassLoader());
    }

    return unwrapObject(wrappedObject, clazz);
  }
}
