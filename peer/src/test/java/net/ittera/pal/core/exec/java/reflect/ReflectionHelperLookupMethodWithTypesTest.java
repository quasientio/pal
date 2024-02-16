package net.ittera.pal.core.exec.java.reflect;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.times;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import net.ittera.pal.core.exec.java.AmbiguousCallException;
import org.junit.Ignore;
import org.junit.Test;

/**
 *
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
public class ReflectionHelperLookupMethodWithTypesTest {

  private final ReflectionHelper reflectionHelper = new ReflectionHelper();
  private final Class<?> clazz = ClassForTestingMethodLookup.class;

  private Object invoke(Method method) throws Exception {
    return invoke(method, null);
  }

  private Object invoke(Method method, Object[] args) throws Exception {
    return method.invoke(clazz.newInstance(), args);
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
  // </editor-fold>

  // <editor-fold desc="Test exceptions">
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

  @Test
  @Ignore
  public void typesGiven_twoMatchingMethods_ambiguousCallException() throws Exception {
    Object[] args = new Object[] {new Object(), 1};
    try {
      reflectionHelper.lookupMethod(
          clazz, args, Arrays.asList(Object.class, Object.class), "methodWithObjectAndNumber");
      fail("Expected AmbiguousCallException");
    } catch (AmbiguousCallException e) {
      assertEquals(2, e.getMatchingExecutables().size());
      List<List<Class<?>>> parameterTypeListsOfMatchedExecutables =
          e.getMatchingExecutables().stream()
              .map(executable -> Arrays.asList(executable.getParameterTypes()))
              .collect(Collectors.toList());
      assertThat(
          parameterTypeListsOfMatchedExecutables,
          containsInAnyOrder(
              Arrays.asList(Object.class, Integer.class),
              Arrays.asList(Object.class, Number.class)));
    }
  }
  // </editor-fold>
}
