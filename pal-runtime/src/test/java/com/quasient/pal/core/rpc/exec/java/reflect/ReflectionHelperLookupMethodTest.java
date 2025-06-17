package com.quasient.pal.core.rpc.exec.java.reflect;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;

/**
 * This class is used for testing ReflectionHelper.lookupMethod(). It contains the same methods as
 * ReflectionHelperLookupMethodTest, but without specifying parameter types.
 *
 * <p>The differences when sending no parameter types to lookupMethod() are:
 *
 * <ul>
 *   <li>with primitive types, and if there is a method that takes the primitive type and another
 *       one that takes the corresponding wrapper type, lookupMethod() will return the method that
 *       matches the wrapper types instead of the one with primitive type, because the argument is
 *       autoboxed when added to Object[] args, and therefore it's type is the wrapper's class.
 *   <li>if there is no corresponding method with the wrapper type, lookupMethod() will return the
 *       method with the primitive type(s), even if the arguments were autoboxed. This is asserted
 *       by the test method <b>methodWithOneFloatAndDoubleParam</b>.
 * </ul>
 *
 * <pre>
 * For primitives & wrapper classes:
 * - one test with the primitive type
 * - one test with the wrapper type
 * </pre>
 *
 * <p>TODO: add tests for generics
 *
 * <p>TODO Test ambiguous calls -> also in corresponding Dispatcher class
 */
public class ReflectionHelperLookupMethodTest extends AbstractReflectionHelperTestBase {

  private final ReflectionHelper reflectionHelper = new ReflectionHelper();
  private final ReflectionHelper reflectionHelperWithNonPublicAccess = new ReflectionHelper(true);
  private final Class<?> clazz = ClassForTestingMethodLookup.class;

  @Override
  protected Class<?> getTestClass() {
    return clazz;
  }

  private Object invoke(Method method) throws Exception {
    return invoke(method, null);
  }

  @Test
  public void methodWithNoParams() throws Exception {
    String methodName = "methodWithNoParams";
    Method method =
        reflectionHelper.lookupMethod(clazz, new Object[] {}, new ArrayList<>(), methodName);

    assertNotNull(method);
    assertEquals(methodName, method.getName());
    assertEquals("methodWithNoParams", invoke(method));
  }

  // <editor-fold desc="String">
  @Test
  public void methodWithOneParam_string() throws Exception {
    String methodName = "methodWithOneParam";
    Object[] args = new Object[] {"str1"};
    Method method =
        reflectionHelper.lookupMethod(
            clazz, args, Collections.singletonList(String.class), methodName);

    assertNotNull(method);
    assertEquals(methodName, method.getName());
    assertEquals("methodWithOneStringParam", invoke(method, args));
  }

  @Test
  public void noTypes_methodWithOneParam_string() throws Exception {
    String methodName = "methodWithOneParam";
    Object[] args = new Object[] {"str1"};
    Method method =
        reflectionHelper.lookupMethod(clazz, args, Collections.singletonList(null), methodName);

    assertNotNull(method);
    assertEquals(methodName, method.getName());
    assertEquals("methodWithOneStringParam", invoke(method, args));
  }

  // </editor-fold>

  // <editor-fold desc="Primitives">
  @Test
  public void methodWithOneParam_char() throws Exception {
    String methodName = "methodWithOneParam";
    Object[] args = new Object[] {'a'};
    Method method =
        reflectionHelper.lookupMethod(
            clazz, args, Collections.singletonList(Character.TYPE), methodName);

    assertNotNull(method);
    assertEquals(methodName, method.getName());
    assertEquals("methodWithOne_charParam", invoke(method, args));
  }

  @Test
  public void noTypes_methodWithOneParam_char() throws Exception {
    String methodName = "methodWithOneParam";
    Object[] args = new Object[] {'a'};
    Method method =
        reflectionHelper.lookupMethod(clazz, args, Collections.singletonList(null), methodName);

    assertNotNull(method);
    assertEquals(methodName, method.getName());
    assertEquals("methodWithOneCharacterParam", invoke(method, args));
  }

  @Test
  public void methodWithOneParam_boolean() throws Exception {
    String methodName = "methodWithOneParam";
    Object[] args = new Object[] {true};
    Method method =
        reflectionHelper.lookupMethod(
            clazz, args, Collections.singletonList(Boolean.TYPE), methodName);

    assertNotNull(method);
    assertEquals(methodName, method.getName());
    assertEquals("methodWithOne_booleanParam", invoke(method, args));
  }

