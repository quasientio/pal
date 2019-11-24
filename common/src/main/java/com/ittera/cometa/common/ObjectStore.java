package com.ittera.cometa.common;

import com.ittera.cometa.common.lang.ObjectRef;

public interface ObjectStore {

  ObjectRef storeObject(Object object);

  Object lookupObject(ObjectRef objectRef);

  boolean containsObjectRef(ObjectRef objectRef);

  void clear();

  long size();

  boolean isEmpty();
}
