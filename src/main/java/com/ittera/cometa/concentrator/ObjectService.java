package com.ittera.cometa.concentrator;

public interface ObjectService {

  String storeObject(Object object);

  Object lookupObject(String objectRef);

  String lookupObjectRef(Object object);

  void clear();

  int size();

  boolean isEmpty();

  boolean containsValue(Object object);

  boolean containsObjectRef(String objectRef);

  Object remove(String objectRef);
}