  @Test
  public void noTypes_methodWithOneParam_boolean() throws Exception {
    String methodName = "methodWithOneParam";
    Object[] args = new Object[] {true};
    Method method =
        reflectionHelper.lookupMethod(clazz, args, Collections.singletonList(null), methodName);

    assertNotNull(method);
    assertEquals(methodName, method.getName());
    assertEquals("methodWithOneBooleanParam", invoke(method, args));
  }

  @Test
  public void methodWithOneParam_byte() throws Exception {
    String methodName = "methodWithOneParam";
    Object[] args = new Object[] {(byte) 1};
    Method method =
        reflectionHelper.lookupMethod(
            clazz, args, Collections.singletonList(Byte.TYPE), methodName);

    assertNotNull(method);
    assertEquals(methodName, method.getName());
    assertEquals("methodWithOne_byteParam", invoke(method, args));
  }

  @Test
  public void noTypes_methodWithOneParam_byte() throws Exception {
    String methodName = "methodWithOneParam";
    Object[] args = new Object[] {(byte) 1};
    Method method =
        reflectionHelper.lookupMethod(clazz, args, Collections.singletonList(null), methodName);

    assertNotNull(method);
    assertEquals(methodName, method.getName());
    assertEquals("methodWithOneByteParam", invoke(method, args));
  }

  @Test
  public void methodWithOneParam_short() throws Exception {
    String methodName = "methodWithOneParam";
    Object[] args = new Object[] {(short) 1};
    Method method =
        reflectionHelper.lookupMethod(
            clazz, args, Collections.singletonList(Short.TYPE), methodName);

    assertNotNull(method);
    assertEquals(methodName, method.getName());
    assertEquals("methodWithOne_shortParam", invoke(method, args));
  }

  @Test
  public void noTypes_methodWithOneParam_short() throws Exception {
    String methodName = "methodWithOneParam";
    Object[] args = new Object[] {(short) 1};
    Method method =
        reflectionHelper.lookupMethod(clazz, args, Collections.singletonList(null), methodName);

    assertNotNull(method);
    assertEquals(methodName, method.getName());
    assertEquals("methodWithOneShortParam", invoke(method, args));
  }

  @Test
  public void methodWithOneParam_int() throws Exception {
    String methodName = "methodWithOneParam";
    Object[] args = new Object[] {1};
    Method method =
        reflectionHelper.lookupMethod(
            clazz, args, Collections.singletonList(Integer.TYPE), methodName);

    assertNotNull(method);
    assertEquals(methodName, method.getName());
    assertEquals("methodWithOne_intParam", invoke(method, args));
  }

  @Test
  public void noTypes_methodWithOneParam_int() throws Exception {
    String methodName = "methodWithOneParam";
    Object[] args = new Object[] {1};
    Method method =
        reflectionHelper.lookupMethod(clazz, args, Collections.singletonList(null), methodName);

    assertNotNull(method);
    assertEquals(methodName, method.getName());
    assertEquals("methodWithOneIntegerParam", invoke(method, args));
  }

  @Test
  public void methodWithOneParam_long() throws Exception {
    String methodName = "methodWithOneParam";
    Object[] args = new Object[] {1L};
    Method method =
        reflectionHelper.lookupMethod(
            clazz, args, Collections.singletonList(Long.TYPE), methodName);

    assertNotNull(method);
    assertEquals(methodName, method.getName());
    assertEquals("methodWithOne_longParam", invoke(method, args));
  }

  @Test
  public void noTypes_methodWithOneParam_long() throws Exception {
    String methodName = "methodWithOneParam";
    Object[] args = new Object[] {1L};
    Method method =
        reflectionHelper.lookupMethod(clazz, args, Collections.singletonList(null), methodName);

    assertNotNull(method);
    assertEquals(methodName, method.getName());
    assertEquals("methodWithOneLongParam", invoke(method, args));
  }

  @Test
  public void methodWithOneParam_float() throws Exception {
    String methodName = "methodWithOneParam";
    Object[] args = new Object[] {1.0f};
    Method method =
        reflectionHelper.lookupMethod(
            clazz, args, Collections.singletonList(Float.TYPE), methodName);

    assertNotNull(method);
    assertEquals(methodName, method.getName());
    assertEquals("methodWithOne_floatParam", invoke(method, args));
  }

