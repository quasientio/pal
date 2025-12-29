/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.serdes.colfer;

import static java.util.stream.Collectors.toList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.emptyString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import io.quasient.pal.common.lang.reflect.FieldSignature;
import io.quasient.pal.common.lang.reflect.Signature;
import io.quasient.pal.common.objects.ObjectRef;
import io.quasient.pal.common.runtime.Context;
import io.quasient.pal.common.util.Classes;
import io.quasient.pal.messages.colfer.Obj;
import io.quasient.pal.serdes.NonWrappableObjectException;
import io.quasient.pal.serdes.WrappingTestBase;
import java.awt.Dimension;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// Naming convention to use: MethodName_StateUnderTest_ExpectedBehavior
public class WrapperTest extends WrappingTestBase {

  private static final Logger logger = LoggerFactory.getLogger("tests");
  private static List<Class<?>> allPrimitiveAndLangClasses;

  @BeforeClass
  public static void setupLists() {

    List<Class<?>> javaLangClasses = new ArrayList<>();
    javaLangClasses.addAll(Classes.getPrimitiveWrapperClasses());
    javaLangClasses.addAll(nonWrapperJavaLangClasses);

    allPrimitiveAndLangClasses = new ArrayList<>();
    allPrimitiveAndLangClasses.addAll(Classes.getPrimitiveClasses());
    allPrimitiveAndLangClasses.addAll(javaLangClasses);
  }

