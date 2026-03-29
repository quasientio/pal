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

import static io.quasient.pal.messages.types.MessageType.EXEC_CLASS_METHOD;
import static io.quasient.pal.messages.types.MessageType.EXEC_CONSTRUCTOR;
import static io.quasient.pal.messages.types.MessageType.EXEC_GET_FIELD;
import static io.quasient.pal.messages.types.MessageType.EXEC_GET_STATIC;
import static io.quasient.pal.messages.types.MessageType.EXEC_INSTANCE_METHOD;
import static io.quasient.pal.messages.types.MessageType.EXEC_PUT_FIELD;
import static io.quasient.pal.messages.types.MessageType.EXEC_PUT_FIELD_DONE;
import static io.quasient.pal.messages.types.MessageType.EXEC_PUT_STATIC;
import static io.quasient.pal.messages.types.MessageType.EXEC_PUT_STATIC_DONE;
import static io.quasient.pal.messages.types.MessageType.EXEC_RETURN_VALUE;
import static io.quasient.pal.messages.types.MessageType.EXEC_THROWABLE;
import static io.quasient.pal.serdes.colfer.ExecMessageUtils.getMessageTypeOf;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import io.quasient.pal.common.directory.nodes.InterceptRequest;
import io.quasient.pal.common.lang.FieldOpType;
import io.quasient.pal.common.lang.intercept.CheckedExceptionPolicy;
import io.quasient.pal.common.lang.intercept.ExceptionPropagationPolicy;
import io.quasient.pal.common.lang.intercept.InterceptType;
import io.quasient.pal.common.lang.intercept.InterceptableFieldOp;
import io.quasient.pal.common.lang.intercept.InterceptableMethodCall;
import io.quasient.pal.common.lang.reflect.ConstructorSignature;
import io.quasient.pal.common.lang.reflect.ExecutableObjectType;
import io.quasient.pal.common.lang.reflect.FieldSignature;
import io.quasient.pal.common.lang.reflect.MethodSignature;
import io.quasient.pal.common.objects.ObjectRef;
import io.quasient.pal.common.runtime.Context;
import io.quasient.pal.common.util.UuidUtils;
import io.quasient.pal.messages.colfer.ControlMessage;
import io.quasient.pal.messages.colfer.ExecMessage;
import io.quasient.pal.messages.colfer.InterceptKeyMessage;
import io.quasient.pal.messages.colfer.InterceptMessage;
import io.quasient.pal.messages.colfer.InterceptResponse;
import io.quasient.pal.messages.colfer.InternalHeader;
import io.quasient.pal.messages.colfer.Message;
import io.quasient.pal.messages.colfer.Obj;
import io.quasient.pal.messages.types.ControlCommandType;
import io.quasient.pal.messages.types.ControlStatusType;
import io.quasient.pal.messages.types.InternalHeaderType;
import io.quasient.pal.messages.types.MessageType;
import io.quasient.pal.serdes.Unwrapper;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.Before;
import org.junit.Test;

// Naming convention to use: methodName_stateUnderTest_expectedBehavior
public class MessageBuilderTest {

  @SuppressWarnings({"unused", "rawtypes"})
  static class DummyClassForTest {
    int anInt;
    static String aStaticField;
    Object anObject;

    public DummyClassForTest() {}

    public DummyClassForTest(String str1, int number) {}

    public void dummyMethodJustPrimitiveArgs(String str1, int number, Boolean myboo) {}

    public void dummyMethod(String str1, int number, List someList) {}

    public int addInts(int x, int y) {
      return x + y;
    }

    public static void dummyStaticMethod(String str1, String str2, boolean myboo) {}
  }

  @SuppressWarnings("PMD.NoFullyQualifiedTypes")
  static class ExtractedFieldOpMessageInfo {
    ObjectRef targetObjectRef;
    io.quasient.pal.messages.colfer.Context context;
    String className;
    String fieldName;
  }

  // <editor-fold desc="Helper methods">

  private Context createContextForConstructor(
      Class<?> constructorClass, Class<?>... constructorArgTypes) throws Exception {
    ConstructorSignature constructorSignature =
        new ConstructorSignature(constructorClass.getDeclaredConstructor(constructorArgTypes));
    String sourceFile = "MessageBuilderTest.java";
    int lineNumber = 17;
    return new Context(sourceFile, lineNumber, constructorClass, constructorSignature);
  }

  private Context createContextForInstanceMethod(
      Class<?> clazz, String method, Class<?>... methodArgTypes) throws Exception {
    MethodSignature methodSignature =
        new MethodSignature(clazz.getDeclaredMethod(method, methodArgTypes));
    String sourceFile = "MessageBuilderTest.java";
    int lineNumber = 20;
    return new Context(sourceFile, lineNumber, clazz, methodSignature);
  }

  private Context createContextForFieldOp(Class<?> clazz, String fieldName) throws Exception {
    FieldSignature fieldSignature = new FieldSignature(clazz.getDeclaredField(fieldName));
    String sourceFile = "MessageBuilderTest.java";
    int lineNumber = 20;
    Class<?> withinType = this.getClass();
    return new Context(sourceFile, lineNumber, withinType, fieldSignature);
  }

  private ExtractedFieldOpMessageInfo extractedFieldOpMessageInfo(ExecMessage fieldOpMessage) {
    ExtractedFieldOpMessageInfo extractedFieldOpMessageInfo = new ExtractedFieldOpMessageInfo();
    MessageType execMessageType = getMessageTypeOf(fieldOpMessage);
    switch (execMessageType) {
      case EXEC_GET_FIELD -> {
        assertNotNull(fieldOpMessage.getInstanceFieldGet());
        extractedFieldOpMessageInfo.targetObjectRef =
            ObjectRef.from(fieldOpMessage.getInstanceFieldGet().getObjectRef());
        extractedFieldOpMessageInfo.context = fieldOpMessage.getInstanceFieldGet().getContext();
        extractedFieldOpMessageInfo.fieldName =
            fieldOpMessage.getInstanceFieldGet().getField().getName();
        extractedFieldOpMessageInfo.className =
            fieldOpMessage.getInstanceFieldGet().getClazz().getName();
      }
      case EXEC_PUT_FIELD -> {
        assertNotNull(fieldOpMessage.getInstanceFieldPut());
        extractedFieldOpMessageInfo.targetObjectRef =
            ObjectRef.from(fieldOpMessage.getInstanceFieldPut().getObjectRef());
        extractedFieldOpMessageInfo.context = fieldOpMessage.getInstanceFieldPut().getContext();
        extractedFieldOpMessageInfo.fieldName =
            fieldOpMessage.getInstanceFieldPut().getField().getName();
        extractedFieldOpMessageInfo.className =
            fieldOpMessage.getInstanceFieldPut().getClazz().getName();
      }
      case EXEC_GET_STATIC -> {
        assertNotNull(fieldOpMessage.getStaticFieldGet());
        extractedFieldOpMessageInfo.context = fieldOpMessage.getStaticFieldGet().getContext();
        extractedFieldOpMessageInfo.fieldName =
            fieldOpMessage.getStaticFieldGet().getField().getName();
        extractedFieldOpMessageInfo.className =
            fieldOpMessage.getStaticFieldGet().getClazz().getName();
      }
      case EXEC_PUT_STATIC -> {
        assertNotNull(fieldOpMessage.getStaticFieldPut());
        extractedFieldOpMessageInfo.context = fieldOpMessage.getStaticFieldPut().getContext();
        extractedFieldOpMessageInfo.fieldName =
            fieldOpMessage.getStaticFieldPut().getField().getName();
        extractedFieldOpMessageInfo.className =
            fieldOpMessage.getStaticFieldPut().getClazz().getName();
      }
      case EXEC_PUT_FIELD_DONE -> {
        assertNotNull(fieldOpMessage.getInstanceFieldPutDone());
        extractedFieldOpMessageInfo.fieldName =
            fieldOpMessage.getInstanceFieldPutDone().getField().getName();
        extractedFieldOpMessageInfo.className =
            fieldOpMessage.getInstanceFieldPutDone().getField().getClazz().getName();
      }
      case EXEC_PUT_STATIC_DONE -> {
        assertNotNull(fieldOpMessage.getStaticFieldPutDone());
        extractedFieldOpMessageInfo.fieldName =
            fieldOpMessage.getStaticFieldPutDone().getField().getName();
        extractedFieldOpMessageInfo.className =
            fieldOpMessage.getStaticFieldPutDone().getField().getClazz().getName();
      }
      default -> fail("Unexpected exec MessageType: " + execMessageType.name());
    }
    return extractedFieldOpMessageInfo;
  }

  // </editor-fold>

  private void assertClassMethodParameters(
      ExecMessage execMessage, String[] parameterTypes, Object[] args)
      throws ClassNotFoundException {
    assertEquals(parameterTypes.length, execMessage.getClassMethodCall().getArgs().length);
    for (int i = 0; i < execMessage.getClassMethodCall().getArgs().length; i++) {
      Obj arg = execMessage.getClassMethodCall().getArgs()[i];
      assertEquals(parameterTypes[i], arg.getClazz().getName());

      Object unwrapped = Unwrapper.unwrapObject(arg);
      Object expected = args[i];

      if (expected instanceof float[] expectedArray
          && unwrapped instanceof float[] unwrappedArray) {
        assertArrayEquals(expectedArray, unwrappedArray, 0f);
      } else if (expected instanceof boolean[] expectedArray
          && unwrapped instanceof boolean[] unwrappedArray) {
        assertArrayEquals(expectedArray, unwrappedArray);
      } else if (expected instanceof Number e && unwrapped instanceof Number u) {
        if (e instanceof Float || u instanceof Float) {
          assertEquals(e.floatValue(), u.floatValue(), 0f);
        } else if (e instanceof Double || u instanceof Double) {
          assertEquals(e.doubleValue(), u.doubleValue(), 0d);
        } else {
          assertEquals(e.longValue(), u.longValue());
        }
      } else {
        assertEquals(expected, unwrapped);
      }
    }
  }

  private final UUID peerId = UUID.randomUUID();
  private MessageBuilder messageBuilder;
  private MessageBuilder messageBuilderWithContext;

  @Before
  public void setUp() throws Exception {
    messageBuilder = new MessageBuilder(peerId);
    messageBuilderWithContext = new MessageBuilder(peerId, Boolean.toString(true));
  }