  @Test
  public void noTypes_methodWithOneParam_float() throws Exception {
    String methodName = "methodWithOneParam";
    Object[] args = new Object[] {1.0f};
    Method method =
        reflectionHelper.lookupMethod(clazz, args, Collections.singletonList(null), methodName);

    assertNotNull(method);
    assertEquals(methodName, method.getName());
    assertEquals("methodWithOneFloatParam", invoke(method, args));
  }

  @Test
  public void methodWithOneParam_double() throws Exception {
    String methodName = "methodWithOneParam";
    Object[] args = new Object[] {1.0d};
    Method method =
        reflectionHelper.lookupMethod(
            clazz, args, Collections.singletonList(Double.TYPE), methodName);

    assertNotNull(method);
    assertEquals(methodName, method.getName());
    assertEquals("methodWithOne_doubleParam", invoke(method, args));
  }

  @Test
  public void noTypes_methodWithOneParam_double() throws Exception {
    String methodName = "methodWithOneParam";
    Object[] args = new Object[] {1.0d};
    Method method =
        reflectionHelper.lookupMethod(clazz, args, Collections.singletonList(null), methodName);

    assertNotNull(method);
    assertEquals(methodName, method.getName());
    assertEquals("methodWithOneDoubleParam", invoke(method, args));
  }

  /**
   * Asserts that if in the absence of a method with the wrapper type(s), the method with the
   * primitive type(s) is returned even if the arguments are autoboxed.
   */
  @Test
  public void noTypes_methodWithFloatAndDoubleParam() throws Exception {
    String methodName = "methodWithOneFloatAndDoubleParam";
    Object[] args = new Object[] {1.0f, 1.0d};
    Method method =
        reflectionHelper.lookupMethod(clazz, args, Arrays.asList(null, null), methodName);

    assertNotNull(method);
    assertEquals(methodName, method.getName());
    assertEquals("methodWithOneFloatAndDoubleParam", invoke(method, args));
  }

  // </editor-fold>

  // <editor-fold desc="Wrappers">

  @Test
  public void methodWithOneParam_Character() throws Exception {
    String methodName = "methodWithOneParam";
    Object[] args = new Object[] {'a'};
    Method method =
        reflectionHelper.lookupMethod(
            clazz, args, Collections.singletonList(Character.class), methodName);

    assertNotNull(method);
    assertEquals(methodName, method.getName());
    assertEquals("methodWithOneCharacterParam", invoke(method, args));
  }

  @Test
  public void noTypes_methodWithOneParam_Character() throws Exception {
    String methodName = "methodWithOneParam";
    Object[] args = new Object[] {'a'};
    Method method =
        reflectionHelper.lookupMethod(clazz, args, Collections.singletonList(null), methodName);

    assertNotNull(method);
    assertEquals(methodName, method.getName());
    assertEquals("methodWithOneCharacterParam", invoke(method, args));
  }

  @Test
  public void methodWithOneParam_Boolean() throws Exception {
    String methodName = "methodWithOneParam";
    Object[] args = new Object[] {true};
    Method method =
        reflectionHelper.lookupMethod(
            clazz, args, Collections.singletonList(Boolean.class), methodName);

    assertNotNull(method);
    assertEquals(methodName, method.getName());
    assertEquals("methodWithOneBooleanParam", invoke(method, args));
  }

  @Test
  public void noTypes_methodWithOneParam_Boolean() throws Exception {
    String methodName = "methodWithOneParam";
    Object[] args = new Object[] {true};
    Method method =
        reflectionHelper.lookupMethod(clazz, args, Collections.singletonList(null), methodName);

    assertNotNull(method);
    assertEquals(methodName, method.getName());
    assertEquals("methodWithOneBooleanParam", invoke(method, args));
  }

  @Test
  public void methodWithOneParam_Byte() throws Exception {
    String methodName = "methodWithOneParam";
    Object[] args = new Object[] {(byte) 1};
    Method method =
        reflectionHelper.lookupMethod(
            clazz, args, Collections.singletonList(Byte.class), methodName);

    assertNotNull(method);
    assertEquals(methodName, method.getName());
    assertEquals("methodWithOneByteParam", invoke(method, args));
  }

  @Test
  public void noTypes_methodWithOneParam_Byte() throws Exception {
    String methodName = "methodWithOneParam";
    Object[] args = new Object[] {(byte) 1};
    Method method =
        reflectionHelper.lookupMethod(clazz, args, Collections.singletonList(null), methodName);

    assertNotNull(method);
    assertEquals(methodName, method.getName());
    assertEquals("methodWithOneByteParam", invoke(method, args));
  }

