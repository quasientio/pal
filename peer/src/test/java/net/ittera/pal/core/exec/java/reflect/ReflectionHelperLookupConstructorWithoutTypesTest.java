package net.ittera.pal.core.exec.java.reflect;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import net.ittera.pal.core.exec.java.AmbiguousCallException;
import org.junit.Ignore;
import org.junit.Test;

/**
 * This class is used for testing ReflectionHelper.lookupConstructor(). It contains the same methods
 * as ReflectionHelperLookupConstructorWithTypesTest, but without specifying parameter types.
 *
 * <p>The differences when sending no parameter types to lookupConstructor() are:
 *
 * <ul>
 *   <li>with primitive types, and if there is a constructor that takes the primitive type and
 *       another one that takes the corresponding wrapper type, lookupConstructor() will return the
 *       method that matches the wrapper types instead of the one with primitive type, because the
 *       argument is autoboxed when added to Object[] args, and therefore it's type is the wrapper's
 *       class.
 *   <li>if there is no corresponding method with the wrapper type, lookupConstructor() will return
 *       the method with the primitive type(s), even if the arguments were autoboxed. This is
 *       asserted by the test method <b>constructorWithOneFloatAndDoubleParam</b>.
 *   <li>when arrays are passed as arguments, the method that will be returned depends on the type
 *       of the array. For example, if an array of double is passed, the method that takes a
 *       double[] will be returned. In contrast, when types are given, the method that takes an
 *       array of Number will be returned if parameter type is Number[], even if the argument is an
 *       array of double. See array tests in ReflectionHelperLookupConstructorWithTypesTest.
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
 * TODO: add tests for generics
 * </pre>
 */
public class ReflectionHelperLookupConstructorWithoutTypesTest {

  private final ReflectionHelper reflectionHelper = new ReflectionHelper();
  private final Class<?> clazz = ClassForTestingConstructorLookup.class;

  private Object invoke(Constructor<?> constructor) throws Exception {
    ClassForTestingConstructorLookup instance =
        (ClassForTestingConstructorLookup) constructor.newInstance((Object[]) null);
    return instance.getParam();
  }

  private Object invoke(Constructor<?> constructor, Object[] args) throws Exception {
    ClassForTestingConstructorLookup instance =
        (ClassForTestingConstructorLookup) constructor.newInstance(args);
    return instance.getParam();
  }

  @Test
  public void constructorWithNoParams() throws Exception {
    Constructor<?> constructor = reflectionHelper.lookupConstructor(clazz, new Object[] {});

    assertNotNull(constructor);
    assertEquals("noParams", invoke(constructor));
  }

  // <editor-fold desc="String">
  @Test
  public void constructorWithOneParam_string() throws Exception {
    Object[] args = new Object[] {"str1"};
    Constructor<?> constructor = reflectionHelper.lookupConstructor(clazz, args);

    assertNotNull(constructor);
    assertEquals("stringParam", invoke(constructor, args));
  }
  // </editor-fold>

  // <editor-fold desc="Primitives">
  @Test
  public void constructorWithOneParam_char() throws Exception {
    Object[] args = new Object[] {'a'};
    Constructor<?> constructor = reflectionHelper.lookupConstructor(clazz, args);

    assertNotNull(constructor);
    assertEquals("CharacterParam", invoke(constructor, args));
  }

  @Test
  public void constructorWithOneParam_boolean() throws Exception {
    Object[] args = new Object[] {true};
    Constructor<?> constructor = reflectionHelper.lookupConstructor(clazz, args);

    assertNotNull(constructor);
    assertEquals("BooleanParam", invoke(constructor, args));
  }

  @Test
  public void constructorWithOneParam_byte() throws Exception {
    Object[] args = new Object[] {(byte) 1};
    Constructor<?> constructor = reflectionHelper.lookupConstructor(clazz, args);

    assertNotNull(constructor);
    assertEquals("ByteParam", invoke(constructor, args));
  }

  @Test
  public void constructorWithOneParam_short() throws Exception {
    Object[] args = new Object[] {(short) 1};
    Constructor<?> constructor = reflectionHelper.lookupConstructor(clazz, args);

    assertNotNull(constructor);
    assertEquals("ShortParam", invoke(constructor, args));
  }