  @Test
  public void messageBuilder_noArgs_newMessageBuilder() {
    messageBuilder = new MessageBuilder();
    assertNotNull(messageBuilder);
  }

  @Test
  public void messageBuilder_withIncludeSourceContextStr_newMessageBuilder() {
    MessageBuilder messageBuilder = new MessageBuilder(peerId, Boolean.toString(false));
    assertNotNull(messageBuilder);
  }

  @Test
  public void messageBuilder_nullPeerId_includeSrcContextTrue_contextIncludedOnClassMethod() {
    MessageBuilder b = new MessageBuilder(null, Boolean.toString(true));
    String[] parameterTypes = null;
    Object[] args = null;
    ObjectRef[] argRefs = null;
    ExecMessage em =
        b.buildClassMethod(
            peerId,
            DummyClassForTest.class.getName(),
            "dummyStaticMethod",
            parameterTypes,
            this,
            ObjectRef.randomRef(),
            args,
            argRefs);
    assertNotNull(em.getClassMethodCall().getContext());
  }

  // <editor-fold desc="Thread-local sequence stamping methods">
  @Test
  public void resetThreadLocalSequence() {
    String className = this.getClass().getName();
    int expectedDispatchSeq = 1;
    int expectedBuilderSeq = 1;

    // create 2 messages, and assert that only builder sequence is incremented
    ExecMessage execMessage = messageBuilder.buildEmptyConstructor(peerId, className);
    assertEquals(expectedBuilderSeq++, execMessage.getBuilderSeq());
    assertEquals(expectedDispatchSeq, execMessage.getDispatchSeq());

    execMessage = messageBuilder.buildEmptyConstructor(peerId, className);
    assertEquals(expectedBuilderSeq, execMessage.getBuilderSeq());
    assertEquals(expectedDispatchSeq, execMessage.getDispatchSeq());

    messageBuilder.resetThreadLocalSequence();
    expectedDispatchSeq += 1;
    expectedBuilderSeq = 1;

    execMessage = messageBuilder.buildEmptyConstructor(peerId, className);
    assertEquals(expectedBuilderSeq, execMessage.getBuilderSeq());
    assertEquals(expectedDispatchSeq, execMessage.getDispatchSeq());
  }

  // </editor-fold>

  // <editor-fold desc="Header messages">
  @Test
  public void buildWriteAheadHeader_validUuid_writeAheadHeader() {
    InternalHeader header = messageBuilder.buildWriteAheadHeader(peerId);
    assertNotNull(header);
    assertEquals(peerId.toString(), header.getValue());
    assertEquals(InternalHeaderType.WRITE_AHEAD.toByte(), header.getHeaderType());
  }

  // </editor-fold>

  // <editor-fold desc="Constructor messages">
  @Test
  public void buildEmptyConstructor_noArguments_constructorMessage() {
    String className = this.getClass().getName();
    ExecMessage execMessage = messageBuilder.buildEmptyConstructor(peerId, className);
    assertNotNull(execMessage);
    assertEquals(EXEC_CONSTRUCTOR, getMessageTypeOf(execMessage));
    assertNotNull(execMessage.getConstructorCall());
    assertEquals(peerId.toString(), UuidUtils.toString(execMessage.getPeerUuid()));
    assertEquals(className, execMessage.constructorCall.getClazz().getName());
    assertNull(execMessage.getConstructorCall().getContext());
  }

  @Test
  public void buildNonEmptyConstructor_primitiveArguments_constructorMessage() {
    String className = this.getClass().getName();
    String[] parameterTypes = {"String", "int"};
    Object[] args = {"test", 123};
    ObjectRef[] argObjRefs = {null, null};
    ExecMessage execMessage =
        messageBuilder.buildNonEmptyConstructor(
            peerId, className, parameterTypes, args, argObjRefs);
    assertNotNull(execMessage);
    assertEquals(EXEC_CONSTRUCTOR, getMessageTypeOf(execMessage));
    assertNotNull(execMessage.getConstructorCall());
    assertEquals(peerId.toString(), UuidUtils.toString(execMessage.getPeerUuid()));
    assertEquals(className, execMessage.constructorCall.getClazz().getName());
    assertNull(execMessage.getConstructorCall().getContext());
    assertEquals(parameterTypes.length, execMessage.getConstructorCall().getArgs().length);
  }

  @Test
  public void buildConstructorEphemeral_withArgs_constructorMessage() throws Exception {
    var clazz = DummyClassForTest.class;
    Object sender = this;
    ObjectRef senderObjRef = ObjectRef.randomRef();
    Object[] args = {"test", 123};
    ObjectRef[] argObjRefs = {null, null};
    Context constructorContext = createContextForConstructor(clazz, String.class, int.class);
    ExecMessage execMessage =
        messageBuilderWithContext.buildConstructorMessageEphemeral(
            constructorContext, sender, senderObjRef, args, argObjRefs);
    assertNotNull(execMessage);
    assertEquals(EXEC_CONSTRUCTOR, getMessageTypeOf(execMessage));
    assertNotNull(execMessage.getConstructorCall());
    assertEquals(peerId.toString(), UuidUtils.toString(execMessage.getPeerUuid()));
    assertEquals(clazz.getName(), execMessage.getConstructorCall().getClazz().getName());
    assertNotNull(execMessage.getConstructorCall().getContext());

    // verify Context
    assertEquals(
        constructorContext.getSourceFilename(),
        execMessage.getConstructorCall().getContext().getSourceLocationFile());
    assertEquals(
        constructorContext.getSourceLine(),
        execMessage.getConstructorCall().getContext().getSourceLocationLine());
    assertEquals(
        constructorContext.getWithinType().getName(),
        execMessage.getConstructorCall().getContext().getSourceLocationType());

    assertEquals(args.length, execMessage.getConstructorCall().getArgs().length);
  }

  @Test
  public void buildConstructor_withMixedArgs_constructorMessage() throws ClassNotFoundException {
    Object sender = this;
    ObjectRef senderObjRef = ObjectRef.randomRef();
    var clazz = ArrayList.class;
    int arrayListInitialCapacity = 23;
    String[] parameterTypes = new String[] {"int"};
    Object[] args = {arrayListInitialCapacity};
    ExecMessage execMessage =
        messageBuilder.buildConstructor(
            peerId, clazz.getName(), parameterTypes, args, sender, senderObjRef);

    assertNotNull(execMessage);
    assertEquals(EXEC_CONSTRUCTOR, getMessageTypeOf(execMessage));
    assertNotNull(execMessage.getConstructorCall());
    assertEquals(peerId.toString(), UuidUtils.toString(execMessage.getPeerUuid()));
    assertEquals(clazz.getName(), execMessage.getConstructorCall().getClazz().getName());
    assertNull(execMessage.getConstructorCall().getContext());
    assertEquals(args.length, execMessage.getConstructorCall().getArgs().length);
    // compare parameter types and args
    for (int i = 0; i < execMessage.getConstructorCall().getArgs().length; i++) {
      Obj arg = execMessage.getConstructorCall().getArgs()[i];
      assertEquals(parameterTypes[i], arg.getClazz().getName());
      assertEquals(args[i], Unwrapper.unwrapObject(arg));
    }
  }

  @Test
  public void buildConstructor_withContext_zeroArgs_usesNamedParametersFromContext()
      throws Exception {
    // use context-based constructor with zero args to exercise createNamedParameters(Context,...)
    var clazz = DummyClassForTest.class;
    Context ctx = createContextForConstructor(clazz);
    Object sender = this;
    ObjectRef senderRef = ObjectRef.randomRef();
    ExecMessage em =
        messageBuilderWithContext.buildConstructor(
            peerId, ctx, sender, senderRef, new Object[0], new ObjectRef[0]);

    assertNotNull(em.getConstructorCall());
    assertEquals(clazz.getName(), em.getConstructorCall().getClazz().getName());
    assertNotNull(em.getConstructorCall().getContext());
    assertNotNull(em.getConstructorCall().getArgs());
    assertEquals(0, em.getConstructorCall().getArgs().length);
  }

  // </editor-fold>

  // <editor-fold desc="Instance method messages">

  @Test
  public void buildInstanceMethod_classNameMethodName_instanceMethodCallMessage() {
    String className = "TestClassName";
    String methodName = "testMethod";
    ObjectRef targetObjRef = ObjectRef.randomRef();
    String[] parameterTypes = {"String", "int"};
    Object[] args = {"test", 123};
    ObjectRef[] argObjRefs = {null, null};
    ExecMessage execMessage =
        messageBuilder.buildInstanceMethod(
            peerId, className, methodName, targetObjRef, parameterTypes, args, argObjRefs);
    assertNotNull(execMessage);
    assertEquals(EXEC_INSTANCE_METHOD, getMessageTypeOf(execMessage));
    assertNotNull(execMessage.getInstanceMethodCall());
    assertEquals(peerId.toString(), UuidUtils.toString(execMessage.getPeerUuid()));
    assertEquals(className, execMessage.getInstanceMethodCall().getClazz().getName());
    assertEquals(methodName, execMessage.getInstanceMethodCall().getName());
    assertNull(execMessage.getInstanceMethodCall().getContext());
    assertEquals(parameterTypes.length, execMessage.getInstanceMethodCall().getArgs().length);
  }

