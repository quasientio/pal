package net.ittera.pal.common.util;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class ClassesTest {

  @Test
  public void getClassForPrimitive() {
    // reminder that <primitive>.class == <Wrapper>.type
    assert boolean.class == Boolean.TYPE;
    assertEquals(boolean.class, Classes.getClassForPrimitive("boolean"));
    assertEquals(Boolean.TYPE, Classes.getClassForPrimitive("boolean"));
    assertEquals(Byte.TYPE, Classes.getClassForPrimitive("byte"));
    assertEquals(Character.TYPE, Classes.getClassForPrimitive("char"));
    assertEquals(Short.TYPE, Classes.getClassForPrimitive("short"));
    assertEquals(Integer.TYPE, Classes.getClassForPrimitive("int"));
    assertEquals(Long.TYPE, Classes.getClassForPrimitive("long"));
    assertEquals(Double.TYPE, Classes.getClassForPrimitive("double"));
    assertEquals(Float.TYPE, Classes.getClassForPrimitive("float"));
    assertEquals(Void.TYPE, Classes.getClassForPrimitive("void"));
  }

  @Test
  public void isPrimitiveWrapper() {
    assertFalse(Classes.isPrimitiveWrapper(boolean.class));
    assertTrue(Classes.isPrimitiveWrapper(Boolean.class));

    assertFalse(Classes.isPrimitiveWrapper(byte.class));
    assertTrue(Classes.isPrimitiveWrapper(Byte.class));

    assertFalse(Classes.isPrimitiveWrapper(char.class));
    assertTrue(Classes.isPrimitiveWrapper(Character.class));

    assertFalse(Classes.isPrimitiveWrapper(short.class));
    assertTrue(Classes.isPrimitiveWrapper(Short.class));

    assertFalse(Classes.isPrimitiveWrapper(int.class));
    assertTrue(Classes.isPrimitiveWrapper(Integer.class));

    assertFalse(Classes.isPrimitiveWrapper(long.class));
    assertTrue(Classes.isPrimitiveWrapper(Long.class));

    assertFalse(Classes.isPrimitiveWrapper(double.class));
    assertTrue(Classes.isPrimitiveWrapper(Double.class));

    assertFalse(Classes.isPrimitiveWrapper(float.class));
    assertTrue(Classes.isPrimitiveWrapper(Float.class));

    assertFalse(Classes.isPrimitiveWrapper(void.class));
    // !! Void.class is a pseudo type, and it's not considered a Wrapper !!
    assertFalse(Classes.isPrimitiveWrapper(Void.class));

    // misc stuff that shouldn't be
    assertFalse(Classes.isPrimitiveWrapper(String.class));
    assertFalse(Classes.isPrimitiveWrapper(Object.class));
    assertFalse(Classes.isPrimitiveWrapper(Enum.class));
    assertFalse(Classes.isPrimitiveWrapper(Throwable.class));
  }

  @Test
  public void isPrimitiveOrWrapper() {
    assertTrue(Classes.isPrimitiveOrWrapper(boolean.class)); // == Boolean.TYPE
    assertTrue(Classes.isPrimitiveOrWrapper(Boolean.class));

    assertTrue(Classes.isPrimitiveOrWrapper(byte.class));
    assertTrue(Classes.isPrimitiveOrWrapper(Byte.class));

    assertTrue(Classes.isPrimitiveOrWrapper(char.class));
    assertTrue(Classes.isPrimitiveOrWrapper(Character.class));

    assertTrue(Classes.isPrimitiveOrWrapper(short.class));
    assertTrue(Classes.isPrimitiveOrWrapper(Short.class));

    assertTrue(Classes.isPrimitiveOrWrapper(int.class));
    assertTrue(Classes.isPrimitiveOrWrapper(Integer.class));

    assertTrue(Classes.isPrimitiveOrWrapper(long.class));
    assertTrue(Classes.isPrimitiveOrWrapper(Long.class));

    assertTrue(Classes.isPrimitiveOrWrapper(double.class));
    assertTrue(Classes.isPrimitiveOrWrapper(Double.class));

    assertTrue(Classes.isPrimitiveOrWrapper(float.class));
    assertTrue(Classes.isPrimitiveOrWrapper(Float.class));

    assertTrue(Classes.isPrimitiveOrWrapper(void.class));
    // !! Void.class is a pseudo type, and it's not considered a Wrapper !!
    assertFalse(Classes.isPrimitiveOrWrapper(Void.class));

    // misc stuff that shouldn't be
    assertFalse(Classes.isPrimitiveWrapper(String.class));
    assertFalse(Classes.isPrimitiveWrapper(Object.class));
    assertFalse(Classes.isPrimitiveWrapper(Enum.class));
    assertFalse(Classes.isPrimitiveWrapper(Throwable.class));
  }
}