  @Test
  public void methodWithOneParam_Short() throws Exception {
    String methodName = "methodWithOneParam";
    Object[] args = new Object[] {(short) 1};
    Method method =
        reflectionHelper.lookupMethod(
            clazz, args, Collections.singletonList(Short.class), methodName);

    assertNotNull(method);
    assertEquals(methodName, method.getName());
    assertEquals("methodWithOneShortParam", invoke(method, args));
  }

  @Test
  public void noTypes_methodWithOneParam_Short() throws Exception {
    String methodName = "methodWithOneParam";
    Object[] args = new Object[] {(short) 1};
    Method method =
        reflectionHelper.lookupMethod(clazz, args, Collections.singletonList(null), methodName);

    assertNotNull(method);
    assertEquals(methodName, method.getName());
    assertEquals("methodWithOneShortParam", invoke(method, args));
  }

  @Test
  public void methodWithOneParam_Integer() throws Exception {
    String methodName = "methodWithOneParam";
    Object[] args = new Object[] {1};
    Method method =
        reflectionHelper.lookupMethod(
            clazz, args, Collections.singletonList(Integer.class), methodName);

    assertNotNull(method);
    assertEquals(methodName, method.getName());
    assertEquals("methodWithOneIntegerParam", invoke(method, args));
  }

  @Test
  public void noTypes_methodWithOneParam_Integer() throws Exception {
    String methodName = "methodWithOneParam";
    Object[] args = new Object[] {1};
    Method method =
        reflectionHelper.lookupMethod(clazz, args, Collections.singletonList(null), methodName);

    assertNotNull(method);
    assertEquals(methodName, method.getName());
    assertEquals("methodWithOneIntegerParam", invoke(method, args));
  }

  @Test
  public void methodWithOneParam_Long() throws Exception {
    String methodName = "methodWithOneParam";
    Object[] args = new Object[] {1L};
    Method method =
        reflectionHelper.lookupMethod(
            clazz, args, Collections.singletonList(Long.class), methodName);

    assertNotNull(method);
    assertEquals(methodName, method.getName());
    assertEquals("methodWithOneLongParam", invoke(method, args));
  }

  @Test
  public void noTypes_methodWithOneParam_Long() throws Exception {
    String methodName = "methodWithOneParam";
    Object[] args = new Object[] {1L};
    Method method =
        reflectionHelper.lookupMethod(clazz, args, Collections.singletonList(null), methodName);

    assertNotNull(method);
    assertEquals(methodName, method.getName());
    assertEquals("methodWithOneLongParam", invoke(method, args));
  }

  @Test
  public void methodWithOneParam_Float() throws Exception {
    String methodName = "methodWithOneParam";
    Object[] args = new Object[] {1.0f};
    Method method =
        reflectionHelper.lookupMethod(
            clazz, args, Collections.singletonList(Float.class), methodName);

    assertNotNull(method);
    assertEquals(methodName, method.getName());
    assertEquals("methodWithOneFloatParam", invoke(method, args));
  }

  @Test
  public void noTypes_methodWithOneParam_Float() throws Exception {
    String methodName = "methodWithOneParam";
    Object[] args = new Object[] {1.0f};
    Method method =
        reflectionHelper.lookupMethod(clazz, args, Collections.singletonList(null), methodName);

    assertNotNull(method);
    assertEquals(methodName, method.getName());
    assertEquals("methodWithOneFloatParam", invoke(method, args));
  }

  @Test
  public void methodWithOneParam_Double() throws Exception {
    String methodName = "methodWithOneParam";
    Object[] args = new Object[] {1.0d};
    Method method =
        reflectionHelper.lookupMethod(
            clazz, args, Collections.singletonList(Double.class), methodName);

    assertNotNull(method);
    assertEquals(methodName, method.getName());
    assertEquals("methodWithOneDoubleParam", invoke(method, args));
  }

  @Test
  public void noTypes_methodWithOneParam_Double() throws Exception {
    String methodName = "methodWithOneParam";
    Object[] args = new Object[] {1.0d};
    Method method =
        reflectionHelper.lookupMethod(clazz, args, Collections.singletonList(null), methodName);

    assertNotNull(method);
    assertEquals(methodName, method.getName());
    assertEquals("methodWithOneDoubleParam", invoke(method, args));
  }

