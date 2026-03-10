/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.core.execution.java.reflect;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import org.apache.commons.lang3.ClassUtils;
import org.junit.Test;

/**
 * Unit tests for {@link ReflectionHelper} focusing on edge cases in reflection utilities.
 *
 * <p>Covers primitive handling, method lookup, arrays, primitive-wrapper compatibility, and
 * instance creation scenarios.
 */
public class ReflectionHelperTest {

  /** ReflectionHelper instance with default configuration (always allows non-public access). */
  private final ReflectionHelper reflectionHelper = new ReflectionHelper();

  // ========================================================================
  // Test: unwrapPrimitive_allPrimitiveTypes_unwrapsCorrectly
  // ========================================================================

  /**
   * Tests that ReflectionHelper correctly handles unwrapping of all 8 wrapper types when looking up
   * methods that take primitive parameters.
   *
   * <p>Given: Wrapper objects for all 8 primitive types (Boolean, Byte, Character, Short, Integer,
   * Long, Float, Double)
   *
   * <p>When: lookupMethod is called with wrapper values but primitive parameter types
   *
   * <p>Then: The correct method taking primitive parameters is found and can be invoked
   */
  @Test
  public void unwrapPrimitive_allPrimitiveTypes_unwrapsCorrectly() throws Exception {
    // Given: Wrapper objects for all 8 primitive types and a class with methods for each
    Class<?> clazz = ClassForTestingMethodLookup.class;

    // Test Boolean -> boolean
    Object[] boolArgs = new Object[] {Boolean.TRUE};
    Method boolMethod =
        reflectionHelper.lookupMethod(
            clazz, boolArgs, Collections.singletonList(Boolean.TYPE), "methodWithOneParam");
    assertNotNull("Should find method taking boolean", boolMethod);
    assertEquals(Boolean.TYPE, boolMethod.getParameterTypes()[0]);

    // Test Byte -> byte
    Object[] byteArgs = new Object[] {Byte.valueOf((byte) 42)};
    Method byteMethod =
        reflectionHelper.lookupMethod(
            clazz, byteArgs, Collections.singletonList(Byte.TYPE), "methodWithOneParam");
    assertNotNull("Should find method taking byte", byteMethod);
    assertEquals(Byte.TYPE, byteMethod.getParameterTypes()[0]);

    // Test Character -> char
    Object[] charArgs = new Object[] {Character.valueOf('A')};
    Method charMethod =
        reflectionHelper.lookupMethod(
            clazz, charArgs, Collections.singletonList(Character.TYPE), "methodWithOneParam");
    assertNotNull("Should find method taking char", charMethod);
    assertEquals(Character.TYPE, charMethod.getParameterTypes()[0]);

    // Test Short -> short
    Object[] shortArgs = new Object[] {Short.valueOf((short) 100)};
    Method shortMethod =
        reflectionHelper.lookupMethod(
            clazz, shortArgs, Collections.singletonList(Short.TYPE), "methodWithOneParam");
    assertNotNull("Should find method taking short", shortMethod);
    assertEquals(Short.TYPE, shortMethod.getParameterTypes()[0]);

    // Test Integer -> int
    Object[] intArgs = new Object[] {Integer.valueOf(12345)};
    Method intMethod =
        reflectionHelper.lookupMethod(
            clazz, intArgs, Collections.singletonList(Integer.TYPE), "methodWithOneParam");
    assertNotNull("Should find method taking int", intMethod);
    assertEquals(Integer.TYPE, intMethod.getParameterTypes()[0]);

    // Test Long -> long
    Object[] longArgs = new Object[] {Long.valueOf(9876543210L)};
    Method longMethod =
        reflectionHelper.lookupMethod(
            clazz, longArgs, Collections.singletonList(Long.TYPE), "methodWithOneParam");
    assertNotNull("Should find method taking long", longMethod);
    assertEquals(Long.TYPE, longMethod.getParameterTypes()[0]);

    // Test Float -> float
    Object[] floatArgs = new Object[] {Float.valueOf(3.14f)};
    Method floatMethod =
        reflectionHelper.lookupMethod(
            clazz, floatArgs, Collections.singletonList(Float.TYPE), "methodWithOneParam");
    assertNotNull("Should find method taking float", floatMethod);
    assertEquals(Float.TYPE, floatMethod.getParameterTypes()[0]);

    // Test Double -> double
    Object[] doubleArgs = new Object[] {Double.valueOf(2.71828)};
    Method doubleMethod =
        reflectionHelper.lookupMethod(
            clazz, doubleArgs, Collections.singletonList(Double.TYPE), "methodWithOneParam");
    assertNotNull("Should find method taking double", doubleMethod);
    assertEquals(Double.TYPE, doubleMethod.getParameterTypes()[0]);
  }

