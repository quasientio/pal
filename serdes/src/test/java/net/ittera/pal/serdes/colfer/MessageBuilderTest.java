package net.ittera.pal.serdes.colfer;

import static net.ittera.pal.messages.types.ExecMessageType.*;
import static org.junit.Assert.*;

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Collectors;
import net.ittera.pal.common.directory.nodes.InterceptRequest;
import net.ittera.pal.common.lang.FieldOpType;
import net.ittera.pal.common.lang.intercept.InterceptType;
import net.ittera.pal.common.lang.intercept.InterceptableFieldOp;
import net.ittera.pal.common.lang.intercept.InterceptableMethodCall;
import net.ittera.pal.common.lang.reflect.ConstructorSignature;
import net.ittera.pal.common.lang.reflect.ExecutableObjectType;
import net.ittera.pal.common.lang.reflect.FieldSignature;
import net.ittera.pal.common.lang.reflect.MethodSignature;
import net.ittera.pal.common.objects.ObjectRef;
import net.ittera.pal.common.runtime.Context;
import net.ittera.pal.messages.colfer.ControlMessage;
import net.ittera.pal.messages.colfer.ExecMessage;
import net.ittera.pal.messages.colfer.InterceptKeyMessage;
import net.ittera.pal.messages.colfer.InterceptMessage;
import net.ittera.pal.messages.colfer.InterceptReply;
import net.ittera.pal.messages.colfer.InternalHeader;
import net.ittera.pal.messages.colfer.Message;
import net.ittera.pal.messages.colfer.Obj;
import net.ittera.pal.messages.colfer.Parameter;
import net.ittera.pal.messages.types.*;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

class DummyClassForTest {
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

/** Naming convention to use: methodName_stateUnderTest_expectedBehavior */
public class MessageBuilderTest {

  static class ExtractedFieldOpMessageInfo {
    ObjectRef targetObjectRef;
    Obj targetObject;
    net.ittera.pal.messages.colfer.Context context;
    String className;
    String fieldName;
  }
  // <editor-fold desc="Helper methods">

  private net.ittera.pal.common.runtime.Context createContextForConstructor(
      Class constructorClass, Class... constructorArgTypes) throws Exception {
    ConstructorSignature constructorSignature =
        new ConstructorSignature(constructorClass.getDeclaredConstructor(constructorArgTypes));
    String sourceFile = "MessageBuilderTest.java";
    int lineNumber = 17;
    Class withinType = constructorClass;
    return new net.ittera.pal.common.runtime.Context(
        sourceFile, lineNumber, withinType, constructorSignature);
  }

  private net.ittera.pal.common.runtime.Context createContextForInstanceMethod(
      Class clazz, String method, Class... methodArgTypes) throws Exception {
    MethodSignature methodSignature =
        new MethodSignature(clazz.getDeclaredMethod(method, methodArgTypes));
    String sourceFile = "MessageBuilderTest.java";
    int lineNumber = 20;
    Class withinType = clazz;
    return new net.ittera.pal.common.runtime.Context(
        sourceFile, lineNumber, withinType, methodSignature);
  }

  private net.ittera.pal.common.runtime.Context createContextForClassMethod(
      MethodSignature methodSignature) throws Exception {

    String sourceFile = "Arrays.java";
    int lineNumber = 2000;
    Class withinType = Arrays.class;
    return new net.ittera.pal.common.runtime.Context(
        sourceFile, lineNumber, withinType, methodSignature);
  }

  private net.ittera.pal.common.runtime.Context createContextForFieldOp(
      Class clazz, String fieldName) throws Exception {
    FieldSignature fieldSignature = new FieldSignature(clazz.getDeclaredField(fieldName));
    String sourceFile = "MessageBuilderTest.java";
    int lineNumber = 20;
    Class withinType = this.getClass();
    return new net.ittera.pal.common.runtime.Context(
        sourceFile, lineNumber, withinType, fieldSignature);
  }

  private ExtractedFieldOpMessageInfo extractedFieldOpMessageInfo(ExecMessage fieldOpMessage) {
    ExtractedFieldOpMessageInfo extractedFieldOpMessageInfo = new ExtractedFieldOpMessageInfo();
    ExecMessageType execMessageType = ExecMessageType.fromByte(fieldOpMessage.execMessageType);
    switch (execMessageType) {
      case GET_FIELD:
        assertNotNull(fieldOpMessage.getInstanceFieldGet());
        extractedFieldOpMessageInfo.targetObject = fieldOpMessage.getInstanceFieldGet().getObject();
        extractedFieldOpMessageInfo.targetObjectRef =
            ObjectRef.from(fieldOpMessage.getInstanceFieldGet().getObjectRef());
        extractedFieldOpMessageInfo.context = fieldOpMessage.getInstanceFieldGet().getContext();
        extractedFieldOpMessageInfo.fieldName =
            fieldOpMessage.getInstanceFieldGet().getField().getName();
        extractedFieldOpMessageInfo.className =
            fieldOpMessage.getInstanceFieldGet().getClazz().getName();
        break;
      case PUT_FIELD:
        assertNotNull(fieldOpMessage.getInstanceFieldPut());
        extractedFieldOpMessageInfo.targetObject = fieldOpMessage.getInstanceFieldPut().getObject();
        extractedFieldOpMessageInfo.targetObjectRef =
            ObjectRef.from(fieldOpMessage.getInstanceFieldPut().getObjectRef());
        extractedFieldOpMessageInfo.context = fieldOpMessage.getInstanceFieldPut().getContext();
        extractedFieldOpMessageInfo.fieldName =
            fieldOpMessage.getInstanceFieldPut().getField().getName();
        extractedFieldOpMessageInfo.className =
            fieldOpMessage.getInstanceFieldPut().getClazz().getName();
        break;
      case GET_STATIC:
        assertNotNull(fieldOpMessage.getStaticFieldGet());
        extractedFieldOpMessageInfo.context = fieldOpMessage.getStaticFieldGet().getContext();
        extractedFieldOpMessageInfo.fieldName =
            fieldOpMessage.getStaticFieldGet().getField().getName();
        extractedFieldOpMessageInfo.className =
            fieldOpMessage.getStaticFieldGet().getClazz().getName();
        break;
      case PUT_STATIC:
        assertNotNull(fieldOpMessage.getStaticFieldPut());
        extractedFieldOpMessageInfo.context = fieldOpMessage.getStaticFieldPut().getContext();
        extractedFieldOpMessageInfo.fieldName =
            fieldOpMessage.getStaticFieldPut().getField().getName();
        extractedFieldOpMessageInfo.className =
            fieldOpMessage.getStaticFieldPut().getClazz().getName();
        break;
      case PUT_FIELD_DONE:
        assertNotNull(fieldOpMessage.getInstanceFieldPutDone());
        extractedFieldOpMessageInfo.fieldName =
            fieldOpMessage.getInstanceFieldPutDone().getField().getName();
        extractedFieldOpMessageInfo.className =
            fieldOpMessage.getInstanceFieldPutDone().getClazz().getName();
        break;
      case PUT_STATIC_DONE:
        assertNotNull(fieldOpMessage.getStaticFieldPutDone());
        extractedFieldOpMessageInfo.fieldName =
            fieldOpMessage.getStaticFieldPutDone().getField().getName();
        extractedFieldOpMessageInfo.className =
            fieldOpMessage.getStaticFieldPutDone().getClazz().getName();
        break;
      default:
        fail(
            "Unexpected ExecMessageType: "
                + ExecMessageType.fromByte(fieldOpMessage.getExecMessageType()));
    }
    return extractedFieldOpMessageInfo;
  }
  // </editor-fold>

  private MessageBuilder messageBuilder;
  private MessageBuilder messageBuilderWithContext;

  @Before
  public void setUp() throws Exception {
    messageBuilder = new MessageBuilder();
    messageBuilderWithContext = new MessageBuilder(Boolean.toString(true));
  }

  @Test
  public void messageBuilder_noArgs_newMessageBuilder() {
    messageBuilder = new MessageBuilder();
    assertNotNull(messageBuilder);
  }

  @Test
  public void messageBuilder_withIncludeSourceContextStr_newMessageBuilder() {
    MessageBuilder messageBuilder = new MessageBuilder(Boolean.toString(false));
    assertNotNull(messageBuilder);
  }

  // <editor-fold desc="Thread-local sequence stamping methods">
  @Test
  public void resetThreadLocalSequence() {
    UUID peerUuid = UUID.randomUUID();
    String className = this.getClass().getName();
    int expectedDispatchSeq = 1;
    int expectedBuilderSeq = 1;

    // create 2 messages, and assert that only builder sequence is incremented
    ExecMessage execMessage = messageBuilder.buildEmptyConstructor(peerUuid, className);
    assertEquals(expectedBuilderSeq++, execMessage.getBuilderSeq());
    assertEquals(expectedDispatchSeq, execMessage.getDispatchSeq());

    execMessage = messageBuilder.buildEmptyConstructor(peerUuid, className);
    assertEquals(expectedBuilderSeq++, execMessage.getBuilderSeq());
    assertEquals(expectedDispatchSeq, execMessage.getDispatchSeq());

    messageBuilder.resetThreadLocalSequence();
    expectedDispatchSeq += 1;
    expectedBuilderSeq = 1;

    execMessage = messageBuilder.buildEmptyConstructor(peerUuid, className);
    assertEquals(expectedBuilderSeq, execMessage.getBuilderSeq());
    assertEquals(expectedDispatchSeq, execMessage.getDispatchSeq());
  }
  // </editor-fold>