  @Test
  @SuppressWarnings("PMD.NoFullyQualifiedTypes")
  public void buildInstanceMethodEphemeral_withArgs_instanceMethodCallMessage() throws Exception {
    Object sender = this;
    ObjectRef senderObjRef = ObjectRef.randomRef();
    var clazz = DummyClassForTest.class;
    String methodName = "dummyMethod";
    ObjectRef targetObjRef = ObjectRef.randomRef();
    Object[] args = {"test", 123, new ArrayList<>()};
    ObjectRef[] argObjRefs = {null, null, ObjectRef.randomRef()};
    Context instanceMethodContext =
        createContextForInstanceMethod(clazz, methodName, String.class, int.class, List.class);
    ExecMessage execMessage =
        messageBuilderWithContext.buildInstanceMethodMessageEphemeral(
            instanceMethodContext, sender, senderObjRef, targetObjRef, args, argObjRefs);
    assertNotNull(execMessage);
    assertEquals(EXEC_INSTANCE_METHOD, getMessageTypeOf(execMessage));
    assertNotNull(execMessage.getInstanceMethodCall());
    assertEquals(peerId.toString(), UuidUtils.toString(execMessage.getPeerUuid()));
    assertEquals(
        DummyClassForTest.class.getName(),
        execMessage.getInstanceMethodCall().getClazz().getName());
    assertEquals("dummyMethod", execMessage.getInstanceMethodCall().getName());
    assertNotNull(execMessage.getInstanceMethodCall().getContext());
    io.quasient.pal.messages.colfer.Context messageContext =
        execMessage.getInstanceMethodCall().getContext();
    assertEquals(instanceMethodContext.getSourceFilename(), messageContext.getSourceLocationFile());
    assertEquals(instanceMethodContext.getSourceLine(), messageContext.getSourceLocationLine());
    assertEquals(
        instanceMethodContext.getWithinType().getName(), messageContext.getSourceLocationType());
    assertEquals(args.length, execMessage.getInstanceMethodCall().getArgs().length);
  }

  @Test
  public void buildInstanceMethod_mixedArgs_instanceMethodCallMessage()
      throws ClassNotFoundException {
    final String methodToCall = "dummyMethodJustPrimitiveArgs";
    String[] parameterTypes = new String[] {"java.lang.String", "int", "java.lang.Boolean"};
    Object[] args = new Object[] {"test", 123, Boolean.FALSE};
    ExecMessage execMessage =
        messageBuilder.buildInstanceMethod(
            peerId,
            DummyClassForTest.class.getName(),
            methodToCall,
            ObjectRef.randomRef(),
            parameterTypes,
            args);

    assertNotNull(execMessage);
    assertEquals(EXEC_INSTANCE_METHOD, getMessageTypeOf(execMessage));
    assertNotNull(execMessage.getInstanceMethodCall());
    assertEquals(peerId.toString(), UuidUtils.toString(execMessage.getPeerUuid()));
    assertEquals(
        DummyClassForTest.class.getName(),
        execMessage.getInstanceMethodCall().getClazz().getName());
    assertEquals(methodToCall, execMessage.getInstanceMethodCall().getName());
    assertNull(execMessage.getInstanceMethodCall().getContext());
    assertEquals(3, execMessage.getInstanceMethodCall().getArgs().length);
    // compare parameter types and args
    for (int i = 0; i < execMessage.getInstanceMethodCall().getArgs().length; i++) {
      Obj arg = execMessage.getInstanceMethodCall().getArgs()[i];
      assertEquals(parameterTypes[i], arg.getClazz().getName());
      assertEquals(args[i], Unwrapper.unwrapObject(arg));
    }
  }

  // </editor-fold>

  // <editor-fold desc="Class method messages">
  @Test
  public void buildClassMethod_withArgsAndNullObjectRefs_classMethodMessage()
      throws ClassNotFoundException {
    String className = "java.util.Arrays";
    String methodName = "binarySearch"; // binarySearch(float[] a, float key)
    String[] parameterTypes = new String[] {"[F", "float"};
    Object sender = this;
    ObjectRef senderObjRef = ObjectRef.randomRef();
    Object[] args = new Object[] {new float[] {4.5f, 54.2f, 9383.23f}, 1235.34f};
    ObjectRef[] argObjRefs = new ObjectRef[] {null, null};

    ExecMessage execMessage =
        messageBuilder.buildClassMethod(
            peerId, className, methodName, parameterTypes, sender, senderObjRef, args, argObjRefs);

    assertNotNull(execMessage);
    assertEquals(EXEC_CLASS_METHOD, getMessageTypeOf(execMessage));
    assertNotNull(execMessage.getClassMethodCall());
    assertEquals(peerId.toString(), UuidUtils.toString(execMessage.getPeerUuid()));
    assertEquals(className, execMessage.getClassMethodCall().getClazz().getName());
    assertEquals(methodName, execMessage.getClassMethodCall().getName());
    assertNull(execMessage.getClassMethodCall().getContext());
    assertClassMethodParameters(execMessage, parameterTypes, args);
  }

  @Test
  public void buildClassMethod_withPrimitiveArgs_classMethodMessage() {
    UUID peerUuid = UUID.randomUUID();
    Class<?> clazz = DummyClassForTest.class;
    String methodName = "dummyStaticMethod";
    Object sender = this;
    ObjectRef senderObjRef = ObjectRef.randomRef();
    String[] paramTypeNames =
        new String[] {String.class.getName(), String.class.getName(), boolean.class.getName()};
    Object[] args = new Object[] {"arg1", "arg2", true};
    ObjectRef[] argObjRefs = new ObjectRef[] {null, null, null};
    ExecMessage execMessage =
        messageBuilderWithContext.buildClassMethod(
            peerUuid,
            clazz.getName(),
            methodName,
            paramTypeNames,
            sender,
            senderObjRef,
            args,
            argObjRefs);

    // assert expected values of ExecMessage
    assertNotNull(execMessage);
    assertEquals(EXEC_CLASS_METHOD, getMessageTypeOf(execMessage));
    assertNotNull(execMessage.getClassMethodCall());
    assertEquals(peerUuid.toString(), UuidUtils.toString(execMessage.getPeerUuid()));
    assertEquals(
        DummyClassForTest.class.getName(), execMessage.getClassMethodCall().getClazz().getName());
    assertEquals("dummyStaticMethod", execMessage.getClassMethodCall().getName());
    assertNotNull(execMessage.getClassMethodCall().getContext());
  }

  @Test
  public void buildClassMethod_staticMethodCall_classMethodMessage() throws ClassNotFoundException {
    Object sender = this;
    ObjectRef senderObjRef = ObjectRef.randomRef();
    // Arrays.fill(boolean[] a, boolean val)
    String method = "fill";
    var clazz = Arrays.class;
    String[] parameterTypes = new String[] {"[Z", "boolean"};
    Object[] args = new Object[] {new boolean[] {false, false, false}, true};

    ExecMessage execMessage =
        messageBuilder.buildClassMethod(
            peerId, clazz.getName(), method, parameterTypes, sender, senderObjRef, args);

    // assert expected values of ExecMessage
    assertNotNull(execMessage);
    assertEquals(EXEC_CLASS_METHOD, getMessageTypeOf(execMessage));
    assertNotNull(execMessage.getClassMethodCall());
    assertEquals(peerId.toString(), UuidUtils.toString(execMessage.getPeerUuid()));
    assertEquals(clazz.getName(), execMessage.getClassMethodCall().getClazz().getName());
    assertEquals(method, execMessage.getClassMethodCall().getName());
    assertNull(execMessage.getClassMethodCall().getContext());
    assertClassMethodParameters(execMessage, parameterTypes, args);
  }

  @Test
  public void buildClassMethod_nullArgsAndRefs_createsNullParameterValue() {
    Object sender = this;
    ObjectRef senderObjRef = ObjectRef.randomRef();
    String[] parameterTypes = new String[] {"int"};
    Object[] args = new Object[] {null};
    ObjectRef[] argObjRefs = new ObjectRef[] {null};

    ExecMessage em =
        messageBuilder.buildClassMethod(
            peerId,
            DummyClassForTest.class.getName(),
            "dummyStaticMethod",
            parameterTypes,
            sender,
            senderObjRef,
            args,
            argObjRefs);

    assertNotNull(em.getClassMethodCall().getArgs());
    assertEquals(1, em.getClassMethodCall().getArgs().length);
    assertTrue(em.getClassMethodCall().getArgs()[0].getIsNull());
  }

  @Test(expected = IllegalArgumentException.class)
  public void buildClassMethod_mismatchedArgs_throwsIAE() {
    Object sender = this;
    ObjectRef senderObjRef = ObjectRef.randomRef();
    String[] parameterTypes = new String[] {"int"};
    Object[] args = new Object[] {1, 2}; // longer than parameterTypes
    ObjectRef[] argObjRefs = new ObjectRef[] {null, null};

    messageBuilder.buildClassMethod(
        peerId,
        DummyClassForTest.class.getName(),
        "dummyStaticMethod",
        parameterTypes,
        sender,
        senderObjRef,
        args,
        argObjRefs);
  }

  // </editor-fold>

  // <editor-fold desc="Field Ops generic">
  @Test
  public void buildFieldOp_allFourOps_fieldOpMessages() throws Exception {

    // create a list of specific args for each of the four field op types
    Map<String, Object> map = new HashMap<>();
    map.put("messageType", EXEC_GET_FIELD);
    map.put("target", new Object());
    map.put("targetObjectRef", ObjectRef.randomRef());
    List<Map<String, Object>> listOfFieldOpArgs = new ArrayList<>();
    listOfFieldOpArgs.add(map);

    map = new HashMap<>();
    map.put("messageType", EXEC_PUT_FIELD);
    map.put("target", new Object());
    map.put("targetObjectRef", ObjectRef.from("492849"));
    map.put("arg", "an argument");
    map.put("argObjRef", ObjectRef.from("234987"));
    listOfFieldOpArgs.add(map);

    map = new HashMap<>();
    map.put("messageType", EXEC_GET_STATIC);
    listOfFieldOpArgs.add(map);

    map = new HashMap<>();
    map.put("messageType", EXEC_PUT_STATIC);
    map.put("arg", "an argument");
    map.put("argObjRef", ObjectRef.from("8702347"));
    listOfFieldOpArgs.add(map);

    // common args for all 4 ops
    String fieldName = "anInt";
    var targetClass = DummyClassForTest.class;
    UUID peerUuid = UUID.randomUUID();
    Object sender = this;
    ObjectRef senderObjRef = ObjectRef.randomRef();
    Context context = createContextForFieldOp(targetClass, fieldName);

    // call buildFieldOp for each of the four field op types
    for (Map<String, Object> fieldOpArgs : listOfFieldOpArgs) {
      MessageType execMessageType = (MessageType) fieldOpArgs.get("messageType");
      ObjectRef targetObjRef = (ObjectRef) fieldOpArgs.get("targetObjectRef");
      Object arg = fieldOpArgs.get("arg");
      ObjectRef argObjRef = (ObjectRef) fieldOpArgs.get("argObjRef");
      ExecMessage execMessage =
          messageBuilderWithContext.buildFieldOp(
              peerUuid,
              context,
              execMessageType,
              sender,
              senderObjRef,
              targetObjRef,
              arg,
              argObjRef);

      assertNotNull(execMessage);
      assertEquals(peerUuid.toString(), UuidUtils.toString(execMessage.getPeerUuid()));
      assertEquals(execMessageType, getMessageTypeOf(execMessage));
      ExtractedFieldOpMessageInfo extractedFieldOpMessageInfo =
          extractedFieldOpMessageInfo(execMessage);
      assertEquals(targetClass.getName(), extractedFieldOpMessageInfo.className);
      assertEquals(fieldName, extractedFieldOpMessageInfo.fieldName);
      assertNotNull(extractedFieldOpMessageInfo.context);
      assertEquals(
          context.getSourceFilename(), extractedFieldOpMessageInfo.context.getSourceLocationFile());
      assertEquals(
          context.getSourceLine(), extractedFieldOpMessageInfo.context.getSourceLocationLine());
      assertEquals(
          context.getWithinType().getName(),
          extractedFieldOpMessageInfo.context.getSourceLocationType());

      if (extractedFieldOpMessageInfo.targetObjectRef != null) {
        assertEquals(targetObjRef, extractedFieldOpMessageInfo.targetObjectRef);
      }
    }
  }

