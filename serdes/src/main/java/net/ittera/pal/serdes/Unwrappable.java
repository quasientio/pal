package net.ittera.pal.serdes;

public interface Unwrappable {
  boolean isNull();

  String getValue();

  String getType();

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
        + ", ref="
        + getRef()
        + '}';
  }
}
