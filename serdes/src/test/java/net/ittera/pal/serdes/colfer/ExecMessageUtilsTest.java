package net.ittera.pal.serdes.colfer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.AccessibleObject;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import net.ittera.pal.common.objects.ObjectRef;
import net.ittera.pal.messages.colfer.ExecMessage;
import org.junit.Test;

public class ExecMessageUtilsTest {
  private final MessageBuilder messageBuilder = new MessageBuilder();

  // <editor-fold desc="getClassname">
  @Test
  public void getClassname_constructor() {
    String className = "TestClass";
    ExecMessage execMessage = messageBuilder.buildEmptyConstructor(UUID.randomUUID(), className);
    assertEquals(className, ExecMessageUtils.getClassname(execMessage));
  }

  @Test
  public void getClassname_instanceMethod() {
    String className = "TestClass";
    ExecMessage execMessage =
        messageBuilder.buildInstanceMethod(
            UUID.randomUUID(), className, "testMethod", ObjectRef.randomRef(), null, null);
    assertEquals(className, ExecMessageUtils.getClassname(execMessage));
  }

  @Test
  public void getClassname_classMethod() {
    String className = "TestClass";
    ExecMessage execMessage =
        messageBuilder.buildClassMethod(
            UUID.randomUUID(), className, "testMethod", null, null, null, null);
    assertEquals(className, ExecMessageUtils.getClassname(execMessage));
  }

  @Test
  public void getClassname_getStatic() {
    String className = "TestClass";
    ExecMessage execMessage =
        messageBuilder.buildGetStatic(UUID.randomUUID(), className, "testField");
    assertEquals(className, ExecMessageUtils.getClassname(execMessage));
  }

  @Test
  public void getClassname_getField() {
    String className = "TestClass";
    ExecMessage execMessage =
        messageBuilder.buildGetObject(
            UUID.randomUUID(), className, "testField", ObjectRef.randomRef());
    assertEquals(className, ExecMessageUtils.getClassname(execMessage));
  }

  @Test
  public void getClassname_putStatic() {
    String className = "TestClass";
    ExecMessage execMessage =
        messageBuilder.buildPutStatic(
            UUID.randomUUID(), className, "testField", ObjectRef.randomRef());
    assertEquals(className, ExecMessageUtils.getClassname(execMessage));
  }

  @Test
  public void getClassname_putField() {
    String className = "TestClass";
    ExecMessage execMessage =
        messageBuilder.buildPutObject(
            UUID.randomUUID(),
            className,
            "testField",
            ObjectRef.randomRef(),
            ObjectRef.randomRef());
    assertEquals(className, ExecMessageUtils.getClassname(execMessage));
  }

  @Test(expected = IllegalArgumentException.class)
  public void getClassname_throwsExceptionForUnsupported_ReturnValue() {
    AccessibleObject accessibleObject = this.getClass().getMethods()[0];
    ExecMessage execMessage =
        messageBuilder.buildReturnValue(
            UUID.randomUUID(),
            "test",
            accessibleObject,
            ObjectRef.randomRef(),
            false,
            UUID.randomUUID().toString());
    ExecMessageUtils.getClassname(execMessage);
  }

  @Test(expected = IllegalArgumentException.class)
  public void getClassname_throwsExceptionForUnsupported_PutObjectDone() {
    AccessibleObject accessibleObject = this.getClass().getDeclaredFields()[0];
    ExecMessage execMessage =
        messageBuilder.buildPutObjectDone(
            UUID.randomUUID(),
            accessibleObject,
            UUID.randomUUID().toString(),
            UUID.randomUUID().toString());
    ExecMessageUtils.getClassname(execMessage);
  }

  @Test(expected = IllegalArgumentException.class)
  public void getClassname_throwsExceptionForUnsupported_PutStaticDone() {
    AccessibleObject accessibleObject = this.getClass().getDeclaredFields()[0];
    ExecMessage execMessage =
        messageBuilder.buildPutStaticDone(
            UUID.randomUUID(),
            accessibleObject,
            UUID.randomUUID().toString(),
            UUID.randomUUID().toString());
    ExecMessageUtils.getClassname(execMessage);
  }

  // </editor-fold>

  // <editor-fold desc="getExecutableName">
  @Test
  public void getExecutableName_constructor() {
    String className = "TestClass";
    ExecMessage execMessage = messageBuilder.buildEmptyConstructor(UUID.randomUUID(), className);
    assertEquals("<init>", ExecMessageUtils.getExecutableName(execMessage));
  }