  // <editor-fold desc="Header messages">
  @Test
  public void buildWriteAheadHeader_validUuid_writeAheadHeader() {
    UUID peerUuid = UUID.randomUUID();
    InternalHeader header = messageBuilder.buildWriteAheadHeader(peerUuid);
    assertNotNull(header);
    assertEquals(peerUuid.toString(), header.getValue());
    assertEquals(InternalHeaderType.WRITE_AHEAD.toByte(), header.getHeaderType());
  }
  // </editor-fold>

  // <editor-fold desc="Constructor messages">
  @Test
  public void buildEmptyConstructor_noArguments_constructorMessage() {
    UUID peerUuid = UUID.randomUUID();
    String className = this.getClass().getName();
    ExecMessage execMessage = messageBuilder.buildEmptyConstructor(peerUuid, className);
    assertNotNull(execMessage);
    assertEquals(ExecMessageType.CONSTRUCTOR.toByte(), execMessage.execMessageType);
    assertNotNull(execMessage.getConstructorCall());
    assertEquals(peerUuid.toString(), execMessage.getPeerUuid());
    assertEquals(className, execMessage.constructorCall.getClazz().getName());
    assertNull(execMessage.getConstructorCall().getContext());
  }

  @Test
  public void buildNonEmptyConstructor_primitiveArguments_constructorMessage() {
    UUID peerUuid = UUID.randomUUID();
    String className = this.getClass().getName();
    String[] parameterTypes = {"String", "int"};
    Object[] args = {"test", 123};
    ObjectRef[] argObjRefs = {null, null};
    ExecMessage execMessage =
        messageBuilder.buildNonEmptyConstructor(
            peerUuid, className, parameterTypes, args, argObjRefs);
    assertNotNull(execMessage);
    assertEquals(ExecMessageType.CONSTRUCTOR.toByte(), execMessage.execMessageType);
    assertNotNull(execMessage.getConstructorCall());
    assertEquals(peerUuid.toString(), execMessage.getPeerUuid());
    assertEquals(className, execMessage.constructorCall.getClazz().getName());
    assertNull(execMessage.getConstructorCall().getContext());
    assertEquals(parameterTypes.length, execMessage.getConstructorCall().getParameters().length);
  }

  @Test
  public void buildConstructor_withContext_constructorMessage() throws Exception {
    UUID peerUuid = UUID.randomUUID();
    Class clazz = DummyClassForTest.class;
    Object sender = this;
    ObjectRef senderObjRef = ObjectRef.randomRef();
    Object[] args = {"test", 123};
    ObjectRef[] argObjRefs = {null, null};
    Context constructorContext = createContextForConstructor(clazz, String.class, int.class);
    ExecMessage execMessage =
        messageBuilderWithContext.buildConstructor(
            peerUuid, constructorContext, sender, senderObjRef, args, argObjRefs);
    assertNotNull(execMessage);
    assertEquals(ExecMessageType.CONSTRUCTOR.toByte(), execMessage.execMessageType);
    assertNotNull(execMessage.getConstructorCall());
    assertEquals(peerUuid.toString(), execMessage.getPeerUuid());
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

    assertEquals(args.length, execMessage.getConstructorCall().getParameters().length);
  }

  @Test
  public void buildConstructor_withMixedArgs_constructorMessage() throws ClassNotFoundException {
    UUID peerUuid = UUID.randomUUID();
    Object sender = this;
    ObjectRef senderObjRef = ObjectRef.randomRef();
    Class clazz = ArrayList.class;
    int arrayListInitialCapacity = 23;
    String[] parameterTypes = new String[] {"int"};
    Object[] args = {arrayListInitialCapacity};
    ExecMessage execMessage =
        messageBuilder.buildConstructor(
            peerUuid, clazz.getName(), parameterTypes, args, sender, senderObjRef);

    assertNotNull(execMessage);
    assertEquals(ExecMessageType.CONSTRUCTOR.toByte(), execMessage.execMessageType);
    assertNotNull(execMessage.getConstructorCall());
    assertEquals(peerUuid.toString(), execMessage.getPeerUuid());
    assertEquals(clazz.getName(), execMessage.getConstructorCall().getClazz().getName());
    assertNull(execMessage.getConstructorCall().getContext());
    assertEquals(args.length, execMessage.getConstructorCall().getParameters().length);
    // compare parameter types and args
    for (int i = 0; i < execMessage.getConstructorCall().getParameters().length; i++) {
      Parameter parameter = execMessage.getConstructorCall().getParameters()[i];
      assertEquals(parameterTypes[i], parameter.getType().getName());
      assertEquals(args[i], Unwrapper.unwrapObject(parameter.getValue()));
    }
  }
  // </editor-fold>

  // <editor-fold desc="Instance method messages">

  @Test
  public void buildInstanceMethod_classNameMethodName_instanceMethodCallMessage() {
    UUID peerUuid = UUID.randomUUID();
    String className = "TestClassName";
    String methodName = "testMethod";
    Object target = new Object();
    ObjectRef targetObjRef = ObjectRef.randomRef();
    String[] parameterTypes = {"String", "int"};
    Object[] args = {"test", 123};
    ObjectRef[] argObjRefs = {null, null};
    ExecMessage execMessage =
        messageBuilder.buildInstanceMethod(
            peerUuid, className, methodName, targetObjRef, parameterTypes, args, argObjRefs);
    assertNotNull(execMessage);
    assertEquals(ExecMessageType.INSTANCE_METHOD.toByte(), execMessage.execMessageType);
    assertNotNull(execMessage.getInstanceMethodCall());
    assertEquals(peerUuid.toString(), execMessage.getPeerUuid());
    assertEquals(className, execMessage.getInstanceMethodCall().getClazz().getName());
    assertEquals(methodName, execMessage.getInstanceMethodCall().getName());
    assertNull(execMessage.getInstanceMethodCall().getContext());
    assertEquals(parameterTypes.length, execMessage.getInstanceMethodCall().getParameters().length);
  }

  @Test
  public void buildInstanceMethod_withContext_instanceMethodCallMessage() throws Exception {
    UUID peerUuid = UUID.randomUUID();
    Object sender = this;
    ObjectRef senderObjRef = ObjectRef.randomRef();
    Class clazz = DummyClassForTest.class;
    String methodName = "dummyMethod";
    Object target = new DummyClassForTest();
    ObjectRef targetObjRef = ObjectRef.randomRef();
    Object[] args = {"test", 123, new ArrayList()};
    ObjectRef[] argObjRefs = {null, null, ObjectRef.randomRef()};
    Context instanceMethodContext =
        createContextForInstanceMethod(clazz, methodName, String.class, int.class, List.class);
    ExecMessage execMessage =
        messageBuilderWithContext.buildInstanceMethod(
            peerUuid,
            instanceMethodContext,
            sender,
            senderObjRef,
            target,
            targetObjRef,
            args,
            argObjRefs);
    assertNotNull(execMessage);
    assertEquals(ExecMessageType.INSTANCE_METHOD.toByte(), execMessage.execMessageType);
    assertNotNull(execMessage.getInstanceMethodCall());
    assertEquals(peerUuid.toString(), execMessage.getPeerUuid());
    assertEquals(
        DummyClassForTest.class.getName(),
        execMessage.getInstanceMethodCall().getClazz().getName());
    assertEquals("dummyMethod", execMessage.getInstanceMethodCall().getName());
    assertNotNull(execMessage.getInstanceMethodCall().getContext());
    net.ittera.pal.messages.colfer.Context messageContext =
        execMessage.getInstanceMethodCall().getContext();
    assertEquals(instanceMethodContext.getSourceFilename(), messageContext.getSourceLocationFile());
    assertEquals(instanceMethodContext.getSourceLine(), messageContext.getSourceLocationLine());
    assertEquals(
        instanceMethodContext.getWithinType().getName(), messageContext.getSourceLocationType());
    assertEquals(args.length, execMessage.getInstanceMethodCall().getParameters().length);
  }

