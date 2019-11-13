package com.ittera.cometa.common.lang.intercept;

public abstract class Interceptable {

  public enum InterceptableType {
    METHOD_CALL,
    FIELD_OP;

    public static final InterceptableType[] values = InterceptableType.values();
  }

  protected final String name;
  protected final InterceptableType type;

  protected Interceptable(String name, InterceptableType type) {
    this.name = name;
    this.type = type;
  }

  public abstract String toSerializedString();

  public String getName() {
    return name;
  }

  public InterceptableType getType() {
    return type;
  }
}