  @Test
  public void buildFieldOpDone_putFieldDone_fieldPutDoneMessage() throws Exception {
    MessageBuilder builder = new MessageBuilder();
    UUID peerUuid = UUID.randomUUID();
    var targetClass = DummyClassForTest.class;
    String fieldName = "anInt";
    Context context = createContextForFieldOp(targetClass, fieldName);
    AccessibleObject field = targetClass.getDeclaredField("anInt");
    MessageType type = EXEC_PUT_FIELD_DONE;

    ExecMessage execMessage = builder.buildFieldOpDone(peerUuid, field, context, type);

    // assert expected values of ExecMessage
    assertNotNull(execMessage);
    assertEquals(peerUuid.toString(), UuidUtils.toString(execMessage.getPeerUuid()));
    assertEquals(type, getMessageTypeOf(execMessage));
    ExtractedFieldOpMessageInfo extractedFieldOpMessageInfo =
        extractedFieldOpMessageInfo(execMessage);
    assertEquals(targetClass.getName(), extractedFieldOpMessageInfo.className);
    assertEquals(fieldName, extractedFieldOpMessageInfo.fieldName);
    assertNull(extractedFieldOpMessageInfo.context);
  }

  @Test
  public void buildFieldOpDone_putStaticDone_staticFieldPutDoneMessage() throws Exception {
    MessageBuilder builder = new MessageBuilder();
    UUID peerUuid = UUID.randomUUID();
    var targetClass = DummyClassForTest.class;
    String fieldName = "anInt";
    Context context = createContextForFieldOp(targetClass, fieldName);
    AccessibleObject field = targetClass.getDeclaredField(fieldName);
    MessageType type = EXEC_PUT_STATIC_DONE;

    ExecMessage execMessage = builder.buildFieldOpDone(peerUuid, field, context, type);

    // assert expected values of ExecMessage
    assertNotNull(execMessage);
    assertEquals(peerUuid.toString(), UuidUtils.toString(execMessage.getPeerUuid()));
    assertEquals(type, getMessageTypeOf(execMessage));
    ExtractedFieldOpMessageInfo extractedFieldOpMessageInfo =
        extractedFieldOpMessageInfo(execMessage);
    assertEquals(targetClass.getName(), extractedFieldOpMessageInfo.className);
    assertEquals(fieldName, extractedFieldOpMessageInfo.fieldName);
    assertNull(extractedFieldOpMessageInfo.context);
  }

  // </editor-fold>

  // <editor-fold desc="Static field get messages">
  @Test
  public void buildGetStatic_withClassNameAndFieldName_staticFieldGetMessage() {
    String className = DummyClassForTest.class.getName();
    String fieldName = "aStaticField";

    ExecMessage execMessage = messageBuilder.buildGetStatic(peerId, className, fieldName);

    // assert expected values of ExecMessage
    assertNotNull(execMessage);
    assertEquals(peerId.toString(), UuidUtils.toString(execMessage.getPeerUuid()));
    assertEquals(EXEC_GET_STATIC, getMessageTypeOf(execMessage));
    assertNotNull(execMessage.getStaticFieldGet());
    assertEquals(className, execMessage.getStaticFieldGet().getClazz().getName());
    assertEquals(fieldName, execMessage.getStaticFieldGet().getField().getName());
    assertNull(execMessage.getStaticFieldGet().getContext());
  }

  // </editor-fold>

  // <editor-fold desc="Instance field get messages">
  @Test
  public void buildGetObject_withInstanceObjRef_instanceFieldGetMessage() {
    String className = DummyClassForTest.class.getName();
    String fieldName = "anInt";
    ObjectRef targetObjRef = ObjectRef.randomRef();

    ExecMessage execMessage =
        messageBuilder.buildGetObject(peerId, className, fieldName, targetObjRef);

    // assert expected values of ExecMessage
    assertNotNull(execMessage);
    assertEquals(peerId.toString(), UuidUtils.toString(execMessage.getPeerUuid()));
    assertEquals(EXEC_GET_FIELD, getMessageTypeOf(execMessage));
    assertNotNull(execMessage.getInstanceFieldGet());
    assertEquals(className, execMessage.getInstanceFieldGet().getClazz().getName());
    assertEquals(fieldName, execMessage.getInstanceFieldGet().getField().getName());
    assertEquals(targetObjRef, ObjectRef.from(execMessage.getInstanceFieldGet().getObjectRef()));
    assertNull(execMessage.getInstanceFieldGet().getContext());
  }

  // </editor-fold>

  // <editor-fold desc="Static field put messages">
  @Test
  public void buildPutStatic_withClassAndValue_staticFieldPutMessage() {
    String className = DummyClassForTest.class.getName();
    String fieldName = "aStaticField";
    String valueClassName = String.class.getName();
    Object value = "a str value";

    ExecMessage execMessage =
        messageBuilder.buildPutStatic(peerId, className, fieldName, valueClassName, value);

    // assert expected values of ExecMessage
    assertNotNull(execMessage);
    assertEquals(peerId.toString(), UuidUtils.toString(execMessage.getPeerUuid()));
    assertEquals(EXEC_PUT_STATIC, getMessageTypeOf(execMessage));
    assertNotNull(execMessage.getStaticFieldPut());
    assertEquals(className, execMessage.getStaticFieldPut().getClazz().getName());
    assertEquals(fieldName, execMessage.getStaticFieldPut().getField().getName());
    assertEquals(
        valueClassName, execMessage.getStaticFieldPut().getValueObject().getClazz().getName());
    assertEquals("\"a str value\"", execMessage.getStaticFieldPut().getValueObject().getValue());
    assertNull(execMessage.getStaticFieldPut().getContext());
  }

  @Test
  public void buildPutStatic_withObjectRefValue_staticFieldPutMessage() {
    String className = DummyClassForTest.class.getName();
    String fieldName = "aStaticField";
    ObjectRef valueObjectRef = ObjectRef.randomRef();

    ExecMessage execMessage =
        messageBuilder.buildPutStatic(peerId, className, fieldName, valueObjectRef);

    // assert expected values of ExecMessage
    assertNotNull(execMessage);
    assertEquals(peerId.toString(), UuidUtils.toString(execMessage.getPeerUuid()));
    assertEquals(EXEC_PUT_STATIC, getMessageTypeOf(execMessage));
    assertNotNull(execMessage.getStaticFieldPut());
    assertEquals(className, execMessage.getStaticFieldPut().getClazz().getName());
    assertEquals(fieldName, execMessage.getStaticFieldPut().getField().getName());
    assertEquals(valueObjectRef.getRef(), execMessage.getStaticFieldPut().getValueObjectRef());
    assertNull(execMessage.getStaticFieldPut().getContext());
  }

  @Test
  public void buildPutStaticDone_withAccessibleObject_staticFieldPutDoneMessage()
      throws NoSuchFieldException {
    var targetClass = DummyClassForTest.class;
    String fieldName = "aStaticField";
    AccessibleObject accessibleObject = targetClass.getDeclaredField(fieldName);
    String staticFieldPutId = UUID.randomUUID().toString();
    String responseToId = UUID.randomUUID().toString();

    ExecMessage execMessage =
        messageBuilder.buildPutStaticDone(peerId, accessibleObject, staticFieldPutId, responseToId);

    // assert expected values of ExecMessage
    assertNotNull(execMessage);
    assertEquals(peerId.toString(), UuidUtils.toString(execMessage.getPeerUuid()));
    assertEquals(EXEC_PUT_STATIC_DONE, getMessageTypeOf(execMessage));
    assertNotNull(execMessage.getStaticFieldPutDone());
    assertEquals(
        targetClass.getName(), execMessage.getStaticFieldPutDone().getField().getClazz().getName());
    assertEquals(fieldName, execMessage.getStaticFieldPutDone().getField().getName());
    assertEquals(staticFieldPutId, execMessage.getStaticFieldPutDone().getStaticFieldPutId());
    assertEquals(responseToId, execMessage.getResponseToId());
  }

  // </editor-fold>

  // <editor-fold desc="Instance field put messages">
  @Test
  public void buildPutObject_withObjectValue_instanceFieldPutMessage()
      throws ClassNotFoundException {
    String className = DummyClassForTest.class.getName();
    String fieldName = "anInt";
    ObjectRef targetObjRef = ObjectRef.randomRef();
    String valueClassName = int.class.getName();
    Object value = 4;

    ExecMessage execMessage =
        messageBuilder.buildPutObject(
            peerId, className, fieldName, targetObjRef, valueClassName, value);

    // assert expected values of ExecMessage
    assertNotNull(execMessage);
    assertEquals(peerId.toString(), UuidUtils.toString(execMessage.getPeerUuid()));
    assertEquals(EXEC_PUT_FIELD, getMessageTypeOf(execMessage));
    assertNotNull(execMessage.getInstanceFieldPut());
    assertEquals(className, execMessage.getInstanceFieldPut().getClazz().getName());
    assertEquals(fieldName, execMessage.getInstanceFieldPut().getField().getName());
    assertEquals(targetObjRef, ObjectRef.from(execMessage.getInstanceFieldPut().getObjectRef()));
    assertEquals(
        valueClassName, execMessage.getInstanceFieldPut().getValueObject().getClazz().getName());
    assertEquals(value, Unwrapper.unwrapObject(execMessage.getInstanceFieldPut().getValueObject()));
    assertNull(execMessage.getInstanceFieldPut().getContext());
  }

