package net.ittera.pal.common.lang;

public enum FieldOpType {
  GET,
  PUT;

  public static final FieldOpType[] values = values();

  public static FieldOpType fromString(String value) {
    if (value.equalsIgnoreCase("GET")) {
      return GET;
    } else if (value.equalsIgnoreCase("PUT")) {
      return PUT;
    } else {
      throw new IllegalArgumentException("Unknown field op type: " + value);
    }
  }
}
