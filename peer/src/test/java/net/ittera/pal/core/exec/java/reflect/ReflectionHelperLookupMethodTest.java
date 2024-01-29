package net.ittera.pal.core.exec.java.reflect;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.times;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import net.ittera.pal.core.exec.java.AmbiguousCallException;
import org.junit.Ignore;
import org.junit.Test;

/**
 *
 *
 * <pre>
 * For primitives & wrapper classes:
 * - one test with the primitive type
 * - one test with the wrapper type in canonical form (ie. "java.lang.Integer")
 * - one test with the wrapper type specified in short form (ie. "Integer" instead of "java.lang.Integer")
 * TODO: ARRAYS
 * TODO: VARARGS
 * </pre>
 *
 * <p>TODO Test ambiguous calls -> also in corresponding Dispatcher class
 */
public class ReflectionHelperLookupMethodTest {

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
  public void methodWithOneParam_stringFQN() throws Exception {
    String methodName = "methodWithOneParam";
    Object[] args = new Object[] {"str1"};
    Method method =
        reflectionHelper.lookupMethod(
            clazz, args, Collections.singletonList("java.lang.String"), methodName);

    assertNotNull(method);
    assertEquals(methodName, method.getName());
    assertEquals("methodWithOneStringParam", invoke(method, args));
  }

  @Test
  public void methodWithOneParam_stringShortName() throws Exception {
    String methodName = "methodWithOneParam";
    Object[] args = new Object[] {"str1"};
    Method method =
        reflectionHelper.lookupMethod(clazz, args, Collections.singletonList("String"), methodName);

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
        reflectionHelper.lookupMethod(clazz, args, Collections.singletonList("char"), methodName);

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
            clazz, args, Collections.singletonList("boolean"), methodName);