  @Test
  public void buildPutObject_withObjectRefValue_instanceFieldPutMessage() {
    String className = DummyClassForTest.class.getName();
    String fieldName = "anObject";
    ObjectRef targetObjRef = ObjectRef.randomRef();
    ObjectRef valueObjectRef = ObjectRef.randomRef();

    ExecMessage execMessage =
        messageBuilder.buildPutObject(peerId, className, fieldName, targetObjRef, valueObjectRef);

    // assert expected values of ExecMessage
    assertNotNull(execMessage);
    assertEquals(peerId.toString(), UuidUtils.toString(execMessage.getPeerUuid()));
    assertEquals(EXEC_PUT_FIELD, getMessageTypeOf(execMessage));
    assertNotNull(execMessage.getInstanceFieldPut());
    assertEquals(className, execMessage.getInstanceFieldPut().getClazz().getName());
    assertEquals(fieldName, execMessage.getInstanceFieldPut().getField().getName());
    assertEquals(targetObjRef, ObjectRef.from(execMessage.getInstanceFieldPut().getObjectRef()));
    assertEquals(valueObjectRef.getRef(), execMessage.getInstanceFieldPut().getValueObjectRef());
    assertNull(execMessage.getInstanceFieldPut().getContext());
  }

  @Test
  public void buildPutObjectDone_withAccessibleAndInstanceFieldPutId_instanceFieldPutDoneMessage()
      throws NoSuchFieldException {
    MessageBuilder builder = new MessageBuilder(peerId, Boolean.toString(false));
    var targetClass = DummyClassForTest.class;
    String fieldName = "anObject";
    AccessibleObject accessibleObject = targetClass.getDeclaredField(fieldName);
    String instanceFieldPutId = UUID.randomUUID().toString();
    String responseToId = UUID.randomUUID().toString();

    ExecMessage execMessage =
        builder.buildPutObjectDone(peerId, accessibleObject, instanceFieldPutId, responseToId);

    // assert expected values of ExecMessage
    assertNotNull(execMessage);
    assertEquals(peerId.toString(), UuidUtils.toString(execMessage.getPeerUuid()));
    assertEquals(EXEC_PUT_FIELD_DONE, getMessageTypeOf(execMessage));
    assertNotNull(execMessage.getInstanceFieldPutDone());
    assertEquals(
        targetClass.getName(),
        execMessage.getInstanceFieldPutDone().getField().getClazz().getName());
    assertEquals(fieldName, execMessage.getInstanceFieldPutDone().getField().getName());
    assertEquals(instanceFieldPutId, execMessage.getInstanceFieldPutDone().getInstanceFieldPutId());
    assertEquals(responseToId, execMessage.getResponseToId());
  }

  // </editor-fold>

  // <editor-fold desc="Intercept messages">
  @Test
  public void buildInterceptMessage_forMethod_interceptMessage() {
    InterceptType type = InterceptType.BEFORE;
    String className = DummyClassForTest.class.getName();
    String methodName = "dummyMethod";
    List<String> parameterTypes = Arrays.asList("String", "int");
    String callbackClassName = this.getClass().getName();
    String callbackMethodName = "callbackMethod";

    InterceptMessage interceptMessage =
        messageBuilder.buildInterceptMessage(
            peerId,
            type,
            className,
            methodName,
            parameterTypes,
            callbackClassName,
            callbackMethodName);

    assertNotNull(interceptMessage);
    assertEquals(peerId.toString(), UuidUtils.toString(interceptMessage.getPeerUuid()));
    assertNotNull(interceptMessage.getMessageId());
    assertEquals(type.toByte(), interceptMessage.interceptType);
    assertEquals(className, interceptMessage.getClazz());
    assertEquals(methodName, interceptMessage.getMethod().getName());
    assertEquals(
        parameterTypes,
        Arrays.stream(interceptMessage.getMethod().getParameterTypes())
            .collect(Collectors.toList()));
    assertEquals(callbackClassName, interceptMessage.getCallbackClass());
    assertEquals(callbackMethodName, interceptMessage.getCallbackMethod());
  }

  @Test
  public void buildInterceptMessage_forField_interceptMessage() {
    InterceptType type = InterceptType.AFTER;
    String className = DummyClassForTest.class.getName();
    String fieldName = "anInt";
    FieldOpType fieldOpType = FieldOpType.GET;
    String callbackClassName = this.getClass().getName();
    String callbackMethodName = "fakeCallbackMethod";

    InterceptMessage interceptMessage =
        messageBuilder.buildInterceptMessage(
            peerId, type, className, fieldName, fieldOpType, callbackClassName, callbackMethodName);

    assertNotNull(interceptMessage);
    assertEquals(peerId.toString(), UuidUtils.toString(interceptMessage.getPeerUuid()));
    assertNotNull(interceptMessage.getMessageId());
    assertEquals(type.toByte(), interceptMessage.interceptType);
    assertEquals(className, interceptMessage.getClazz());
    assertNotNull(interceptMessage.getField());
    assertEquals(fieldName, interceptMessage.getField().getName());
    assertEquals(fieldOpType.toByte(), interceptMessage.getField().getFieldOpType());
    assertEquals(callbackClassName, interceptMessage.getCallbackClass());
    assertEquals(callbackMethodName, interceptMessage.getCallbackMethod());
  }

  @Test
  public void buildInterceptMessage_afterFieldGetInterceptRequest_interceptMessage() {
    UUID interceptRequestUuid = UUID.randomUUID();
    InterceptType type = InterceptType.AFTER;
    var clazz = DummyClassForTest.class;
    var callbackClass = this.getClass();
    String callbackMethod = "fakeCallbackMethod";
    String fieldName = "anInt";
    InterceptableFieldOp interceptableFieldOp =
        new InterceptableFieldOp(fieldName, FieldOpType.GET);
    var interceptRequest =
        new InterceptRequest<>(
            interceptRequestUuid,
            peerId,
            type,
            clazz.getName(),
            callbackClass.getName(),
            callbackMethod,
            interceptableFieldOp);
    InterceptMessage interceptMessage = messageBuilder.buildInterceptMessage(interceptRequest);

    assertNotNull(interceptMessage);
    assertEquals(peerId.toString(), UuidUtils.toString(interceptMessage.getPeerUuid()));
    assertEquals(interceptRequestUuid.toString(), interceptMessage.getMessageId());
    assertEquals(type.toByte(), interceptMessage.interceptType);
    assertEquals(clazz.getName(), interceptMessage.getClazz());
    assertNotNull(interceptMessage.getField());
    assertEquals(fieldName, interceptMessage.getField().getName());
    assertEquals(
        interceptableFieldOp.getFieldOpType().toByte(),
        interceptMessage.getField().getFieldOpType());
    assertEquals(callbackClass.getName(), interceptMessage.getCallbackClass());
    assertEquals(callbackMethod, interceptMessage.getCallbackMethod());
  }

  @Test
  public void buildInterceptMessage_beforeMethodCallInterceptRequest_interceptMessage() {
    UUID interceptRequestUuid = UUID.randomUUID();
    InterceptType type = InterceptType.BEFORE;
    var clazz = DummyClassForTest.class;
    var callbackClass = this.getClass();
    String callbackMethod = "fakeCallbackMethod";
    String method = "dummyMethod";
    List<String> parameterTypes = Arrays.asList("String", "int", "java.util.List");
    InterceptableMethodCall interceptableMethodCall =
        new InterceptableMethodCall(method, parameterTypes);
    var interceptRequest =
        new InterceptRequest<>(
            interceptRequestUuid,
            peerId,
            type,
            clazz.getName(),
            callbackClass.getName(),
            callbackMethod,
            interceptableMethodCall);
    InterceptMessage interceptMessage = messageBuilder.buildInterceptMessage(interceptRequest);

    assertNotNull(interceptMessage);
    assertEquals(peerId.toString(), UuidUtils.toString(interceptMessage.getPeerUuid()));
    assertEquals(interceptRequestUuid.toString(), interceptMessage.getMessageId());
    assertEquals(type.toByte(), interceptMessage.interceptType);
    assertEquals(clazz.getName(), interceptMessage.getClazz());
    assertNotNull(interceptMessage.getMethod());
    assertEquals(method, interceptMessage.getMethod().getName());
    assertEquals(
        parameterTypes,
        Arrays.stream(interceptMessage.getMethod().getParameterTypes())
            .collect(Collectors.toList()));
    assertEquals(callbackClass.getName(), interceptMessage.getCallbackClass());
    assertEquals(callbackMethod, interceptMessage.getCallbackMethod());
  }

  @Test
  public void buildInterceptResponse_uuidResponseToIdResult_validInterceptResponse() {
    UUID responseToId = UUID.randomUUID();
    boolean result = true;

    InterceptResponse interceptResponse =
        messageBuilder.buildInterceptResponse(peerId, responseToId.toString(), result);

    assertNotNull(interceptResponse);
    assertEquals(peerId.toString(), UuidUtils.toString(interceptResponse.getPeerUuid()));
    assertEquals(responseToId.toString(), interceptResponse.getResponseToId());
    assertEquals(result, interceptResponse.getResult());
  }

