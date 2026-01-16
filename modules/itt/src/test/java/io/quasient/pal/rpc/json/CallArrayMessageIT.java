/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.rpc.json;

import static org.junit.Assert.assertArrayEquals;

import io.quasient.pal.apps.rpc.ArrayVars;
import io.quasient.pal.common.util.Classes;
import io.quasient.pal.messages.jsonrpc.JsonRpcResponse;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import javax.annotation.Nonnull;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

/**
 * Call Instance Getter/Setter Array Message Integration Test.
 *
 * <pre>
 *  For each array type, this test:
 *  - Creates a new instance of ArrayVars
 *  - Invokes setter with a known non-empty array value obtained from a static field
 *  - Invokes the corresponding getter and verifies that the * returned array matches
 *  the expected value.
 * </pre>
 */
@RunWith(Parameterized.class)
public class CallArrayMessageIT extends AbstractJsonRpcMessageIT {

  private static final String CLASS_NAME = "io.quasient.pal.apps.rpc.ArrayVars";

  private final String getterMethodName; // e.g. getInstanceIntPrimitiveArray
  private final String setterMethodName; // e.g. setInstanceIntPrimitiveArray
  private final Class<?> arrayType; // e.g. int[].class
  private final Object nonEmptyValue; // the array we got from reflection on a_*_Array

  public CallArrayMessageIT(
      TargetType targetType,
      String instanceFieldName,
      String getterMethodName,
      String setterMethodName,
      Class<?> arrayType,
      Object nonEmptyValue) {
    super(targetType);
    this.getterMethodName = getterMethodName;
    this.setterMethodName = setterMethodName;
    this.arrayType = arrayType;
    this.nonEmptyValue = nonEmptyValue;

    logger.debug(
        "Created CallArrayMessageIT for target: {}, field: {}, getter: {}, setter: {}, type: {}",
        targetType,
        instanceFieldName,
        getterMethodName,
        setterMethodName,
        arrayType);
  }

  @Parameters(name = "{index}: targetType={0}, arrayType={3}")
  public static Collection<Object[]> data() throws Exception {

    var targetTypeParams = getSendTargetParameters();

    List<Object[]> arrayTestData = new ArrayList<>();
    // Primitive arrays
    addInstanceArrayTestData(arrayTestData, true);
    // Wrapper arrays + String arrays
    addInstanceArrayTestData(arrayTestData, false);

    // Build the Cartesian product: targetType params X arrayType params
    List<Object[]> combined = new ArrayList<>();
    for (Object[] targetTypeEntry : targetTypeParams) {
      TargetType rpcTargetType = (TargetType) targetTypeEntry[0]; // PEER or LOG
      for (Object[] arrayEntry : arrayTestData) {
        String instanceFieldName = (String) arrayEntry[0];
        String getterMethodName = (String) arrayEntry[1];
        String setterMethodName = (String) arrayEntry[2];
        Class<?> arrayClass = (Class<?>) arrayEntry[3];
        Object nonEmptyValue = arrayEntry[4];

        combined.add(
            new Object[] {
              rpcTargetType,
              instanceFieldName,
              getterMethodName,
              setterMethodName,
              arrayClass,
              nonEmptyValue
            });
      }
    }

    return combined;
  }

  private static void addInstanceArrayTestData(List<Object[]> testData, boolean isPrimitive)
      throws Exception {
    List<Class<?>> arrayClasses =
        isPrimitive
            ? Classes.getPrimitiveArrayClasses()
            : Classes.getPrimitiveWrapperArrayClasses();

    for (Class<?> arrayClass : arrayClasses) {
      Object nonEmptyValue = getNonEmptyStaticValueFor(arrayClass);
      if (nonEmptyValue == null) {
        // If for some reason no nonEmpty value is found, skip this type
        continue;
      }

      // Determine the instance field and corresponding getter/setter method names
      String instanceFieldName = getInstanceFieldName(arrayClass);
      String getterMethodName = getGetterMethodName(instanceFieldName);
      String setterMethodName = getSetterMethodName(instanceFieldName);

      testData.add(
          new Object[] {
            instanceFieldName, getterMethodName, setterMethodName, arrayClass, nonEmptyValue
          });
    }

    // Handle String arrays separately
    if (!isPrimitive) {
      Class<?> stringArrayClass = String[].class;
      String instanceFieldName = "instanceStringArray";
      Object nonEmptyValue = getNonEmptyStaticValueFor(stringArrayClass);
      String getterMethodName = getGetterMethodName(instanceFieldName);
      String setterMethodName = getSetterMethodName(instanceFieldName);
      testData.add(
          new Object[] {
            instanceFieldName, getterMethodName, setterMethodName, stringArrayClass, nonEmptyValue
          });
    }
  }

