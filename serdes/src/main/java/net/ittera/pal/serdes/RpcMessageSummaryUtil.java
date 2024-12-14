package net.ittera.pal.serdes;

public abstract class RpcMessageSummaryUtil {

  // Helper method to get the short class name
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

  // Helper method to get the object/ref representation
  protected static String getObjRepr(Boolean isNull, String value, String objectRef) {
    if (isNull != null && isNull) {
      return "=NULL";
    }

    String repr = "";
    if (objectRef != null && !objectRef.isEmpty()) {
      repr = "@" + objectRef;
    }
    if (value != null && !value.isEmpty()) {
      repr += "(=" + value + ")";
    }
    return repr;
  }
}