  @Test
  public void buildInterceptKey_instanceMethodCall_interceptKeyMessage() {

    // create an ExecMessage that we can use to build an InterceptKeyMessage
    UUID peerUuid = UUID.randomUUID();
    var clazz = DummyClassForTest.class;
    String methodName = "dummyMethod";
    Object target = new DummyClassForTest();
    ObjectRef targetObjRef = ObjectRef.randomRef();
    Object[] args = {"test", 123, new ArrayList<>()};
    ObjectRef[] argObjRefs = {null, null, ObjectRef.randomRef()};
    String[] paramTypes =
        new String[] {String.class.getName(), int.class.getName(), List.class.getName()};
    ExecMessage execMessage =
        messageBuilderWithContext.buildInstanceMethod(
            peerUuid, clazz.getName(), methodName, targetObjRef, paramTypes, args, argObjRefs);
    InterceptKeyMessage interceptKeyMessage = messageBuilder.buildInterceptKey(execMessage);

    assertNotNull(interceptKeyMessage);
    assertEquals(target.getClass().getName(), interceptKeyMessage.getClazz());
    assertEquals(target.getClass().getName(), interceptKeyMessage.getClazz());
    assertEquals(
        getMessageTypeOf(execMessage), MessageType.fromId(interceptKeyMessage.getExecMsgType()));
    assertEquals(
        execMessage.getInstanceMethodCall().getName(), interceptKeyMessage.getExecutableName());
    assertEquals(
        execMessage.getInstanceMethodCall().getArgs().length,
        interceptKeyMessage.getParameterTypes().length);
    assertEquals(
        execMessage.getInstanceMethodCall().getArgs()[0].getClazz().getName(),
        interceptKeyMessage.getParameterTypes()[0]);
    assertEquals(
        execMessage.getInstanceMethodCall().getArgs()[1].getClazz().getName(),
        interceptKeyMessage.getParameterTypes()[1]);
    assertEquals(
        execMessage.getInstanceMethodCall().getArgs()[2].getClazz().getName(),
        interceptKeyMessage.getParameterTypes()[2]);
  }

  // </editor-fold>

  // <editor-fold desc="Throwable messages">
  @Test
  public void buildAccessibleObjectThrowable_withConstructor_raisedThrowableMessage()
      throws NoSuchMethodException, NoSuchFieldException {

    List<AccessibleObject> accessibleObjects = new ArrayList<>();
    accessibleObjects.add(DummyClassForTest.class.getConstructor(String.class, int.class));
    accessibleObjects.add(
        DummyClassForTest.class.getDeclaredMethod(
            "dummyMethod", String.class, int.class, List.class));
    accessibleObjects.add(DummyClassForTest.class.getDeclaredField("anInt"));

    UUID peerUuid = UUID.randomUUID();
    String throwableMessage = "my throwable message";
    Throwable throwable = new Throwable(throwableMessage);
    String responseToId = UUID.randomUUID().toString();

    for (AccessibleObject accessibleObject : accessibleObjects) {
      ExecMessage execMessage;
      try {
        execMessage =
            messageBuilder.buildAccessibleObjectThrowable(
                peerUuid, accessibleObject, throwable, responseToId);
      } catch (UnsupportedOperationException e) {
        assertTrue(e.getMessage().contains("Unsupported accessibleObject type:"));
        continue;
      }

      assertNotNull(execMessage);
      assertEquals(peerUuid.toString(), UuidUtils.toString(execMessage.getPeerUuid()));
      assertEquals(EXEC_THROWABLE, getMessageTypeOf(execMessage));
      assertNotNull(execMessage.getRaisedThrowable());
      if (accessibleObject instanceof Method method) {
        assertEquals(method.getModifiers(), execMessage.getRaisedThrowable().getModifiers());
        assertEquals(
            method.getName(), execMessage.getRaisedThrowable().getFrom().getMethod().getName());
      } else if (accessibleObject instanceof Constructor<?> ctor) {
        assertEquals(ctor.getModifiers(), execMessage.getRaisedThrowable().getModifiers());
        assertEquals(
            ctor.getDeclaringClass().getName(),
            execMessage.getRaisedThrowable().getFrom().getConstructor().getClazz().getName());
      } else if (accessibleObject instanceof Field field) {
        assertEquals(field.getModifiers(), execMessage.getRaisedThrowable().getModifiers());
        assertEquals(
            field.getName(), execMessage.getRaisedThrowable().getFrom().getField().getName());
      } else {
        fail("Unexpected AccessibleObject: " + accessibleObject);
      }
      assertNotNull(execMessage.getRaisedThrowable().getThrowable());
      assertEquals(
          throwable.getClass().getName(),
          execMessage.getRaisedThrowable().getThrowable().getType());
      assertEquals(
          throwable.getClass().getName(),
          execMessage.getRaisedThrowable().getThrowable().getType());
      assertEquals(throwableMessage, execMessage.getRaisedThrowable().getThrowable().getMessage());
      assertNotNull(execMessage.getRaisedThrowable().getThrowable().getStackTraceElements());
      assertNull(execMessage.getRaisedThrowable().getThrowable().getCause());
      assertEquals(responseToId, execMessage.getResponseToId());
    }
  }

  @Test
  public void
      buildAccessibleObjectThrowable_withAllExecutableObjectTypes_raisedThrowableMessages() {
    UUID peerUuid = UUID.randomUUID();
    String throwableMessage = "my throwable message";
    Throwable throwable = new Throwable(throwableMessage);
    String responseToId = UUID.randomUUID().toString();

    Arrays.asList(ExecutableObjectType.values())
        .forEach(
            executableObjectType -> {
              ExecMessage execMessage =
                  messageBuilder.buildAccessibleObjectThrowable(
                      peerUuid, null, throwable, responseToId);

              assertNotNull(execMessage);
              assertEquals(peerUuid.toString(), UuidUtils.toString(execMessage.getPeerUuid()));
              assertEquals(EXEC_THROWABLE, getMessageTypeOf(execMessage));
              assertNotNull(execMessage.getRaisedThrowable());
              switch (executableObjectType) {
                case METHOD, CONSTRUCTOR, FIELD ->
                    assertNull(execMessage.getRaisedThrowable().getFrom());
                default -> fail("Unexpected ExecutableObjectType: " + executableObjectType);
              }
              assertNotNull(execMessage.getRaisedThrowable().getThrowable());
              assertEquals(
                  throwable.getClass().getName(),
                  execMessage.getRaisedThrowable().getThrowable().getType());
              assertEquals(
                  throwableMessage, execMessage.getRaisedThrowable().getThrowable().getMessage());
              assertNotNull(
                  execMessage.getRaisedThrowable().getThrowable().getStackTraceElements());
              assertNull(execMessage.getRaisedThrowable().getThrowable().getCause());
              assertEquals(responseToId, execMessage.getResponseToId());
            });
  }

  // </editor-fold>

  // <editor-fold desc="Return value messages">
  @Test
  public void buildReturnValue_withConstructor_returnValueMessage() {
    var constructor = DummyClassForTest.class.getConstructors()[0];
    Object returnValue = new DummyClassForTest();
    ObjectRef returnValueObjRef = ObjectRef.randomRef();
    String responseToId = UUID.randomUUID().toString();

    ExecMessage execMessage =
        messageBuilder.buildReturnValue(
            returnValue, constructor, returnValueObjRef, false, responseToId);

    assertNotNull(execMessage);
    assertEquals(peerId.toString(), UuidUtils.toString(execMessage.getPeerUuid()));
    assertEquals(EXEC_RETURN_VALUE, getMessageTypeOf(execMessage));
    assertNotNull(execMessage.getReturnValue());
    assertEquals(
        constructor.getName(),
        execMessage.getReturnValue().getFrom().getConstructor().getClazz().getName());
    assertEquals(
        returnValue.getClass().getName(),
        execMessage.getReturnValue().getObject().getClazz().getName());
    assertEquals(returnValueObjRef.getRef(), execMessage.getReturnValue().getObject().getRef());
    assertEquals(responseToId, execMessage.getResponseToId());
  }

  @Test
  public void buildReturnValue_withMethod_returnValueMessage() throws NoSuchMethodException {
    Method method = DummyClassForTest.class.getMethod("addInts", int.class, int.class);
    ObjectRef returnValueObjRef = ObjectRef.randomRef();
    int returnValue = 4;
    String responseToId = UUID.randomUUID().toString();

    ExecMessage execMessage =
        messageBuilder.buildReturnValue(
            returnValue,
            method,
            returnValueObjRef,
            method.getReturnType() == void.class,
            responseToId);

    assertNotNull(execMessage);
    assertEquals(peerId.toString(), UuidUtils.toString(execMessage.getPeerUuid()));
    assertEquals(EXEC_RETURN_VALUE, getMessageTypeOf(execMessage));
    assertNotNull(execMessage.getReturnValue());
    assertEquals(method.getName(), execMessage.getReturnValue().getFrom().getMethod().getName());
    assertEquals("int", execMessage.getReturnValue().getObject().getClazz().getName());
    assertEquals(returnValueObjRef.getRef(), execMessage.getReturnValue().getObject().getRef());
    assertEquals(responseToId, execMessage.getResponseToId());
  }

  @Test
  public void buildReturnValue_withGetField_returnValueMessage() throws NoSuchFieldException {
    Field field = DummyClassForTest.class.getDeclaredField("anObject");
    ObjectRef returnValueObjRef = ObjectRef.randomRef();
    Object returnValue = new Object();
    String responseToId = UUID.randomUUID().toString();

    ExecMessage execMessage =
        messageBuilder.buildReturnValue(returnValue, field, returnValueObjRef, false, responseToId);

    assertNotNull(execMessage);
    assertEquals(peerId.toString(), UuidUtils.toString(execMessage.getPeerUuid()));
    assertEquals(EXEC_RETURN_VALUE, getMessageTypeOf(execMessage));
    assertNotNull(execMessage.getReturnValue());
    assertEquals(field.getName(), execMessage.getReturnValue().getFrom().getField().getName());
    assertEquals(
        returnValue.getClass().getName(),
        execMessage.getReturnValue().getObject().getClazz().getName());
    assertEquals(returnValueObjRef.getRef(), execMessage.getReturnValue().getObject().getRef());
    assertEquals(responseToId, execMessage.getResponseToId());
  }

  @Test
  public void buildReturnValue_emptyResponseId_notSetOnMessage() throws NoSuchMethodException {
    Method method = DummyClassForTest.class.getMethod("addInts", int.class, int.class);
    ObjectRef returnValueObjRef = ObjectRef.randomRef();
    int returnValue = 4;

    ExecMessage execMessage =
        messageBuilder.buildReturnValue(returnValue, method, returnValueObjRef, false, "");

    assertNotNull(execMessage);
    assertEquals(EXEC_RETURN_VALUE, getMessageTypeOf(execMessage));
    assertEquals("", execMessage.getResponseToId());
  }