  @Test
  public void getExecutableName_instanceMethod() {
    String className = "TestClass";
    String methodName = "testMethod";
    ExecMessage execMessage =
        messageBuilder.buildInstanceMethod(
            UUID.randomUUID(), className, methodName, ObjectRef.randomRef(), null, null);
    assertEquals(methodName, ExecMessageUtils.getExecutableName(execMessage));
  }

  @Test
  public void getExecutableName_classMethod() {
    String className = "TestClass";
    String methodName = "testMethod";
    ExecMessage execMessage =
        messageBuilder.buildClassMethod(
            UUID.randomUUID(), className, methodName, null, null, null, null);
    assertEquals(methodName, ExecMessageUtils.getExecutableName(execMessage));
  }

  @Test
  public void getExecutableName_getStatic() {
    String className = "TestClass";
    String fieldName = "testField";
    ExecMessage execMessage =
        messageBuilder.buildGetStatic(UUID.randomUUID(), className, fieldName);
    assertEquals(fieldName, ExecMessageUtils.getExecutableName(execMessage));
  }

  @Test
  public void getExecutableName_getField() {
    String className = "TestClass";
    String fieldName = "testField";
    ExecMessage execMessage =
        messageBuilder.buildGetObject(
            UUID.randomUUID(), className, fieldName, ObjectRef.randomRef());
    assertEquals(fieldName, ExecMessageUtils.getExecutableName(execMessage));
  }

  @Test
  public void getExecutableName_putStatic() {
    String className = "TestClass";
    String fieldName = "testField";
    ExecMessage execMessage =
        messageBuilder.buildPutStatic(
            UUID.randomUUID(), className, fieldName, ObjectRef.randomRef());
    assertEquals(fieldName, ExecMessageUtils.getExecutableName(execMessage));
  }

  @Test
  public void getExecutableName_putField() {
    String className = "TestClass";
    String fieldName = "testField";
    ExecMessage execMessage =
        messageBuilder.buildPutObject(
            UUID.randomUUID(),
            className,
            "testField",
            ObjectRef.randomRef(),
            ObjectRef.randomRef());
    assertEquals(fieldName, ExecMessageUtils.getExecutableName(execMessage));
  }

  @Test(expected = IllegalArgumentException.class)
  public void getExecutableName_throwsExceptionForUnsupported_ReturnValue() {
    AccessibleObject accessibleObject = this.getClass().getMethods()[0];
    ExecMessage execMessage =
        messageBuilder.buildReturnValue(
            UUID.randomUUID(),
            "test",
            accessibleObject,
            ObjectRef.randomRef(),
            false,
            UUID.randomUUID().toString());
    ExecMessageUtils.getExecutableName(execMessage);
  }

  @Test(expected = IllegalArgumentException.class)
  public void getExecutableName_throwsExceptionForUnsupported_PutObjectDone() {
    AccessibleObject accessibleObject = this.getClass().getDeclaredFields()[0];
    ExecMessage execMessage =
        messageBuilder.buildPutObjectDone(
            UUID.randomUUID(),
            accessibleObject,
            UUID.randomUUID().toString(),
            UUID.randomUUID().toString());
    ExecMessageUtils.getExecutableName(execMessage);
  }

  @Test(expected = IllegalArgumentException.class)
  public void getExecutableName_throwsExceptionForUnsupported_PutStaticDone() {
    AccessibleObject accessibleObject = this.getClass().getDeclaredFields()[0];
    ExecMessage execMessage =
        messageBuilder.buildPutStaticDone(
            UUID.randomUUID(),
            accessibleObject,
            UUID.randomUUID().toString(),
            UUID.randomUUID().toString());
    ExecMessageUtils.getExecutableName(execMessage);
  }

  // </editor-fold>

  // <editor-fold desc="getParameterTypes">
  @Test
  public void getParameterTypes_Constructor() {
    String className = "TestClass";
    String[] parameterTypes = new String[] {Integer.TYPE.getName(), String.class.getName()};
    Object[] args = new Object[] {1, "test"};
    ExecMessage execMessage =
        messageBuilder.buildConstructor(
            UUID.randomUUID(), className, parameterTypes, args, null, null);

    List<String> returnedTypes = ExecMessageUtils.getParameterTypes(execMessage);
    assertNotNull(returnedTypes);
    assertEquals(2, returnedTypes.size());
    assertTrue(returnedTypes.containsAll(Arrays.asList(parameterTypes)));
  }

