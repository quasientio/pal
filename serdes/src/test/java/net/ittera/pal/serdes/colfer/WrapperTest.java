/*
   Copyright (c) 2020 Contributors listed in the AUTHORS file

   This file is part of PAL, the friendly java runtime.

   PAL is free software: you can redistribute it and/or modify
   it under the terms of the GNU General Public License as published by
   the Free Software Foundation, either version 3 of the License, or
   (at your option) any later version.

   PAL is distributed in the hope that it will be useful,
   but WITHOUT ANY WARRANTY; without even the implied warranty of
   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
   GNU General Public License for more details.

   You should have received a copy of the GNU General Public License
   along with this program. If not, see <http://www.gnu.org/licenses/>.
*/

package net.ittera.pal.serdes.colfer;

import static java.util.stream.Collectors.toList;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

import java.awt.Dimension;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.ittera.pal.common.lang.reflect.FieldSignature;
import net.ittera.pal.common.lang.reflect.Signature;
import net.ittera.pal.common.objects.ObjectRef;
import net.ittera.pal.common.runtime.Context;
import net.ittera.pal.messages.colfer.Obj;
import net.ittera.pal.serdes.NonWrappableObjectException;
import net.ittera.pal.serdes.WrappingTestBase;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Naming convention to use: MethodName_StateUnderTest_ExpectedBehavior */
public class WrapperTest extends WrappingTestBase {

  private static final Logger logger = LoggerFactory.getLogger("tests");
  private static List<Class> allPrimitiveAndLangClasses;

  @BeforeClass
  public static void setupLists() {

    List<Class> javaLangClasses = new ArrayList<>();
    javaLangClasses.addAll(primitiveWrapperClasses);
    javaLangClasses.addAll(nonWrapperJavaLangClasses);

    allPrimitiveAndLangClasses = new ArrayList<>();
    allPrimitiveAndLangClasses.addAll(primitiveClasses);
    allPrimitiveAndLangClasses.addAll(javaLangClasses);
  }

  private static <T> T[] getArrayOf(Class<T> clazz, int size) {
    return (T[]) Array.newInstance(clazz, size);
  }

  // <editor-fold defaultstate="collapsed" desc="isWrappable tests">
  @Test
  public void isWrappable_nonWrappableObject_false() {

    for (Object obj : someNonWrappableObjects) {
      assertFalse(String.format("%s should not be wrappable!", obj), Wrapper.isWrappable(obj));
    }
  }

  @Test
  public void isWrappable_wrappableObject_true() {

    for (Object obj : wrappableObjects) {
      assertTrue(String.format("%s is not wrappable!", obj), Wrapper.isWrappable(obj));
    }
  }

  @Test
  public void isWrappable_oneDimArrayOfNonWrappableObject_false() {

    List<Object[]> nonWrappableArrays =
        someNonWrappableObjects.stream().map(o -> getArrayOf(o.getClass(), 1)).collect(toList());

    for (Object array : nonWrappableArrays) {
      assertFalse(String.format("%s should not be wrappable!", array), Wrapper.isWrappable(array));
    }
  }

  @Test
  public void isWrappable_oneDimArrayOfPrimitive_true() {

    int arraySize = 1;

    for (Class clazz : primitiveClasses) {
      Object primitiveArray = Array.newInstance(clazz, arraySize);
      assertTrue(
          String.format("%s is not wrappable!", primitiveArray),
          Wrapper.isWrappable(primitiveArray));
    }
  }

  @Test
  public void isWrappable_oneDimArrayOfWrapper_true() {

    // create list of 1-dimensional arrays, one for each of primitiveWrapperClasses, with length=1
    List wrapperArrays =
        primitiveWrapperClasses.stream().map(c -> getArrayOf(c, 1)).collect(toList());

    for (Object wrapperArray : wrapperArrays) {
      assertTrue(
          String.format(
              "Array of type %s is not wrappable", wrapperArray.getClass().getComponentType()),
          Wrapper.isWrappable(wrapperArray));
    }
  }

  /** 1-dimensional CharSequence arrays (String, StringBuffer, StringBuilder) */
  @Test
  public void isWrappable_oneDimCharSequenceTypeArray_true() {

    List<CharSequence[]> charSeqArrays = new ArrayList<>();
    charSeqArrays.add(new StringBuffer[10]);
    charSeqArrays.add(new StringBuilder[10]);

    for (CharSequence[] array : charSeqArrays) {
      assertTrue(
          String.format("Array of type %s is not wrappable", array.getClass().getComponentType()),
          Wrapper.isWrappable(array));
    }
  }
  // </editor-fold>

  // <editor-fold defaultstate="collapsed" desc="isWrappableCharSeqClass tests">
  @Test
  public void isWrappableCharSeqClass_charSeqClasses_true() {

    assertTrue(Wrapper.isWrappableCharSeqClass(StringBuffer.class));
    assertTrue(Wrapper.isWrappableCharSeqClass(StringBuilder.class));
  }