  @Test
  public void buildInstanceMethod_mixedArgs_instanceMethodCallMessage()
      throws ClassNotFoundException {
    UUID peerUuid = UUID.randomUUID();
    final String methodToCall = "dummyMethodJustPrimitiveArgs";
    String[] parameterTypes = new String[] {"java.lang.String", "int", "java.lang.Boolean"};
    Object[] args = new Object[] {"test", 123, Boolean.FALSE};
    ExecMessage execMessage =
        messageBuilder.buildInstanceMethod(
            peerUuid,
            DummyClassForTest.class.getName(),
            methodToCall,
            ObjectRef.randomRef(),
            parameterTypes,
            args);

    assertNotNull(execMessage);
    assertEquals(ExecMessageType.INSTANCE_METHOD.toByte(), execMessage.execMessageType);
    assertNotNull(execMessage.getInstanceMethodCall());
    assertEquals(peerUuid.toString(), execMessage.getPeerUuid());
    assertEquals(
        DummyClassForTest.class.getName(),
        execMessage.getInstanceMethodCall().getClazz().getName());
    assertEquals(methodToCall, execMessage.getInstanceMethodCall().getName());
    assertNull(execMessage.getInstanceMethodCall().getContext());
    assertEquals(3, execMessage.getInstanceMethodCall().getParameters().length);
    // compare parameter types and args
    for (int i = 0; i < execMessage.getInstanceMethodCall().getParameters().length; i++) {
      Parameter parameter = execMessage.getInstanceMethodCall().getParameters()[i];
      assertEquals(parameterTypes[i], parameter.getType().getName());
      assertEquals(args[i], Unwrapper.unwrapObject(parameter.getValue()));
    }
  }
  // </editor-fold>

  // <editor-fold desc="Class method messages">
  @Test
  public void buildClassMethod_withArgsAndNullObjectRefs_classMethodMessage()
      throws ClassNotFoundException {
    UUID peerUuid = UUID.randomUUID();
    String className = "java.util.Arrays";
    String methodName = "binarySearch"; // binarySearch(float[] a, float key)
    String[] parameterTypes = new String[] {"[F", "float"};
    Object sender = this;
    ObjectRef senderObjRef = ObjectRef.randomRef();
    Object[] args = new Object[] {new float[] {4.5f, 54.2f, 9383.23f}, 1235.34f};
    ObjectRef[] argObjRefs = new ObjectRef[] {null, null};

    ExecMessage execMessage =
        messageBuilder.buildClassMethod(
            peerUuid,
            className,
            methodName,
            parameterTypes,
            sender,
            senderObjRef,
            args,
            argObjRefs);

    assertNotNull(execMessage);
    assertEquals(ExecMessageType.CLASS_METHOD.toByte(), execMessage.execMessageType);
    assertNotNull(execMessage.getClassMethodCall());
    assertEquals(peerUuid.toString(), execMessage.getPeerUuid());
    assertEquals(className, execMessage.getClassMethodCall().getClazz().getName());
    assertEquals(methodName, execMessage.getClassMethodCall().getName());
    assertNull(execMessage.getClassMethodCall().getContext());
    assertEquals(parameterTypes.length, execMessage.getClassMethodCall().getParameters().length);

    // compare parameter types
    for (int i = 0; i < execMessage.getClassMethodCall().getParameters().length; i++) {
      Parameter parameter = execMessage.getClassMethodCall().getParameters()[i];
      assertEquals(parameterTypes[i], parameter.getType().getName());
    }
    // compare parameter values
    assertArrayEquals(
        (float[]) args[0],
        (float[])
            Unwrapper.unwrapObject(execMessage.getClassMethodCall().getParameters()[0].getValue()),
        0f);
    assertEquals(
        (float) args[1],
        (float)
            Unwrapper.unwrapObject(execMessage.getClassMethodCall().getParameters()[1].getValue()),
        0f);
  }

  @Test
  public void buildClassMethod_withContextAndPrimitiveArgs_classMethodMessage() throws Exception {
    UUID peerUuid = UUID.randomUUID();
    Context context =
        createContextForClassMethod(
            new MethodSignature(
                DummyClassForTest.class.getDeclaredMethod(
                    "dummyStaticMethod", String.class, String.class, boolean.class)));
    Object sender = this;
    ObjectRef senderObjRef = ObjectRef.randomRef();
    Object[] args = new Object[] {"arg1", "arg2", true};
    ObjectRef[] argObjRefs = new ObjectRef[] {null, null, null};
    ExecMessage execMessage =
        messageBuilderWithContext.buildClassMethod(
            peerUuid, context, sender, senderObjRef, args, argObjRefs);

    // assert expected values of ExecMessage
    assertNotNull(execMessage);
    assertEquals(ExecMessageType.CLASS_METHOD.toByte(), execMessage.execMessageType);
    assertNotNull(execMessage.getClassMethodCall());
    assertEquals(peerUuid.toString(), execMessage.getPeerUuid());
    assertEquals(
        DummyClassForTest.class.getName(), execMessage.getClassMethodCall().getClazz().getName());
    assertEquals("dummyStaticMethod", execMessage.getClassMethodCall().getName());
    assertNotNull(execMessage.getClassMethodCall().getContext());
  }

  @Ignore(
      "TODO: enable and complete once we implement wrapping of Object arrays in the Wrapper class")
  @Test
  public void buildClassMethod_withContextAndObjectArrayArgs_classMethodMessage() throws Exception {
    UUID peerUuid = UUID.randomUUID();
    // Arrays.deepEquals(Object[] a1, Object[] a2)
    Context context =
        createContextForClassMethod(
            new MethodSignature(
                Arrays.class.getDeclaredMethod(
                    "deepEquals", new Class[] {Object[].class, Object[].class})));
    Object sender = this;
    ObjectRef senderObjRef = ObjectRef.randomRef();
    Object[] args = new Object[] {new Object[] {4.5f, 54.2f}, new Object[] {"float1", "float2"}};
    ObjectRef[] argObjRefs = new ObjectRef[] {null, null};
    ExecMessage execMessage =
        messageBuilderWithContext.buildClassMethod(
            peerUuid, context, sender, senderObjRef, args, argObjRefs);

    // assert expected values of ExecMessage
    // TODO
  }

  @Test
  public void buildClassMethod_staticMethodCall_classMethodMessage() throws ClassNotFoundException {
    UUID peerUuid = UUID.randomUUID();
    Object sender = this;
    ObjectRef senderObjRef = ObjectRef.randomRef();
    // Arrays.fill(boolean[] a, boolean val)
    String method = "fill";
    Class clazz = Arrays.class;
    String[] parameterTypes = new String[] {"[Z", "boolean"};
    Object[] args = new Object[] {new boolean[] {false, false, false}, true};

    ExecMessage execMessage =
        messageBuilder.buildClassMethod(
            peerUuid, clazz.getName(), method, parameterTypes, sender, senderObjRef, args);

    // assert expected values of ExecMessage
    assertNotNull(execMessage);
    assertEquals(ExecMessageType.CLASS_METHOD.toByte(), execMessage.execMessageType);
    assertNotNull(execMessage.getClassMethodCall());
    assertEquals(peerUuid.toString(), execMessage.getPeerUuid());
    assertEquals(clazz.getName(), execMessage.getClassMethodCall().getClazz().getName());
    assertEquals(method, execMessage.getClassMethodCall().getName());
    assertNull(execMessage.getClassMethodCall().getContext());
    assertEquals(parameterTypes.length, execMessage.getClassMethodCall().getParameters().length);
    // compare parameter types and values
    for (int i = 0; i < execMessage.getClassMethodCall().getParameters().length; i++) {
      Parameter parameter = execMessage.getClassMethodCall().getParameters()[i];
      assertEquals(parameterTypes[i], parameter.getType().getName());
    }
    // compare parameter values
    assertArrayEquals(
        (boolean[]) args[0],
        (boolean[])
            Unwrapper.unwrapObject(execMessage.getClassMethodCall().getParameters()[0].getValue()));
    assertEquals(
        args[1],
        Unwrapper.unwrapObject(execMessage.getClassMethodCall().getParameters()[1].getValue()));
  }
  // </editor-fold>

  // <editor-fold desc="Field Ops generic">
  @Test
  public void buildFieldOp_allFourOps_fieldOpMessages() throws Exception {

    // common args for all 4 ops
    UUID peerUuid = UUID.randomUUID();
    Class targetClass = DummyClassForTest.class;
    String fieldName = "anInt";
    Object sender = this;
    ObjectRef senderObjRef = ObjectRef.randomRef();
    Context context = createContextForFieldOp(targetClass, fieldName);

    // create a list of specific args for each of the four field op types
    List<Map<String, Object>> listOfFieldOpArgs =
        Arrays.asList(
            new HashMap<String, Object>() {
              {
                put("messageType", GET_FIELD);
                put("target", new Object());
                put("targetObjectRef", ObjectRef.randomRef());
              }
            },
            new HashMap<String, Object>() {
              {
                put("messageType", PUT_FIELD);
                put("target", new Object());
                put("targetObjectRef", ObjectRef.from("492849"));
                put("arg", "an argument");
                put("argObjRef", ObjectRef.from("234987"));
              }
            },
            new HashMap<String, Object>() {
              {
                put("messageType", GET_STATIC);
              }
            },
            new HashMap<String, Object>() {
              {
                put("messageType", PUT_STATIC);
                put("arg", "an argument");
                put("argObjRef", ObjectRef.from("8702347"));
              }
            });

    // call buildFieldOp for each of the four field op types
    for (Map<String, Object> map : listOfFieldOpArgs) {
      ExecMessageType execMessageType = (ExecMessageType) map.get("messageType");
      Object target = map.get("target");
      ObjectRef targetObjRef = (ObjectRef) map.get("targetObjectRef");
      Object arg = map.get("arg");
      ObjectRef argObjRef = (ObjectRef) map.get("argObjRef");
      ExecMessage execMessage =
          messageBuilderWithContext.buildFieldOp(
              peerUuid,
              context,
              execMessageType,
              sender,
              senderObjRef,
              target,
              targetObjRef,
              arg,
              argObjRef);

      assertNotNull(execMessage);
      assertEquals(peerUuid.toString(), execMessage.getPeerUuid());
      assertEquals(execMessageType.toByte(), execMessage.execMessageType);
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

      // for non-static field ops, assert that the target object and objectref are in the message
      if (extractedFieldOpMessageInfo.targetObject != null) {
        assertEquals(
            targetClass.getName(), extractedFieldOpMessageInfo.targetObject.getClazz().getName());
      }
      if (extractedFieldOpMessageInfo.targetObjectRef != null) {
        assertEquals(targetObjRef, extractedFieldOpMessageInfo.targetObjectRef);
      }
    }
  }