  @Test
  public void getParameterTypes_instanceMethod() {
    String className = "TestClass";
    String[] parameterTypes = new String[] {Integer.TYPE.getName(), String.class.getName()};
    Object[] args = new Object[] {1, "test"};
    ExecMessage execMessage =
        messageBuilder.buildInstanceMethod(
            UUID.randomUUID(),
            className,
            "testMethod",
            ObjectRef.randomRef(),
            parameterTypes,
            args);
    List<String> returnedTypes = ExecMessageUtils.getParameterTypes(execMessage);
    assertNotNull(returnedTypes);
    assertEquals(2, returnedTypes.size());
    assertTrue(returnedTypes.containsAll(Arrays.asList(parameterTypes)));
  }

  @Test
  public void getParameterTypes_classMethod() {
    String className = "TestClass";
    String[] parameterTypes = new String[] {Integer.TYPE.getName(), String.class.getName()};
    Object[] args = new Object[] {1, "test"};
    ExecMessage execMessage =
        messageBuilder.buildClassMethod(
            UUID.randomUUID(), className, "testMethod", parameterTypes, null, null, args);
    List<String> returnedTypes = ExecMessageUtils.getParameterTypes(execMessage);
    assertNotNull(returnedTypes);
    assertEquals(2, returnedTypes.size());
    assertTrue(returnedTypes.containsAll(Arrays.asList(parameterTypes)));
  }

  @Test
  public void getParameterTypes_returnsEmptyListForEmptyConstructor() {
    String className = "TestClass";
    ExecMessage execMessage = messageBuilder.buildEmptyConstructor(UUID.randomUUID(), className);
    List<String> returnedTypes = ExecMessageUtils.getParameterTypes(execMessage);
    assertNotNull(returnedTypes);
    assertTrue(returnedTypes.isEmpty());
  }

  @Test
  public void getParameterTypes_returnsNullForUnsupported_FieldOps() {
    String className = "TestClass";
    String fieldName = "testField";

    // GetStatic
    ExecMessage execMessage =
        messageBuilder.buildGetStatic(UUID.randomUUID(), className, fieldName);

    assertNull(ExecMessageUtils.getParameterTypes(execMessage));

    // GetField
    execMessage =
        messageBuilder.buildGetObject(
            UUID.randomUUID(), className, fieldName, ObjectRef.randomRef());
    assertNull(ExecMessageUtils.getParameterTypes(execMessage));

    // PutStatic
    execMessage =
        messageBuilder.buildPutStatic(
            UUID.randomUUID(), className, fieldName, ObjectRef.randomRef());
    assertNull(ExecMessageUtils.getParameterTypes(execMessage));

    // PutField
    execMessage =
        messageBuilder.buildPutObject(
            UUID.randomUUID(),
            className,
            "testField",
            ObjectRef.randomRef(),
            ObjectRef.randomRef());
    assertNull(ExecMessageUtils.getParameterTypes(execMessage));

    // PutFieldDone
    AccessibleObject accessibleObject = this.getClass().getDeclaredFields()[0];
    execMessage =
        messageBuilder.buildPutObjectDone(
            UUID.randomUUID(),
            accessibleObject,
            UUID.randomUUID().toString(),
            UUID.randomUUID().toString());
    assertNull(ExecMessageUtils.getParameterTypes(execMessage));

    // PutStaticDone
    accessibleObject = this.getClass().getDeclaredFields()[0];
    execMessage =
        messageBuilder.buildPutStaticDone(
            UUID.randomUUID(),
            accessibleObject,
            UUID.randomUUID().toString(),
            UUID.randomUUID().toString());
    assertNull(ExecMessageUtils.getParameterTypes(execMessage));
  }

  @Test
  public void getParameterTypes_returnsNullForUnsupported_ReturnValue() {
    AccessibleObject accessibleObject = this.getClass().getMethods()[0];
    ExecMessage execMessage =
        messageBuilder.buildReturnValue(
            UUID.randomUUID(),
            "test",
            accessibleObject,
            ObjectRef.randomRef(),
            false,
            UUID.randomUUID().toString());
    assertNull(ExecMessageUtils.getParameterTypes(execMessage));
  }
  // </editor-fold>
}
