/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.quasient.pal.serdes.colfer;

import static io.quasient.pal.messages.types.MessageType.EXEC_CONSTRUCTOR;
import static io.quasient.pal.messages.types.MessageType.EXEC_GET_FIELD;
import static io.quasient.pal.messages.types.MessageType.EXEC_GET_STATIC;
import static io.quasient.pal.messages.types.MessageType.EXEC_PUT_FIELD;
import static io.quasient.pal.messages.types.MessageType.EXEC_PUT_FIELD_DONE;
import static io.quasient.pal.messages.types.MessageType.EXEC_PUT_STATIC;
import static io.quasient.pal.messages.types.MessageType.EXEC_PUT_STATIC_DONE;
import static io.quasient.pal.messages.types.MessageType.EXEC_RETURN_VALUE;
import static io.quasient.pal.serdes.colfer.ExecMessageUtils.getMessageTypeOf;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import io.quasient.pal.common.lang.reflect.FieldSignature;
import io.quasient.pal.common.lang.reflect.MethodSignature;
import io.quasient.pal.common.objects.ObjectRef;
import io.quasient.pal.common.runtime.Context;
import io.quasient.pal.messages.colfer.ExecMessage;
import io.quasient.pal.messages.colfer.Parameter;
import java.io.IOException;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.junit.Before;
import org.junit.Test;

/**
 * Branch-coverage focused tests for {@link MessageBuilder}. Targets uncovered switch branches in
 * resolveClass, buildFieldOp, buildFieldOpDone, buildReturnValue, extractDeclaredExceptions, and
 * createNamedParameter methods to improve branch coverage from 68.4% toward 80%+.
 *
 * <p>Naming convention: methodName_stateUnderTest_expectedBehavior
 */
@SuppressWarnings({"UnusedMethod", "UnusedVariable"})
public class MessageBuilderBranchCoverageTest {

  /**
   * Dummy class with a field and methods used for building field op and return value messages in
   * tests.
   */
  @SuppressWarnings("unused")
  static class DummyTarget {
    /** An instance field for field-op tests. */
    int instanceField;

    /** A static field for static field-op tests. */
    static String staticField;

    /** A method that declares multiple checked exceptions for extractDeclaredExceptions tests. */
    public static void methodWithExceptions(String arg)
        throws IOException, ClassNotFoundException {}

    /** A method with no declared exceptions. */
    public static void methodWithoutExceptions() {}

    /** An instance method returning int, for return-value tests. */
    public int addInts(int x, int y) {
      return x + y;
    }

    /** A void method for void-return tests. */
    public void voidMethod() {}
  }

  /** Peer UUID used across tests. */
  private final UUID peerId = UUID.randomUUID();

  /** Builder under test. */
  private MessageBuilder builder;

  /** Sets up a fresh {@link MessageBuilder} before each test. */
  @Before
  public void setUp() {
    builder = new MessageBuilder(peerId, Boolean.toString(false));
  }

  /**
   * Creates a {@link Context} wrapping a field signature for the given class and field name.
   *
   * @param clazz the class declaring the field
   * @param fieldName the name of the field
   * @return a Context with a FieldSignature
   * @throws Exception if the field is not found
   */
  private static Context ctxForField(Class<?> clazz, String fieldName) throws Exception {
    FieldSignature fs = new FieldSignature(clazz.getDeclaredField(fieldName));
    return new Context("MessageBuilderBranchCoverageTest.java", 1, clazz, fs);
  }

  /**
   * Creates a {@link Context} wrapping a method signature for the given class and method.
   *
   * @param clazz the class declaring the method
   * @param methodName the name of the method
   * @param argTypes the parameter types of the method
   * @return a Context with a MethodSignature
   * @throws Exception if the method is not found
   */
  private static Context ctxForMethod(Class<?> clazz, String methodName, Class<?>... argTypes)
      throws Exception {
    MethodSignature ms = new MethodSignature(clazz.getDeclaredMethod(methodName, argTypes));
    return new Context("MessageBuilderBranchCoverageTest.java", 1, clazz, ms);
  }

  /**
   * Invokes the private {@code resolveClass} method on the builder via reflection.
   *
   * @param typeName the type name to resolve
   * @return the resolved Class
   * @throws Exception if reflection or the underlying method fails
   */
  private Class<?> invokeResolveClass(String typeName) throws Exception {
    Method m = MessageBuilder.class.getDeclaredMethod("resolveClass", String.class);
    m.setAccessible(true);
    return (Class<?>) m.invoke(builder, typeName);
  }