  @Test
  @SuppressWarnings("PMD.NoFullyQualifiedTypes")
  public void buildAccessibleObjectThrowable_withCause_andNullMessage_setsCauseRecursively()
      throws NoSuchMethodException {
    Method method = DummyClassForTest.class.getMethod("addInts", int.class, int.class);
    Throwable cause = new RuntimeException("cause");
    Throwable ex = new RuntimeException(null, cause);
    String rid = UUID.randomUUID().toString();

    ExecMessage em = messageBuilder.buildAccessibleObjectThrowable(peerId, method, ex, rid);
    assertEquals(EXEC_THROWABLE, getMessageTypeOf(em));
    io.quasient.pal.messages.colfer.Throwable t = em.getRaisedThrowable().getThrowable();
    assertNotNull(t);
    // message is null when exception message is null
    // message may be empty when not provided
    assertTrue(t.getMessage() == null || t.getMessage().isEmpty());
    // cause is present and has a type
    assertNotNull(t.getCause());
    assertEquals("java.lang.RuntimeException", t.getCause().getType());
  }

  @Test
  @SuppressWarnings("PMD.NoFullyQualifiedTypes")
  public void buildAccessibleObjectThrowable_nullAccessible_withMessage_setsMessageOnly() {
    String rid = UUID.randomUUID().toString();
    RuntimeException ex = new RuntimeException("oops");
    ExecMessage em = messageBuilder.buildAccessibleObjectThrowable(peerId, null, ex, rid);
    assertEquals(EXEC_THROWABLE, getMessageTypeOf(em));
    io.quasient.pal.messages.colfer.Throwable t = em.getRaisedThrowable().getThrowable();
    assertEquals("oops", t.getMessage());
    // from is absent when accessible is null
    assertNull(em.getRaisedThrowable().getFrom());
  }

  @Test
  public void messageBuilder_includeSourceContext_nullString_defaultsFalse() {
    MessageBuilder mb = new MessageBuilder(peerId, null);
    // build something that would include context when true; here it should not
    String className = this.getClass().getName();
    ExecMessage em = mb.buildEmptyConstructor(peerId, className);
    assertNotNull(em);
    assertNull(em.getConstructorCall().getContext());
  }

  // </editor-fold>

  // <editor-fold desc="Control messages">
  @Test
  public void buildDeleteObjectControlMessage_withBody_deleteCommandMessage() {
    MessageBuilder builder = new MessageBuilder(peerId, Boolean.toString(false));
    UUID fromPeer = UUID.randomUUID();
    ObjectRef objectRef = ObjectRef.randomRef();
    ControlMessage controlMessage = builder.buildDeleteObjectCommandMessage(fromPeer, objectRef);

    assertNotNull(controlMessage);
    assertNotNull(controlMessage.getMessageId());
    assertEquals(fromPeer.toString(), UuidUtils.toString(controlMessage.getFromPeer()));
    assertEquals(objectRef.getRef(), controlMessage.getParams()[0].getRef());
    assertEquals(ControlCommandType.DELETE_OBJECT.getId(), controlMessage.getCommand());
  }

  @Test
  public void buildDeleteSessionControlMessage_fromPeer_deleteSessionCommandMessage() {
    MessageBuilder builder = new MessageBuilder(peerId, Boolean.toString(false));
    UUID fromPeer = UUID.randomUUID();

    ControlMessage controlMessage = builder.buildDeleteSessionCommandMessage(fromPeer);

    assertNotNull(controlMessage);
    assertNotNull(controlMessage.getMessageId());
    assertEquals(fromPeer.toString(), UuidUtils.toString(controlMessage.getFromPeer()));
    assertEquals("", controlMessage.getBody());
    assertEquals(ControlCommandType.DELETE_SESSION.getId(), controlMessage.getCommand());
  }

  @Test
  public void buildControlMessage_withStatusTypeAndBody_controlStatusMessage() {
    MessageBuilder builder = new MessageBuilder(peerId, Boolean.toString(false));
    String requestId = UUID.randomUUID().toString();
    UUID fromPeerUuid = UUID.randomUUID();
    ControlStatusType statusType = ControlStatusType.OK;
    String body = "someBody";

    ControlMessage controlMessage =
        builder.buildControlStatusMessage(fromPeerUuid, statusType, requestId, body);

    assertNotNull(controlMessage);
    assertNotNull(controlMessage.getMessageId());
    assertEquals(fromPeerUuid.toString(), UuidUtils.toString(controlMessage.getFromPeer()));
    assertEquals(requestId, controlMessage.getResponseToId());
    assertEquals(body, controlMessage.getBody());
    assertEquals(statusType.toId(), controlMessage.getStatus());
  }

  @Test
  public void buildControlMessage_withStatusTypeAndNoBody_controlStatusMessage() {
    MessageBuilder builder = new MessageBuilder(peerId, Boolean.toString(false));
    String requestId = UUID.randomUUID().toString();
    UUID fromPeerUuid = UUID.randomUUID();
    ControlStatusType statusType = ControlStatusType.NO_SUCH_SESSION;

    ControlMessage controlMessage =
        builder.buildControlStatusMessage(fromPeerUuid, statusType, requestId);

    assertNotNull(controlMessage);
    assertNotNull(controlMessage.getMessageId());
    assertEquals(fromPeerUuid.toString(), UuidUtils.toString(controlMessage.getFromPeer()));
    assertEquals(requestId, controlMessage.getResponseToId());
    assertEquals("", controlMessage.getBody());
    assertEquals(statusType.toId(), controlMessage.getStatus());
  }

  // </editor-fold>

  // <editor-fold desc="Message Wrapper">
  @Test
  public void wrap_execMessage_wrappedExecMessage() {
    String className = DummyClassForTest.class.getName();
    ExecMessage execMessage = messageBuilder.buildEmptyConstructor(peerId, className);

    Message wrappedMessage = messageBuilder.wrap(execMessage);

    assertNotNull(wrappedMessage);
    assertEquals(EXEC_CONSTRUCTOR, getMessageTypeOf(execMessage));
    assertEquals(execMessage, wrappedMessage.getExecMessage());
  }

  @Test
  public void wrap_interceptMessage_wrappedInterceptMessage() {
    String className = DummyClassForTest.class.getName();
    String fieldName = "anInt";
    String callbackClass = this.getClass().getName();
    String callbackMethod = "callbackMethod";
    InterceptMessage interceptMessage =
        messageBuilder.buildInterceptMessage(
            peerId,
            InterceptType.BEFORE,
            className,
            fieldName,
            FieldOpType.GET,
            callbackClass,
            callbackMethod);

    Message wrappedMessage = messageBuilder.wrap(interceptMessage);

    assertNotNull(wrappedMessage);
    assertEquals(
        MessageType.INTERCEPT_MESSAGE, MessageType.fromId(wrappedMessage.getMessageType()));
    assertEquals(interceptMessage, wrappedMessage.getInterceptMessage());
  }

  @Test
  public void wrap_interceptKeyMessage_wrappedInterceptKeyMessage() {
    String className = DummyClassForTest.class.getName();
    ExecMessage execMessage = messageBuilder.buildEmptyConstructor(peerId, className);

    InterceptKeyMessage interceptKeyMessage = messageBuilder.buildInterceptKey(execMessage);

    Message wrappedMessage = messageBuilder.wrap(interceptKeyMessage);

    assertNotNull(wrappedMessage);
    assertEquals(MessageType.INTERCEPT_KEY, MessageType.fromId(wrappedMessage.getMessageType()));
    assertEquals(interceptKeyMessage, wrappedMessage.getInterceptKeyMessage());
  }

  @Test
  public void wrap_interceptResponse_wrappedInterceptResponse() {
    UUID responseToId = UUID.randomUUID();
    boolean result = true;

    InterceptResponse interceptResponse =
        messageBuilder.buildInterceptResponse(peerId, responseToId.toString(), result);

    Message wrappedInterceptResponse = messageBuilder.wrap(interceptResponse);

    assertNotNull(wrappedInterceptResponse);
    assertEquals(
        MessageType.INTERCEPT_RESPONSE,
        MessageType.fromId(wrappedInterceptResponse.getMessageType()));
    assertEquals(interceptResponse, wrappedInterceptResponse.getInterceptResponse());
  }

  @Test
  public void wrap_controlMessage_wrappedControlMessage() {
    UUID fromPeerUuid = UUID.randomUUID();
    String requestId = UUID.randomUUID().toString();
    ControlStatusType statusType = ControlStatusType.OK;
    ControlMessage controlMessage =
        messageBuilder.buildControlStatusMessage(fromPeerUuid, statusType, requestId);

    Message wrappedMessage = messageBuilder.wrap(controlMessage);

    assertNotNull(wrappedMessage);
    assertEquals(
        MessageType.CONTROL_MESSAGE_RESPONSE, MessageType.fromId(wrappedMessage.getMessageType()));
    assertEquals(requestId, controlMessage.getResponseToId());
    assertEquals(controlMessage, wrappedMessage.getControlMessage());
  }

  // </editor-fold>

  // <editor-fold desc="InterceptMessage with exception policies">

  /**
   * Test specification for InterceptMessage with exception policies.
   *
   * <p>Acceptance Criterion: [TEST:MessageBuilderTest.shouldBuildInterceptMessageWithPolicies]
   * InterceptMessage includes exception policies from InterceptRequest
   */
  @Test
  public void shouldBuildInterceptMessageWithPolicies() {
    // Given: InterceptRequest with both exception policies set
    UUID uuid = UUID.randomUUID();
    UUID peer = UUID.randomUUID();
    InterceptableMethodCall interceptable =
        new InterceptableMethodCall("testMethod", List.of("java.lang.String"));
    InterceptRequest<InterceptableMethodCall> interceptRequest =
        new InterceptRequest<>(
            uuid,
            peer,
            InterceptType.BEFORE,
            "com.example.TestClass",
            "com.example.CallbackClass",
            "callbackMethod",
            interceptable,
            false,
            ExceptionPropagationPolicy.PROPAGATE_EXPLICIT_ONLY,
            CheckedExceptionPolicy.WRAP);

    // When: Building InterceptMessage from InterceptRequest
    InterceptMessage interceptMessage = messageBuilder.buildInterceptMessage(interceptRequest);

    // Then: InterceptMessage includes the exception policies as bytes
    assertEquals(
        (byte) 1, interceptMessage.getExceptionPropagationPolicy()); // PROPAGATE_EXPLICIT_ONLY = 1
    assertEquals((byte) 0, interceptMessage.getCheckedExceptionPolicy()); // WRAP = 0
  }