  @Test
  public void buildFieldOpDone_putFieldDone_fieldPutDoneMessage() throws Exception {
    MessageBuilder builder = new MessageBuilder();
    UUID peerUuid = UUID.randomUUID();
    Class targetClass = DummyClassForTest.class;
    String fieldName = "anInt";
    Context context = createContextForFieldOp(targetClass, fieldName);
    AccessibleObject field = targetClass.getDeclaredField("anInt");
    ExecMessageType type = PUT_FIELD_DONE;

    ExecMessage execMessage = builder.buildFieldOpDone(peerUuid, field, context, type);

    // assert expected values of ExecMessage
    assertNotNull(execMessage);
    assertEquals(peerUuid.toString(), execMessage.getPeerUuid());
    assertEquals(type.toByte(), execMessage.execMessageType);
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
    Class targetClass = DummyClassForTest.class;
    String fieldName = "anInt";
    Context context = createContextForFieldOp(targetClass, fieldName);
    AccessibleObject field = targetClass.getDeclaredField(fieldName);
    ExecMessageType type = PUT_STATIC_DONE;

    ExecMessage execMessage = builder.buildFieldOpDone(peerUuid, field, context, type);

    // assert expected values of ExecMessage
    assertNotNull(execMessage);
    assertEquals(peerUuid.toString(), execMessage.getPeerUuid());
    assertEquals(type.toByte(), execMessage.execMessageType);
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
    UUID peerUuid = UUID.randomUUID();
    String className = DummyClassForTest.class.getName();
    String fieldName = "aStaticField";

    ExecMessage execMessage = messageBuilder.buildGetStatic(peerUuid, className, fieldName);

    // assert expected values of ExecMessage
    assertNotNull(execMessage);
    assertEquals(peerUuid.toString(), execMessage.getPeerUuid());
    assertEquals(ExecMessageType.GET_STATIC.toByte(), execMessage.execMessageType);
    assertNotNull(execMessage.getStaticFieldGet());
    assertEquals(className, execMessage.getStaticFieldGet().getClazz().getName());
    assertEquals(fieldName, execMessage.getStaticFieldGet().getField().getName());
    assertNull(execMessage.getStaticFieldGet().getContext());
  }
  // </editor-fold>

  // <editor-fold desc="Instance field get messages">
  @Test
  public void buildGetObject_withInstanceObjRef_instanceFieldGetMessage() {
    UUID peerUuid = UUID.randomUUID();
    String className = DummyClassForTest.class.getName();
    String fieldName = "anInt";
    ObjectRef targetObjRef = ObjectRef.randomRef();

    ExecMessage execMessage =
        messageBuilder.buildGetObject(peerUuid, className, fieldName, targetObjRef);

    // assert expected values of ExecMessage
    assertNotNull(execMessage);
    assertEquals(peerUuid.toString(), execMessage.getPeerUuid());
    assertEquals(ExecMessageType.GET_FIELD.toByte(), execMessage.execMessageType);
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
    UUID peerUuid = UUID.randomUUID();
    String className = DummyClassForTest.class.getName();
    String fieldName = "aStaticField";
    String valueClassName = String.class.getName();
    Object value = "a str value";

    ExecMessage execMessage =
        messageBuilder.buildPutStatic(peerUuid, className, fieldName, valueClassName, value);

    // assert expected values of ExecMessage
    assertNotNull(execMessage);
    assertEquals(peerUuid.toString(), execMessage.getPeerUuid());
    assertEquals(ExecMessageType.PUT_STATIC.toByte(), execMessage.execMessageType);
    assertNotNull(execMessage.getStaticFieldPut());
    assertEquals(className, execMessage.getStaticFieldPut().getClazz().getName());
    assertEquals(fieldName, execMessage.getStaticFieldPut().getField().getName());
    assertEquals(
        valueClassName, execMessage.getStaticFieldPut().getValueObject().getClazz().getName());
    assertEquals(value, execMessage.getStaticFieldPut().getValueObject().getValue());
    assertNull(execMessage.getStaticFieldPut().getContext());
  }

  @Test
  public void buildPutStatic_withObjectRefValue_staticFieldPutMessage() {
    UUID peerUuid = UUID.randomUUID();
    String className = DummyClassForTest.class.getName();
    String fieldName = "aStaticField";
    ObjectRef valueObjectRef = ObjectRef.randomRef();

    ExecMessage execMessage =
        messageBuilder.buildPutStatic(peerUuid, className, fieldName, valueObjectRef);

    // assert expected values of ExecMessage
    assertNotNull(execMessage);
    assertEquals(peerUuid.toString(), execMessage.getPeerUuid());
    assertEquals(ExecMessageType.PUT_STATIC.toByte(), execMessage.execMessageType);
    assertNotNull(execMessage.getStaticFieldPut());
    assertEquals(className, execMessage.getStaticFieldPut().getClazz().getName());
    assertEquals(fieldName, execMessage.getStaticFieldPut().getField().getName());
    assertEquals(
        valueObjectRef.getRef(),
        Integer.parseInt(execMessage.getStaticFieldPut().getValueObjectRef()));
    assertNull(execMessage.getStaticFieldPut().getContext());
  }

  @Test
  public void buildPutStaticDone_withAccessibleObject_staticFieldPutDoneMessage()
      throws NoSuchFieldException {
    UUID peerUuid = UUID.randomUUID();
    Class targetClass = DummyClassForTest.class;
    String fieldName = "aStaticField";
    AccessibleObject accessibleObject = targetClass.getDeclaredField(fieldName);
    String staticFieldPutUuid = UUID.randomUUID().toString();
    String followingUuid = UUID.randomUUID().toString();

    ExecMessage execMessage =
        messageBuilder.buildPutStaticDone(
            peerUuid, accessibleObject, staticFieldPutUuid, followingUuid);

    // assert expected values of ExecMessage
    assertNotNull(execMessage);
    assertEquals(peerUuid.toString(), execMessage.getPeerUuid());
    assertEquals(ExecMessageType.PUT_STATIC_DONE.toByte(), execMessage.execMessageType);
    assertNotNull(execMessage.getStaticFieldPutDone());
    assertEquals(targetClass.getName(), execMessage.getStaticFieldPutDone().getClazz().getName());
    assertEquals(fieldName, execMessage.getStaticFieldPutDone().getField().getName());
    assertEquals(staticFieldPutUuid, execMessage.getStaticFieldPutDone().getStaticFieldPutUuid());
    assertEquals(followingUuid, execMessage.getFollowingUuid());
  }
  // </editor-fold>

  // <editor-fold desc="Instance field put messages">
  @Test
  public void buildPutObject_withObjectValue_instanceFieldPutMessage() {
    UUID peerUuid = UUID.randomUUID();
    String className = DummyClassForTest.class.getName();
    String fieldName = "anInt";
    ObjectRef targetObjRef = ObjectRef.randomRef();
    String valueClassName = int.class.getName();
    Object value = "4";

    ExecMessage execMessage =
        messageBuilder.buildPutObject(
            peerUuid, className, fieldName, targetObjRef, valueClassName, value);

    // assert expected values of ExecMessage
    assertNotNull(execMessage);
    assertEquals(peerUuid.toString(), execMessage.getPeerUuid());
    assertEquals(ExecMessageType.PUT_FIELD.toByte(), execMessage.execMessageType);
    assertNotNull(execMessage.getInstanceFieldPut());
    assertEquals(className, execMessage.getInstanceFieldPut().getClazz().getName());
    assertEquals(fieldName, execMessage.getInstanceFieldPut().getField().getName());
    assertEquals(targetObjRef, ObjectRef.from(execMessage.getInstanceFieldPut().getObjectRef()));
    assertEquals(
        valueClassName, execMessage.getInstanceFieldPut().getValueObject().getClazz().getName());
    assertEquals(value, execMessage.getInstanceFieldPut().getValueObject().getValue());
    assertNull(execMessage.getInstanceFieldPut().getContext());
  }