  // ========================================================================
  // resolveClass branches (private method, tested via reflection)
  // ========================================================================

  /** Tests that resolveClass returns {@code boolean.class} for "boolean". */
  @Test
  public void resolveClass_boolean_returnsPrimitiveClass() throws Exception {
    assertThat(invokeResolveClass("boolean"), is((Class<?>) boolean.class));
  }

  /** Tests that resolveClass returns {@code byte.class} for "byte". */
  @Test
  public void resolveClass_byte_returnsPrimitiveClass() throws Exception {
    assertThat(invokeResolveClass("byte"), is((Class<?>) byte.class));
  }

  /** Tests that resolveClass returns {@code char.class} for "char". */
  @Test
  public void resolveClass_char_returnsPrimitiveClass() throws Exception {
    assertThat(invokeResolveClass("char"), is((Class<?>) char.class));
  }

  /** Tests that resolveClass returns {@code short.class} for "short". */
  @Test
  public void resolveClass_short_returnsPrimitiveClass() throws Exception {
    assertThat(invokeResolveClass("short"), is((Class<?>) short.class));
  }

  /** Tests that resolveClass returns {@code int.class} for "int". */
  @Test
  public void resolveClass_int_returnsPrimitiveClass() throws Exception {
    assertThat(invokeResolveClass("int"), is((Class<?>) int.class));
  }

  /** Tests that resolveClass returns {@code long.class} for "long". */
  @Test
  public void resolveClass_long_returnsPrimitiveClass() throws Exception {
    assertThat(invokeResolveClass("long"), is((Class<?>) long.class));
  }

  /** Tests that resolveClass returns {@code float.class} for "float". */
  @Test
  public void resolveClass_float_returnsPrimitiveClass() throws Exception {
    assertThat(invokeResolveClass("float"), is((Class<?>) float.class));
  }

  /** Tests that resolveClass returns {@code double.class} for "double". */
  @Test
  public void resolveClass_double_returnsPrimitiveClass() throws Exception {
    assertThat(invokeResolveClass("double"), is((Class<?>) double.class));
  }

  /** Tests that resolveClass returns {@code void.class} for "void". */
  @Test
  public void resolveClass_void_returnsPrimitiveClass() throws Exception {
    assertThat(invokeResolveClass("void"), is((Class<?>) void.class));
  }

  /** Tests that resolveClass returns the correct class via Class.forName for a FQCN. */
  @Test
  public void resolveClass_className_returnsClassForName() throws Exception {
    assertThat(invokeResolveClass("java.lang.String"), is((Class<?>) String.class));
  }

  /** Tests that resolveClass throws ClassNotFoundException for a non-existent class. */
  @Test
  public void resolveClass_nonExistentClass_throwsClassNotFoundException() throws Exception {
    Method m = MessageBuilder.class.getDeclaredMethod("resolveClass", String.class);
    m.setAccessible(true);
    try {
      m.invoke(builder, "nonexistent.Foo");
      fail("Expected InvocationTargetException wrapping ClassNotFoundException");
    } catch (InvocationTargetException e) {
      assertTrue(
          "Expected ClassNotFoundException but got " + e.getCause().getClass(),
          e.getCause() instanceof ClassNotFoundException);
    }
  }

  // ========================================================================
  // createNamedParameter / createNamedParameters branches
  // ========================================================================

  /** Tests createNamedParameter with a non-null paramName uses the given name. */
  @Test
  @SuppressWarnings("PMD.NoFullyQualifiedTypes")
  public void createNamedParameter_withParamName_usesGivenName() throws Exception {
    // Use a real method parameter from DummyTarget.methodWithExceptions
    java.lang.reflect.Parameter param =
        DummyTarget.class.getDeclaredMethod("methodWithExceptions", String.class)
            .getParameters()[0];
    Method m =
        MessageBuilder.class.getDeclaredMethod(
            "createNamedParameter",
            java.lang.reflect.Parameter.class,
            String.class,
            String.class,
            Object.class,
            ObjectRef.class);
    m.setAccessible(true);

    Parameter result =
        (Parameter) m.invoke(builder, param, "customName", "java.lang.String", "hello", null);

    assertThat(result.getName(), is("customName"));
  }

