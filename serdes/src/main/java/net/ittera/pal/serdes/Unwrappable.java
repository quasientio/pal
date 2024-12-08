package net.ittera.pal.serdes;

import java.util.Arrays;

public interface Unwrappable {
  boolean isNull();

  String getValue();

  String getType();

  Unwrappable[] getArrayValues();

  Integer getRef();

  default String asString() {
    return "Unwrappable{"
        + "isNull="
        + isNull()
        + ", value='"
        + getValue()
        + '\''
        + ", type='"
        + getType()
        + '\''
        + ", arrayValues="
        + Arrays.toString(getArrayValues())
        + ", ref="
        + getRef()
        + '}';
  }
}
