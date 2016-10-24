package com.ittera.cometa.concentrator;

import junit.framework.TestCase;

public class SandboxTests extends TestCase {

  public void testParamTypesToString() {
    String[] args = new String[]{"arg1", "arg2"};
    Class[] parameterTypes = new Class[]{String[].class};

    assertEquals(args.getClass().getName(), parameterTypes[0].getName());
  }

  public void testPrimitiveTypes() {
    int anInt = 1;
    Integer anInteger = 2;
    Object[] array = new Object[]{anInt, anInteger};
    for (int i = 0; i < array.length; i++) {
      System.out.println("type of " + array[i] + " : " + array[i].getClass().getName() + " isPrimitive ? -> " + array[i].getClass().isPrimitive());
    }
  }
}