  @Test
  public void isWrappableCharSeqClass_nonCharSeqClasses_false() {

    assertFalse(Wrapper.isWrappableCharSeqClass(Integer.class));
    assertFalse(Wrapper.isWrappableCharSeqClass(String.class));
    assertFalse(Wrapper.isWrappableCharSeqClass(Character.class));
  }
  // </editor-fold>

  // <editor-fold defaultstate="collapsed" desc="getWrappedClass tests">

  @Test
  public void getWrappedClass_nullClass_unknownClassNoName() {
    net.ittera.pal.messages.colfer.Class wrappedClass = Wrapper.getWrappedClass((Class) null);
    assertNotNull(wrappedClass);
    assertTrue(wrappedClass.getUnknown());
    assertThat(wrappedClass.getName(), is(emptyString()));
  }

  @Test
  public void getWrappedClass_javaLangOrPrimitiveClass_wrappedOk() {
    net.ittera.pal.messages.colfer.Class wrappedClass;
    for (Class clazz : allPrimitiveAndLangClasses) {
      wrappedClass = Wrapper.getWrappedClass(clazz);

      // neither null nor unknown
      assertNotNull(wrappedClass);
      assertFalse(wrappedClass.getUnknown());

      // name is set and correctly
      assertEquals(clazz.getName(), wrappedClass.getName());
    }
  }

  @Test
  public void getWrappedClass_nullClassName_wrappedOk() {

    net.ittera.pal.messages.colfer.Class wrappedClass;
    wrappedClass = Wrapper.getWrappedClass((String) null);

    assertNotNull(wrappedClass);
    assertTrue(wrappedClass.getUnknown());

    // name is set and correctly
    assert (wrappedClass.getName().isEmpty());
  }

  @Test
  public void getWrappedClass_javaLangOrPrimitiveClassName_wrappedOk() {

    List<String> classNames =
        allPrimitiveAndLangClasses.stream().map(Class::getName).collect(toList());

    net.ittera.pal.messages.colfer.Class wrappedClass;
    for (String classname : classNames) {
      wrappedClass = Wrapper.getWrappedClass(classname);

      // neither null nor unknown
      assertNotNull(wrappedClass);
      assertFalse(wrappedClass.getUnknown());

      // name is set and correctly
      assertEquals(classname, wrappedClass.getName());
    }
  }

  /** Class of all wrappableObjects must be wrappable as well */
  @Test
  public void getWrappedClass_wrappableClass_wrappedOk() {

    List<Class> classes =
        wrappableObjects.stream().filter(Objects::nonNull).map(Object::getClass).collect(toList());

    net.ittera.pal.messages.colfer.Class wrappedClass;
    for (Class clazz : classes) {
      wrappedClass = Wrapper.getWrappedClass(clazz);

      // neither null nor unknown
      assertNotNull(wrappedClass);
      assertFalse(wrappedClass.getUnknown());

      // name is set and correctly
      assertEquals(clazz.getName(), wrappedClass.getName());
    }
  }
  // </editor-fold>

  // <editor-fold defaultstate="collapsed" desc="getWrappedField tests">
  @Test
  public void getWrappedField_fieldAndClass_wrappedOk() {

    Class clazz = Integer.class;
    String fieldName = "height";

    net.ittera.pal.messages.colfer.Field wrappedField = Wrapper.getWrappedField(clazz, fieldName);
    assertNotNull(wrappedField);
    assertEquals(fieldName, wrappedField.getName());
    assertEquals(clazz.getName(), wrappedField.getClazz().getName());
  }

  @Test
  public void wrapField_ValidField_wrappedField() throws NoSuchFieldException {
    Field field = Dimension.class.getDeclaredField("height");
    net.ittera.pal.messages.colfer.Field wrappedField = Wrapper.getWrappedField(field);
    assertNotNull(wrappedField);
    assertEquals(field.getName(), wrappedField.getName());
  }

  @Test
  public void getWrappedField_fieldAndClassName_wrappedOk() {
    String className = Integer.class.getName();
    String fieldName = "height";

    net.ittera.pal.messages.colfer.Field wrappedField =
        Wrapper.getWrappedField(className, fieldName);
    assertNotNull(wrappedField);
    assertEquals(fieldName, wrappedField.getName());
    assertEquals(className, wrappedField.getClazz().getName());
  }
  // </editor-fold>

  // <editor-fold defaultstate="collapsed" desc="getWrappedObject tests">

  @Test
  public void getWrappedObject_NullClass_wrapped() {
    Wrapper.getWrappedObject(new Object(), null, ObjectRef.randomRef());
  }

  @Test(expected = IllegalArgumentException.class)
  public void getWrappedObject_InvalidObjectType_illegalArgumentException() {
    Wrapper.getWrappedObject(new Object(), "238923", ObjectRef.randomRef());
  }