  // ========================================================================
  // Test: findMethod_withPrimitiveParams_findsMethod
  // ========================================================================

  /**
   * Tests that lookupMethod correctly finds methods that take primitive parameters when called with
   * wrapper parameter types due to autoboxing/unboxing compatibility.
   *
   * <p>Given: A class with methods taking primitive parameters (int, double, boolean)
   *
   * <p>When: lookupMethod is called with wrapper types but primitive type hints
   *
   * <p>Then: The method taking primitive parameters is successfully found
   */
  @Test
  public void findMethod_withPrimitiveParams_findsMethod() throws Exception {
    // Given: A class with methods taking primitive parameters
    Class<?> clazz = PrimitiveMethodTestFixture.class;

    // When: lookupMethod is called with wrapper values but primitive type hints
    Object[] intArgs = new Object[] {Integer.valueOf(42)};
    Method intMethod =
        reflectionHelper.lookupMethod(
            clazz, intArgs, Collections.singletonList(Integer.TYPE), "processInt");

    Object[] doubleArgs = new Object[] {Double.valueOf(3.14159)};
    Method doubleMethod =
        reflectionHelper.lookupMethod(
            clazz, doubleArgs, Collections.singletonList(Double.TYPE), "processDouble");

    Object[] boolArgs = new Object[] {Boolean.TRUE};
    Method boolMethod =
        reflectionHelper.lookupMethod(
            clazz, boolArgs, Collections.singletonList(Boolean.TYPE), "processBoolean");

    // Then: The methods taking primitive parameters are found
    assertNotNull("Should find processInt method", intMethod);
    assertEquals("processInt", intMethod.getName());
    assertEquals(Integer.TYPE, intMethod.getParameterTypes()[0]);

    assertNotNull("Should find processDouble method", doubleMethod);
    assertEquals("processDouble", doubleMethod.getName());
    assertEquals(Double.TYPE, doubleMethod.getParameterTypes()[0]);

    assertNotNull("Should find processBoolean method", boolMethod);
    assertEquals("processBoolean", boolMethod.getName());
    assertEquals(Boolean.TYPE, boolMethod.getParameterTypes()[0]);

    // Verify methods can be invoked with wrapper values
    PrimitiveMethodTestFixture instance = new PrimitiveMethodTestFixture();
    assertEquals("int:42", intMethod.invoke(instance, 42));
    assertEquals("double:3.14159", doubleMethod.invoke(instance, 3.14159));
    assertEquals("boolean:true", boolMethod.invoke(instance, true));
  }

  // ========================================================================
  // Test: getArrayComponentType_multiDimensionalArray_returnsBaseType
  // ========================================================================

  /**
   * Tests that multi-dimensional array component types can be correctly extracted down to the base
   * type.
   *
   * <p>Given: Multi-dimensional array classes such as int[][][], String[][], Object[][][][][]
   *
   * <p>When: The base component type is extracted by traversing getComponentType() recursively
   *
   * <p>Then: Returns the base type (int.class, String.class, Object.class)
   */
  @Test
  public void getArrayComponentType_multiDimensionalArray_returnsBaseType() {
    // Given: Multi-dimensional array classes

    // Test 3D int array - int[][][].class
    Class<?> intArray3D = int[][][].class;
    Class<?> intBaseType = getBaseComponentType(intArray3D);
    assertEquals("Base type of int[][][] should be int", int.class, intBaseType);

    // Test 2D String array - String[][].class
    Class<?> stringArray2D = String[][].class;
    Class<?> stringBaseType = getBaseComponentType(stringArray2D);
    assertEquals("Base type of String[][] should be String", String.class, stringBaseType);

    // Test 5D Object array - Object[][][][][].class
    Class<?> objectArray5D = Object[][][][][].class;
    Class<?> objectBaseType = getBaseComponentType(objectArray5D);
    assertEquals("Base type of Object[][][][][] should be Object", Object.class, objectBaseType);

    // Test 1D Double array - Double[].class
    Class<?> doubleArray1D = Double[].class;
    Class<?> doubleBaseType = getBaseComponentType(doubleArray1D);
    assertEquals("Base type of Double[] should be Double", Double.class, doubleBaseType);

    // Test primitive double 2D array
    Class<?> primitiveDouble2D = double[][].class;
    Class<?> primitiveDoubleBaseType = getBaseComponentType(primitiveDouble2D);
    assertEquals("Base type of double[][] should be double", double.class, primitiveDoubleBaseType);
  }

