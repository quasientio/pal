package net.ittera.pal.rpc.json;

import static org.junit.Assert.assertArrayEquals;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import net.ittera.pal.apps.rpc.ArrayVars;
import net.ittera.pal.common.util.Classes;
import net.ittera.pal.messages.jsonrpc.JsonRpcResponse;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

/**
 * Get & Put Array Message Integration Test.
 *
 * <pre>
 * Parameterized tests to cover:
 *  - get/put static primitive and wrapper array fields (null, empty, non-empty)
 *  - get/put instance primitive and wrapper array fields (null, empty, non-empty)
 * </pre>
 */
@RunWith(Parameterized.class)
public class PutGetArrayMessageIT extends AbstractJsonRpcMessageIT {

  private static int messageId = 0;
  private static final String CLASS_NAME = "net.ittera.pal.apps.rpc.ArrayVars";

  private final String nullFieldName;
  private final String emptyFieldName;
  private final String nonEmptyFieldName;
  private final Class<?> arrayType;
  private final Object nullValue;
  private final Object emptyValue;
  private final Object nonEmptyValue;

  public PutGetArrayMessageIT(
      String nullFieldName,
      String emptyFieldName,
      String nonEmptyFieldName,
      Class<?> arrayType,
      Object nullValue,
      Object emptyValue,
      Object nonEmptyValue) {
    this.nullFieldName = nullFieldName;
    this.emptyFieldName = emptyFieldName;
    this.nonEmptyFieldName = nonEmptyFieldName;
    this.arrayType = arrayType;
    this.nullValue = nullValue;
    this.emptyValue = emptyValue;
    this.nonEmptyValue = nonEmptyValue;
    logger.debug(
        "Created PutGetArrayMessageIT with nullField: {}, emptyField: {}, nonEmptyField: {}, arrayType: {}",
        nullFieldName,
        emptyFieldName,
        nonEmptyFieldName,
        arrayType);
  }

  @Parameters(name = "{index}: nullField={0}, emptyField={1}, nonEmptyField={2}, arrayType={3}")
  public static Collection<Object[]> data() throws Exception {
    List<Object[]> testData = new ArrayList<>();

    // Add test data for primitive arrays
    addArrayTestData(testData, true);

    // Add test data for wrapper arrays
    addArrayTestData(testData, false);
    return testData;
  }

  private static void addArrayTestData(List<Object[]> testData, boolean isPrimitive)
      throws Exception {
    List<Class<?>> arrayClasses =
        isPrimitive
            ? Classes.getPrimitiveArrayClasses()
            : Classes.getPrimitiveWrapperArrayClasses();

    for (Class<?> arrayClass : arrayClasses) {
      String typeName = arrayClass.getComponentType().getSimpleName();

      // For primitive arrays, adjust the field naming convention
      String baseFieldName =
          isPrimitive ? typeName.toLowerCase(Locale.ENGLISH) + "Array" : typeName + "Array";

      // Generate field names
      String nullFieldName = isPrimitive ? "aNull_" + baseFieldName : "aNull" + baseFieldName;
      String emptyFieldName = isPrimitive ? "anEmpty_" + baseFieldName : "anEmpty" + baseFieldName;
      String nonEmptyFieldName = isPrimitive ? "a_" + baseFieldName : "a" + baseFieldName;

      // Retrieve expected values via reflection
      Object nullValue = getFieldValue(nullFieldName);
      Object emptyValue = getFieldValue(emptyFieldName);
      Object nonEmptyValue = getFieldValue(nonEmptyFieldName);

      // Add the full test data object
      testData.add(
          new Object[] {
            nullFieldName,
            emptyFieldName,
            nonEmptyFieldName,
            arrayClass,
            nullValue,
            emptyValue,
            nonEmptyValue
          });
    }

    // Handle String arrays separately
    if (!isPrimitive) {
      String[] fieldNames = {"aNullStringArray", "anEmptyStringArray", "aStringArray"};
      Object nullValue = getFieldValue(fieldNames[0]);
      Object emptyValue = getFieldValue(fieldNames[1]);
      Object nonEmptyValue = getFieldValue(fieldNames[2]);
      testData.add(
          new Object[] {
            fieldNames[0],
            fieldNames[1],
            fieldNames[2],
            String[].class,
            nullValue,
            emptyValue,
            nonEmptyValue
          });
    }
  }