  @Test
  public void getWrappedObject_EmptyArray_wrappedArrayWithZeroLength() {
    Integer[] emptyArray = new Integer[0];
    Obj wrapped = Wrapper.getWrappedObject(emptyArray, emptyArray.getClass().getName(), null);
    assertNotNull(wrapped);
    assertEquals(0, wrapped.getArrayValues().length);
  }

  @Test
  public void getWrappedObject_nonWrappableObj_nonWrappableException() {

    for (Object obj : someNonWrappableObjects) {
      try {
        logger.debug(
            "Calling Wrapper.getWrappedObject with " + obj + " and class " + obj.getClass());
        Wrapper.getWrappedObject(obj, obj.getClass().getName(), null);
        fail("Should have thrown an exception");
      } catch (NonWrappableObjectException ex) {
        // all good
      }
    }
  }

  @Test
  public void getWrappedObject_wrappableValuedObj_wrappedWithValue() {

    // test all wrappable objects except null & void
    List<Object> valuedWrappableObjs =
        wrappableObjects.stream()
            .filter(o -> o != null && o != void.class && o != Void.class)
            .collect(toList());

    for (Object obj : valuedWrappableObjs) {
      Obj wrappedObj = Wrapper.getWrappedObject(obj, obj.getClass().getName(), null);
      assertNotNull(wrappedObj);
      assertNotNull(wrappedObj.getClazz());
      assertNotNull(wrappedObj.getClazz().getName());

      assertThat(wrappedObj.getRef(), is(emptyString()));
      assertFalse(wrappedObj.getIsNull());
      if (wrappedObj.isArray) {
        assertNotNull(wrappedObj.getArrayValues());
      } else {
        assertThat(wrappedObj.getValue(), is(not(emptyString())));
        assertThat(wrappedObj.getArrayValues(), is(emptyArray()));
      }
      assertFalse(wrappedObj.getIsVoid());
    }
  }

  @Test
  public void getWrappedObject_voidObject_wrappedOk() {

    net.ittera.pal.messages.colfer.Obj wrappedObj =
        Wrapper.getWrappedObject(void.class, void.class.getName(), null);

    assertNotNull(wrappedObj);
    assertNotNull(wrappedObj.getClazz());
    assertNotNull(wrappedObj.getClazz().getName());

    assertThat(wrappedObj.getRef(), is(emptyString()));
    assertFalse(wrappedObj.getIsNull());
    assertThat(wrappedObj.getValue(), is(emptyString()));
    assertTrue(wrappedObj.getIsVoid());
  }

  @Test
  public void getWrappedObject_voidClassObject_wrappedOk() {

    Obj wrappedObj = Wrapper.getWrappedObject(Void.class, Void.class.getName(), null);

    assertNotNull(wrappedObj);
    assertNotNull(wrappedObj.getClazz());
    assertNotNull(wrappedObj.getClazz().getName());

    assertThat(wrappedObj.getRef(), is(emptyString()));
    assertFalse(wrappedObj.getIsNull());
    assertThat(wrappedObj.getValue(), is(emptyString()));
    assertTrue(wrappedObj.getIsVoid());
  }

  @Test
  public void getWrappedObject_nullObjAndGivenClassname_wrappedOk() {

    Obj wrappedObj = Wrapper.getWrappedObject(null, "java.lang.String", null);

    assertNotNull(wrappedObj);
    assertNotNull(wrappedObj.getClazz());
    assertNotNull(wrappedObj.getClazz().getName());

    assertThat(wrappedObj.getRef(), is(emptyString()));
    assertTrue(wrappedObj.getIsNull());
    assertThat(wrappedObj.getValue(), is(emptyString()));
    assertFalse(wrappedObj.getIsVoid());
  }

  @Test
  public void getWrappedObject_ObjectWithObjectRef_wrappedObjectWithRef() {
    ObjectRef objectRef = ObjectRef.randomRef();
    Object object = new ArrayList<String>();
    Obj wrapped = Wrapper.getWrappedObject(object, object.getClass().getName(), objectRef);
    assertNotNull(wrapped);
    assertEquals(String.valueOf(objectRef.getRef()), wrapped.getRef());
  }

  // </editor-fold>

  // <editor-fold defaultstate="collapsed" desc="getWrappedContext tests">
  @Test
  public void wrapContext_VariousContexts_wrappedContext() throws NoSuchFieldException {
    Signature signature = new FieldSignature(java.awt.Dimension.class.getDeclaredField("width"));
    String sourceFile = "SomeJavaClass.java";
    int lineNumber = 16;
    Class withinType = java.awt.Dimension.class;
    Context context = new Context(sourceFile, lineNumber, withinType, signature);
    net.ittera.pal.messages.colfer.Context wrappedContext =
        Wrapper.getWrappedContext(context, this, ObjectRef.randomRef());
    assertNotNull(wrappedContext);
    assertEquals(sourceFile, wrappedContext.getSourceLocationFile());
    assertEquals(lineNumber, wrappedContext.getSourceLocationLine());
    assertEquals(withinType.getName(), wrappedContext.getSourceLocationType());
  }
  // </editor-fold>
}
