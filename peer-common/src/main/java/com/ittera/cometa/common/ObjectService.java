package com.ittera.cometa.common;

import com.ittera.cometa.common.lang.ObjectRef;

public interface ObjectService {

  ObjectRef storeObject(Object object);

  Object lookupObject(ObjectRef objectRef);

  ObjectRef lookupObjectRef(Object object);

  void clear();

  int size();

  boolean isEmpty();

  boolean containsValue(Object object);

  boolean containsObjectRef(ObjectRef objectRef);

  Object remove(ObjectRef objectRef);
}