  private static Object getNonEmptyStaticValueFor(Class<?> arrayClass) throws Exception {
    Class<?> componentType = arrayClass.getComponentType();
    String baseFieldName = getNonEmptyFieldName(componentType);

    Field f = ArrayVars.class.getDeclaredField(baseFieldName);
    f.setAccessible(true);
    return f.get(null); // static field
  }

  @Nonnull
  private static String getNonEmptyFieldName(Class<?> componentType) {
    // For primitive arrays, the nonEmpty field name is "a_<typeName>_Array"
    // For wrapper arrays, the nonEmpty field name is "a<TypeName>Array"
    // For String arrays, it's aStringArray
    String baseTypeName = componentType.getSimpleName();
    boolean isPrimitive = componentType.isPrimitive();
    String baseFieldName;
    if (componentType == String.class) {
      baseFieldName = "aStringArray";
    } else if (isPrimitive) {
      // primitive arrays: "a_<lowercase type>_array"
      baseFieldName = "a_" + baseTypeName.toLowerCase(Locale.ENGLISH) + "Array";
    } else {
      // wrapper arrays: "a<TypeName>Array"
      // For Character => aCharacterArray, for Integer => aIntegerArray, etc.
      baseFieldName = "a" + baseTypeName + "Array";
    }
    return baseFieldName;
  }

  private static String getInstanceFieldName(Class<?> arrayType) {
    Class<?> componentType = arrayType.getComponentType();
    if (componentType == String.class) {
      return "instanceStringArray";
    }

    String baseTypeName = componentType.getSimpleName();
    String capitalized = Character.toUpperCase(baseTypeName.charAt(0)) + baseTypeName.substring(1);

    if (componentType.isPrimitive()) {
      return "instance" + capitalized + "PrimitiveArray";
    } else {
      return "instance" + capitalized + "WrapperArray";
    }
  }

  private static String getGetterMethodName(String instanceFieldName) {
    // instanceIntPrimitiveArray => getInstanceIntPrimitiveArray
    return "get" + capitalizeFirstLetter(instanceFieldName);
  }

  private static String getSetterMethodName(String instanceFieldName) {
    // instanceIntPrimitiveArray => setInstanceIntPrimitiveArray
    return "set" + capitalizeFirstLetter(instanceFieldName);
  }

  private static String capitalizeFirstLetter(String s) {
    return Character.toUpperCase(s.charAt(0)) + s.substring(1);
  }

  @Test
  public void testCallInstanceGetterSetter() throws Exception {
    // 1) create an instance
    long instanceRef = createNewInstance(CLASS_NAME);

    // 2) call setter with nonEmptyValue
    String jsonArg = arrayToJsonString(nonEmptyValue, false);
    String argsJson =
            """
            [
              {
                "type": "%s",
                "value": %s
              }
            ]
            """
            .formatted(arrayType.getName(), jsonArg);

    JsonRpcResponse setResponse =
        callInstanceMethod(instanceRef, CLASS_NAME, setterMethodName, argsJson);
    assertPutResultIsVoid(setResponse);

    // 3) call getter (no args)
    JsonRpcResponse getResponse =
        callInstanceMethod(instanceRef, CLASS_NAME, getterMethodName, null);

    // 4) verify result
    Object resultValue = assertValueIsArrayOfType(getResponse, arrayType.getName());
    assertArrayEquals(unwrapArray(nonEmptyValue), unwrapArray(resultValue));
  }
}