  // </editor-fold>

  // <editor-fold desc="Arrays testing">
  @Test
  public void methodWithDoublePrimitiveArrayParam() throws Exception {
    String methodName = "methodWithArrayParam";
    Object[] args = new Object[] {new double[] {1.0, 2.0, 3.0}};
    Method method =
        reflectionHelper.lookupMethod(
            clazz, args, Collections.singletonList(double[].class), methodName);

    assertNotNull(method);
    assertEquals(methodName, method.getName());
    assertEquals("doubleArrayParam", invoke(method, args));
  }

  @Test
  public void noTypes_methodWithDoublePrimitiveArrayParam() throws Exception {
    String methodName = "methodWithArrayParam";
    Object[] args = new Object[] {new double[] {1.0, 2.0, 3.0}};
    Method method =
        reflectionHelper.lookupMethod(clazz, args, Collections.singletonList(null), methodName);

    assertNotNull(method);
    assertEquals(methodName, method.getName());
    assertEquals("doubleArrayParam", invoke(method, args));
  }

  @Test
  public void methodWithDoubleArrayParam() throws Exception {
    String methodName = "methodWithArrayParam";
    Object[] args = new Object[] {new Double[] {1.0, 2.0, 3.0}};
    Method method =
        reflectionHelper.lookupMethod(
            clazz, args, Collections.singletonList(Double[].class), methodName);

    assertNotNull(method);
    assertEquals(methodName, method.getName());
    assertEquals("DoubleArrayParam", invoke(method, args));
  }

  @Test
  public void noTypes_methodWithDoubleArrayParam() throws Exception {
    String methodName = "methodWithArrayParam";
    Object[] args = new Object[] {new Double[] {1.0, 2.0, 3.0}};
    Method method =
        reflectionHelper.lookupMethod(clazz, args, Collections.singletonList(null), methodName);

    assertNotNull(method);
    assertEquals(methodName, method.getName());
    assertEquals("DoubleArrayParam", invoke(method, args));
  }

  @Test
  public void methodWithNumberArrayParam() throws Exception {
    String methodName = "methodWithArrayParam";

    // passing the args inside a Number[] array
    Object[] args = new Object[] {new Number[] {14.3d, 2.1d, 3.9d}};
    Method method =
        reflectionHelper.lookupMethod(
            clazz, args, Collections.singletonList(Number[].class), methodName);

    assertNotNull(method);
    assertEquals(methodName, method.getName());
    assertEquals("NumberArrayParam", invoke(method, args));

    // passing the args inside a Double[] array
    args = new Object[] {new Double[] {14.3d, 2.1d, 3.9d}};
    method =
        reflectionHelper.lookupMethod(
            clazz, args, Collections.singletonList(Number[].class), methodName);

    assertNotNull(method);
    assertEquals(methodName, method.getName());
    assertEquals("NumberArrayParam", invoke(method, args));
  }

  @Test
  public void noTypes_methodWithNumberArrayParam() throws Exception {
    String methodName = "methodWithArrayParam";
    Object[] args = new Object[] {new Number[] {14.3d, 2.1d, 3.9d}};
    Method method =
        reflectionHelper.lookupMethod(clazz, args, Collections.singletonList(null), methodName);

    assertNotNull(method);
    assertEquals(methodName, method.getName());
    assertEquals("NumberArrayParam", invoke(method, args));
  }

  @Test
  public void methodWithObjectArrayParam() throws Exception {
    String methodName = "methodWithArrayParam";

    // passing the args inside an Object[] array
    Object[] args = new Object[] {new Object[] {14.3d, 2.1d, 3.9d}};
    Method method =
        reflectionHelper.lookupMethod(
            clazz, args, Collections.singletonList(Object[].class), methodName);

    assertNotNull(method);
    assertEquals(methodName, method.getName());
    assertEquals("ObjectArrayParam", invoke(method, args));

    // passing the args inside a Number[] array
    args = new Object[] {new Number[] {14.3d, 2.1d, 3.9d}};
    method =
        reflectionHelper.lookupMethod(
            clazz, args, Collections.singletonList(Object[].class), methodName);

    assertNotNull(method);
    assertEquals(methodName, method.getName());
    assertEquals("ObjectArrayParam", invoke(method, args));

    // passing the args inside a Double[] array
    args = new Object[] {new Double[] {14.3d, 2.1d, 3.9d}};
    method =
        reflectionHelper.lookupMethod(
            clazz, args, Collections.singletonList(Object[].class), methodName);

    assertNotNull(method);
    assertEquals(methodName, method.getName());
    assertEquals("ObjectArrayParam", invoke(method, args));
  }