  /** Tests createNamedParameter with null paramName uses the reflective parameter name. */
  @Test
  @SuppressWarnings("PMD.NoFullyQualifiedTypes")
  public void createNamedParameter_withNullParamName_usesReflectiveName() throws Exception {
    java.lang.reflect.Parameter param =
        DummyTarget.class.getDeclaredMethod("methodWithExceptions", String.class)
            .getParameters()[0];
    Method m =
        MessageBuilder.class.getDeclaredMethod(
            "createNamedParameter",
            java.lang.reflect.Parameter.class,
            String.class,
            String.class,
            Object.class,
            ObjectRef.class);
    m.setAccessible(true);

    Parameter result =
        (Parameter) m.invoke(builder, param, null, "java.lang.String", "hello", null);

    // With null paramName, parameter.getName() is used (e.g., "arg0" or the actual name)
    assertNotNull(result.getName());
    assertThat(result.getName(), is(param.getName()));
  }

  /** Tests createNamedParameters returns an empty array when paramTypes are null. */
  @Test
  @SuppressWarnings("PMD.NoFullyQualifiedTypes")
  public void createNamedParameters_withContext_nullParams_returnsEmpty() throws Exception {
    // Build a context from a method with known params, then we'll invoke via the public
    // buildClassMethodMessageEphemeral with null args to cover the null-params branch
    // in createNamedParameters(Context, Object[], ObjectRef[]).
    // This method is private, so invoke via reflection.
    Context ctx = ctxForMethod(DummyTarget.class, "methodWithExceptions", String.class);

    Method m =
        MessageBuilder.class.getDeclaredMethod(
            "createNamedParameters", Context.class, Object[].class, ObjectRef[].class);
    m.setAccessible(true);

    // Pass null args and null argObjRefs to exercise null-safety branches
    Parameter[] result = (Parameter[]) m.invoke(builder, ctx, (Object) null, (Object) null);

    // The method's CodeSignature has 1 paramType (String.class), so paramCount=1
    // but args=null and argObjRefs=null, so the null-safety branches are exercised
    assertNotNull(result);
    assertThat(result.length, is(1));
  }

  // ========================================================================
  // buildFieldOp switch branches
  // ========================================================================

  /** Tests buildFieldOp with EXEC_GET_FIELD covers the GET_FIELD switch branch. */
  @Test
  public void buildFieldOp_getField_coversSwitch() throws Exception {
    Context ctx = ctxForField(DummyTarget.class, "instanceField");
    ObjectRef targetRef = ObjectRef.randomRef();

    ExecMessage msg =
        builder.buildFieldOp(
            peerId, ctx, EXEC_GET_FIELD, this, ObjectRef.randomRef(), targetRef, null, null);

    assertNotNull(msg);
    assertThat(getMessageTypeOf(msg), is(EXEC_GET_FIELD));
    assertNotNull(msg.getInstanceFieldGet());
    assertThat(msg.getInstanceFieldGet().getObjectRef(), is(targetRef.getRef()));
  }

  /** Tests buildFieldOp with EXEC_PUT_FIELD covers the PUT_FIELD switch branch. */
  @Test
  public void buildFieldOp_putField_coversSwitch() throws Exception {
    Context ctx = ctxForField(DummyTarget.class, "instanceField");
    ObjectRef targetRef = ObjectRef.randomRef();

    ExecMessage msg =
        builder.buildFieldOp(
            peerId, ctx, EXEC_PUT_FIELD, this, ObjectRef.randomRef(), targetRef, 42, null);

    assertNotNull(msg);
    assertThat(getMessageTypeOf(msg), is(EXEC_PUT_FIELD));
    assertNotNull(msg.getInstanceFieldPut());
    assertThat(msg.getInstanceFieldPut().getObjectRef(), is(targetRef.getRef()));
  }

  /** Tests buildFieldOp with EXEC_GET_STATIC covers the GET_STATIC switch branch. */
  @Test
  public void buildFieldOp_getStatic_coversSwitch() throws Exception {
    Context ctx = ctxForField(DummyTarget.class, "staticField");

    ExecMessage msg =
        builder.buildFieldOp(
            peerId, ctx, EXEC_GET_STATIC, this, ObjectRef.randomRef(), null, null, null);

    assertNotNull(msg);
    assertThat(getMessageTypeOf(msg), is(EXEC_GET_STATIC));
    assertNotNull(msg.getStaticFieldGet());
    assertThat(msg.getStaticFieldGet().getClazz().getName(), is(DummyTarget.class.getName()));
  }

