package net.ittera.pal.core.exec.java.reflect;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.lang.reflect.Constructor;
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
public class ReflectionHelperLookupConstructorTest {

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
    Constructor<?> constructor =
        reflectionHelper.lookupConstructor(clazz, new Object[] {}, Collections.emptyList());

    assertNotNull(constructor);
    assertEquals("noParams", invoke(constructor));
  }

  // <editor-fold desc="String">
  @Test
  public void constructorWithOneParam_stringFQN() throws Exception {
    Object[] args = new Object[] {"str1"};
    Constructor<?> constructor =
        reflectionHelper.lookupConstructor(
            clazz, args, Collections.singletonList("java.lang.String"));

    assertNotNull(constructor);
    assertEquals("stringParam", invoke(constructor, args));
  }

  @Test
  public void constructorWithOneParam_stringShortName() throws Exception {
    Object[] args = new Object[] {"str1"};
    Constructor<?> constructor =
        reflectionHelper.lookupConstructor(clazz, args, Collections.singletonList("String"));

    assertNotNull(constructor);
    assertEquals("stringParam", invoke(constructor, args));
  }
  // </editor-fold>

  // <editor-fold desc="Primitives">
  @Test
  public void constructorWithOneParam_char() throws Exception {
    Object[] args = new Object[] {'a'};
    Constructor<?> constructor =
        reflectionHelper.lookupConstructor(clazz, args, Collections.singletonList("char"));

    assertNotNull(constructor);
    assertEquals("charParam", invoke(constructor, args));
  }

  @Test
  public void constructorWithOneParam_boolean() throws Exception {
    Object[] args = new Object[] {true};
    Constructor<?> constructor =
        reflectionHelper.lookupConstructor(clazz, args, Collections.singletonList("boolean"));

    assertNotNull(constructor);
    assertEquals("booleanParam", invoke(constructor, args));
  }

  @Test
  public void constructorWithOneParam_byte() throws Exception {
    Object[] args = new Object[] {(byte) 1};
    Constructor<?> constructor =
        reflectionHelper.lookupConstructor(clazz, args, Collections.singletonList("byte"));

    assertNotNull(constructor);
    assertEquals("byteParam", invoke(constructor, args));
  }

  @Test
  public void constructorWithOneParam_short() throws Exception {
    Object[] args = new Object[] {(short) 1};
    Constructor<?> constructor =
        reflectionHelper.lookupConstructor(clazz, args, Collections.singletonList("short"));

    assertNotNull(constructor);
    assertEquals("shortParam", invoke(constructor, args));
  }

  @Test
  public void constructorWithOneParam_int() throws Exception {
    Object[] args = new Object[] {1};
    Constructor<?> constructor =
        reflectionHelper.lookupConstructor(clazz, args, Collections.singletonList("int"));

    assertNotNull(constructor);
    assertEquals("intParam", invoke(constructor, args));
  }

  @Test
  public void constructorWithOneParam_long() throws Exception {
    Object[] args = new Object[] {1L};
    Constructor<?> constructor =
        reflectionHelper.lookupConstructor(clazz, args, Collections.singletonList("long"));

    assertNotNull(constructor);
    assertEquals("longParam", invoke(constructor, args));
  }

  @Test
  public void constructorWithOneParam_float() throws Exception {
    Object[] args = new Object[] {1.0f};
    Constructor<?> constructor =
        reflectionHelper.lookupConstructor(clazz, args, Collections.singletonList("float"));

    assertNotNull(constructor);
    assertEquals("floatParam", invoke(constructor, args));
  }

  @Test
  public void constructorWithOneParam_double() throws Exception {
    Object[] args = new Object[] {1.0d};
    Constructor<?> constructor =
        reflectionHelper.lookupConstructor(clazz, args, Collections.singletonList("double"));

    assertNotNull(constructor);
    assertEquals("doubleParam", invoke(constructor, args));
  }

  // </editor-fold>

  // <editor-fold desc="Wrappers">

  @Test
  public void constructorWithOneParam_Character_canonicalName() throws Exception {
    Object[] args = new Object[] {'a'};
    Constructor<?> constructor =
        reflectionHelper.lookupConstructor(
            clazz, args, Collections.singletonList("java.lang.Character"));

    assertNotNull(constructor);
    assertEquals("CharacterParam", invoke(constructor, args));
  }

  @Test
  public void constructorWithOneParam_Character_shortName() throws Exception {
    Object[] args = new Object[] {'a'};
    Constructor<?> constructor =
        reflectionHelper.lookupConstructor(clazz, args, Collections.singletonList("Character"));

    assertNotNull(constructor);
    assertEquals("CharacterParam", invoke(constructor, args));
  }

  @Test
  public void constructorWithOneParam_Boolean_canonicalName() throws Exception {
    Object[] args = new Object[] {true};
    Constructor<?> constructor =
        reflectionHelper.lookupConstructor(
            clazz, args, Collections.singletonList("java.lang.Boolean"));

    assertNotNull(constructor);
    assertEquals("BooleanParam", invoke(constructor, args));
  }

  @Test
  public void constructorWithOneParam_Boolean_shortName() throws Exception {
    Object[] args = new Object[] {true};
    Constructor<?> constructor =
        reflectionHelper.lookupConstructor(clazz, args, Collections.singletonList("Boolean"));

    assertNotNull(constructor);
    assertEquals("BooleanParam", invoke(constructor, args));
  }

  @Test
  public void constructorWithOneParam_Byte_canonicalName() throws Exception {
    Object[] args = new Object[] {(byte) 1};
    Constructor<?> constructor =
        reflectionHelper.lookupConstructor(
            clazz, args, Collections.singletonList("java.lang.Byte"));

    assertNotNull(constructor);
    assertEquals("ByteParam", invoke(constructor, args));
  }

  @Test
  public void constructorWithOneParam_Byte_shortName() throws Exception {
    Object[] args = new Object[] {(byte) 1};
    Constructor<?> constructor =
        reflectionHelper.lookupConstructor(clazz, args, Collections.singletonList("Byte"));

    assertNotNull(constructor);
    assertEquals("ByteParam", invoke(constructor, args));
  }

  @Test
  public void constructorWithOneParam_Short_canonicalName() throws Exception {
    Object[] args = new Object[] {(short) 1};
    Constructor<?> constructor =
        reflectionHelper.lookupConstructor(
            clazz, args, Collections.singletonList("java.lang.Short"));

    assertNotNull(constructor);
    assertEquals("ShortParam", invoke(constructor, args));
  }

  @Test
  public void constructorWithOneParam_Short_shortName() throws Exception {
    Object[] args = new Object[] {(short) 1};
    Constructor<?> constructor =
        reflectionHelper.lookupConstructor(clazz, args, Collections.singletonList("Short"));

    assertNotNull(constructor);
    assertEquals("ShortParam", invoke(constructor, args));
  }

  @Test
  public void constructorWithOneParam_Integer_canonicalName() throws Exception {
    Object[] args = new Object[] {1};
    Constructor<?> constructor =
        reflectionHelper.lookupConstructor(
            clazz, args, Collections.singletonList("java.lang.Integer"));

    assertNotNull(constructor);
    assertEquals("IntegerParam", invoke(constructor, args));
  }

  @Test
  public void constructorWithOneParam_Integer_shortName() throws Exception {
    Object[] args = new Object[] {1};
    Constructor<?> constructor =
        reflectionHelper.lookupConstructor(clazz, args, Collections.singletonList("Integer"));

    assertNotNull(constructor);
    assertEquals("IntegerParam", invoke(constructor, args));
  }

  @Test
  public void constructorWithOneParam_Long_canonicalName() throws Exception {
    Object[] args = new Object[] {1L};
    Constructor<?> constructor =
        reflectionHelper.lookupConstructor(
            clazz, args, Collections.singletonList("java.lang.Long"));

    assertNotNull(constructor);
    assertEquals("LongParam", invoke(constructor, args));
  }

  @Test
  public void constructorWithOneParam_Long_shortName() throws Exception {
    Object[] args = new Object[] {1L};
    Constructor<?> constructor =
        reflectionHelper.lookupConstructor(clazz, args, Collections.singletonList("Long"));

    assertNotNull(constructor);
    assertEquals("LongParam", invoke(constructor, args));
  }

  @Test
  public void constructorWithOneParam_Float_canonicalName() throws Exception {
    Object[] args = new Object[] {1.0f};
    Constructor<?> constructor =
        reflectionHelper.lookupConstructor(
            clazz, args, Collections.singletonList("java.lang.Float"));

    assertNotNull(constructor);
    assertEquals("FloatParam", invoke(constructor, args));
  }

  @Test
  public void constructorWithOneParam_Float_shortName() throws Exception {
    Object[] args = new Object[] {1.0f};
    Constructor<?> constructor =
        reflectionHelper.lookupConstructor(clazz, args, Collections.singletonList("Float"));

    assertNotNull(constructor);
    assertEquals("FloatParam", invoke(constructor, args));
  }

  @Test
  public void constructorWithOneParam_Double_canonicalName() throws Exception {
    Object[] args = new Object[] {1.0d};
    Constructor<?> constructor =
        reflectionHelper.lookupConstructor(
            clazz, args, Collections.singletonList("java.lang.Double"));

    assertNotNull(constructor);
    assertEquals("DoubleParam", invoke(constructor, args));
  }

  @Test
  public void constructorWithOneParam_Double_shortName() throws Exception {
    Object[] args = new Object[] {1.0d};
    Constructor<?> constructor =
        reflectionHelper.lookupConstructor(clazz, args, Collections.singletonList("Double"));

    assertNotNull(constructor);
    assertEquals("DoubleParam", invoke(constructor, args));
  }
  // </editor-fold>

  // <editor-fold desc="Test caching">
  @Test
  public void testCaching() throws Exception {
    List<String> paramTypes =
        Arrays.asList("java.lang.Integer", "java.lang.Float", "java.lang.String");
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
        .lookupInCache(clazz, paramTypes);

    // call lookupConstructor using the spy
    Constructor<?> constructor = spyReflectionHelper.lookupConstructor(clazz, args, paramTypes);
    assertNotNull(constructor);
    assertEquals("IntegerFloatStringParams", invoke(constructor, args));
    verify(spyReflectionHelper, times(1)).lookupInCache(clazz, paramTypes);
    assertEquals(0, cacheHits.get());

    // 2nd call to lookupConstructor - this time the constructor should be retrieved from the cache
    constructor = spyReflectionHelper.lookupConstructor(clazz, args, paramTypes);
    assertNotNull(constructor);
    assertEquals("IntegerFloatStringParams", invoke(constructor, args));
    verify(spyReflectionHelper, times(2)).lookupInCache(clazz, paramTypes);

    // verify that the cache was hit once
    assertEquals(1, cacheHits.get());
  }
  // </editor-fold>

  // <editor-fold desc="Test exceptions">
  @Test(expected = NoSuchMethodException.class)
  public void constructor_noMatchingParams_noSuchMethodException() throws Exception {
    Object[] args = new Object[] {1.0f, 1.0f, 1, 1};
    Constructor<?> constructor =
        reflectionHelper.lookupConstructor(
            clazz, args, Arrays.asList("float", "float", "int", "int"));
  }

  @Ignore
  @Test(expected = AmbiguousCallException.class)
  public void constructor_twoMatchingConstructors_ambiguousCallException() throws Exception {
    Object[] args = new Object[] {new Object(), 1};
    Constructor<?> constructor =
        reflectionHelper.lookupConstructor(
            clazz, args, Arrays.asList("java.lang.Object", "java.lang.Object"));
  }

  // </editor-fold>
}