  @Test
  public void noTypes_methodWithObjectArrayParam() throws Exception {
    String methodName = "methodWithArrayParam";
    Object[] args = new Object[] {new Object[] {14.3d, 2.1d, 3.9d}};
    Method method =
        reflectionHelper.lookupMethod(clazz, args, Collections.singletonList(null), methodName);

    assertNotNull(method);
    assertEquals(methodName, method.getName());
    assertEquals("ObjectArrayParam", invoke(method, args));
  }

  // </editor-fold>

  // <editor-fold desc="Varargs">
  @Test
  public void methodWithFloatVarargs() throws Exception {
    String methodName = "methodWithFloatVarargs";
    Object[] args = new Object[] {new Float[] {1.0f, 20f, 3.013f}};
    Method method =
        reflectionHelper.lookupMethod(
            clazz, args, Collections.singletonList(Float[].class), methodName);

    assertNotNull(method);
    assertEquals(methodName, method.getName());
    assertEquals("FloatVarargs", invoke(method, args));
  }

  @Test
  public void noTypes_methodWithFloatVarargs() throws Exception {
    String methodName = "methodWithFloatVarargs";
    Object[] args = new Object[] {new Float[] {1.0f, 20f, 3.013f}};
    Method method =
        reflectionHelper.lookupMethod(clazz, args, Collections.singletonList(null), methodName);

    assertNotNull(method);
    assertEquals(methodName, method.getName());
    assertEquals("FloatVarargs", invoke(method, args));
  }

  @Test
  public void methodWithVarargsWithTypeStringArray() throws Exception {
    String methodName = "methodWithVarargs";
    Object[] args = new Object[] {new String[] {"str1", "str2", "str3"}};
    Method method =
        reflectionHelper.lookupMethod(
            clazz, args, Collections.singletonList(String[].class), methodName);

    assertNotNull(method);
    assertEquals(methodName, method.getName());
    assertEquals("methodWithStringVarargs", invoke(method, args));
  }

  @Test
  public void noTypes_methodWithVarargsStrings() throws Exception {
    String methodName = "methodWithVarargs";
    Object[] args = new Object[] {new String[] {"str1", "str2", "str3"}};
    Method method =
        reflectionHelper.lookupMethod(clazz, args, Collections.singletonList(null), methodName);

    assertNotNull(method);
    assertEquals(methodName, method.getName());
    assertEquals("methodWithStringVarargs", invoke(method, args));
  }

  /**
   * Lookup method that has a varargs parameter of type int, passing argument and param type of int
   * array.
   *
   * @throws Exception when either lookupMethod or invoke throws an exception
   */
  @Test
  public void methodWithIntVarargsWithTypeIntArray() throws Exception {
    String methodName = "methodWithVarargs";
    Object[] args = new Object[] {"waa", new int[] {45, 56}};
    Method method =
        reflectionHelper.lookupMethod(
            clazz, args, Arrays.asList(String.class, int[].class), methodName);

    assertNotNull(method);
    assertEquals(methodName, method.getName());
    assertEquals("methodWithIntVarargs", invoke(method, args));
  }

  /**
   * Lookup method that has a varargs parameter of type int, passing argument and param type of int
   * (i.e. the component type of the array)
   *
   * @throws Exception when either lookupMethod or invoke throws an exception
   */
  @Test
  public void methodWithIntVarargsWithTypeInt() throws Exception {
    String methodName = "methodWithVarargs";
    Object[] args = new Object[] {"waa", 45};
    Method method =
        reflectionHelper.lookupMethod(
            clazz, args, Arrays.asList(String.class, Integer.TYPE), methodName);

    assertNotNull(method);
    assertEquals(methodName, method.getName());
    assertEquals("methodWithIntVarargs", invoke(method, args));
  }

  /**
   * Lookup method that has a varargs parameter of type int, passing argument of type int (i.e. the
   * component type of the array) but no parameter types
   *
   * @throws Exception when either lookupMethod or invoke throws an exception
   */
  @Test
  public void noTypes_methodWithIntVarargsWithTypeInt() throws Exception {
    String methodName = "methodWithVarargs";
    Object[] args = new Object[] {"waa", 45};
    Method method =
        reflectionHelper.lookupMethod(clazz, args, Arrays.asList(null, null), methodName);

    assertNotNull(method);
    assertEquals(methodName, method.getName());
    assertEquals("methodWithIntVarargs", invoke(method, args));
  }

