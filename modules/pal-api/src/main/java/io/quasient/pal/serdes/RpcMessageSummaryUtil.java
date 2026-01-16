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

/**
 * Provides utility methods for summarizing RPC (Remote Procedure Call) messages. This abstract
 * class contains helper functions to format class names and object representations used in RPC
 * message summaries.
 */
public abstract class RpcMessageSummaryUtil {

  /**
   * Retrieves the short class name from a fully qualified class name. Handles array type prefixes
   * if present.
   *
   * @param className the fully qualified name of the class
   * @return the short class name without the package prefix
   */
  protected static String shortClassname(String className) {
    if (className.contains(".")) {
      String prefix = "";
      if (className.startsWith("[L")) {
        prefix = "[L";
      } else if (className.startsWith("[")) {
        prefix = "[";
      }
      return prefix + className.substring(className.lastIndexOf('.') + 1);
    } else {
      return className;
    }
  }

  /**
   * Constructs a string representation of an object reference. If the object is null, returns
   * "=NULL". Otherwise, includes the object reference and its value.
   *
   * @param isNull indicates whether the object is null
   * @param value the string representation of the object's value
   * @param objectRef the reference identifier of the object
   * @return a formatted string representing the object's state
   */
  protected static String getObjRepr(Boolean isNull, String value, int objectRef) {
    if (isNull != null && isNull) {
      return "=NULL";
    }

    String repr = "";
    if (objectRef != 0) {
      repr = "@" + objectRef;
    }
    if (value != null && !value.isEmpty()) {
      repr += "(=" + value + ")";
    }
    return repr;
  }
}