  /** Tests buildFieldOp with EXEC_PUT_STATIC covers the PUT_STATIC switch branch. */
  @Test
  public void buildFieldOp_putStatic_coversSwitch() throws Exception {
    Context ctx = ctxForField(DummyTarget.class, "staticField");

    ExecMessage msg =
        builder.buildFieldOp(
            peerId, ctx, EXEC_PUT_STATIC, this, ObjectRef.randomRef(), null, "val", null);

    assertNotNull(msg);
    assertThat(getMessageTypeOf(msg), is(EXEC_PUT_STATIC));
    assertNotNull(msg.getStaticFieldPut());
    assertThat(msg.getStaticFieldPut().getClazz().getName(), is(DummyTarget.class.getName()));
  }

  /** Tests buildFieldOp with an unexpected MessageType throws IllegalArgumentException. */
  @Test(expected = IllegalArgumentException.class)
  public void buildFieldOp_unexpectedType_throwsIllegalArgument() throws Exception {
    Context ctx = ctxForField(DummyTarget.class, "instanceField");

    builder.buildFieldOp(
        peerId,
        ctx,
        EXEC_CONSTRUCTOR,
        this,
        ObjectRef.randomRef(),
        ObjectRef.randomRef(),
        null,
        null);
  }

  // ========================================================================
  // buildFieldOpDone switch branches
  // ========================================================================

  /** Tests buildFieldOpDone with EXEC_PUT_FIELD_DONE covers the PUT_FIELD_DONE branch. */
  @Test
  public void buildFieldOpDone_putFieldDone_coversSwitch() throws Exception {
    Context ctx = ctxForField(DummyTarget.class, "instanceField");
    AccessibleObject field = DummyTarget.class.getDeclaredField("instanceField");

    ExecMessage msg = builder.buildFieldOpDone(peerId, field, ctx, EXEC_PUT_FIELD_DONE);

    assertNotNull(msg);
    assertThat(getMessageTypeOf(msg), is(EXEC_PUT_FIELD_DONE));
    assertNotNull(msg.getInstanceFieldPutDone());
    assertThat(msg.getInstanceFieldPutDone().getField().getName(), is("instanceField"));
  }

  /** Tests buildFieldOpDone with EXEC_PUT_STATIC_DONE covers the PUT_STATIC_DONE branch. */
  @Test
  public void buildFieldOpDone_putStaticDone_coversSwitch() throws Exception {
    Context ctx = ctxForField(DummyTarget.class, "staticField");
    AccessibleObject field = DummyTarget.class.getDeclaredField("staticField");

    ExecMessage msg = builder.buildFieldOpDone(peerId, field, ctx, EXEC_PUT_STATIC_DONE);

    assertNotNull(msg);
    assertThat(getMessageTypeOf(msg), is(EXEC_PUT_STATIC_DONE));
    assertNotNull(msg.getStaticFieldPutDone());
    assertThat(msg.getStaticFieldPutDone().getField().getName(), is("staticField"));
  }

  /** Tests buildFieldOpDone with an unexpected MessageType throws IllegalArgumentException. */
  @Test(expected = IllegalArgumentException.class)
  public void buildFieldOpDone_unexpectedType_throwsIllegalArgument() throws Exception {
    Context ctx = ctxForField(DummyTarget.class, "instanceField");
    AccessibleObject field = DummyTarget.class.getDeclaredField("instanceField");

    builder.buildFieldOpDone(peerId, field, ctx, EXEC_CONSTRUCTOR);
  }

  /** Tests buildFieldOpDone with a non-Field accessible object throws IllegalArgumentException. */
  @Test(expected = IllegalArgumentException.class)
  public void buildFieldOpDone_nonFieldAccessible_throwsIllegalArgument() throws Exception {
    Context ctx = ctxForField(DummyTarget.class, "instanceField");
    // Pass a Method instead of a Field
    AccessibleObject method = DummyTarget.class.getDeclaredMethod("methodWithoutExceptions");

    builder.buildFieldOpDone(peerId, method, ctx, EXEC_PUT_FIELD_DONE);
  }

  // ========================================================================
  // buildReturnValue branches
  // ========================================================================

  /**
   * Tests buildReturnValue with isVoid=true: the returned ExecMessage should not have a wrapped
   * object set in the return value.
   */
  @Test
  public void buildReturnValue_voidReturn_setsVoidFlag() throws Exception {
    Method method = DummyTarget.class.getDeclaredMethod("voidMethod");
    String responseToId = UUID.randomUUID().toString();

    ExecMessage msg = builder.buildReturnValue(null, method, null, true, responseToId);

    assertNotNull(msg);
    assertThat(getMessageTypeOf(msg), is(EXEC_RETURN_VALUE));
    assertNotNull(msg.getReturnValue());
    assertTrue(msg.getReturnValue().getIsVoid());
    // When isVoid=true, the object should not be set
    assertNull(msg.getReturnValue().getObject());
  }

