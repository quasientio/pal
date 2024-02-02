package net.ittera.pal.core.exec.java.reflect;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import net.ittera.pal.core.exec.java.AmbiguousCallException;
import org.junit.Ignore;
import org.junit.Test;

/**
 * This class is used for testing ReflectionHelper.lookupMethod(). It contains the same methods as
 * ReflectionHelperLookupMethodWithTypesTest, but without specifying parameter types.
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
 * <p>For primitives & wrapper classes:
 *
 * <ul>
 *   <li>one test with the primitive type
 *   <li>one test with the wrapper type
 * </ul>
 *
 * <pre>
 * TODO: add tests for ARRAYS
 * TODO: add tests for VARARGS
 * TODO: add tests for generics
 * </pre>
 */
public class ReflectionHelperLookupMethodWithoutTypesTest {

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
    Method method = reflectionHelper.lookupMethod(clazz, new Object[] {}, methodName);

    assertNotNull(method);
    assertEquals(methodName, method.getName());
    assertEquals("methodWithNoParams", invoke(method));
  }

  // <editor-fold desc="String">
  @Test
  public void methodWithOneParam_string() throws Exception {
    String methodName = "methodWithOneParam";
    Object[] args = new Object[] {"str1"};
    Method method = reflectionHelper.lookupMethod(clazz, args, methodName);

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
    Method method = reflectionHelper.lookupMethod(clazz, args, methodName);

    assertNotNull(method);
    assertEquals(methodName, method.getName());
    assertEquals("methodWithOneCharacterParam", invoke(method, args));
  }

  @Test
  public void methodWithOneParam_boolean() throws Exception {
    String methodName = "methodWithOneParam";
    Object[] args = new Object[] {true};
    Method method = reflectionHelper.lookupMethod(clazz, args, methodName);

    assertNotNull(method);
    assertEquals(methodName, method.getName());
    assertEquals("methodWithOneBooleanParam", invoke(method, args));
  }

  @Test
  public void methodWithOneParam_byte() throws Exception {
    String methodName = "methodWithOneParam";
    Object[] args = new Object[] {(byte) 1};
    Method method = reflectionHelper.lookupMethod(clazz, args, methodName);

    assertNotNull(method);
    assertEquals(methodName, method.getName());
    assertEquals("methodWithOneByteParam", invoke(method, args));
  }

  @Test
  public void methodWithOneParam_short() throws Exception {
    String methodName = "methodWithOneParam";
    Object[] args = new Object[] {(short) 1};
    Method method = reflectionHelper.lookupMethod(clazz, args, methodName);

    assertNotNull(method);
    assertEquals(methodName, method.getName());
    assertEquals("methodWithOneShortParam", invoke(method, args));
  }

  @Test
  public void methodWithOneParam_int() throws Exception {
    String methodName = "methodWithOneParam";
    Object[] args = new Object[] {1};
    Method method = reflectionHelper.lookupMethod(clazz, args, methodName);

    assertNotNull(method);
    assertEquals(methodName, method.getName());
    assertEquals("methodWithOneIntegerParam", invoke(method, args));
  }

  @Test
  public void methodWithOneParam_long() throws Exception {
    String methodName = "methodWithOneParam";
    Object[] args = new Object[] {1L};
    Method method = reflectionHelper.lookupMethod(clazz, args, methodName);

    assertNotNull(method);
    assertEquals(methodName, method.getName());
    assertEquals("methodWithOneLongParam", invoke(method, args));
  }

  @Test
  public void methodWithOneParam_float() throws Exception {
    String methodName = "methodWithOneParam";
    Object[] args = new Object[] {1.0f};
    Method method = reflectionHelper.lookupMethod(clazz, args, methodName);

    assertNotNull(method);
    assertEquals(methodName, method.getName());
    assertEquals("methodWithOneFloatParam", invoke(method, args));
  }

  @Test
  public void methodWithOneParam_double() throws Exception {
    String methodName = "methodWithOneParam";
    Object[] args = new Object[] {1.0d};
    Method method = reflectionHelper.lookupMethod(clazz, args, methodName);

    assertNotNull(method);
    assertEquals(methodName, method.getName());
    assertEquals("methodWithOneDoubleParam", invoke(method, args));
  }

  /**
   * Asserts that if in the absence of a method with the wrapper type(s), the method with the
   * primitive type(s) is returned even if the arguments are autoboxed.
   */
  @Test
  public void methodWithOneFloatAndDoubleParam() throws Exception {
    String methodName = "methodWithOneFloatAndDoubleParam";
    Object[] args = new Object[] {1.0f, 1.0d};
    Method method = reflectionHelper.lookupMethod(clazz, args, methodName);

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
    Method method = reflectionHelper.lookupMethod(clazz, args, methodName);

    assertNotNull(method);
    assertEquals(methodName, method.getName());
    assertEquals("methodWithOneCharacterParam", invoke(method, args));
  }

  @Test
  public void methodWithOneParam_Boolean() throws Exception {
    String methodName = "methodWithOneParam";
    Object[] args = new Object[] {true};
    Method method = reflectionHelper.lookupMethod(clazz, args, methodName);

    assertNotNull(method);
    assertEquals(methodName, method.getName());
    assertEquals("methodWithOneBooleanParam", invoke(method, args));
  }

  @Test
  public void methodWithOneParam_Byte() throws Exception {
    String methodName = "methodWithOneParam";
    Object[] args = new Object[] {(byte) 1};
    Method method = reflectionHelper.lookupMethod(clazz, args, methodName);

    assertNotNull(method);
    assertEquals(methodName, method.getName());
    assertEquals("methodWithOneByteParam", invoke(method, args));
  }

  @Test
  public void methodWithOneParam_Short() throws Exception {
    String methodName = "methodWithOneParam";
    Object[] args = new Object[] {(short) 1};
    Method method = reflectionHelper.lookupMethod(clazz, args, methodName);

    assertNotNull(method);
    assertEquals(methodName, method.getName());
    assertEquals("methodWithOneShortParam", invoke(method, args));
  }

  @Test
  public void methodWithOneParam_Integer() throws Exception {
    String methodName = "methodWithOneParam";
    Object[] args = new Object[] {1};
    Method method = reflectionHelper.lookupMethod(clazz, args, methodName);

    assertNotNull(method);
    assertEquals(methodName, method.getName());
    assertEquals("methodWithOneIntegerParam", invoke(method, args));
  }

  @Test
  public void methodWithOneParam_Long() throws Exception {
    String methodName = "methodWithOneParam";
    Object[] args = new Object[] {1L};
    Method method = reflectionHelper.lookupMethod(clazz, args, methodName);

    assertNotNull(method);
    assertEquals(methodName, method.getName());
    assertEquals("methodWithOneLongParam", invoke(method, args));
  }

  @Test
  public void methodWithOneParam_Float() throws Exception {
    String methodName = "methodWithOneParam";
    Object[] args = new Object[] {1.0f};
    Method method = reflectionHelper.lookupMethod(clazz, args, methodName);

    assertNotNull(method);
    assertEquals(methodName, method.getName());
    assertEquals("methodWithOneFloatParam", invoke(method, args));
  }

  @Test
  public void methodWithOneParam_Double() throws Exception {
    String methodName = "methodWithOneParam";
    Object[] args = new Object[] {1.0d};
    Method method = reflectionHelper.lookupMethod(clazz, args, methodName);

    assertNotNull(method);
    assertEquals(methodName, method.getName());
    assertEquals("methodWithOneDoubleParam", invoke(method, args));
  }
  // </editor-fold>

  // <editor-fold desc="Test caching">
  @Test
  public void testCaching() throws Exception {
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
    Method method = spyReflectionHelper.lookupMethod(clazz, args, methodName);
    assertNotNull(method);
    assertEquals("methodForCacheTest", invoke(method, args));

    verify(spyReflectionHelper, times(1))
        .lookupInCache(clazz, methodName, expectedButNotGivenParamTypes, Method.class);
    assertEquals(0, cacheHits.get());

    // 2nd call to lookupMethod - this time the method should be retrieved from the cache
    method = spyReflectionHelper.lookupMethod(clazz, args, methodName);
    assertNotNull(method);
    assertEquals("methodForCacheTest", invoke(method, args));
    verify(spyReflectionHelper, times(2))
        .lookupInCache(clazz, methodName, expectedButNotGivenParamTypes, Method.class);

    // verify cache was hit once
    assertEquals(1, cacheHits.get());
  }
  // </editor-fold>

  // <editor-fold desc="Test exceptions">
  @Test(expected = NoSuchMethodException.class)
  public void method_noMatchingParams_noSuchMethodException() throws Exception {
    Object[] args = new Object[] {1.0f, 1.0f, 1, 1};
    String methodName = "ghostMethod";
    reflectionHelper.lookupMethod(clazz, args, methodName);
  }

  @Test
  @Ignore
  public void typesGiven_twoMatchingMethods_ambiguousCallException() throws Exception {
    Object[] args = new Object[] {new Object(), 1};
    try {
      reflectionHelper.lookupMethod(clazz, args, "methodWithObjectAndNumber");
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
