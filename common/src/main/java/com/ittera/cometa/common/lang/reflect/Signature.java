package com.ittera.cometa.common.lang.reflect;

public abstract class Signature {

  private final Class declaringType;
  private final String declaringTypeName;
  private final int modifiers;
  private final String name;

  Signature(Class declaringType, String declaringTypeName, int modifiers, String name) {
    this.declaringType = declaringType;
    this.declaringTypeName = declaringTypeName;
    this.modifiers = modifiers;
    this.name = name;
  }

  public Class getDeclaringType() {
    return declaringType;
  }

  public String getDeclaringTypeName() {
    return declaringTypeName;
  }

  public int getModifiers() {
    return modifiers;
  }

  public String getName() {
    return name;
  }
}
