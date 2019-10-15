package com.ittera.cometa.messages.protobuf;

import static java.util.stream.Collectors.toList;
import static org.junit.Assert.*;

import com.ittera.cometa.messages.protobuf.data.Primitives;
import java.util.List;
import org.junit.Test;

public class UnwrapperTest {

  @Test
  public void unwrapObject_wrappedNullObject_originalValue() throws ClassNotFoundException {

    Float wrappable = null;
    Primitives.Object wrappedObj = Wrapper.getWrappedObject(wrappable, "java.lang.Float", null);

    Object unwrapped = Unwrapper.unwrapObject(wrappedObj);
    assertEquals(wrappable, unwrapped);
  }

  @Test
  public void unwrapObject_wrappedVoidObject_originalValue() throws ClassNotFoundException {

    Object wrappable = void.class;
    Primitives.Object wrappedObj = Wrapper.getWrappedObject(wrappable, wrappable.getClass(), null);

    Object unwrapped = Unwrapper.unwrapObject(wrappedObj);
    assertEquals(void.class, unwrapped);
  }

  @Test
  public void unwrapObject_wrappedVoidClass_originalValue() throws ClassNotFoundException {

    Object wrappable = Void.class;
    Primitives.Object wrappedObj = Wrapper.getWrappedObject(wrappable, wrappable.getClass(), null);

    Object unwrapped = Unwrapper.unwrapObject(wrappedObj);
    assertEquals(void.class, unwrapped);
  }

  @Test
  public void unwrapObject_stringBuilder_originalValue() throws ClassNotFoundException {

    StringBuilder wrappable = new StringBuilder("world");
    Primitives.Object wrappedObj = Wrapper.getWrappedObject(wrappable, wrappable.getClass(), null);
    Object unwrapped = Unwrapper.unwrapObject(wrappedObj);

    // compare class and value
    assertEquals(wrappable.getClass(), unwrapped.getClass());
    assertEquals(wrappable.toString(), unwrapped.toString());
  }

  @Test
  public void unwrapObject_stringBuffer_originalValue() throws ClassNotFoundException {

    StringBuffer wrappable = new StringBuffer("world");
    Primitives.Object wrappedObj = Wrapper.getWrappedObject(wrappable, wrappable.getClass(), null);
    Object unwrapped = Unwrapper.unwrapObject(wrappedObj);

    // compare class and value
    assertEquals(wrappable.getClass(), unwrapped.getClass());
    assertEquals(wrappable.toString(), unwrapped.toString());
  }

  @Test
  public void unwrapObject_valuedWrappable_originalValue() throws ClassNotFoundException {

    // test all wrappables with value except charSeq's
    List<Object> valuedWrappableObjs =
        WrapperTest.wrappableObjects.stream()
            .filter(o -> o != null && o != void.class && o != Void.class)
            .filter(o -> !Wrapper.reconstructableCharSeqClasses.contains(o.getClass()))
            .collect(toList());

    for (Object wrappable : valuedWrappableObjs) {
      Primitives.Object wrappedObj =
          Wrapper.getWrappedObject(wrappable, wrappable.getClass(), null);
      Object unwrapped = Unwrapper.unwrapObject(wrappedObj);
      // compare class and value
      assertEquals(wrappable.getClass(), unwrapped.getClass());
      assertEquals(wrappable, unwrapped);
    }
  }

  // TODO Arrays (include array of null's and void's)
}