  /**
   * Test specification for InterceptMessage with null exception policies.
   *
   * <p>Acceptance Criterion: [TEST:MessageBuilderTest.shouldBuildInterceptMessageWithNullPolicies]
   * InterceptMessage uses sentinel value 255 for null policies
   */
  @Test
  public void shouldBuildInterceptMessageWithNullPolicies() {
    // Given: InterceptRequest with null exception policies (defer to global)
    UUID uuid = UUID.randomUUID();
    UUID peer = UUID.randomUUID();
    InterceptableMethodCall interceptable =
        new InterceptableMethodCall("testMethod", List.of("java.lang.String"));
    InterceptRequest<InterceptableMethodCall> interceptRequest =
        new InterceptRequest<>(
            uuid,
            peer,
            InterceptType.BEFORE,
            "com.example.TestClass",
            "com.example.CallbackClass",
            "callbackMethod",
            interceptable);

    // When: Building InterceptMessage from InterceptRequest
    InterceptMessage interceptMessage = messageBuilder.buildInterceptMessage(interceptRequest);

    // Then: InterceptMessage uses sentinel value 255 for null policies
    assertEquals((byte) 255, interceptMessage.getExceptionPropagationPolicy());
    assertEquals((byte) 255, interceptMessage.getCheckedExceptionPolicy());
  }

  /**
   * Test specification for InterceptMessage with mixed null and non-null policies.
   *
   * <p>Acceptance Criterion: [TEST:MessageBuilderTest.shouldBuildInterceptMessageWithMixedPolicies]
   * InterceptMessage correctly handles mixed null and non-null policies
   */
  @Test
  public void shouldBuildInterceptMessageWithMixedPolicies() {
    // Given: InterceptRequest with one policy set and one null
    UUID uuid = UUID.randomUUID();
    UUID peer = UUID.randomUUID();
    InterceptableMethodCall interceptable =
        new InterceptableMethodCall("testMethod", List.of("java.lang.String"));
    InterceptRequest<InterceptableMethodCall> interceptRequest =
        new InterceptRequest<>(
            uuid,
            peer,
            InterceptType.AROUND,
            "com.example.TestClass",
            "com.example.CallbackClass",
            "callbackMethod",
            interceptable,
            true,
            ExceptionPropagationPolicy.SWALLOW_ALL,
            null);

    // When: Building InterceptMessage from InterceptRequest
    InterceptMessage interceptMessage = messageBuilder.buildInterceptMessage(interceptRequest);

    // Then: Non-null policy is included, null policy uses sentinel value
    assertEquals((byte) 2, interceptMessage.getExceptionPropagationPolicy()); // SWALLOW_ALL = 2
    assertEquals((byte) 255, interceptMessage.getCheckedExceptionPolicy()); // null = 255
    assertTrue(interceptMessage.getForceImmediate());
  }

  // </editor-fold>

  // <editor-fold desc="InterceptMessage with callbackTimeoutMs">

  /**
   * Tests that a null callbackTimeoutMs on InterceptRequest maps to 0L on InterceptMessage.
   *
   * <p>The value 0L is the Colfer default (skipped during marshal), meaning "defer to global
   * timeout" — on unmarshal, init() sets 0L, which resolveCallbackTimeout() interprets as "use
   * global".
   */
  @Test
  public void buildInterceptMessage_callbackTimeoutNull_mapsToZero() {
    UUID uuid = UUID.randomUUID();
    UUID peer = UUID.randomUUID();
    InterceptableMethodCall interceptable =
        new InterceptableMethodCall("testMethod", List.of("java.lang.String"));
    InterceptRequest<InterceptableMethodCall> interceptRequest =
        new InterceptRequest<>(
            uuid,
            peer,
            InterceptType.BEFORE,
            "com.example.TestClass",
            "com.example.CallbackClass",
            "callbackMethod",
            interceptable,
            false,
            null,
            null,
            0,
            0L,
            null);

    InterceptMessage interceptMessage = messageBuilder.buildInterceptMessage(interceptRequest);

    assertEquals(0L, interceptMessage.getCallbackTimeoutMs());
  }

  /**
   * Tests that a zero callbackTimeoutMs on InterceptRequest maps to -1L on InterceptMessage.
   *
   * <p>A value of 0L at PAL level means "no timeout" (infinite wait), which maps to -1L on the wire
   * to avoid Colfer's zero-value optimization that would skip serializing the field.
   */
  @Test
  public void buildInterceptMessage_callbackTimeoutZero_mapsToMinusOne() {
    UUID uuid = UUID.randomUUID();
    UUID peer = UUID.randomUUID();
    InterceptableMethodCall interceptable =
        new InterceptableMethodCall("testMethod", List.of("java.lang.String"));
    InterceptRequest<InterceptableMethodCall> interceptRequest =
        new InterceptRequest<>(
            uuid,
            peer,
            InterceptType.BEFORE,
            "com.example.TestClass",
            "com.example.CallbackClass",
            "callbackMethod",
            interceptable,
            false,
            null,
            null,
            0,
            0L,
            0L);

    InterceptMessage interceptMessage = messageBuilder.buildInterceptMessage(interceptRequest);

    assertEquals(-1L, interceptMessage.getCallbackTimeoutMs());
  }

  /**
   * Tests that a positive callbackTimeoutMs on InterceptRequest maps directly on InterceptMessage.
   */
  @Test
  public void buildInterceptMessage_callbackTimeoutPositive_mapsDirectly() {
    UUID uuid = UUID.randomUUID();
    UUID peer = UUID.randomUUID();
    InterceptableMethodCall interceptable =
        new InterceptableMethodCall("testMethod", List.of("java.lang.String"));
    InterceptRequest<InterceptableMethodCall> interceptRequest =
        new InterceptRequest<>(
            uuid,
            peer,
            InterceptType.BEFORE,
            "com.example.TestClass",
            "com.example.CallbackClass",
            "callbackMethod",
            interceptable,
            false,
            null,
            null,
            0,
            0L,
            5000L);

    InterceptMessage interceptMessage = messageBuilder.buildInterceptMessage(interceptRequest);

    assertEquals(5000L, interceptMessage.getCallbackTimeoutMs());
  }

  // </editor-fold>

  // <editor-fold desc="InterceptMessage callbackTimeoutMs round-trip (marshal/unmarshal)">

  /**
   * Tests that a null callbackTimeoutMs survives the full round-trip: InterceptRequest →
   * MessageBuilder → Colfer marshal → Colfer unmarshal. The unmarshaled value should resolve to
   * "defer to global" (wire value 0, the Colfer default for int64).
   */
  @Test
  public void callbackTimeoutNull_roundTrip_survivesColferMarshal() {
    UUID uuid = UUID.randomUUID();
    UUID peer = UUID.randomUUID();
    InterceptableMethodCall interceptable =
        new InterceptableMethodCall("testMethod", List.of("java.lang.String"));
    InterceptRequest<InterceptableMethodCall> interceptRequest =
        new InterceptRequest<>(
            uuid,
            peer,
            InterceptType.BEFORE,
            "com.example.TestClass",
            "com.example.CallbackClass",
            "callbackMethod",
            interceptable,
            false,
            null,
            null,
            0,
            0L,
            null);

    InterceptMessage original = messageBuilder.buildInterceptMessage(interceptRequest);
    byte[] buf = new byte[original.marshalFit()];
    int len = original.marshal(buf, 0);

    InterceptMessage deserialized = new InterceptMessage();
    deserialized.unmarshal(buf, 0, len);

    // 0 on wire = defer to global (Colfer skips 0, init() sets 0)
    assertEquals(0L, deserialized.getCallbackTimeoutMs());
  }

  /**
   * Tests that callbackTimeoutMs=0 (no timeout / infinite wait) survives the full round-trip. This
   * is the critical case: Colfer's zero-value optimization skips int64 fields equal to 0, so "no
   * timeout" must be encoded as -1 on the wire to survive marshal/unmarshal.
   */
  @Test
  public void callbackTimeoutZero_roundTrip_survivesColferMarshal() {
    UUID uuid = UUID.randomUUID();
    UUID peer = UUID.randomUUID();
    InterceptableMethodCall interceptable =
        new InterceptableMethodCall("testMethod", List.of("java.lang.String"));
    InterceptRequest<InterceptableMethodCall> interceptRequest =
        new InterceptRequest<>(
            uuid,
            peer,
            InterceptType.BEFORE,
            "com.example.TestClass",
            "com.example.CallbackClass",
            "callbackMethod",
            interceptable,
            false,
            null,
            null,
            0,
            0L,
            0L);

    InterceptMessage original = messageBuilder.buildInterceptMessage(interceptRequest);
    byte[] buf = new byte[original.marshalFit()];
    int len = original.marshal(buf, 0);

    InterceptMessage deserialized = new InterceptMessage();
    deserialized.unmarshal(buf, 0, len);

    // -1 on wire = no timeout; must NOT be 0 (which Colfer would silently drop)
    assertEquals(-1L, deserialized.getCallbackTimeoutMs());
  }

  /**
   * Tests that a positive callbackTimeoutMs survives the full round-trip through Colfer
   * marshal/unmarshal without loss.
   */
  @Test
  public void callbackTimeoutPositive_roundTrip_survivesColferMarshal() {
    UUID uuid = UUID.randomUUID();
    UUID peer = UUID.randomUUID();
    InterceptableMethodCall interceptable =
        new InterceptableMethodCall("testMethod", List.of("java.lang.String"));
    InterceptRequest<InterceptableMethodCall> interceptRequest =
        new InterceptRequest<>(
            uuid,
            peer,
            InterceptType.BEFORE,
            "com.example.TestClass",
            "com.example.CallbackClass",
            "callbackMethod",
            interceptable,
            false,
            null,
            null,
            0,
            0L,
            5000L);

    InterceptMessage original = messageBuilder.buildInterceptMessage(interceptRequest);
    byte[] buf = new byte[original.marshalFit()];
    int len = original.marshal(buf, 0);

    InterceptMessage deserialized = new InterceptMessage();
    deserialized.unmarshal(buf, 0, len);

    assertEquals(5000L, deserialized.getCallbackTimeoutMs());
  }

  // </editor-fold>
}