  /**
   * Helper method to extract the base component type from a multi-dimensional array type.
   *
   * @param arrayClass the array class to analyze
   * @return the base component type (non-array type)
   */
  private Class<?> getBaseComponentType(Class<?> arrayClass) {
    Class<?> componentType = arrayClass;
    while (componentType.isArray()) {
      componentType = componentType.getComponentType();
    }
    return componentType;
  }

  // ========================================================================
  // Test: isAssignableFrom_primitiveToWrapper_returnsTrue
  // ========================================================================

  /**
   * Tests that ClassUtils.isAssignable (used by ReflectionHelper) correctly identifies
   * compatibility between primitive types and their corresponding wrapper types.
   *
   * <p>Given: Primitive and wrapper type pairs for all 8 primitives
   *
   * <p>When: isAssignable check is performed in both directions
   *
   * <p>Then: Returns true for all compatible pairs
   */
  @Test
  public void isAssignableFrom_primitiveToWrapper_returnsTrue() {
    // Test all 8 primitive-wrapper pairs using ClassUtils.isAssignable
    // which is the same method used by ReflectionHelper.isAssignable internally

    // boolean <-> Boolean
    assertTrue(
        "boolean should be assignable to Boolean",
        ClassUtils.isAssignable(boolean.class, Boolean.class));
    assertTrue(
        "Boolean should be assignable to boolean",
        ClassUtils.isAssignable(Boolean.class, boolean.class));

    // byte <-> Byte
    assertTrue(
        "byte should be assignable to Byte", ClassUtils.isAssignable(byte.class, Byte.class));
    assertTrue(
        "Byte should be assignable to byte", ClassUtils.isAssignable(Byte.class, byte.class));

    // char <-> Character
    assertTrue(
        "char should be assignable to Character",
        ClassUtils.isAssignable(char.class, Character.class));
    assertTrue(
        "Character should be assignable to char",
        ClassUtils.isAssignable(Character.class, char.class));

    // short <-> Short
    assertTrue(
        "short should be assignable to Short", ClassUtils.isAssignable(short.class, Short.class));
    assertTrue(
        "Short should be assignable to short", ClassUtils.isAssignable(Short.class, short.class));

    // int <-> Integer
    assertTrue(
        "int should be assignable to Integer", ClassUtils.isAssignable(int.class, Integer.class));
    assertTrue(
        "Integer should be assignable to int", ClassUtils.isAssignable(Integer.class, int.class));

    // long <-> Long
    assertTrue(
        "long should be assignable to Long", ClassUtils.isAssignable(long.class, Long.class));
    assertTrue(
        "Long should be assignable to long", ClassUtils.isAssignable(Long.class, long.class));

    // float <-> Float
    assertTrue(
        "float should be assignable to Float", ClassUtils.isAssignable(float.class, Float.class));
    assertTrue(
        "Float should be assignable to float", ClassUtils.isAssignable(Float.class, float.class));

    // double <-> Double
    assertTrue(
        "double should be assignable to Double",
        ClassUtils.isAssignable(double.class, Double.class));
    assertTrue(
        "Double should be assignable to double",
        ClassUtils.isAssignable(Double.class, double.class));
  }

  // ========================================================================
  // Test: createInstance_noArgConstructor_createsInstance
  // ========================================================================

  /**
   * Tests that lookupConstructor correctly finds and allows creation of an instance using a public
   * no-argument constructor.
   *
   * <p>Given: A class with a public no-arg constructor
   *
   * <p>When: lookupConstructor is called with empty parameters
   *
   * <p>Then: A new instance is successfully created
   */
  @Test
  public void createInstance_noArgConstructor_createsInstance() throws Exception {
    // Given: ClassForTestingConstructorLookup has a public no-arg constructor
    Class<?> clazz = ClassForTestingConstructorLookup.class;

    // When: lookupConstructor is called with empty parameters
    Constructor<?> constructor =
        reflectionHelper.lookupConstructor(clazz, new Object[] {}, new ArrayList<>());

    // Then: The constructor is found and can be used to create an instance
    assertNotNull("Should find no-arg constructor", constructor);
    assertEquals(0, constructor.getParameterCount());

    Object instance = constructor.newInstance();
    assertNotNull("Should create a new instance", instance);
    assertTrue("Instance should be of the correct type", clazz.isInstance(instance));

    // Verify via the getParam() method that it was the no-arg constructor
    ClassForTestingConstructorLookup typedInstance = (ClassForTestingConstructorLookup) instance;
    assertEquals("noParams", typedInstance.getParam());
  }

