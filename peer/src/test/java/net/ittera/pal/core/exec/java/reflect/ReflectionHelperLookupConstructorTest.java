package net.ittera.pal.core.exec.java.reflect;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.junit.Assert.*;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import net.ittera.pal.core.exec.java.AmbiguousCallException;
import org.junit.Ignore;
import org.junit.Test;

/**
 * This class is used for testing ReflectionHelper.lookupConstructor(). It contains the same methods
 * as ReflectionHelperLookupConstructorTest, but without specifying parameter types.
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
 *       array of double. See array tests in ReflectionHelperLookupConstructorTest.
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
public class ReflectionHelperLookupConstructorTest extends AbstractReflectionHelperTestBase {

  private final ReflectionHelper reflectionHelper = new ReflectionHelper();
  private final ReflectionHelper reflectionHelperWithNonPublicAccess = new ReflectionHelper(true);
  private final Class<?> clazz = ClassForTestingConstructorLookup.class;

  @Override
  protected Class<?> getTestClass() {
    return clazz;
  }

  private Object invoke(Constructor<?> constructor) throws Exception {
    return invoke(constructor, null);
  }

  @Override
  protected Object invoke(Executable executable, Object[] args) throws Exception {
    ClassForTestingConstructorLookup instance =
        (ClassForTestingConstructorLookup) super.invoke(executable, args);
    return instance.getParam();
  }

  @Test
  public void constructorWithNoParams() throws Exception {
    Constructor<?> constructor =
        reflectionHelper.lookupConstructor(clazz, new Object[] {}, Collections.emptyList());

    assertNotNull(constructor);
    assertEquals("noParams", invoke(constructor));
  }

  // <editor-fold desc="String">
  @Test
  public void constructorWithOneParam_string() throws Exception {
    Object[] args = new Object[] {"str1"};
    Constructor<?> constructor =
        reflectionHelper.lookupConstructor(clazz, args, Collections.singletonList(String.class));

    assertNotNull(constructor);
    assertEquals("stringParam", invoke(constructor, args));
  }

  @Test
  public void noTypes_constructorWithOneParam_string() throws Exception {
    Object[] args = new Object[] {"str1"};
    Constructor<?> constructor =
        reflectionHelper.lookupConstructor(clazz, args, Collections.singletonList(null));

    assertNotNull(constructor);
    assertEquals("stringParam", invoke(constructor, args));
  }
  // </editor-fold>

  // <editor-fold desc="Primitives">
  @Test
  public void constructorWithOneParam_char() throws Exception {
    Object[] args = new Object[] {'a'};
    Constructor<?> constructor =
        reflectionHelper.lookupConstructor(clazz, args, Collections.singletonList(Character.TYPE));

    assertNotNull(constructor);
    assertEquals("charParam", invoke(constructor, args));
  }

  @Test
  public void noTypes_constructorWithOneParam_char() throws Exception {
    Object[] args = new Object[] {'a'};
    Constructor<?> constructor =
        reflectionHelper.lookupConstructor(clazz, args, Collections.singletonList(null));

    assertNotNull(constructor);
    assertEquals("CharacterParam", invoke(constructor, args));
  }

  @Test
  public void constructorWithOneParam_boolean() throws Exception {
    Object[] args = new Object[] {true};
    Constructor<?> constructor =
        reflectionHelper.lookupConstructor(clazz, args, Collections.singletonList(Boolean.TYPE));

    assertNotNull(constructor);
    assertEquals("booleanParam", invoke(constructor, args));
  }

  @Test
  public void noTypes_constructorWithOneParam_boolean() throws Exception {
    Object[] args = new Object[] {true};
    Constructor<?> constructor =
        reflectionHelper.lookupConstructor(clazz, args, Collections.singletonList(null));

    assertNotNull(constructor);
    assertEquals("BooleanParam", invoke(constructor, args));
  }

  @Test
  public void constructorWithOneParam_byte() throws Exception {
    Object[] args = new Object[] {(byte) 1};
    Constructor<?> constructor =
        reflectionHelper.lookupConstructor(clazz, args, Collections.singletonList(Byte.TYPE));

    assertNotNull(constructor);
    assertEquals("byteParam", invoke(constructor, args));
  }

  @Test
  public void noTypes_constructorWithOneParam_byte() throws Exception {
    Object[] args = new Object[] {(byte) 1};
    Constructor<?> constructor =
        reflectionHelper.lookupConstructor(clazz, args, Collections.singletonList(null));

    assertNotNull(constructor);
    assertEquals("ByteParam", invoke(constructor, args));
  }

  @Test
  public void constructorWithOneParam_short() throws Exception {
    Object[] args = new Object[] {(short) 1};
    Constructor<?> constructor =
        reflectionHelper.lookupConstructor(clazz, args, Collections.singletonList(Short.TYPE));

    assertNotNull(constructor);
    assertEquals("shortParam", invoke(constructor, args));
  }

  @Test
  public void noTypes_constructorWithOneParam_short() throws Exception {
    Object[] args = new Object[] {(short) 1};
    Constructor<?> constructor =
        reflectionHelper.lookupConstructor(clazz, args, Collections.singletonList(null));

    assertNotNull(constructor);
    assertEquals("ShortParam", invoke(constructor, args));
  }

  @Test
  public void constructorWithOneParam_int() throws Exception {
    Object[] args = new Object[] {1};
    Constructor<?> constructor =
        reflectionHelper.lookupConstructor(clazz, args, Collections.singletonList(Integer.TYPE));

    assertNotNull(constructor);
    assertEquals("intParam", invoke(constructor, args));
  }

  @Test
  public void noTypes_constructorWithOneParam_int() throws Exception {
    Object[] args = new Object[] {1};
    Constructor<?> constructor =
        reflectionHelper.lookupConstructor(clazz, args, Collections.singletonList(null));

    assertNotNull(constructor);
    assertEquals("IntegerParam", invoke(constructor, args));
  }

  @Test
  public void constructorWithOneParam_long() throws Exception {
    Object[] args = new Object[] {1L};
    Constructor<?> constructor =
        reflectionHelper.lookupConstructor(clazz, args, Collections.singletonList(Long.TYPE));

    assertNotNull(constructor);
    assertEquals("longParam", invoke(constructor, args));
  }

  @Test
  public void noTypes_constructorWithOneParam_long() throws Exception {
    Object[] args = new Object[] {1L};
    Constructor<?> constructor =
        reflectionHelper.lookupConstructor(clazz, args, Collections.singletonList(null));

    assertNotNull(constructor);
    assertEquals("LongParam", invoke(constructor, args));
  }

  @Test
  public void constructorWithOneParam_float() throws Exception {
    Object[] args = new Object[] {1.0f};
    Constructor<?> constructor =
        reflectionHelper.lookupConstructor(clazz, args, Collections.singletonList(Float.TYPE));

    assertNotNull(constructor);
    assertEquals("floatParam", invoke(constructor, args));
  }

  @Test
  public void noTypes_constructorWithOneParam_float() throws Exception {
    Object[] args = new Object[] {1.0f};
    Constructor<?> constructor =
        reflectionHelper.lookupConstructor(clazz, args, Collections.singletonList(null));

    assertNotNull(constructor);
    assertEquals("FloatParam", invoke(constructor, args));
  }

  @Test
  public void constructorWithOneParam_double() throws Exception {
    Object[] args = new Object[] {1.0d};
    Constructor<?> constructor =
        reflectionHelper.lookupConstructor(clazz, args, Collections.singletonList(Double.TYPE));

    assertNotNull(constructor);
    assertEquals("doubleParam", invoke(constructor, args));
  }

  @Test
  public void noTypes_constructorWithOneParam_double() throws Exception {
    Object[] args = new Object[] {1.0d};
    Constructor<?> constructor =
        reflectionHelper.lookupConstructor(clazz, args, Collections.singletonList(null));

    assertNotNull(constructor);
    assertEquals("DoubleParam", invoke(constructor, args));
  }
  /**
   * Asserts that if in the absence of a method with the wrapper type(s), the constructor with the
   * primitive type(s) is returned even if the arguments are autoboxed.
   */
  @Test
  public void noTypes_constructorWithBooleanFloatAndDoubleParam() throws Exception {
    Object[] args = new Object[] {true, 1.0f, 1.0d};
    Constructor<?> constructor =
        reflectionHelper.lookupConstructor(clazz, args, Arrays.asList(null, null, null));
    assertNotNull(constructor);
    assertEquals("boolFloatAndDoubleParams", invoke(constructor, args));
  }

  // </editor-fold>

  // <editor-fold desc="Wrappers">

  @Test
  public void constructorWithOneParam_Character() throws Exception {
    Object[] args = new Object[] {'a'};
    Constructor<?> constructor =
        reflectionHelper.lookupConstructor(clazz, args, Collections.singletonList(Character.class));

    assertNotNull(constructor);
    assertEquals("CharacterParam", invoke(constructor, args));
  }

  @Test
  public void noTypes_constructorWithOneParam_Character() throws Exception {
    Object[] args = new Object[] {'a'};
    Constructor<?> constructor =
        reflectionHelper.lookupConstructor(clazz, args, Collections.singletonList(null));

    assertNotNull(constructor);
    assertEquals("CharacterParam", invoke(constructor, args));
  }

  @Test
  public void constructorWithOneParam_Boolean() throws Exception {
    Object[] args = new Object[] {true};
    Constructor<?> constructor =
        reflectionHelper.lookupConstructor(clazz, args, Collections.singletonList(Boolean.class));

    assertNotNull(constructor);
    assertEquals("BooleanParam", invoke(constructor, args));
  }

  @Test
  public void noTypes_constructorWithOneParam_Boolean() throws Exception {
    Object[] args = new Object[] {true};
    Constructor<?> constructor =
        reflectionHelper.lookupConstructor(clazz, args, Collections.singletonList(null));

    assertNotNull(constructor);
    assertEquals("BooleanParam", invoke(constructor, args));
  }

  @Test
  public void constructorWithOneParam_Byte() throws Exception {
    Object[] args = new Object[] {(byte) 1};
    Constructor<?> constructor =
        reflectionHelper.lookupConstructor(clazz, args, Collections.singletonList(Byte.class));

    assertNotNull(constructor);
    assertEquals("ByteParam", invoke(constructor, args));
  }

  @Test
  public void noTypes_constructorWithOneParam_Byte() throws Exception {
    Object[] args = new Object[] {(byte) 1};
    Constructor<?> constructor =
        reflectionHelper.lookupConstructor(clazz, args, Collections.singletonList(null));

    assertNotNull(constructor);
    assertEquals("ByteParam", invoke(constructor, args));
  }

  @Test
  public void constructorWithOneParam_Short() throws Exception {
    Object[] args = new Object[] {(short) 1};
    Constructor<?> constructor =
        reflectionHelper.lookupConstructor(clazz, args, Collections.singletonList(Short.class));

    assertNotNull(constructor);
    assertEquals("ShortParam", invoke(constructor, args));
  }

  @Test
  public void noTypes_constructorWithOneParam_Short() throws Exception {
    Object[] args = new Object[] {(short) 1};
    Constructor<?> constructor =
        reflectionHelper.lookupConstructor(clazz, args, Collections.singletonList(null));

    assertNotNull(constructor);
    assertEquals("ShortParam", invoke(constructor, args));
  }

  @Test
  public void constructorWithOneParam_Integer() throws Exception {
    Object[] args = new Object[] {1};
    Constructor<?> constructor =
        reflectionHelper.lookupConstructor(clazz, args, Collections.singletonList(Integer.class));

    assertNotNull(constructor);
    assertEquals("IntegerParam", invoke(constructor, args));
  }

  @Test
  public void noTypes_constructorWithOneParam_Integer() throws Exception {
    Object[] args = new Object[] {1};
    Constructor<?> constructor =
        reflectionHelper.lookupConstructor(clazz, args, Collections.singletonList(null));

    assertNotNull(constructor);
    assertEquals("IntegerParam", invoke(constructor, args));
  }

  @Test
  public void constructorWithOneParam_Long() throws Exception {
    Object[] args = new Object[] {1L};
    Constructor<?> constructor =
        reflectionHelper.lookupConstructor(clazz, args, Collections.singletonList(Long.class));

    assertNotNull(constructor);
    assertEquals("LongParam", invoke(constructor, args));
  }

  @Test
  public void noTypes_constructorWithOneParam_Long() throws Exception {
    Object[] args = new Object[] {1L};
    Constructor<?> constructor =
        reflectionHelper.lookupConstructor(clazz, args, Collections.singletonList(null));

    assertNotNull(constructor);
    assertEquals("LongParam", invoke(constructor, args));
  }

  @Test
  public void constructorWithOneParam_Float() throws Exception {
    Object[] args = new Object[] {1.0f};
    Constructor<?> constructor =
        reflectionHelper.lookupConstructor(clazz, args, Collections.singletonList(Float.class));

    assertNotNull(constructor);
    assertEquals("FloatParam", invoke(constructor, args));
  }

  @Test
  public void noTypes_constructorWithOneParam_Float() throws Exception {
    Object[] args = new Object[] {1.0f};
    Constructor<?> constructor =
        reflectionHelper.lookupConstructor(clazz, args, Collections.singletonList(null));

    assertNotNull(constructor);
    assertEquals("FloatParam", invoke(constructor, args));
  }

  @Test
  public void constructorWithOneParam_Double() throws Exception {
    Object[] args = new Object[] {1.0d};
    Constructor<?> constructor =
        reflectionHelper.lookupConstructor(clazz, args, Collections.singletonList(Double.class));

    assertNotNull(constructor);
    assertEquals("DoubleParam", invoke(constructor, args));
  }

  @Test
  public void noTypes_constructorWithOneParam_Double() throws Exception {
    Object[] args = new Object[] {1.0d};
    Constructor<?> constructor =
        reflectionHelper.lookupConstructor(clazz, args, Collections.singletonList(null));

    assertNotNull(constructor);
    assertEquals("DoubleParam", invoke(constructor, args));
  }
  // </editor-fold>

  // <editor-fold desc="Arrays testing">
  @Test
  public void constructorWithArrayOfDoublePrimitive() throws Exception {
    Object[] args = new Object[] {new double[] {1.0d, 4.6d}};
    Constructor<?> constructor =
        reflectionHelper.lookupConstructor(clazz, args, Collections.singletonList(double[].class));
    assertNotNull(constructor);
    assertEquals("doubleArrayParam", invoke(constructor, args));
  }

  @Test
  public void noTypes_constructorWithArrayOfDoublePrimitive() throws Exception {
    Object[] args = new Object[] {new double[] {1.0d, 4.6d}};
    Constructor<?> constructor =
        reflectionHelper.lookupConstructor(clazz, args, Collections.singletonList(null));
    assertNotNull(constructor);
    assertEquals("doubleArrayParam", invoke(constructor, args));
  }

  @Test
  public void constructorWithArrayOfDoubleWrapper() throws Exception {
    Object[] args = new Object[] {new Double[] {1.0d, 4.6d}};
    Constructor<?> constructor =
        reflectionHelper.lookupConstructor(clazz, args, Collections.singletonList(Double[].class));
    assertNotNull(constructor);
    assertEquals("DoubleArrayParam", invoke(constructor, args));
  }

  @Test
  public void noTypes_constructorWithArrayOfDoubleWrapper() throws Exception {
    Object[] args = new Object[] {new Double[] {1.0d, 4.6d}};
    Constructor<?> constructor =
        reflectionHelper.lookupConstructor(clazz, args, Collections.singletonList(null));
    assertNotNull(constructor);
    assertEquals("DoubleArrayParam", invoke(constructor, args));
  }

  @Test
  public void constructorWithArrayOfNumber() throws Exception {

    // passing the args inside a Number[] array
    Object[] args = new Object[] {new Number[] {1.0d, 4.6d}};
    Constructor<?> constructor =
        reflectionHelper.lookupConstructor(clazz, args, Collections.singletonList(Number[].class));
    assertNotNull(constructor);
    assertEquals("NumberArrayParam", invoke(constructor, args));

    // passing the args inside a Double[] array
    args = new Object[] {new Double[] {1.0d, 4.6d}};
    constructor =
        reflectionHelper.lookupConstructor(clazz, args, Collections.singletonList(Number[].class));
    assertNotNull(constructor);
    assertEquals("NumberArrayParam", invoke(constructor, args));
  }

  @Test
  public void noTypes_constructorWithArrayOfNumber() throws Exception {
    Object[] args = new Object[] {new Number[] {1.0d, 4.6d}};
    Constructor<?> constructor =
        reflectionHelper.lookupConstructor(clazz, args, Collections.singletonList(null));
    assertNotNull(constructor);
    assertEquals("NumberArrayParam", invoke(constructor, args));
  }

  @Test
  public void constructorWithArrayOfObject() throws Exception {
    // passing the args inside an Object[] array
    Object[] args = new Object[] {new Object[] {1.0d, 4.6d}};
    Constructor<?> constructor =
        reflectionHelper.lookupConstructor(clazz, args, Collections.singletonList(Object[].class));
    assertNotNull(constructor);
    assertEquals("ObjectArrayParam", invoke(constructor, args));

    // passing the args inside a Number[] array
    args = new Object[] {new Number[] {1.0d, 4.6d}};
    constructor =
        reflectionHelper.lookupConstructor(clazz, args, Collections.singletonList(Object[].class));
    assertNotNull(constructor);
    assertEquals("ObjectArrayParam", invoke(constructor, args));

    // passing the args inside a Double[] array
    args = new Object[] {new Double[] {1.0d, 4.6d}};
    constructor =
        reflectionHelper.lookupConstructor(clazz, args, Collections.singletonList(Object[].class));
    assertNotNull(constructor);
    assertEquals("ObjectArrayParam", invoke(constructor, args));
  }

  @Test
  public void noTypes_constructorWithArrayOfObject() throws Exception {
    Object[] args = new Object[] {new Object[] {1.0d, 4.6d}};
    Constructor<?> constructor =
        reflectionHelper.lookupConstructor(clazz, args, Collections.singletonList(null));
    assertNotNull(constructor);
    assertEquals("ObjectArrayParam", invoke(constructor, args));
  }
  // </editor-fold>

  // <editor-fold desc="Varargs">
  @Test
  public void constructorWithFloatVarargs() throws Exception {
    Object[] args = new Object[] {new Float[] {1.0f, 20f, 3.013f}};
    Constructor<?> constructor =
        reflectionHelper.lookupConstructor(clazz, args, Collections.singletonList(Float[].class));
    assertNotNull(constructor);
    assertEquals("FloatVarargs", invoke(constructor, args));
  }

  @Test
  public void noTypes_constructorWithFloatVarargs() throws Exception {
    Object[] args = new Object[] {new Float[] {1.0f, 20f, 3.013f}};
    Constructor<?> constructor =
        reflectionHelper.lookupConstructor(clazz, args, Collections.singletonList(null));
    assertNotNull(constructor);
    assertEquals("FloatVarargs", invoke(constructor, args));
  }

  @Test
  public void constructorWithStringVarargsStringArray() throws Exception {
    Object[] args = new Object[] {12, new String[] {"str1", "str2", "str3"}};
    Constructor<?> constructor =
        reflectionHelper.lookupConstructor(
            clazz, args, Arrays.asList(Integer.TYPE, String[].class));
    assertNotNull(constructor);
    assertEquals("constructorWithStringVarargs", invoke(constructor, args));
  }

  @Test
  public void noTypes_constructorWithStringVarargsStringArray() throws Exception {
    Object[] args = new Object[] {12, new String[] {"str1", "str2", "str3"}};
    Constructor<?> constructor =
        reflectionHelper.lookupConstructor(clazz, args, Arrays.asList(null, null));
    assertNotNull(constructor);
    assertEquals("constructorWithStringVarargs", invoke(constructor, args));
  }

  @Test
  public void constructorWithObjectVarargs() throws Exception {
    Object[] args = new Object[] {12, new Object[] {"str1", "str2", "str3"}};
    Constructor<?> constructor =
        reflectionHelper.lookupConstructor(
            clazz, args, Arrays.asList(Integer.TYPE, Object[].class));
    assertNotNull(constructor);
    assertEquals("constructorWithObjectVarargs", invoke(constructor, args));
  }

  @Test
  public void noTypes_constructorWithObjectVarargs() throws Exception {
    Object[] args = new Object[] {12, new Object[] {"str1", "str2", "str3"}};
    Constructor<?> constructor =
        reflectionHelper.lookupConstructor(clazz, args, Arrays.asList(null, null));
    assertNotNull(constructor);
    assertEquals("constructorWithObjectVarargs", invoke(constructor, args));
  }

  /**
   * Lookup constructor that has a varargs parameter of type int, passing argument and param type of
   * int array
   *
   * @throws Exception
   */
  @Test
  public void constructorWithIntVarargsWithTypeIntArray() throws Exception {
    Object[] args = new Object[] {"waa", new int[] {45, 56}};
    Constructor<?> constructor =
        reflectionHelper.lookupConstructor(clazz, args, Arrays.asList(String.class, int[].class));
    assertNotNull(constructor);
    assertEquals("constructorWithIntVarargs", invoke(constructor, args));
  }
  /**
   * Lookup constructor that has a varargs parameter of type int, passing argument and param type of
   * int (i.e. the component type of the array)
   *
   * @throws Exception
   */
  @Test
  public void constructorWithIntVarargsWithTypeInt() throws Exception {
    Object[] args = new Object[] {"waa", 45};
    Constructor<?> constructor =
        reflectionHelper.lookupConstructor(clazz, args, Arrays.asList(String.class, Integer.TYPE));
    assertNotNull(constructor);
    assertEquals("constructorWithIntVarargs", invoke(constructor, args));
  }

  /**
   * Lookup constructor that has a varargs parameter of type int, passing argument of type int (i.e.
   * the component type of the array), but no parameter types
   *
   * @throws Exception
   */
  @Test
  public void noTypes_constructorWithIntVarargsWithTypeInt() throws Exception {
    Object[] args = new Object[] {"waa", 45};
    Constructor<?> constructor =
        reflectionHelper.lookupConstructor(clazz, args, Arrays.asList(null, null));
    assertNotNull(constructor);
    assertEquals("constructorWithIntVarargs", invoke(constructor, args));
  }
  // </editor-fold>

  // <editor-fold desc="Test caching">
  @Test
  public void testCaching() throws Exception {
    List<Class<?>> paramTypes = Arrays.asList(Integer.class, Float.class, String.class);
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
        .lookupInCache(clazz, null, paramTypes, Constructor.class);

    // call lookupConstructor using the spy
    Constructor<?> constructor = spyReflectionHelper.lookupConstructor(clazz, args, paramTypes);
    assertNotNull(constructor);
    assertEquals("IntegerFloatStringParams", invoke(constructor, args));
    verify(spyReflectionHelper, times(1)).lookupInCache(clazz, null, paramTypes, Constructor.class);
    assertEquals(0, cacheHits.get());

    // 2nd call to lookupConstructor - this time the constructor should be retrieved from the cache
    constructor = spyReflectionHelper.lookupConstructor(clazz, args, paramTypes);
    assertNotNull(constructor);
    assertEquals("IntegerFloatStringParams", invoke(constructor, args));
    verify(spyReflectionHelper, times(2)).lookupInCache(clazz, null, paramTypes, Constructor.class);

    // verify that the cache was hit once
    assertEquals(1, cacheHits.get());
  }

  @Test
  public void noTypes_testCaching() throws Exception {
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
    Constructor<?> constructor =
        spyReflectionHelper.lookupConstructor(clazz, args, Arrays.asList(null, null, null));
    assertNotNull(constructor);
    assertEquals("IntegerFloatStringParams", invoke(constructor, args));
    verify(spyReflectionHelper, times(1))
        .lookupInCache(clazz, null, expectedButNotGivenParamTypes, Constructor.class);
    assertEquals(0, cacheHits.get());

    // 2nd call to lookupConstructor - this time the constructor should be retrieved from the cache
    constructor =
        spyReflectionHelper.lookupConstructor(clazz, args, Arrays.asList(null, null, null));
    assertNotNull(constructor);
    assertEquals("IntegerFloatStringParams", invoke(constructor, args));
    verify(spyReflectionHelper, times(2))
        .lookupInCache(clazz, null, expectedButNotGivenParamTypes, Constructor.class);

    // verify that the cache was hit once
    assertEquals(1, cacheHits.get());
  }
  // </editor-fold>

  // <editor-fold desc="Visibility testing">
  @Test
  public void publicConstructor() throws Exception {
    Object[] args = new Object[] {(byte) 3, (byte) 7};
    Constructor<?> constructor =
        reflectionHelper.lookupConstructor(clazz, args, Arrays.asList(Byte.TYPE, Byte.TYPE));

    assertNotNull(constructor);
    assertEquals("publicConstructor", invoke(constructor, args));
  }

  @Test(expected = NoSuchMethodException.class)
  public void privateConstructor() throws Exception {
    Object[] args = new Object[] {(byte) 3, (byte) 7, 4};
    reflectionHelper.lookupConstructor(
        clazz, args, Arrays.asList(Byte.TYPE, Byte.TYPE, Integer.TYPE));
  }

  @Test
  public void privateConstructor_withNonPublicReflectionHelper() throws Exception {
    Object[] args = new Object[] {(byte) 3, (byte) 7, 4};
    Constructor<?> constructor =
        reflectionHelperWithNonPublicAccess.lookupConstructor(
            clazz, args, Arrays.asList(Byte.TYPE, Byte.TYPE, Integer.TYPE));

    assertNotNull(constructor);
    assertEquals("privateConstructor", invoke(constructor, args));
  }

  @Test(expected = NoSuchMethodException.class)
  public void protectedConstructor() throws Exception {
    Object[] args = new Object[] {(byte) 3, (byte) 7, (short) 4};
    reflectionHelper.lookupConstructor(
        clazz, args, Arrays.asList(Byte.TYPE, Byte.TYPE, Short.TYPE));
  }

  @Test
  public void protectedConstructor_withNonPublicReflectionHelper() throws Exception {
    Object[] args = new Object[] {(byte) 3, (byte) 7, (short) 4};
    Constructor<?> constructor =
        reflectionHelperWithNonPublicAccess.lookupConstructor(
            clazz, args, Arrays.asList(Byte.TYPE, Byte.TYPE, Short.TYPE));

    assertNotNull(constructor);
    assertEquals("protectedConstructor", invoke(constructor, args));
  }

  @Test(expected = NoSuchMethodException.class)
  public void packageProtectedConstructor() throws Exception {
    Object[] args = new Object[] {(byte) 3, (byte) 7, 4L};
    reflectionHelper.lookupConstructor(clazz, args, Arrays.asList(Byte.TYPE, Byte.TYPE, Long.TYPE));
  }

  @Test
  public void packageProtectedConstructor_withNonPublicReflectionHelper() throws Exception {
    Object[] args = new Object[] {(byte) 3, (byte) 7, 4L};
    Constructor<?> constructor =
        reflectionHelperWithNonPublicAccess.lookupConstructor(
            clazz, args, Arrays.asList(Byte.TYPE, Byte.TYPE, Long.TYPE));

    assertNotNull(constructor);
    assertEquals("packageProtectedConstructor", invoke(constructor, args));
  }
  // </editor-fold>

  // <editor-fold desc="Test misc exceptions">
  @Test(expected = IllegalArgumentException.class)
  public void constructor_paramAndParamTypesOfDifferentLength_illegalArgumentException()
      throws Exception {
    Object[] args = new Object[] {1.0f, 1.0f, 1};
    reflectionHelper.lookupConstructor(
        clazz, args, Arrays.asList(Float.TYPE, Float.TYPE, Integer.TYPE, Integer.TYPE));
  }

  @Test(expected = NoSuchMethodException.class)
  public void constructor_noMatchingParams_noSuchMethodException() throws Exception {
    Object[] args = new Object[] {1.0f, 1.0f, 1, 1};
    reflectionHelper.lookupConstructor(
        clazz, args, Arrays.asList(Float.TYPE, Float.TYPE, Integer.TYPE, Integer.TYPE));
  }

  @Test(expected = NoSuchMethodException.class)
  public void noTypes_constructor_noMatchingParams_noSuchMethodException() throws Exception {
    Object[] args = new Object[] {1.0f, 1.0f, 1, 1};
    reflectionHelper.lookupConstructor(clazz, args, Arrays.asList(null, null, null, null));
  }

  @Test
  @Ignore
  public void constructor_twoMatchingConstructors_ambiguousCallException() throws Exception {
    Object[] args = new Object[] {new Object(), 1};
    try {
      reflectionHelper.lookupConstructor(clazz, args, Arrays.asList(Object.class, Number.class));
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

  @Test
  @Ignore
  public void noTypes_constructor_twoMatchingConstructors_ambiguousCallException()
      throws Exception {
    Object[] args = new Object[] {new Object(), 1};
    try {
      reflectionHelper.lookupConstructor(clazz, args, Arrays.asList(null, null));
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