  @Test
  public void buildPutObject_withObjectRefValue_instanceFieldPutMessage() {
    UUID peerUuid = UUID.randomUUID();
    String className = DummyClassForTest.class.getName();
    String fieldName = "anObject";
    ObjectRef targetObjRef = ObjectRef.randomRef();
    ObjectRef valueObjectRef = ObjectRef.randomRef();

    ExecMessage execMessage =
        messageBuilder.buildPutObject(peerUuid, className, fieldName, targetObjRef, valueObjectRef);

    // assert expected values of ExecMessage
    assertNotNull(execMessage);
    assertEquals(peerUuid.toString(), execMessage.getPeerUuid());
    assertEquals(ExecMessageType.PUT_FIELD.toByte(), execMessage.execMessageType);
    assertNotNull(execMessage.getInstanceFieldPut());
    assertEquals(className, execMessage.getInstanceFieldPut().getClazz().getName());
    assertEquals(fieldName, execMessage.getInstanceFieldPut().getField().getName());
    assertEquals(targetObjRef, ObjectRef.from(execMessage.getInstanceFieldPut().getObjectRef()));
    assertEquals(
        valueObjectRef.getRef(),
        Integer.parseInt(execMessage.getInstanceFieldPut().getValueObjectRef()));
    assertNull(execMessage.getInstanceFieldPut().getContext());
  }

  @Test
  public void buildPutObjectDone_withAccessibleAndInstanceFieldPutUuid_instanceFieldPutDoneMessage()
      throws NoSuchFieldException {
    MessageBuilder builder = new MessageBuilder(Boolean.toString(false));
    UUID peerUuid = UUID.randomUUID();
    Class targetClass = DummyClassForTest.class;
    String fieldName = "anObject";
    AccessibleObject accessibleObject = targetClass.getDeclaredField(fieldName);
    String instanceFieldPutUuid = UUID.randomUUID().toString();
    String followingUuid = UUID.randomUUID().toString();

    ExecMessage execMessage =
        builder.buildPutObjectDone(peerUuid, accessibleObject, instanceFieldPutUuid, followingUuid);

    // assert expected values of ExecMessage
    assertNotNull(execMessage);
    assertEquals(peerUuid.toString(), execMessage.getPeerUuid());
    assertEquals(ExecMessageType.PUT_FIELD_DONE.toByte(), execMessage.execMessageType);
    assertNotNull(execMessage.getInstanceFieldPutDone());
    assertEquals(targetClass.getName(), execMessage.getInstanceFieldPutDone().getClazz().getName());
    assertEquals(fieldName, execMessage.getInstanceFieldPutDone().getField().getName());
    assertEquals(
        instanceFieldPutUuid, execMessage.getInstanceFieldPutDone().getInstanceFieldPutUuid());
    assertEquals(followingUuid, execMessage.getFollowingUuid());
  }
  // </editor-fold>