  /**
   * Tests that lookupConstructor can find a private constructor, since the default ReflectionHelper
   * always allows non-public access.
   *
   * @throws Exception if constructor lookup fails unexpectedly
   */
  @Test
  public void createInstance_privateConstructor_succeeds() throws Exception {
    // Given: A class with only private constructors
    Class<?> clazz = PrivateConstructorOnly.class;

    // When: lookupConstructor is called (default allows non-public access)
    Constructor<?> constructor =
        reflectionHelper.lookupConstructor(clazz, new Object[] {}, new ArrayList<>());

    // Then: The private constructor is found
    assertNotNull("Should find private constructor", constructor);
    assertEquals(0, constructor.getParameterCount());

    // Can create instance after setting accessible
    constructor.setAccessible(true);
    Object instance = constructor.newInstance();
    assertNotNull("Should create instance via private constructor", instance);
  }

  // ========================================================================
  // Test Fixture Classes
  // ========================================================================

  /**
   * Test fixture class with methods that take only primitive parameters. Used for testing method
   * lookup with primitive-to-wrapper compatibility.
   */
  @SuppressWarnings("unused")
  public static class PrimitiveMethodTestFixture {

    /**
     * Processes an int value.
     *
     * @param value the int value to process
     * @return a string representation of the processed value
     */
    public String processInt(int value) {
      return "int:" + value;
    }

    /**
     * Processes a double value.
     *
     * @param value the double value to process
     * @return a string representation of the processed value
     */
    public String processDouble(double value) {
      return "double:" + value;
    }

    /**
     * Processes a boolean value.
     *
     * @param value the boolean value to process
     * @return a string representation of the processed value
     */
    public String processBoolean(boolean value) {
      return "boolean:" + value;
    }
  }

  /**
   * Test fixture class with only a private constructor. Used for testing that lookupConstructor
   * correctly finds private constructors.
   */
  public static class PrivateConstructorOnly {

    /** Private no-arg constructor - not accessible via default ReflectionHelper. */
    private PrivateConstructorOnly() {
      // Private constructor for testing
    }
  }

  // ============================================================================
  // ReflectionHelper constructor and configuration tests
  // ============================================================================

  /**
   * Tests that the default no-argument constructor creates a ReflectionHelper that always allows
   * non-public access.
   *
   * <p>Given: No parameters
   *
   * <p>When: The default ReflectionHelper() constructor is called
   *
   * <p>Then: A ReflectionHelper is created that can look up both public and non-public members
   *
   * <p>Acceptance criteria: [TEST:ReflectionHelperTest.testDefaultConstructor_createsHelper]
   */
  @Test
  public void testDefaultConstructor_createsHelper() throws Exception {
    // Given: No parameters
    // When: Constructor called with new ReflectionHelper()
    ReflectionHelper helper = new ReflectionHelper();

    // Then: Helper is created successfully (not null)
    assertNotNull("ReflectionHelper should be created", helper);

    // Verify public method can be found
    Method publicMethod =
        helper.lookupMethod(
            ClassForTestingMethodLookup.class,
            new Object[] {"test"},
            Collections.singletonList(String.class),
            "publicMethodWithOneParam");
    assertNotNull("Should find public method", publicMethod);
    assertEquals("publicMethodWithOneParam", publicMethod.getName());

    // Verify public constructor can be found
    Constructor<?> publicConstructor =
        helper.lookupConstructor(
            ClassForTestingConstructorLookup.class,
            new Object[] {(byte) 1, (byte) 2},
            Arrays.asList(Byte.TYPE, Byte.TYPE));
    assertNotNull("Should find public constructor", publicConstructor);

    // Verify private method lookup succeeds (default always allows non-public access)
    Method privateMethod =
        helper.lookupMethod(
            ClassForTestingMethodLookup.class,
            new Object[] {"test"},
            Collections.singletonList(String.class),
            "privateMethodWithOneParam");
    assertNotNull("Should find private method", privateMethod);
    assertEquals("privateMethodWithOneParam", privateMethod.getName());

    // Verify private constructor lookup succeeds (default always allows non-public access)
    Constructor<?> privateConstructor =
        helper.lookupConstructor(
            ClassForTestingConstructorLookup.class,
            new Object[] {(byte) 1, (byte) 2, 3},
            Arrays.asList(Byte.TYPE, Byte.TYPE, Integer.TYPE));
    assertNotNull("Should find private constructor", privateConstructor);
  }