  @Test
  public void methodWithVarargsOverloadedObjects() throws Exception {
    String methodName = "methodWithVarargs";
    Object[] args = new Object[] {new Object[] {"str1", "str2", "str3"}};
    Method method =
        reflectionHelper.lookupMethod(
            clazz, args, Collections.singletonList(Object[].class), methodName);

    assertNotNull(method);
    assertEquals(methodName, method.getName());
    assertEquals("methodWithObjectVarargs", invoke(method, args));
  }

  @Test
  public void noTypes_methodWithVarargsOverloadedObjects() throws Exception {
    String methodName = "methodWithVarargs";
    Object[] args = new Object[] {new Object[] {"str1", "str2", "str3"}};
    Method method =
        reflectionHelper.lookupMethod(clazz, args, Collections.singletonList(null), methodName);

    assertNotNull(method);
    assertEquals(methodName, method.getName());
    assertEquals("methodWithObjectVarargs", invoke(method, args));
  }

  // </editor-fold>

  // <editor-fold desc="Test caching">
  @Test
  public void testCaching() throws Exception {
    List<Class<?>> paramTypes = Arrays.asList(String.class, Float.class, String.class);
    Object[] args = new Object[] {"hello10", 4.9f, "str123"};
    String methodName = "methodForCacheTest";

    AtomicInteger cacheHits = new AtomicInteger(0);

    // spy on reflectionHelper to check for cache hits
    ReflectionHelper spyReflectionHelper = spy(reflectionHelper);

    // check the return value of lookupInCache()
    doAnswer(
            invocation -> {
              Method returnedMethod = (Method) invocation.callRealMethod();
              if (returnedMethod != null) {
                cacheHits.incrementAndGet();
              }
              return returnedMethod;
            })
        .when(spyReflectionHelper)
        .lookupInCache(clazz, methodName, paramTypes, Method.class);

    // call lookupMethod using the spy
    Method method = spyReflectionHelper.lookupMethod(clazz, args, paramTypes, methodName);
    assertNotNull(method);
    assertEquals("methodForCacheTest", invoke(method, args));
    verify(spyReflectionHelper, times(1))
        .lookupInCache(clazz, methodName, paramTypes, Method.class);
    assertEquals(0, cacheHits.get());

    // 2nd call to lookupMethod - this time the method should be retrieved from the cache
    method = spyReflectionHelper.lookupMethod(clazz, args, paramTypes, methodName);
    assertNotNull(method);
    assertEquals("methodForCacheTest", invoke(method, args));
    verify(spyReflectionHelper, times(2))
        .lookupInCache(clazz, methodName, paramTypes, Method.class);

    // verify cache was hit once
    assertEquals(1, cacheHits.get());
  }

  @Test
  public void noTypes_testCaching() throws Exception {
    Object[] args = new Object[] {"hello10", 4.9f, "str123"};
    List<Class<?>> expectedButNotGivenParamTypes =
        Arrays.asList(String.class, Float.class, String.class);
    String methodName = "methodForCacheTest";

    AtomicInteger cacheHits = new AtomicInteger(0);

    // spy on reflectionHelper to check for cache hits
    ReflectionHelper spyReflectionHelper = spy(reflectionHelper);

    // check the return value of lookupInCache()
    doAnswer(
            invocation -> {
              Method returnedMethod = (Method) invocation.callRealMethod();
              if (returnedMethod != null) {
                cacheHits.incrementAndGet();
              }
              return returnedMethod;
            })
        .when(spyReflectionHelper)
        .lookupInCache(clazz, methodName, expectedButNotGivenParamTypes, Method.class);

    // call lookupMethod using the spy
    Method method =
        spyReflectionHelper.lookupMethod(clazz, args, Arrays.asList(null, null, null), methodName);
    assertNotNull(method);
    assertEquals("methodForCacheTest", invoke(method, args));

    verify(spyReflectionHelper, times(1))
        .lookupInCache(clazz, methodName, expectedButNotGivenParamTypes, Method.class);
    assertEquals(0, cacheHits.get());

    // 2nd call to lookupMethod - this time the method should be retrieved from the cache
    method =
        spyReflectionHelper.lookupMethod(clazz, args, Arrays.asList(null, null, null), methodName);
    assertNotNull(method);
    assertEquals("methodForCacheTest", invoke(method, args));
    verify(spyReflectionHelper, times(2))
        .lookupInCache(clazz, methodName, expectedButNotGivenParamTypes, Method.class);

    // verify cache was hit once
    assertEquals(1, cacheHits.get());
  }

