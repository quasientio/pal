package com.ittera.cometa.concentrator;

import java.util.List;
import java.util.ArrayList;

import org.apache.commons.lang3.ClassUtils;

import org.junit.Test;

import static org.junit.Assert.*;

public class DummyTests {

  @Test
  public void someTest() {
    Object obj = new Integer(3);
    assertTrue(ClassUtils.isPrimitiveOrWrapper(obj.getClass()));
    obj = new Integer[]{1, 2, 3};
    assertTrue(ClassUtils.isPrimitiveOrWrapper(obj.getClass().getComponentType()));
    assertTrue(obj.getClass().isArray());
  }
}