  // <editor-fold desc="Intercept messages">
  @Test
  public void buildInterceptMessage_forMethod_interceptMessage() {
    UUID peerUuid = UUID.randomUUID();
    InterceptType type = InterceptType.BEFORE;
    String className = DummyClassForTest.class.getName();
    String methodName = "dummyMethod";
    List<String> parameterTypes = Arrays.asList("String", "int");
    String callbackClassName = this.getClass().getName();
    String callbackMethodName = "callbackMethod";

    InterceptMessage interceptMessage =
        messageBuilder.buildInterceptMessage(
            peerUuid,
            type,
            className,
            methodName,
            parameterTypes,
            callbackClassName,
            callbackMethodName);

    assertNotNull(interceptMessage);
    assertEquals(peerUuid.toString(), interceptMessage.getPeerUuid());
    assertNotNull(interceptMessage.getMessageUuid());
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
    UUID peerUuid = UUID.randomUUID();
    InterceptType type = InterceptType.AFTER;
    String className = DummyClassForTest.class.getName();
    String fieldName = "anInt";
    FieldOpType fieldOpType = FieldOpType.GET;
    String callbackClassName = this.getClass().getName();
    String callbackMethodName = "fakeCallbackMethod";

    InterceptMessage interceptMessage =
        messageBuilder.buildInterceptMessage(
            peerUuid,
            type,
            className,
            fieldName,
            fieldOpType,
            callbackClassName,
            callbackMethodName);

    assertNotNull(interceptMessage);
    assertEquals(peerUuid.toString(), interceptMessage.getPeerUuid());
    assertNotNull(interceptMessage.getMessageUuid());
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
    UUID peer = UUID.randomUUID();
    InterceptType type = InterceptType.AFTER;
    Class clazz = DummyClassForTest.class;
    Class callbackClass = this.getClass();
    String callbackMethod = "fakeCallbackMethod";
    String fieldName = "anInt";
    InterceptableFieldOp interceptableFieldOp =
        new InterceptableFieldOp(fieldName, FieldOpType.GET);
    InterceptRequest interceptRequest =
        new InterceptRequest<>(
            interceptRequestUuid,
            peer,
            type,
            clazz.getName(),
            callbackClass.getName(),
            callbackMethod,
            interceptableFieldOp);
    InterceptMessage interceptMessage = messageBuilder.buildInterceptMessage(interceptRequest);

    assertNotNull(interceptMessage);
    assertEquals(peer.toString(), interceptMessage.getPeerUuid());
    assertEquals(interceptRequestUuid.toString(), interceptMessage.getMessageUuid());
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
    UUID peer = UUID.randomUUID();
    InterceptType type = InterceptType.BEFORE;
    Class clazz = DummyClassForTest.class;
    Class callbackClass = this.getClass();
    String callbackMethod = "fakeCallbackMethod";
    String method = "dummyMethod";
    List<String> parameterTypes = Arrays.asList("String", "int", "java.util.List");
    InterceptableMethodCall interceptableMethodCall =
        new InterceptableMethodCall(method, parameterTypes);
    InterceptRequest interceptRequest =
        new InterceptRequest<>(
            interceptRequestUuid,
            peer,
            type,
            clazz.getName(),
            callbackClass.getName(),
            callbackMethod,
            interceptableMethodCall);
    InterceptMessage interceptMessage = messageBuilder.buildInterceptMessage(interceptRequest);

    assertNotNull(interceptMessage);
    assertEquals(peer.toString(), interceptMessage.getPeerUuid());
    assertEquals(interceptRequestUuid.toString(), interceptMessage.getMessageUuid());
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
  public void buildInterceptReply_uuidFollowingUuidResult_validInterceptReply() {
    UUID peerUuid = UUID.randomUUID();
    UUID followingUuid = UUID.randomUUID();
    boolean result = true;

    InterceptReply interceptReply =
        messageBuilder.buildInterceptReply(peerUuid, followingUuid, result);

    assertNotNull(interceptReply);
    assertEquals(peerUuid.toString(), interceptReply.getPeerUuid());
    assertEquals(followingUuid.toString(), interceptReply.getFollowingUuid());
    assertEquals(result, interceptReply.getResult());
  }

  @Test
  public void buildInterceptKey_instanceMethodCall_interceptKeyMessage() throws Exception {

    // create an ExecMessage that we can use to build an InterceptKeyMessage
    UUID peerUuid = UUID.randomUUID();
    Object sender = this;
    ObjectRef senderObjRef = ObjectRef.randomRef();
    Class clazz = DummyClassForTest.class;
    Object target = new DummyClassForTest();
    ObjectRef targetObjRef = ObjectRef.randomRef();
    Object[] args = {"test", 123, new ArrayList()};
    ObjectRef[] argObjRefs = {null, null, ObjectRef.randomRef()};
    Context instanceMethodContext =
        createContextForInstanceMethod(clazz, "dummyMethod", String.class, int.class, List.class);
    ExecMessage execMessage =
        messageBuilderWithContext.buildInstanceMethod(
            peerUuid,
            instanceMethodContext,
            sender,
            senderObjRef,
            target,
            targetObjRef,
            args,
            argObjRefs);
    InterceptKeyMessage interceptKeyMessage = messageBuilder.buildInterceptKey(execMessage);

    assertNotNull(interceptKeyMessage);
    assertEquals(target.getClass().getName(), interceptKeyMessage.getClazz());
    assertEquals(target.getClass().getName(), interceptKeyMessage.getClazz());
    assertEquals(execMessage.getExecMessageType(), interceptKeyMessage.getExecMsgType());
    assertEquals(
        execMessage.getInstanceMethodCall().getName(), interceptKeyMessage.getExecutableName());
    assertEquals(
        execMessage.getInstanceMethodCall().getParameters().length,
        interceptKeyMessage.getParameterTypes().length);
    assertEquals(
        execMessage.getInstanceMethodCall().getParameters()[0].getType().getName(),
        interceptKeyMessage.getParameterTypes()[0]);
    assertEquals(
        execMessage.getInstanceMethodCall().getParameters()[1].getType().getName(),
        interceptKeyMessage.getParameterTypes()[1]);
    assertEquals(
        execMessage.getInstanceMethodCall().getParameters()[2].getType().getName(),
        interceptKeyMessage.getParameterTypes()[2]);
  }

  @Test
  public void buildCallbackForInterceptRequest_constructorToBeIntercepted_callbackExecMessage()
      throws Exception {
    // create an ExecMessage out of a Constructor that we can use as interceptedMessage
    UUID peerUuid = UUID.randomUUID();
    Object sender = this;
    ObjectRef senderObjRef = ObjectRef.randomRef();
    Object target = new DummyClassForTest();
    Object[] args = {"test", 123};
    ObjectRef[] argObjRefs = {null, null};
    Context context = createContextForConstructor(target.getClass(), String.class, int.class);
    ExecMessage interceptedExecMessage =
        messageBuilder.buildConstructor(peerUuid, context, sender, senderObjRef, args, argObjRefs);

    // create an InterceptMessage from which the callback will be built
    String callbackClassName = "SomeClassWithCallbackMethod";
    String callbackMethodName = "callbackMethod";
    InterceptMessage interceptMessage =
        messageBuilder.buildInterceptMessage(
            peerUuid,
            InterceptType.AFTER,
            target.getClass().getName(),
            "new",
            Arrays.asList("String", "int"),
            callbackClassName,
            callbackMethodName);

    ExecMessage callbackExecMessage =
        messageBuilder.buildCallbackForInterceptRequest(
            peerUuid, interceptedExecMessage, interceptMessage);

    assertNotNull(callbackExecMessage);
    assertEquals(peerUuid.toString(), callbackExecMessage.getPeerUuid());
    assertEquals(ExecMessageType.CLASS_METHOD.toByte(), callbackExecMessage.getExecMessageType());
    assertNotNull(callbackExecMessage.getClassMethodCall());
    assertEquals(callbackClassName, callbackExecMessage.getClassMethodCall().getClazz().getName());
    assertEquals(callbackMethodName, callbackExecMessage.getClassMethodCall().getName());
    assertEquals(
        interceptedExecMessage.getConstructorCall().getParameters().length,
        callbackExecMessage.getClassMethodCall().getParameters().length);
    assertEquals(
        interceptedExecMessage.getConstructorCall().getParameters()[0].getType().getName(),
        callbackExecMessage.getClassMethodCall().getParameters()[0].getType().getName());
    assertEquals(
        interceptedExecMessage.getConstructorCall().getParameters()[1].getType().getName(),
        callbackExecMessage.getClassMethodCall().getParameters()[1].getType().getName());
  }

  @Test
  public void buildCallbackForInterceptRequest_classMethodToBeIntercepted_callbackExecMessage()
      throws Exception {
    // create an ExecMessage out of a ClassMethod that we can use as interceptedMessage
    UUID peerUuid = UUID.randomUUID();
    Object sender = this;
    ObjectRef senderObjRef = ObjectRef.randomRef();
    Object target = new DummyClassForTest();
    String methodName = "dummyStaticMethod";
    String[] parameterTypes = new String[] {"String", "String", "boolean"};
    Object[] args = {"str1", "str2", true};
    ObjectRef[] argObjRefs = {null, null, null};
    ExecMessage interceptedExecMessage =
        messageBuilder.buildClassMethod(
            peerUuid,
            target.getClass().getName(),
            methodName,
            parameterTypes,
            sender,
            senderObjRef,
            args,
            argObjRefs);

    // create an InterceptMessage from which the callback will be built
    String callbackClassName = "SomeClassWithCallbackMethod";
    String callbackMethodName = "callbackMethod";
    InterceptMessage interceptMessage =
        messageBuilder.buildInterceptMessage(
            peerUuid,
            InterceptType.AFTER,
            target.getClass().getName(),
            methodName,
            Arrays.asList(parameterTypes),
            callbackClassName,
            callbackMethodName);

    ExecMessage callbackExecMessage =
        messageBuilder.buildCallbackForInterceptRequest(
            peerUuid, interceptedExecMessage, interceptMessage);

    assertNotNull(callbackExecMessage);
    assertEquals(peerUuid.toString(), callbackExecMessage.getPeerUuid());
    assertEquals(ExecMessageType.CLASS_METHOD.toByte(), callbackExecMessage.getExecMessageType());
    assertNotNull(callbackExecMessage.getClassMethodCall());
    assertEquals(callbackClassName, callbackExecMessage.getClassMethodCall().getClazz().getName());
    assertEquals(callbackMethodName, callbackExecMessage.getClassMethodCall().getName());
    assertEquals(
        interceptedExecMessage.getClassMethodCall().getParameters().length,
        callbackExecMessage.getClassMethodCall().getParameters().length);
    assertEquals(
        interceptedExecMessage.getClassMethodCall().getParameters()[0].getType().getName(),
        callbackExecMessage.getClassMethodCall().getParameters()[0].getType().getName());
    assertEquals(
        interceptedExecMessage.getClassMethodCall().getParameters()[1].getType().getName(),
        callbackExecMessage.getClassMethodCall().getParameters()[1].getType().getName());
  }

  @Test
  public void buildCallbackForInterceptRequest_instanceMethodToBeIntercepted_callbackExecMessage()
      throws Exception {
    // create an ExecMessage out of an InstanceMethod that we can use as interceptedMessage
    UUID peerUuid = UUID.randomUUID();
    Object sender = this;
    ObjectRef senderObjRef = ObjectRef.randomRef();
    Object target = new DummyClassForTest();
    ObjectRef targetObjRef = ObjectRef.randomRef();
    String methodName = "dummyMethod";
    Object[] args = {"test", 123, new ArrayList()};
    ObjectRef[] argObjRefs = {null, null, ObjectRef.randomRef()};
    Context context =
        createContextForInstanceMethod(
            target.getClass(), methodName, String.class, int.class, List.class);
    ExecMessage interceptedExecMessage =
        messageBuilder.buildInstanceMethod(
            peerUuid, context, sender, senderObjRef, target, targetObjRef, args, argObjRefs);

    // create an InterceptMessage from which the callback will be built
    String callbackClassName = "SomeClassWithCallbackMethod";
    String callbackMethodName = "callbackMethod";
    InterceptMessage interceptMessage =
        messageBuilder.buildInterceptMessage(
            peerUuid,
            InterceptType.AFTER,
            target.getClass().getName(),
            methodName,
            Arrays.asList("String", "int", "java.util.List"),
            callbackClassName,
            callbackMethodName);

    ExecMessage callbackExecMessage =
        messageBuilder.buildCallbackForInterceptRequest(
            peerUuid, interceptedExecMessage, interceptMessage);

    assertNotNull(callbackExecMessage);
    assertEquals(peerUuid.toString(), callbackExecMessage.getPeerUuid());
    assertEquals(ExecMessageType.CLASS_METHOD.toByte(), callbackExecMessage.getExecMessageType());
    assertNotNull(callbackExecMessage.getClassMethodCall());
    assertEquals(callbackClassName, callbackExecMessage.getClassMethodCall().getClazz().getName());
    assertEquals(callbackMethodName, callbackExecMessage.getClassMethodCall().getName());
    assertEquals(
        interceptedExecMessage.getInstanceMethodCall().getParameters().length,
        callbackExecMessage.getClassMethodCall().getParameters().length);
    assertEquals(
        interceptedExecMessage.getInstanceMethodCall().getParameters()[0].getType().getName(),
        callbackExecMessage.getClassMethodCall().getParameters()[0].getType().getName());
    assertEquals(
        interceptedExecMessage.getInstanceMethodCall().getParameters()[1].getType().getName(),
        callbackExecMessage.getClassMethodCall().getParameters()[1].getType().getName());
    assertEquals(
        interceptedExecMessage.getInstanceMethodCall().getParameters()[2].getType().getName(),
        callbackExecMessage.getClassMethodCall().getParameters()[2].getType().getName());
  }

  @Test
  public void buildCallbackForInterceptRequest_fieldOpsToBeIntercepted_callbackExecMessages()
      throws Exception {

    // common for all four field ops
    UUID peerUuid = UUID.randomUUID();
    Object sender = this;
    ObjectRef senderObjRef = ObjectRef.randomRef();
    String fieldName = "anInt";

    // create a list of specific args for each of the four field op types
    List<Map<String, Object>> listOfFieldOpArgs =
        Arrays.asList(
            new HashMap<String, Object>() {
              {
                put("messageType", GET_FIELD);
                put("fieldOpType", FieldOpType.GET);
                put("target", new DummyClassForTest());
                put("targetObjectRef", ObjectRef.randomRef());
              }
            },
            new HashMap<String, Object>() {
              {
                put("messageType", PUT_FIELD);
                put("fieldOpType", FieldOpType.PUT);
                put("target", new DummyClassForTest());
                put("targetObjectRef", ObjectRef.from("734524"));
                put("arg", "87");
                put("argObjRef", ObjectRef.from("2872346"));
              }
            },
            new HashMap<String, Object>() {
              {
                put("fieldOpType", FieldOpType.GET);
                put("messageType", GET_STATIC);
              }
            },
            new HashMap<String, Object>() {
              {
                put("fieldOpType", FieldOpType.PUT);
                put("messageType", PUT_STATIC);
                put("arg", "378");
                put("argObjRef", ObjectRef.from("2987234"));
              }
            });

    // call buildFieldOp for each of the four field op types
    for (Map<String, Object> map : listOfFieldOpArgs) {
      ExecMessageType execMessageType = (ExecMessageType) map.get("messageType");
      FieldOpType fieldOpType = (FieldOpType) map.get("fieldOpType");
      Class targetClass = DummyClassForTest.class;
      Object target = map.get("target");
      ObjectRef targetObjRef = (ObjectRef) map.get("targetObjectRef");
      Object arg = map.get("arg");
      ObjectRef argObjRef = (ObjectRef) map.get("argObjRef");
      Context context = createContextForFieldOp(targetClass, fieldName);
      ExecMessage interceptedExecMessage =
          messageBuilderWithContext.buildFieldOp(
              peerUuid,
              context,
              execMessageType,
              sender,
              senderObjRef,
              target,
              targetObjRef,
              arg,
              argObjRef);

      // create an InterceptMessage from which the callback will be built
      String callbackClassName = "SomeClassWithCallbackMethod";
      String callbackMethodName = "callbackMethod";
      InterceptMessage interceptMessage =
          messageBuilder.buildInterceptMessage(
              peerUuid,
              InterceptType.AFTER,
              targetClass.getName(),
              fieldName,
              fieldOpType,
              callbackClassName,
              callbackMethodName);

      ExecMessage callbackExecMessage =
          messageBuilder.buildCallbackForInterceptRequest(
              peerUuid, interceptedExecMessage, interceptMessage);

      assertNotNull(callbackExecMessage);
      assertEquals(peerUuid.toString(), callbackExecMessage.getPeerUuid());
      assertEquals(ExecMessageType.CLASS_METHOD.toByte(), callbackExecMessage.getExecMessageType());
      assertNotNull(callbackExecMessage.getClassMethodCall());
      assertEquals(
          callbackClassName, callbackExecMessage.getClassMethodCall().getClazz().getName());
      assertEquals(callbackMethodName, callbackExecMessage.getClassMethodCall().getName());

      // compare argument values
      switch (execMessageType) {
        case GET_FIELD:
          break;
        case PUT_FIELD:
          assertEquals(
              interceptedExecMessage.getInstanceFieldPut().getValueObject().getValue(),
              callbackExecMessage.getClassMethodCall().getParameters()[0].getValue().getValue());
          break;
        case GET_STATIC:
          break;
        case PUT_STATIC:
          assertEquals(
              interceptedExecMessage.getStaticFieldPut().getValueObject().getValue(),
              callbackExecMessage.getClassMethodCall().getParameters()[0].getValue().getValue());
          break;
        default:
          fail("Unexpected ExecMessageType: " + execMessageType);
      }
    }
  }

  // </editor-fold>

  // <editor-fold desc="Throwable messages">
  @Test
  public void buildAccessibleObjectThrowable_withConstructor_raisedThrowableMessage() {
    UUID peerUuid = UUID.randomUUID();
    String throwableMessage = "my throwable message";
    Throwable throwable = new Throwable(throwableMessage);
    String followingUuid = UUID.randomUUID().toString();

    List<AccessibleObject> accessibleObjects =
        new ArrayList() {
          {
            add(DummyClassForTest.class.getDeclaredConstructors()[0]);
            add(DummyClassForTest.class.getDeclaredMethods()[0]);
            add(DummyClassForTest.class.getDeclaredFields()[0]);
            add(new AccessibleObject() {}); // anonymous class to check for unsupported types
          }
        };

    for (AccessibleObject accessibleObject : accessibleObjects) {
      ExecMessage execMessage = null;
      try {
        execMessage =
            messageBuilder.buildAccessibleObjectThrowable(
                peerUuid, Optional.of(accessibleObject), null, throwable, followingUuid);
      } catch (UnsupportedOperationException e) {
        assertTrue(e.getMessage().contains("Unsupported accessibleObject type:"));
        continue;
      }

      assertNotNull(execMessage);
      assertEquals(peerUuid.toString(), execMessage.getPeerUuid());
      assertEquals(THROWABLE.toByte(), execMessage.getExecMessageType());
      assertNotNull(execMessage.getRaisedThrowable());
      if (accessibleObject instanceof Method) {
        assertEquals(
            ((Method) accessibleObject).getModifiers(),
            execMessage.getRaisedThrowable().getModifiers());
        assertEquals(
            ((Method) accessibleObject).getName(), execMessage.getRaisedThrowable().getMethod());
      } else if (accessibleObject instanceof Constructor) {
        assertEquals(
            ((Constructor) accessibleObject).getModifiers(),
            execMessage.getRaisedThrowable().getModifiers());
        assertEquals(
            ((Constructor) accessibleObject).getDeclaringClass().getName(),
            execMessage.getRaisedThrowable().getConstructor());
      } else if (accessibleObject instanceof Field) {
        assertEquals(
            ((Field) accessibleObject).getModifiers(),
            execMessage.getRaisedThrowable().getModifiers());
        assertEquals(
            ((Field) accessibleObject).getName(), execMessage.getRaisedThrowable().getField());
      } else {
        fail("Unexpected AccessibleObject: " + accessibleObject);
      }
      assertNotNull(execMessage.getRaisedThrowable().getThrowable());
      assertEquals(
          throwable.getClass().getName(), execMessage.getRaisedThrowable().getClazz().getName());
      assertEquals(
          throwable.getClass().getName(),
          execMessage.getRaisedThrowable().getThrowable().getType());
      assertEquals(throwableMessage, execMessage.getRaisedThrowable().getThrowable().getMessage());
      assertNotNull(execMessage.getRaisedThrowable().getThrowable().getStackTraceElements());
      assertNull(execMessage.getRaisedThrowable().getThrowable().getCause());
      assertEquals(followingUuid, execMessage.getFollowingUuid());
    }
  }

  @Test
  public void
      buildAccessibleObjectThrowable_withAllExecutableObjectTypes_raisedThrowableMessages() {
    UUID peerUuid = UUID.randomUUID();
    String throwableMessage = "my throwable message";
    Throwable throwable = new Throwable(throwableMessage);
    String followingUuid = UUID.randomUUID().toString();

    Arrays.asList(ExecutableObjectType.values()).stream()
        .forEach(
            executableObjectType -> {
              ExecMessage execMessage =
                  messageBuilder.buildAccessibleObjectThrowable(
                      peerUuid, Optional.empty(), executableObjectType, throwable, followingUuid);

              assertNotNull(execMessage);
              assertEquals(peerUuid.toString(), execMessage.getPeerUuid());
              assertEquals(THROWABLE.toByte(), execMessage.getExecMessageType());
              assertNotNull(execMessage.getRaisedThrowable());
              switch (executableObjectType) {
                case METHOD:
                  assertEquals(
                      "<info not available>", execMessage.getRaisedThrowable().getMethod());
                  break;
                case CONSTRUCTOR:
                  assertEquals(
                      "<info not available>", execMessage.getRaisedThrowable().getConstructor());
                  break;
                case FIELD:
                  assertEquals("<info not available>", execMessage.getRaisedThrowable().getField());
                  break;
                default:
                  fail("Unexpected ExecutableObjectType: " + executableObjectType);
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
              assertEquals(followingUuid, execMessage.getFollowingUuid());
            });
  }
  // </editor-fold>

  // <editor-fold desc="Return value messages">
  @Test
  public void buildReturnValue_withConstructor_returnValueMessage() {
    UUID peerUuid = UUID.randomUUID();
    Constructor constructor = DummyClassForTest.class.getConstructors()[0];
    Object returnValue = new DummyClassForTest();
    ObjectRef returnValueObjRef = ObjectRef.randomRef();
    String followingUuid = UUID.randomUUID().toString();

    ExecMessage execMessage =
        messageBuilder.buildReturnValue(
            peerUuid, returnValue, constructor, returnValueObjRef, false, followingUuid);

    assertNotNull(execMessage);
    assertEquals(peerUuid.toString(), execMessage.getPeerUuid());
    assertEquals(RETURN_VALUE.toByte(), execMessage.getExecMessageType());
    assertNotNull(execMessage.getReturnValue());
    assertEquals(
        constructor.toGenericString(),
        execMessage.getReturnValue().getFrom().getConstructor().getRepr());
    assertEquals(
        returnValue.getClass().getName(),
        execMessage.getReturnValue().getObject().getClazz().getName());
    assertEquals(
        returnValueObjRef.getRef(),
        Integer.parseInt(execMessage.getReturnValue().getObject().getRef()));
    assertEquals(followingUuid, execMessage.getFollowingUuid());
  }

  @Test
  public void buildReturnValue_withMethod_returnValueMessage() throws NoSuchMethodException {
    UUID peerUuid = UUID.randomUUID();
    Method method = DummyClassForTest.class.getMethod("addInts", int.class, int.class);
    ObjectRef returnValueObjRef = ObjectRef.randomRef();
    int returnValue = 4;
    String followingUuid = UUID.randomUUID().toString();

    ExecMessage execMessage =
        messageBuilder.buildReturnValue(
            peerUuid,
            returnValue,
            method,
            returnValueObjRef,
            method.getReturnType() == void.class,
            followingUuid);

    assertNotNull(execMessage);
    assertEquals(peerUuid.toString(), execMessage.getPeerUuid());
    assertEquals(RETURN_VALUE.toByte(), execMessage.getExecMessageType());
    assertNotNull(execMessage.getReturnValue());
    assertEquals(
        method.toGenericString(), execMessage.getReturnValue().getFrom().getMethod().getRepr());
    assertEquals("int", execMessage.getReturnValue().getObject().getClazz().getName());
    assertEquals(
        returnValueObjRef.getRef(),
        Integer.parseInt(execMessage.getReturnValue().getObject().getRef()));
    assertEquals(followingUuid, execMessage.getFollowingUuid());
  }

  @Test
  public void buildReturnValue_withGetField_returnValueMessage() throws NoSuchFieldException {
    UUID peerUuid = UUID.randomUUID();
    Field field = DummyClassForTest.class.getDeclaredField("anObject");
    ObjectRef returnValueObjRef = ObjectRef.randomRef();
    Object returnValue = new Object();
    String followingUuid = UUID.randomUUID().toString();

    ExecMessage execMessage =
        messageBuilder.buildReturnValue(
            peerUuid, returnValue, field, returnValueObjRef, false, followingUuid);

    assertNotNull(execMessage);
    assertEquals(peerUuid.toString(), execMessage.getPeerUuid());
    assertEquals(RETURN_VALUE.toByte(), execMessage.getExecMessageType());
    assertNotNull(execMessage.getReturnValue());
    assertEquals(
        field.toGenericString(), execMessage.getReturnValue().getFrom().getField().getRepr());
    assertEquals(
        returnValue.getClass().getName(),
        execMessage.getReturnValue().getObject().getClazz().getName());
    assertEquals(
        returnValueObjRef.getRef(),
        Integer.parseInt(execMessage.getReturnValue().getObject().getRef()));
    assertEquals(followingUuid, execMessage.getFollowingUuid());
  }
  // </editor-fold>

  // <editor-fold desc="Control messages">
  @Test
  public void buildDeleteObjectControlMessage_withBody_deleteControlMessage() {
    MessageBuilder builder = new MessageBuilder(Boolean.toString(false));
    UUID fromPeer = UUID.randomUUID();
    String body = "someBody";
    ControlMessage controlMessage = builder.buildDeleteObjectControlMessage(fromPeer, body);

    assertNotNull(controlMessage);
    assertNotNull(controlMessage.getMessageUuid());
    assertEquals(fromPeer.toString(), controlMessage.getFromPeer());
    assertEquals(body, controlMessage.getBody());
    assertEquals(ControlCommandType.DELETE_OBJECT.toByte(), controlMessage.getCommand());
  }

  @Test
  public void buildDeleteObjectControlMessage_withNoBody_deleteControlMessage() {
    MessageBuilder builder = new MessageBuilder(Boolean.toString(false));
    UUID fromPeer = UUID.randomUUID();
    String body = null;
    ControlMessage controlMessage = builder.buildDeleteObjectControlMessage(fromPeer, body);

    assertNotNull(controlMessage);
    assertNotNull(controlMessage.getMessageUuid());
    assertEquals(fromPeer.toString(), controlMessage.getFromPeer());
    assertEquals("", controlMessage.getBody());
    assertEquals(ControlCommandType.DELETE_OBJECT.toByte(), controlMessage.getCommand());
  }

  @Test
  public void buildDeleteSessionControlMessage_fromPeer_deleteSessionControlMessage() {
    MessageBuilder builder = new MessageBuilder(Boolean.toString(false));
    UUID fromPeer = UUID.randomUUID();

    ControlMessage controlMessage = builder.buildDeleteSessionControlMessage(fromPeer);

    assertNotNull(controlMessage);
    assertNotNull(controlMessage.getMessageUuid());
    assertEquals(fromPeer.toString(), controlMessage.getFromPeer());
    assertEquals("", controlMessage.getBody());
    assertEquals(ControlCommandType.DELETE_SESSION.toByte(), controlMessage.getCommand());
  }

  @Test
  public void buildControlMessage_withStatusTypeAndBody_controlMessage() {
    MessageBuilder builder = new MessageBuilder(Boolean.toString(false));
    UUID fromPeerUuid = UUID.randomUUID();
    ControlStatusType statusType = ControlStatusType.OK;
    String body = "someBody";

    ControlMessage controlMessage = builder.buildControlMessage(fromPeerUuid, statusType, body);

    assertNotNull(controlMessage);
    assertNotNull(controlMessage.getMessageUuid());
    assertEquals(fromPeerUuid.toString(), controlMessage.getFromPeer());
    assertEquals(body, controlMessage.getBody());
    assertEquals(statusType.toByte(), controlMessage.getStatus());
  }

  @Test
  public void buildControlMessage_withStatusTypeAndNoBody_controlMessage() {
    MessageBuilder builder = new MessageBuilder(Boolean.toString(false));
    UUID fromPeerUuid = UUID.randomUUID();
    ControlStatusType statusType = ControlStatusType.NO_SUCH_SESSION;

    ControlMessage controlMessage = builder.buildControlMessage(fromPeerUuid, statusType);

    assertNotNull(controlMessage);
    assertNotNull(controlMessage.getMessageUuid());
    assertEquals(fromPeerUuid.toString(), controlMessage.getFromPeer());
    assertEquals("", controlMessage.getBody());
    assertEquals(statusType.toByte(), controlMessage.getStatus());
  }

  // </editor-fold>

  // <editor-fold desc="Message Wrapper">
  @Test
  public void wrap_execMessage_wrappedExecMessage() {
    UUID peerUuid = UUID.randomUUID();
    String className = DummyClassForTest.class.getName();
    ExecMessage execMessage = messageBuilder.buildEmptyConstructor(peerUuid, className);

    Message wrappedMessage = messageBuilder.wrap(execMessage);

    assertNotNull(wrappedMessage);
    assertEquals(MessageType.EXEC_MESSAGE.toByte(), wrappedMessage.getMessageType());
    assertEquals(execMessage, wrappedMessage.getExecMessage());
  }

  @Test
  public void wrap_interceptMessage_wrappedInterceptMessage() {
    UUID peerUuid = UUID.randomUUID();
    String className = DummyClassForTest.class.getName();
    String fieldName = "anInt";
    String callbackClass = this.getClass().getName();
    String callbackMethod = "callbackMethod";
    InterceptMessage interceptMessage =
        messageBuilder.buildInterceptMessage(
            peerUuid,
            InterceptType.BEFORE,
            className,
            fieldName,
            FieldOpType.GET,
            callbackClass,
            callbackMethod);

    Message wrappedMessage = messageBuilder.wrap(interceptMessage);

    assertNotNull(wrappedMessage);
    assertEquals(MessageType.INTERCEPT_MESSAGE.toByte(), wrappedMessage.getMessageType());
    assertEquals(interceptMessage, wrappedMessage.getInterceptMessage());
  }

  @Test
  public void wrap_interceptKeyMessage_wrappedInterceptKeyMessage() {
    UUID peerUuid = UUID.randomUUID();
    String className = DummyClassForTest.class.getName();
    ExecMessage execMessage = messageBuilder.buildEmptyConstructor(peerUuid, className);

    InterceptKeyMessage interceptKeyMessage = messageBuilder.buildInterceptKey(execMessage);

    Message wrappedMessage = messageBuilder.wrap(interceptKeyMessage);

    assertNotNull(wrappedMessage);
    assertEquals(MessageType.INTERCEPT_KEY.toByte(), wrappedMessage.getMessageType());
    assertEquals(interceptKeyMessage, wrappedMessage.getInterceptKeyMessage());
  }

  @Test
  public void wrap_interceptReply_wrappedInterceptReply() {
    UUID peerUuid = UUID.randomUUID();
    UUID followingUuid = UUID.randomUUID();
    boolean result = true;

    InterceptReply interceptReply =
        messageBuilder.buildInterceptReply(peerUuid, followingUuid, result);

    Message wrappedInterceptReply = messageBuilder.wrap(interceptReply);

    assertNotNull(wrappedInterceptReply);
    assertEquals(MessageType.INTERCEPT_REPLY.toByte(), wrappedInterceptReply.getMessageType());
    assertEquals(interceptReply, wrappedInterceptReply.getInterceptReply());
  }

  @Test
  public void wrap_controlMessage_wrappedControlMessage() {
    UUID fromPeerUuid = UUID.randomUUID();
    ControlStatusType statusType = ControlStatusType.OK;
    ControlMessage controlMessage = messageBuilder.buildControlMessage(fromPeerUuid, statusType);

    Message wrappedMessage = messageBuilder.wrap(controlMessage);

    assertNotNull(wrappedMessage);
    assertEquals(MessageType.CONTROL_MESSAGE.toByte(), wrappedMessage.getMessageType());
    assertEquals(controlMessage, wrappedMessage.getControlMessage());
  }
  // </editor-fold>
}