  /**
   * Tests that narrowDownConstructorMatches correctly selects the best matching constructor when
   * multiple constructors are assignable from the given parameter types.
   *
   * <p>Given: Multiple constructor candidates that are all assignable from the provided parameter
   * types (due to primitive widening or inheritance)
   *
   * <p>When: lookupConstructor is called (which internally uses narrowDownConstructorMatches)
   *
   * <p>Then: The constructor with exact type matches for non-primitive/non-wrapper types is
   * selected, narrowing down from multiple candidates to a single best match
   *
   * <p>Acceptance criteria:
   * [TEST:ReflectionHelperTest.testNarrowDownConstructorMatches_selectsBestMatch]
   */
  @Test
  public void testNarrowDownConstructorMatches_selectsBestMatch() throws Exception {
    // Test case 1: String is more specific than Object or CharSequence
    // Given: Class with constructors for Object, CharSequence, and String
    // When: lookupConstructor called with String parameter
    Constructor<?> stringCtor =
        reflectionHelper.lookupConstructor(
            NarrowingTestFixture.class,
            new Object[] {"test"},
            Collections.singletonList(String.class));

    // Then: The constructor with String.class parameter is selected (exact match)
    assertNotNull("Should find constructor", stringCtor);
    assertEquals(1, stringCtor.getParameterCount());
    assertEquals(
        String.class, stringCtor.getParameterTypes()[0]); // Should select String, not Object

    // Test case 2: With multiple parameters, String is more specific
    // Given: Multiple constructors with different specificity
    Constructor<?> twoStringCtor =
        reflectionHelper.lookupConstructor(
            NarrowingTestFixture.class,
            new Object[] {"a", "b"},
            Arrays.asList(String.class, String.class));

    // Then: The constructor with (String, String) is selected over (String, Object)
    assertNotNull("Should find constructor with two params", twoStringCtor);
    assertEquals(2, twoStringCtor.getParameterCount());
    assertEquals(String.class, twoStringCtor.getParameterTypes()[0]);
    assertEquals(String.class, twoStringCtor.getParameterTypes()[1]);

    // Test case 3: Verify via instantiation that the correct constructor was selected
    NarrowingTestFixture instance = (NarrowingTestFixture) stringCtor.newInstance("narrowing test");
    assertEquals("String", instance.getConstructorUsed());

    NarrowingTestFixture instance2 =
        (NarrowingTestFixture) twoStringCtor.newInstance("first", "second");
    assertEquals("StringString", instance2.getConstructorUsed());
  }