  /**
   * Tests buildReturnValue with isVoid=false and a non-null object: the returned ExecMessage should
   * have the wrapped object set.
   */
  @Test
  public void buildReturnValue_objectReturn_setsObject() throws Exception {
    Method method = DummyTarget.class.getDeclaredMethod("addInts", int.class, int.class);
    ObjectRef ref = ObjectRef.randomRef();
    String responseToId = UUID.randomUUID().toString();

    ExecMessage msg = builder.buildReturnValue(42, method, ref, false, responseToId);

    assertNotNull(msg);
    assertThat(getMessageTypeOf(msg), is(EXEC_RETURN_VALUE));
    assertNotNull(msg.getReturnValue());
    assertNotNull(msg.getReturnValue().getObject());
    assertThat(msg.getReturnValue().getObject().getClazz().getName(), is("int"));
    assertThat(msg.getReturnValue().getFrom().getMethod().getName(), is("addInts"));
  }

  /**
   * Tests buildReturnValue with isVoid=false and null return object: the returned ExecMessage
   * handles null gracefully.
   */
  @Test
  public void buildReturnValue_nullReturn_handlesGracefully() throws Exception {
    Method method = DummyTarget.class.getDeclaredMethod("addInts", int.class, int.class);
    String responseToId = UUID.randomUUID().toString();

    ExecMessage msg = builder.buildReturnValue(null, method, null, false, responseToId);

    assertNotNull(msg);
    assertThat(getMessageTypeOf(msg), is(EXEC_RETURN_VALUE));
    assertNotNull(msg.getReturnValue());
    // Object is set but wraps a null value
    assertNotNull(msg.getReturnValue().getObject());
  }

  // ========================================================================
  // extractDeclaredExceptions branches
  // ========================================================================

  /**
   * Tests extractDeclaredExceptions for a method with no declared exceptions: returns empty array.
   */
  @Test
  public void extractDeclaredExceptions_noExceptions_returnsNull() throws Exception {
    Method m =
        MessageBuilder.class.getDeclaredMethod(
            "extractDeclaredExceptions", String.class, String.class, String[].class);
    m.setAccessible(true);

    String[] result =
        (String[])
            m.invoke(
                builder, DummyTarget.class.getName(), "methodWithoutExceptions", new String[0]);

    assertNotNull(result);
    assertThat(result.length, is(0));
  }

  /**
   * Tests extractDeclaredExceptions for a method with multiple declared exceptions: returns all
   * exception class names.
   */
  @Test
  public void extractDeclaredExceptions_multipleExceptions_returnsAll() throws Exception {
    Method m =
        MessageBuilder.class.getDeclaredMethod(
            "extractDeclaredExceptions", String.class, String.class, String[].class);
    m.setAccessible(true);

    String[] result =
        (String[])
            m.invoke(
                builder,
                DummyTarget.class.getName(),
                "methodWithExceptions",
                new String[] {"java.lang.String"});

    assertNotNull(result);
    assertThat(result.length, is(2));
    // Verify both exception names are present (order may vary)
    List<String> names = Arrays.asList(result);
    assertTrue(
        "Expected IOException in exceptions list", names.contains(IOException.class.getName()));
    assertTrue(
        "Expected ClassNotFoundException in exceptions list",
        names.contains(ClassNotFoundException.class.getName()));
  }

  // ========================================================================
  // buildClassMethod with includeDeclaredExceptions branch
  // ========================================================================

  /**
   * Tests buildClassMethod with includeDeclaredExceptions=true: the resulting ExecMessage should
   * have declaredExceptions populated.
   */
  @Test
  public void buildClassMethod_withDeclaredExceptions_includesExceptions() {
    ExecMessage msg =
        builder.buildClassMethod(
            peerId,
            DummyTarget.class.getName(),
            "methodWithExceptions",
            new String[] {"java.lang.String"},
            this,
            ObjectRef.randomRef(),
            new Object[] {"arg"},
            new ObjectRef[] {null},
            true);

    assertNotNull(msg);
    String[] exceptions = msg.getDeclaredExceptions();
    assertNotNull(exceptions);
    assertThat(exceptions.length, is(2));
    List<String> names = Arrays.asList(exceptions);
    assertTrue(names.contains(IOException.class.getName()));
    assertTrue(names.contains(ClassNotFoundException.class.getName()));
  }
}