  private static Object getFieldValue(String fieldName) throws Exception {
    Field f = ArrayVars.class.getDeclaredField(fieldName);
    f.setAccessible(true);
    return f.get(null); // static fields for these 3
  }

  // ========== Test Methods ==========

  @Test
  public void testGetStaticArray() throws Exception {
    // a 'get' of the nonEmpty static array field should return the expected value (loaded via
    // reflection)
    JsonRpcResponse response = callGetStaticField(++messageId, CLASS_NAME, nonEmptyFieldName);
    Object resultValue = assertValueIsArrayOfType(response, arrayType.getName());
    Object[] expected = unwrapArray(nonEmptyValue);
    Object[] actual = unwrapArray(resultValue);
    assertArrayEquals(expected, actual);
  }

  @Test
  public void testPutAndGetStaticArrays() {
    List.of(true, false)
        .forEach(
            withNumericSuffix -> {
              try {
                putStaticAndVerify(nullFieldName, nonEmptyValue, withNumericSuffix);
                putStaticAndVerify(nullFieldName, emptyValue, withNumericSuffix);
                putStaticAndVerify(emptyFieldName, nullValue, withNumericSuffix);
              } catch (Exception e) {
                throw new RuntimeException(e);
              }
            });
  }

  private void putStaticAndVerify(String fieldName, Object value, boolean withNumericSuffix)
      throws Exception {
    String jsonValue =
        String.format(
            "{\"value\": %s, \"type\": \"%s\"}",
            arrayToJsonString(value, withNumericSuffix), arrayType.getName());
    JsonRpcResponse putResponse = callPutStaticField(++messageId, CLASS_NAME, fieldName, jsonValue);
    assertPutResultIsVoid(putResponse);

    // Now get and verify
    JsonRpcResponse getResponse = callGetStaticField(++messageId, CLASS_NAME, fieldName);

    if (value == null) {
      assertValueIsNullArrayOfType(getResponse, arrayType.getName());
    } else {
      Object resultValue = assertValueIsArrayOfType(getResponse, arrayType.getName());
      assertArrayEquals(unwrapArray(value), unwrapArray(resultValue));
    }
  }

  @Test
  public void testPutAndGetInstanceArrays() throws Exception {
    // Create an instance
    long instanceRef = createNewInstance(++messageId, CLASS_NAME);
    List.of(true, false)
        .forEach(
            withNumericSuffix -> {
              try {
                String instanceFieldName = getInstanceFieldName();
                putInstanceAndVerify(
                    instanceRef, instanceFieldName, nonEmptyValue, withNumericSuffix);
                putInstanceAndVerify(instanceRef, instanceFieldName, emptyValue, withNumericSuffix);
                putInstanceAndVerify(instanceRef, instanceFieldName, nullValue, withNumericSuffix);
              } catch (Exception e) {
                throw new RuntimeException(e);
              }
            });
  }

  private String getInstanceFieldName() {
    Class<?> componentType = arrayType.getComponentType();

    // Handle String arrays
    if (componentType == String.class) {
      return "instanceStringArray";
    }

    // Determine the base name: primitive vs wrapper
    String baseTypeName = componentType.getSimpleName();
    String capitalized = Character.toUpperCase(baseTypeName.charAt(0)) + baseTypeName.substring(1);

    if (componentType.isPrimitive()) {
      return "instance" + capitalized + "PrimitiveArray";
    } else {
      return "instance" + capitalized + "WrapperArray";
    }
  }

  private void putInstanceAndVerify(
      long instanceRef, String fieldName, Object value, boolean withNumericSuffix)
      throws Exception {
    String jsonValue =
        String.format(
            "{\"value\": %s, \"type\": \"%s\"}",
            arrayToJsonString(value, withNumericSuffix), arrayType.getName());
    JsonRpcResponse putResponse =
        callPutInstanceField(++messageId, CLASS_NAME, fieldName, instanceRef, jsonValue);
    assertPutResultIsVoid(putResponse);

    // get and verify
    JsonRpcResponse getResponse =
        callGetInstanceField(++messageId, CLASS_NAME, fieldName, instanceRef);
    if (value == null) {
      assertValueIsNullArrayOfType(getResponse, arrayType.getName());
    } else {
      Object resultValue = assertValueIsArrayOfType(getResponse, arrayType.getName());
      assertArrayEquals(unwrapArray(value), unwrapArray(resultValue));
    }
  }
}