  /**
   * Tests that narrowDownMethodMatches correctly selects the best matching method when multiple
   * methods with the same name are assignable from the given parameter types.
   *
   * <p>Given: Multiple method candidates (overloads) that are all assignable from the provided
   * parameter types
   *
   * <p>When: lookupMethod is called (which internally uses narrowDownMethodMatches)
   *
   * <p>Then: The method with exact type matches for non-primitive/non-wrapper types is selected,
   * narrowing down from multiple candidates to a single best match
   *
   * <p>Acceptance criteria:
   * [TEST:ReflectionHelperTest.testNarrowDownMethodMatches_selectsBestMatch]
   */
  @Test
  public void testNarrowDownMethodMatches_selectsBestMatch() throws Exception {
    // Test case 1: String is more specific than Object
    // Given: Methods process(Object), process(String), process(CharSequence)
    // When: lookupMethod called with String parameter
    Method stringMethod =
        reflectionHelper.lookupMethod(
            NarrowingTestFixture.class,
            new Object[] {"test"},
            Collections.singletonList(String.class),
            "process");

    // Then: The method with String.class parameter is selected (exact match)
    assertNotNull("Should find method", stringMethod);
    assertEquals("process", stringMethod.getName());
    assertEquals(1, stringMethod.getParameterCount());
    assertEquals(String.class, stringMethod.getParameterTypes()[0]);

    // Verify via invocation
    NarrowingTestFixture fixture = new NarrowingTestFixture();
    assertEquals("String", stringMethod.invoke(fixture, "test"));

    // Test case 2: Multiple parameters - (String, String) more specific than (String, Object)
    Method twoStringMethod =
        reflectionHelper.lookupMethod(
            NarrowingTestFixture.class,
            new Object[] {"a", "b"},
            Arrays.asList(String.class, String.class),
            "compute");

    assertNotNull("Should find method with two params", twoStringMethod);
    assertEquals("compute", twoStringMethod.getName());
    assertEquals(2, twoStringMethod.getParameterCount());
    assertEquals(String.class, twoStringMethod.getParameterTypes()[0]);
    assertEquals(String.class, twoStringMethod.getParameterTypes()[1]);

    // Verify via invocation
    assertEquals("StringString", twoStringMethod.invoke(fixture, "a", "b"));

    // Test case 3: Object parameter - should find the Object overload
    Method objectMethod =
        reflectionHelper.lookupMethod(
            NarrowingTestFixture.class,
            new Object[] {Integer.valueOf(42)}, // Integer is not a String
            Collections.singletonList(Integer.class),
            "process");

    assertNotNull("Should find method for Object", objectMethod);
    // Integer is assignable to Object but not to String or CharSequence
    assertEquals(Object.class, objectMethod.getParameterTypes()[0]);
  }

  // ========================================================================
  // Test Fixture for Narrowing Tests
  // ========================================================================

  /**
   * Test fixture class with overloaded constructors and methods for testing the narrowDown logic.
   * The overloads are designed to test that more specific types are selected over less specific
   * ones.
   */
  @SuppressWarnings("unused")
  public static class NarrowingTestFixture {
    private final String constructorUsed;

    /**
     * Returns which constructor was used.
     *
     * @return the constructor identifier
     */
    public String getConstructorUsed() {
      return constructorUsed;
    }

    /** Default constructor. */
    public NarrowingTestFixture() {
      this.constructorUsed = "default";
    }

    /**
     * Constructor accepting Object (most general).
     *
     * @param obj any object
     */
    public NarrowingTestFixture(Object obj) {
      this.constructorUsed = "Object";
    }

    /**
     * Constructor accepting CharSequence (less general than Object).
     *
     * @param cs a char sequence
     */
    public NarrowingTestFixture(CharSequence cs) {
      this.constructorUsed = "CharSequence";
    }

    /**
     * Constructor accepting String (most specific for String args).
     *
     * @param str a string
     */
    public NarrowingTestFixture(String str) {
      this.constructorUsed = "String";
    }

    /**
     * Constructor with two parameters - (String, Object).
     *
     * @param a first parameter
     * @param b second parameter
     */
    public NarrowingTestFixture(String a, Object b) {
      this.constructorUsed = "StringObject";
    }

    /**
     * Constructor with two parameters - (String, String) more specific.
     *
     * @param a first parameter
     * @param b second parameter
     */
    public NarrowingTestFixture(String a, String b) {
      this.constructorUsed = "StringString";
    }

    // ----- Method overloads for narrowDownMethodMatches testing -----

    /**
     * Process method accepting Object.
     *
     * @param obj any object
     * @return identifier for which overload was called
     */
    public String process(Object obj) {
      return "Object";
    }

    /**
     * Process method accepting CharSequence.
     *
     * @param cs a char sequence
     * @return identifier for which overload was called
     */
    public String process(CharSequence cs) {
      return "CharSequence";
    }

    /**
     * Process method accepting String (most specific).
     *
     * @param str a string
     * @return identifier for which overload was called
     */
    public String process(String str) {
      return "String";
    }

    /**
     * Compute method with (Object, Object) parameters.
     *
     * @param a first parameter
     * @param b second parameter
     * @return identifier for which overload was called
     */
    public String compute(Object a, Object b) {
      return "ObjectObject";
    }

    /**
     * Compute method with (String, Object) parameters.
     *
     * @param a first parameter
     * @param b second parameter
     * @return identifier for which overload was called
     */
    public String compute(String a, Object b) {
      return "StringObject";
    }

    /**
     * Compute method with (String, String) parameters (most specific).
     *
     * @param a first parameter
     * @param b second parameter
     * @return identifier for which overload was called
     */
    public String compute(String a, String b) {
      return "StringString";
    }
  }
}