    assertNotNull(method);
    assertEquals(methodName, method.getName());
    assertEquals("methodWithOne_booleanParam", invoke(method, args));
  }

  @Test
  public void methodWithOneParam_byte() throws Exception {
    String methodName = "methodWithOneParam";
    Object[] args = new Object[] {(byte) 1};
    Method method =
        reflectionHelper.lookupMethod(clazz, args, Collections.singletonList("byte"), methodName);

    assertNotNull(method);
    assertEquals(methodName, method.getName());
    assertEquals("methodWithOne_byteParam", invoke(method, args));
  }

  @Test
  public void methodWithOneParam_short() throws Exception {
    String methodName = "methodWithOneParam";
    Object[] args = new Object[] {(short) 1};
    Method method =
        reflectionHelper.lookupMethod(clazz, args, Collections.singletonList("short"), methodName);

    assertNotNull(method);
    assertEquals(methodName, method.getName());
    assertEquals("methodWithOne_shortParam", invoke(method, args));
  }

  @Test
  public void methodWithOneParam_int() throws Exception {
    String methodName = "methodWithOneParam";
    Object[] args = new Object[] {1};
    Method method =
        reflectionHelper.lookupMethod(clazz, args, Collections.singletonList("int"), methodName);

    assertNotNull(method);
    assertEquals(methodName, method.getName());
    assertEquals("methodWithOne_intParam", invoke(method, args));
  }

  @Test
  public void methodWithOneParam_long() throws Exception {
    String methodName = "methodWithOneParam";
    Object[] args = new Object[] {1L};
    Method method =
        reflectionHelper.lookupMethod(clazz, args, Collections.singletonList("long"), methodName);

    assertNotNull(method);
    assertEquals(methodName, method.getName());
    assertEquals("methodWithOne_longParam", invoke(method, args));
  }

  @Test
  public void methodWithOneParam_float() throws Exception {
    String methodName = "methodWithOneParam";
    Object[] args = new Object[] {1.0f};
    Method method =
        reflectionHelper.lookupMethod(clazz, args, Collections.singletonList("float"), methodName);

    assertNotNull(method);
    assertEquals(methodName, method.getName());
    assertEquals("methodWithOne_floatParam", invoke(method, args));
  }

  @Test
  public void methodWithOneParam_double() throws Exception {
    String methodName = "methodWithOneParam";
    Object[] args = new Object[] {1.0d};
    Method method =
        reflectionHelper.lookupMethod(clazz, args, Collections.singletonList("double"), methodName);

    assertNotNull(method);
    assertEquals(methodName, method.getName());
    assertEquals("methodWithOne_doubleParam", invoke(method, args));
  }

  // </editor-fold>

  // <editor-fold desc="Wrappers">

  @Test
  public void methodWithOneParam_Character_canonicalName() throws Exception {
    String methodName = "methodWithOneParam";
    Object[] args = new Object[] {'a'};
    Method method =
        reflectionHelper.lookupMethod(
            clazz, args, Collections.singletonList("java.lang.Character"), methodName);

    assertNotNull(method);
    assertEquals(methodName, method.getName());
    assertEquals("methodWithOneCharacterParam", invoke(method, args));
  }

  @Test
  public void methodWithOneParam_Character_shortName() throws Exception {
    String methodName = "methodWithOneParam";
    Object[] args = new Object[] {'a'};
    Method method =
        reflectionHelper.lookupMethod(
            clazz, args, Collections.singletonList("Character"), methodName);

    assertNotNull(method);
    assertEquals(methodName, method.getName());
    assertEquals("methodWithOneCharacterParam", invoke(method, args));
  }

  @Test
  public void methodWithOneParam_Boolean_canonicalName() throws Exception {
    String methodName = "methodWithOneParam";
    Object[] args = new Object[] {true};
    Method method =
        reflectionHelper.lookupMethod(
            clazz, args, Collections.singletonList("java.lang.Boolean"), methodName);

    assertNotNull(method);
    assertEquals(methodName, method.getName());
    assertEquals("methodWithOneBooleanParam", invoke(method, args));
  }

  @Test
  public void methodWithOneParam_Boolean_shortName() throws Exception {
    String methodName = "methodWithOneParam";
    Object[] args = new Object[] {true};
    Method method =
        reflectionHelper.lookupMethod(
            clazz, args, Collections.singletonList("Boolean"), methodName);

    assertNotNull(method);
    assertEquals(methodName, method.getName());
    assertEquals("methodWithOneBooleanParam", invoke(method, args));
  }

  @Test
  public void methodWithOneParam_Byte_canonicalName() throws Exception {
    String methodName = "methodWithOneParam";
    Object[] args = new Object[] {(byte) 1};
    Method method =
        reflectionHelper.lookupMethod(
            clazz, args, Collections.singletonList("java.lang.Byte"), methodName);

    assertNotNull(method);
    assertEquals(methodName, method.getName());
    assertEquals("methodWithOneByteParam", invoke(method, args));
  }

  @Test
  public void methodWithOneParam_Byte_shortName() throws Exception {
    String methodName = "methodWithOneParam";
    Object[] args = new Object[] {(byte) 1};
    Method method =
        reflectionHelper.lookupMethod(clazz, args, Collections.singletonList("Byte"), methodName);

    assertNotNull(method);
    assertEquals(methodName, method.getName());
    assertEquals("methodWithOneByteParam", invoke(method, args));
  }

  @Test
  public void methodWithOneParam_Short_canonicalName() throws Exception {
    String methodName = "methodWithOneParam";
    Object[] args = new Object[] {(short) 1};
    Method method =
        reflectionHelper.lookupMethod(
            clazz, args, Collections.singletonList("java.lang.Short"), methodName);

    assertNotNull(method);
    assertEquals(methodName, method.getName());
    assertEquals("methodWithOneShortParam", invoke(method, args));
  }

  @Test
  public void methodWithOneParam_Short_shortName() throws Exception {
    String methodName = "methodWithOneParam";
    Object[] args = new Object[] {(short) 1};
    Method method =
        reflectionHelper.lookupMethod(clazz, args, Collections.singletonList("Short"), methodName);

    assertNotNull(method);
    assertEquals(methodName, method.getName());
    assertEquals("methodWithOneShortParam", invoke(method, args));
  }

  @Test
  public void methodWithOneParam_Integer_canonicalName() throws Exception {
    String methodName = "methodWithOneParam";
    Object[] args = new Object[] {1};
    Method method =
        reflectionHelper.lookupMethod(
            clazz, args, Collections.singletonList("java.lang.Integer"), methodName);

    assertNotNull(method);
    assertEquals(methodName, method.getName());
    assertEquals("methodWithOneIntegerParam", invoke(method, args));
  }

  @Test
  public void methodWithOneParam_Integer_shortName() throws Exception {
    String methodName = "methodWithOneParam";
    Object[] args = new Object[] {1};
    Method method =
        reflectionHelper.lookupMethod(
            clazz, args, Collections.singletonList("Integer"), methodName);

    assertNotNull(method);
    assertEquals(methodName, method.getName());
    assertEquals("methodWithOneIntegerParam", invoke(method, args));
  }

  @Test
  public void methodWithOneParam_Long_canonicalName() throws Exception {
    String methodName = "methodWithOneParam";
    Object[] args = new Object[] {1L};
    Method method =
        reflectionHelper.lookupMethod(
            clazz, args, Collections.singletonList("java.lang.Long"), methodName);

    assertNotNull(method);
    assertEquals(methodName, method.getName());
    assertEquals("methodWithOneLongParam", invoke(method, args));
  }

  @Test
  public void methodWithOneParam_Long_shortName() throws Exception {
    String methodName = "methodWithOneParam";
    Object[] args = new Object[] {1L};
    Method method =
        reflectionHelper.lookupMethod(clazz, args, Collections.singletonList("Long"), methodName);

    assertNotNull(method);
    assertEquals(methodName, method.getName());
    assertEquals("methodWithOneLongParam", invoke(method, args));
  }

  @Test
  public void methodWithOneParam_Float_canonicalName() throws Exception {
    String methodName = "methodWithOneParam";
    Object[] args = new Object[] {1.0f};
    Method method =
        reflectionHelper.lookupMethod(
            clazz, args, Collections.singletonList("java.lang.Float"), methodName);

    assertNotNull(method);
    assertEquals(methodName, method.getName());
    assertEquals("methodWithOneFloatParam", invoke(method, args));
  }

  @Test
  public void methodWithOneParam_Float_shortName() throws Exception {
    String methodName = "methodWithOneParam";
    Object[] args = new Object[] {1.0f};
    Method method =
        reflectionHelper.lookupMethod(clazz, args, Collections.singletonList("Float"), methodName);

    assertNotNull(method);
    assertEquals(methodName, method.getName());
    assertEquals("methodWithOneFloatParam", invoke(method, args));
  }

  @Test
  public void methodWithOneParam_Double_canonicalName() throws Exception {
    String methodName = "methodWithOneParam";
    Object[] args = new Object[] {1.0d};
    Method method =
        reflectionHelper.lookupMethod(
            clazz, args, Collections.singletonList("java.lang.Double"), methodName);

    assertNotNull(method);
    assertEquals(methodName, method.getName());
    assertEquals("methodWithOneDoubleParam", invoke(method, args));
  }

  @Test
  public void methodWithOneParam_Double_shortName() throws Exception {
    String methodName = "methodWithOneParam";
    Object[] args = new Object[] {1.0d};
    Method method =
        reflectionHelper.lookupMethod(clazz, args, Collections.singletonList("Double"), methodName);

    assertNotNull(method);
    assertEquals(methodName, method.getName());
    assertEquals("methodWithOneDoubleParam", invoke(method, args));
  }
  // </editor-fold>

  // <editor-fold desc="Test caching">
  @Test
  public void testCaching() throws Exception {
    List<String> paramTypes =
        Arrays.asList("java.lang.String", "java.lang.Float", "java.lang.String");
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
  @Test(expected = NoSuchMethodException.class)
  public void method_noMatchingParams_noSuchMethodException() throws Exception {
    Object[] args = new Object[] {1.0f, 1.0f, 1, 1};
    String methodName = "ghostMethod";
    Method method =
        reflectionHelper.lookupMethod(
            clazz, args, Arrays.asList("float", "float", "int", "int"), methodName);
  }

  @Ignore
  @Test(expected = AmbiguousCallException.class)
  public void method_twoMatchingMethods_ambiguousCallException() throws Exception {
    Object[] args = new Object[] {new Object(), 1};
    Constructor<?> constructor =
        reflectionHelper.lookupConstructor(
            clazz, args, Arrays.asList("java.lang.Object", "java.lang.Object"));
  }

  // </editor-fold>
}