  @Test
  public void constructorWithOneParam_int() throws Exception {
    Object[] args = new Object[] {1};
    Constructor<?> constructor = reflectionHelper.lookupConstructor(clazz, args);

    assertNotNull(constructor);
    assertEquals("IntegerParam", invoke(constructor, args));
  }

  @Test
  public void constructorWithOneParam_long() throws Exception {
    Object[] args = new Object[] {1L};
    Constructor<?> constructor = reflectionHelper.lookupConstructor(clazz, args);

    assertNotNull(constructor);
    assertEquals("LongParam", invoke(constructor, args));
  }

  @Test
  public void constructorWithOneParam_float() throws Exception {
    Object[] args = new Object[] {1.0f};
    Constructor<?> constructor = reflectionHelper.lookupConstructor(clazz, args);

    assertNotNull(constructor);
    assertEquals("FloatParam", invoke(constructor, args));
  }

  @Test
  public void constructorWithOneParam_double() throws Exception {
    Object[] args = new Object[] {1.0d};
    Constructor<?> constructor = reflectionHelper.lookupConstructor(clazz, args);

    assertNotNull(constructor);
    assertEquals("DoubleParam", invoke(constructor, args));
  }
  /**
   * Asserts that if in the absence of a method with the wrapper type(s), the constructor with the
   * primitive type(s) is returned even if the arguments are autoboxed.
   */
  @Test
  public void constructorWithOneFloatAndDoubleParam() throws Exception {
    Object[] args = new Object[] {true, 1.0f, 1.0d};
    Constructor<?> constructor = reflectionHelper.lookupConstructor(clazz, args);
    assertNotNull(constructor);
    assertEquals("boolFloatAndDoubleParams", invoke(constructor, args));
  }

  // </editor-fold>

  // <editor-fold desc="Wrappers">

  @Test
  public void constructorWithOneParam_Character() throws Exception {
    Object[] args = new Object[] {'a'};
    Constructor<?> constructor = reflectionHelper.lookupConstructor(clazz, args);

    assertNotNull(constructor);
    assertEquals("CharacterParam", invoke(constructor, args));
  }

  @Test
  public void constructorWithOneParam_Boolean() throws Exception {
    Object[] args = new Object[] {true};
    Constructor<?> constructor = reflectionHelper.lookupConstructor(clazz, args);

    assertNotNull(constructor);
    assertEquals("BooleanParam", invoke(constructor, args));
  }

  @Test
  public void constructorWithOneParam_Byte() throws Exception {
    Object[] args = new Object[] {(byte) 1};
    Constructor<?> constructor = reflectionHelper.lookupConstructor(clazz, args);

    assertNotNull(constructor);
    assertEquals("ByteParam", invoke(constructor, args));
  }

  @Test
  public void constructorWithOneParam_Short() throws Exception {
    Object[] args = new Object[] {(short) 1};
    Constructor<?> constructor = reflectionHelper.lookupConstructor(clazz, args);

    assertNotNull(constructor);
    assertEquals("ShortParam", invoke(constructor, args));
  }

  @Test
  public void constructorWithOneParam_Integer() throws Exception {
    Object[] args = new Object[] {1};
    Constructor<?> constructor = reflectionHelper.lookupConstructor(clazz, args);

    assertNotNull(constructor);
    assertEquals("IntegerParam", invoke(constructor, args));
  }

  @Test
  public void constructorWithOneParam_Long() throws Exception {
    Object[] args = new Object[] {1L};
    Constructor<?> constructor = reflectionHelper.lookupConstructor(clazz, args);

    assertNotNull(constructor);
    assertEquals("LongParam", invoke(constructor, args));
  }

  @Test
  public void constructorWithOneParam_Float() throws Exception {
    Object[] args = new Object[] {1.0f};
    Constructor<?> constructor = reflectionHelper.lookupConstructor(clazz, args);

    assertNotNull(constructor);
    assertEquals("FloatParam", invoke(constructor, args));
  }

  @Test
  public void constructorWithOneParam_Double() throws Exception {
    Object[] args = new Object[] {1.0d};
    Constructor<?> constructor = reflectionHelper.lookupConstructor(clazz, args);

    assertNotNull(constructor);
    assertEquals("DoubleParam", invoke(constructor, args));
  }
  // </editor-fold>

