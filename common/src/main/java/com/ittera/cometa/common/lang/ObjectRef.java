package com.ittera.cometa.common.lang;

public class ObjectRef {

  private final String ref;

  public ObjectRef(String ref) {
    this.ref = ref;
  }

  public String getRef() {
    return ref;
  }

  public static ObjectRef from(String ref) {
    return new ObjectRef(ref);
  }

  @Override
  public int hashCode() {
    return Integer.parseInt(ref);
  }

  @Override
  public boolean equals(Object obj) {
    if (obj instanceof ObjectRef) {
      return this.hashCode() == obj.hashCode();
    }
    return false;
  }

  @Override
  public String toString() {
    return "objectRef: " + ref;
  }
}
