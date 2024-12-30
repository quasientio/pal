package net.ittera.pal.rpc.json;

import static org.junit.Assert.assertArrayEquals;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import net.ittera.pal.apps.rpc.StaticArrayVars;
import net.ittera.pal.common.util.Classes;
import net.ittera.pal.messages.jsonrpc.JsonRpcResponse;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

/**
 * Naming convention to use: methodName_stateUnderTest_expectedBehavior.
 *
 * <pre>
 *   Coverage: Arrays of Primitives & Wrappers.
 *   For each type, there are 3 tests:
 *   - a null array
 *   - an empty array
 *   - an array populated with some values
 * </pre>
 *
 * <p>TODO: introduce null values
 */
@RunWith(Parameterized.class)
public class GetArrayMessageIT extends AbstractJsonRpcMessageIT {

  private static int messageId = 0;
  private static final String CLASS_NAME = "net.ittera.pal.apps.rpc.StaticArrayVars";
  private static final Class<?> CLASS = StaticArrayVars.class;

  // Test parameters
  private final String fieldName;
  private final Class<?> fieldType;
  private final Object expectedValue;

  // Constructor for parameterized test
  public GetArrayMessageIT(
      TargetType targetType, String fieldName, Class<?> fieldType, Object expectedValue) {
    super(targetType);
    this.fieldName = fieldName;
    this.fieldType = fieldType;
    this.expectedValue = expectedValue;
  }

  // Method that provides test data
  @Parameters(name = "{index}: targetType={0} fieldName={1}, fieldType={2}")
  public static Collection<Object[]> data() throws Exception {
    var targetTypeParams = getSendTargetParameters();
    List<Object[]> arrayTestData = new ArrayList<>();

    // Add test data for primitive arrays
    addArrayTestData(arrayTestData, true);

    // Add test data for wrapper arrays
    addArrayTestData(arrayTestData, false);

    // Build the Cartesian product: targetType params X arrayType params
    List<Object[]> combined = new ArrayList<>();
    for (Object[] targetTypeEntry : targetTypeParams) {
      TargetType rpcTargetType = (TargetType) targetTypeEntry[0]; // PEER or LOG
      for (Object[] arrayEntry : arrayTestData) {
        String fieldName = (String) arrayEntry[0];
        Class<?> fieldType = (Class<?>) arrayEntry[1];
        Object fieldValue = arrayEntry[2];

        combined.add(new Object[] {rpcTargetType, fieldName, fieldType, fieldValue});
      }
    }

    return combined;
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

      // Add test data
      testData.add(new Object[] {nullFieldName, arrayClass, nullValue});
      testData.add(new Object[] {emptyFieldName, arrayClass, emptyValue});
      testData.add(new Object[] {nonEmptyFieldName, arrayClass, nonEmptyValue});
    }

    // Handle String arrays separately
    if (!isPrimitive) {
      // Add String array tests
      String[] fieldNames = {"aNullStringArray", "anEmptyStringArray", "aStringArray"};
      for (String fieldName : fieldNames) {
        Object value = getFieldValue(fieldName);
        testData.add(new Object[] {fieldName, String[].class, value});
      }
    }
  }

  private static Object getFieldValue(String fieldName) throws Exception {
    Field field = CLASS.getDeclaredField(fieldName);
    field.setAccessible(true);
    return field.get(null); // Static field, so instance is null
  }

  @Test
  public void testGetStaticArrayField() throws Exception {
    String fieldTypeName = fieldType.getName();

    // Call the get static field via JSON-RPC
    JsonRpcResponse response = callGetStaticField(++messageId, CLASS_NAME, fieldName);

    if (expectedValue == null) {
      // Expecting null array
      assertValueIsNullArrayOfType(response, fieldTypeName);
    } else {
      // Expecting non-null array
      Object resultValue = assertValueIsArrayOfType(response, fieldTypeName);

      // Unwrap the expected value if necessary
      Object[] expectedArray = unwrapArray(expectedValue);
      Object[] actualArray = unwrapArray(resultValue);

      // Use assertArrayEquals to compare arrays
      assertArrayEquals(expectedArray, actualArray);
    }
  }
}