  // <editor-fold desc="Arrays testing">
  @Test
  public void constructorWithArrayOfDoublePrimitive() throws Exception {
    Object[] args = new Object[] {new double[] {1.0d, 4.6d}};
    Constructor<?> constructor = reflectionHelper.lookupConstructor(clazz, args);
    assertNotNull(constructor);
    assertEquals("doubleArrayParam", invoke(constructor, args));
  }

  @Test
  public void constructorWithArrayOfDoubleWrapper() throws Exception {
    Object[] args = new Object[] {new Double[] {1.0d, 4.6d}};
    Constructor<?> constructor = reflectionHelper.lookupConstructor(clazz, args);
    assertNotNull(constructor);
    assertEquals("DoubleArrayParam", invoke(constructor, args));
  }

  @Test
  public void constructorWithArrayOfNumber() throws Exception {
    Object[] args = new Object[] {new Number[] {1.0d, 4.6d}};
    Constructor<?> constructor = reflectionHelper.lookupConstructor(clazz, args);
    assertNotNull(constructor);
    assertEquals("NumberArrayParam", invoke(constructor, args));
  }

  @Test
  public void constructorWithArrayOfObject() throws Exception {
    Object[] args = new Object[] {new Object[] {1.0d, 4.6d}};
    Constructor<?> constructor = reflectionHelper.lookupConstructor(clazz, args);
    assertNotNull(constructor);
    assertEquals("ObjectArrayParam", invoke(constructor, args));
  }

  @Test
  public void constructorWithFloatVarargs() throws Exception {
    Object[] args = new Object[] {new Float[] {1.0f, 20f, 3.013f}};
    Constructor<?> constructor = reflectionHelper.lookupConstructor(clazz, args);
    assertNotNull(constructor);
    assertEquals("FloatVarargs", invoke(constructor, args));
  }
  // </editor-fold>

  // <editor-fold desc="Test caching">
  @Test
  public void testCaching() throws Exception {
    List<Class<?>> expectedButNotGivenParamTypes =
        Arrays.asList(Integer.class, Float.class, String.class);
    Object[] args = new Object[] {10, 4.9f, "str123"};

    AtomicInteger cacheHits = new AtomicInteger(0);

    // spy on reflectionHelper to check for cache hits
    ReflectionHelper spyReflectionHelper = spy(reflectionHelper);

    // check the return value of lookupInCache()
    doAnswer(
            invocation -> {
              Constructor<?> returnedConstructor = (Constructor<?>) invocation.callRealMethod();
              if (returnedConstructor != null) {
                cacheHits.incrementAndGet();
              }
              return returnedConstructor;
            })
        .when(spyReflectionHelper)
        .lookupInCache(clazz, null, expectedButNotGivenParamTypes, Constructor.class);

    // call lookupConstructor using the spy
    Constructor<?> constructor = spyReflectionHelper.lookupConstructor(clazz, args);
    assertNotNull(constructor);
    assertEquals("IntegerFloatStringParams", invoke(constructor, args));
    verify(spyReflectionHelper, times(1))
        .lookupInCache(clazz, null, expectedButNotGivenParamTypes, Constructor.class);
    assertEquals(0, cacheHits.get());

    // 2nd call to lookupConstructor - this time the constructor should be retrieved from the cache
    constructor = spyReflectionHelper.lookupConstructor(clazz, args);
    assertNotNull(constructor);
    assertEquals("IntegerFloatStringParams", invoke(constructor, args));
    verify(spyReflectionHelper, times(2))
        .lookupInCache(clazz, null, expectedButNotGivenParamTypes, Constructor.class);

    // verify that the cache was hit once
    assertEquals(1, cacheHits.get());
  }
  // </editor-fold>

  // <editor-fold desc="Test exceptions">
  @Test(expected = NoSuchMethodException.class)
  public void constructor_noMatchingParams_noSuchMethodException() throws Exception {
    Object[] args = new Object[] {1.0f, 1.0f, 1, 1};
    reflectionHelper.lookupConstructor(clazz, args);
  }

  @Test
  @Ignore
  public void constructor_twoMatchingConstructors_ambiguousCallException() throws Exception {
    Object[] args = new Object[] {new Object(), 1};
    try {
      reflectionHelper.lookupConstructor(clazz, args);
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