  @SuppressWarnings("unchecked")
  private static <T> T[] getArrayOfLength1(Class<T> clazz) {
    return (T[]) Array.newInstance(clazz, 1);
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
  public void isWrappable_oneDimArrayOfPrimitive_true() {

    int arraySize = 1;

    for (Class<?> clazz : Classes.getPrimitiveClasses()) {
      Object primitiveArray = Array.newInstance(clazz, arraySize);
      assertTrue(
          String.format("%s is not wrappable!", primitiveArray),
          Wrapper.isWrappable(primitiveArray));
    }
  }

  @Test
  public void isWrappable_oneDimArrayOfWrapper_true() {

    // create list of 1-dimensional arrays, one for each of primitiveWrapperClasses, with length=1
    List<?> wrapperArrays =
        Classes.getPrimitiveWrapperClasses().stream().map(WrapperTest::getArrayOfLength1).toList();

    for (Object wrapperArray : wrapperArrays) {
      assertTrue(
          String.format(
              "Array of type %s is not wrappable", wrapperArray.getClass().getComponentType()),
          Wrapper.isWrappable(wrapperArray));
    }
  }

  // </editor-fold>

  // <editor-fold defaultstate="collapsed" desc="getWrappedClass tests">

  @Test
  @SuppressWarnings("PMD.NoFullyQualifiedTypes")
  public void getWrappedClass_nullClass_unknownClassNoName() {
    io.quasient.pal.messages.colfer.Class wrappedClass = Wrapper.getWrappedClass((Class<?>) null);
    assertNotNull(wrappedClass);
    assertThat(wrappedClass.getName(), is(emptyString()));
  }

  @Test
  @SuppressWarnings("PMD.NoFullyQualifiedTypes")
  public void getWrappedClass_javaLangOrPrimitiveClass_wrappedOk() {
    io.quasient.pal.messages.colfer.Class wrappedClass;
    for (Class<?> clazz : allPrimitiveAndLangClasses) {
      wrappedClass = Wrapper.getWrappedClass(clazz);

      // neither null nor empty name
      assertNotNull(wrappedClass);
      assertFalse(wrappedClass.getName().isEmpty());

      // name is set and correctly
      assertEquals(clazz.getName(), wrappedClass.getName());
    }
  }

  @Test
  @SuppressWarnings("PMD.NoFullyQualifiedTypes")
  public void getWrappedClass_nullClassName_wrappedOk() {

    io.quasient.pal.messages.colfer.Class wrappedClass;
    wrappedClass = Wrapper.getWrappedClass((String) null);

    assertNotNull(wrappedClass);

    // name is empty
    assertTrue(wrappedClass.getName().isEmpty());
  }

  @Test
  @SuppressWarnings("PMD.NoFullyQualifiedTypes")
  public void getWrappedClass_javaLangOrPrimitiveClassName_wrappedOk() {

    List<String> classNames = allPrimitiveAndLangClasses.stream().map(Class::getName).toList();

    io.quasient.pal.messages.colfer.Class wrappedClass;
    for (String classname : classNames) {
      wrappedClass = Wrapper.getWrappedClass(classname);

      // neither null nor name unknown
      assertNotNull(wrappedClass);
      assertFalse(wrappedClass.getName().isEmpty());

      // name is set and correctly
      assertEquals(classname, wrappedClass.getName());
    }
  }

  // Class of all wrappableObjects must be wrappable as well
  @Test
  @SuppressWarnings("PMD.NoFullyQualifiedTypes")
  public void getWrappedClass_wrappableClass_wrappedOk() {

    List<Class<?>> classes =
        wrappableObjects.stream().filter(Objects::nonNull).map(Object::getClass).collect(toList());

    io.quasient.pal.messages.colfer.Class wrappedClass;
    for (Class<?> clazz : classes) {
      wrappedClass = Wrapper.getWrappedClass(clazz);

      // neither null nor name unknown
      assertNotNull(wrappedClass);
      assertFalse(wrappedClass.getName().isEmpty());

      // name is set and correctly
      assertEquals(clazz.getName(), wrappedClass.getName());
    }
  }

  // </editor-fold>

  // <editor-fold defaultstate="collapsed" desc="getWrappedField tests">
  @Test
  @SuppressWarnings("PMD.NoFullyQualifiedTypes")
  public void getWrappedField_fieldAndClass_wrappedOk() {

    var clazz = Integer.class;
    String fieldName = "height";
    int modifiers = 3;

    io.quasient.pal.messages.colfer.Field wrappedField =
        Wrapper.getWrappedField(clazz, fieldName, modifiers);
    assertNotNull(wrappedField);
    assertEquals(fieldName, wrappedField.getName());
    assertEquals(clazz.getName(), wrappedField.getClazz().getName());
    assertEquals(modifiers, wrappedField.getModifiers());
  }

  @Test
  @SuppressWarnings("PMD.NoFullyQualifiedTypes")
  public void wrapField_ValidField_wrappedField() throws NoSuchFieldException {
    Field field = Dimension.class.getDeclaredField("height");
    io.quasient.pal.messages.colfer.Field wrappedField = Wrapper.getWrappedField(field);
    assertNotNull(wrappedField);
    assertEquals(field.getName(), wrappedField.getName());
  }

  @Test
  @SuppressWarnings("PMD.NoFullyQualifiedTypes")
  public void getWrappedField_fieldAndClassName_wrappedOk() {
    String className = Integer.class.getName();
    String fieldName = "height";
    int modifiers = 2;

    io.quasient.pal.messages.colfer.Field wrappedField =
        Wrapper.getWrappedField(className, fieldName, modifiers);
    assertNotNull(wrappedField);
    assertEquals(fieldName, wrappedField.getName());
    assertEquals(className, wrappedField.getClazz().getName());
    assertEquals(modifiers, wrappedField.getModifiers());
  }

  // </editor-fold>

  // <editor-fold defaultstate="collapsed" desc="getWrappedObject tests">

  @Test
  public void getWrappedObject_NullClass_wrapped() {
    Wrapper.getWrappedObject(
        new Object(), null, ObjectRef.randomRef(), WrapPolicy.PREFER_REFERENCE);
  }

  @Test(expected = IllegalArgumentException.class)
  public void getWrappedObject_InvalidObjectType_illegalArgumentException() {
    Wrapper.getWrappedObject(
        new Object(), "238923", ObjectRef.randomRef(), WrapPolicy.PREFER_REFERENCE);
  }

  @Test
  public void getWrappedObject_nullArguments_objNull() {
    Obj wrapped = Wrapper.getWrappedObject(null, null, null, WrapPolicy.PREFER_REFERENCE);
    assertNotNull(wrapped);
    assertTrue(wrapped.isNull);
    assertThat(wrapped.getValue(), is(emptyString()));
    assertThat(wrapped.getRef(), is(0));
  }

  @Test
  public void getWrappedObject_arrayOfPrimitives_wrappedArrayValuesAndRef() {
    Integer[] intArray = new Integer[] {1, 2, 3};
    Obj wrapped =
        Wrapper.getWrappedObject(
            intArray, intArray.getClass().getName(), ObjectRef.randomRef(), WrapPolicy.DETECT);
    assertNotNull(wrapped);
    assertFalse(wrapped.isNull);
    assertThat(wrapped.getValue(), is("[1,2,3]"));
    assertThat(wrapped.getRef(), is(not(0)));
  }

  @Test
  public void getWrappedObject_arrayOfObjects_onlyWrappedRef() {
    @SuppressWarnings("InstantiatingAThreadWithDefaultRunMethod")
    Thread[] threadArray = new Thread[] {new Thread(), new Thread()};
    Obj wrapped =
        Wrapper.getWrappedObject(
            threadArray,
            threadArray.getClass().getName(),
            ObjectRef.randomRef(),
            WrapPolicy.PREFER_REFERENCE);
    assertNotNull(wrapped);
    assertFalse(wrapped.isNull);
    assertThat(wrapped.getValue(), is(emptyString()));
    assertThat(wrapped.getRef(), is(not(0)));
  }

  @Test
  public void getWrappedObject_EmptyArray_wrappedArrayWithZeroLength() {
    Integer[] emptyArray = new Integer[0];
    Obj wrapped =
        Wrapper.getWrappedObject(
            emptyArray, emptyArray.getClass().getName(), null, WrapPolicy.PREFER_REFERENCE);
    assertNotNull(wrapped);
  }

  @Test
  public void getWrappedObject_nonWrappableObj_nonWrappableException() {

    for (Object obj : someNonWrappableObjects) {
      try {
        logger.debug("Calling Wrapper.getWrappedObject with {} and class {}", obj, obj.getClass());
        Wrapper.getWrappedObject(obj, obj.getClass().getName(), null, WrapPolicy.PREFER_REFERENCE);
        fail("Should have thrown an exception");
      } catch (NonWrappableObjectException ex) {
        // all good
      }
    }
  }

  @Test
  public void getWrappedObject_wrappableValuedObj_wrappedWithValue() {

    // test all wrappable objects except null & void
    List<Object> valuedWrappableObjects =
        wrappableObjects.stream()
            .filter(o -> o != null && o != void.class && o != Void.class)
            .toList();

    for (Object obj : valuedWrappableObjects) {
      Obj wrappedObj =
          Wrapper.getWrappedObject(
              obj, obj.getClass().getName(), null, WrapPolicy.PREFER_REFERENCE);
      assertNotNull(wrappedObj);
      assertNotNull(wrappedObj.getClazz());
      assertNotNull(wrappedObj.getClazz().getName());

      assertThat(wrappedObj.getRef(), is(0));
      assertFalse(wrappedObj.getIsNull());
      assertThat(wrappedObj.getValue(), is(not(emptyString())));
    }
  }

  @Test
  public void getWrappedObject_nullObjAndGivenClassname_wrappedOk() {

    Obj wrappedObj =
        Wrapper.getWrappedObject(null, "java.lang.String", null, WrapPolicy.PREFER_REFERENCE);

    assertNotNull(wrappedObj);
    assertNotNull(wrappedObj.getClazz());
    assertNotNull(wrappedObj.getClazz().getName());

    assertThat(wrappedObj.getRef(), is(0));
    assertTrue(wrappedObj.getIsNull());
    assertThat(wrappedObj.getValue(), is(emptyString()));
  }

  @Test
  public void getWrappedObject_ObjectWithObjectRef_wrappedObjectWithRef() {
    ObjectRef objectRef = ObjectRef.randomRef();
    Object object = new ArrayList<String>();
    Obj wrapped =
        Wrapper.getWrappedObject(
            object, object.getClass().getName(), objectRef, WrapPolicy.PREFER_REFERENCE);
    assertNotNull(wrapped);
    assertEquals(objectRef.getRef(), wrapped.getRef());
  }

  // </editor-fold>

  // <editor-fold defaultstate="collapsed" desc="getWrappedContext tests">
  @Test
  @SuppressWarnings("PMD.NoFullyQualifiedTypes")
  public void wrapContext_VariousContexts_wrappedContext() throws NoSuchFieldException {
    Signature signature = new FieldSignature(Dimension.class.getDeclaredField("width"));
    String sourceFile = "SomeJavaClass.java";
    int lineNumber = 16;
    var withinType = Dimension.class;
    Context context = new Context(sourceFile, lineNumber, withinType, signature);
    io.quasient.pal.messages.colfer.Context wrappedContext =
        Wrapper.getWrappedContext(context, this, ObjectRef.randomRef());
    assertNotNull(wrappedContext);
    assertEquals(sourceFile, wrappedContext.getSourceLocationFile());
    assertEquals(lineNumber, wrappedContext.getSourceLocationLine());
    assertEquals(withinType.getName(), wrappedContext.getSourceLocationType());
  }

  // </editor-fold>

  // <editor-fold defaultstate="collapsed" desc="isSimpleType tests">
  @Test
  public void isSimpleType_simpleTypes_true() {
    assertTrue(Wrapper.isSimpleType(null));
    assertTrue(Wrapper.isSimpleType(false));
    assertTrue(Wrapper.isSimpleType(23));
    assertTrue(Wrapper.isSimpleType("another one"));
  }

  @Test
  public void isSimpleType_nonSimpleTypes_false() {
    assertFalse(Wrapper.isSimpleType(new Object()));
    assertFalse(Wrapper.isSimpleType(new int[] {}));
  }

  // </editor-fold>

  // <editor-fold defaultstate="collapsed" desc="isSimpleTypeArray tests">
  @Test
  public void isSimpleTypeArray_simpleTypeArrays_true() {
    assertTrue(Wrapper.isSimpleTypeArray(new boolean[] {true, true}));
    assertTrue(Wrapper.isSimpleTypeArray(new int[] {23, 34, 56}));
    assertTrue(Wrapper.isSimpleTypeArray(new String[] {"another one", "bytes"}));
    assertTrue(Wrapper.isSimpleTypeArray(new char[] {'s', 'p', 'l', 'i', 't'}));
  }

  @Test
  public void isSimpleTypeArray_nonSimpleTypeArrays_false() {
    assertFalse(Wrapper.isSimpleTypeArray(null));
    assertFalse(Wrapper.isSimpleTypeArray(new Object[] {null}));
  }
  // </editor-fold>
}