  // </editor-fold>

  // <editor-fold desc="Visibility testing">
  @Test
  public void publicMethod() throws Exception {
    String methodName = "publicMethodWithOneParam";
    Object[] args = new Object[] {"str1"};
    Method method =
        reflectionHelper.lookupMethod(
            clazz, args, Collections.singletonList(String.class), methodName);

    assertNotNull(method);
    assertEquals(methodName, method.getName());
  }

  @Test(expected = NoSuchMethodException.class)
  public void privateMethod() throws Exception {
    String methodName = "privateMethodWithOneParam";
    Object[] args = new Object[] {"str1"};
    reflectionHelper.lookupMethod(clazz, args, Collections.singletonList(String.class), methodName);
  }

  @Test
  public void privateMethod_withNonPublicReflectionHelper() throws Exception {
    String methodName = "privateMethodWithOneParam";
    Object[] args = new Object[] {"str1"};
    Method method =
        reflectionHelperWithNonPublicAccess.lookupMethod(
            clazz, args, Collections.singletonList(String.class), methodName);

    assertNotNull(method);
    assertEquals(methodName, method.getName());
  }

  @Test(expected = NoSuchMethodException.class)
  public void protectedMethod() throws Exception {
    String methodName = "protectedMethodWithOneParam";
    Object[] args = new Object[] {"str1"};
    reflectionHelper.lookupMethod(clazz, args, Collections.singletonList(String.class), methodName);
  }

  @Test
  public void protectedMethod_withNonPublicReflectionHelper() throws Exception {
    String methodName = "protectedMethodWithOneParam";
    Object[] args = new Object[] {"str1"};
    Method method =
        reflectionHelperWithNonPublicAccess.lookupMethod(
            clazz, args, Collections.singletonList(String.class), methodName);

    assertNotNull(method);
    assertEquals(methodName, method.getName());
  }

  @Test(expected = NoSuchMethodException.class)
  public void packageProtectedMethod() throws Exception {
    String methodName = "packageProtectedMethodWithOneParam";
    Object[] args = new Object[] {"str1"};
    reflectionHelper.lookupMethod(clazz, args, Collections.singletonList(String.class), methodName);
  }

  @Test
  public void packageProtectedMethod_withNonPublicReflectionHelper() throws Exception {
    String methodName = "packageProtectedMethodWithOneParam";
    Object[] args = new Object[] {"str1"};
    Method method =
        reflectionHelperWithNonPublicAccess.lookupMethod(
            clazz, args, Collections.singletonList(String.class), methodName);

    assertNotNull(method);
    assertEquals(methodName, method.getName());
  }

  // </editor-fold>

  // <editor-fold desc="Test misc exceptions">
  @Test(expected = IllegalArgumentException.class)
  public void method_paramAndParamTypesOfDifferentLength_illegalArgumentException()
      throws Exception {
    Object[] args = new Object[] {1.0f, 1.0f, 1};
    String methodName = "anyMethod";
    reflectionHelper.lookupMethod(
        clazz, args, Arrays.asList(Float.TYPE, Float.TYPE, Integer.TYPE, Integer.TYPE), methodName);
  }

  @Test(expected = NoSuchMethodException.class)
  public void method_noMatchingParams_noSuchMethodException() throws Exception {
    Object[] args = new Object[] {1.0f, 1.0f, 1, 1};
    String methodName = "ghostMethod";
    reflectionHelper.lookupMethod(
        clazz, args, Arrays.asList(Float.TYPE, Float.TYPE, Integer.TYPE, Integer.TYPE), methodName);
  }

  @Test(expected = NoSuchMethodException.class)
  public void noTypes_method_noMatchingParams_noSuchMethodException() throws Exception {
    Object[] args = new Object[] {1.0f, 1.0f, 1, 1};
    String methodName = "ghostMethod";
    reflectionHelper.lookupMethod(clazz, args, Arrays.asList(null, null, null, null), methodName);
  }
  // </editor-fold>
}
