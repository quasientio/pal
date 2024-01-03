package net.ittera.pal.serdes.colfer;

import static net.ittera.pal.messages.types.ExecMessageType.*;
import static org.junit.Assert.*;

import java.util.*;
import net.ittera.pal.common.api.rmi.InstanceMethodCall;
import net.ittera.pal.common.api.rmi.StaticMethodCall;
import net.ittera.pal.common.lang.reflect.ConstructorSignature;
import net.ittera.pal.common.lang.reflect.FieldSignature;
import net.ittera.pal.common.lang.reflect.MethodSignature;
import net.ittera.pal.common.objects.ObjectRef;
import net.ittera.pal.common.runtime.Context;
import net.ittera.pal.messages.colfer.ExecMessage;
import net.ittera.pal.messages.colfer.InternalHeader;
import net.ittera.pal.messages.colfer.Obj;
import net.ittera.pal.messages.colfer.Parameter;
import net.ittera.pal.messages.types.ExecMessageType;
import net.ittera.pal.messages.types.InternalHeaderType;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

class DummyClassForTest {
  int anInt;

  DummyClassForTest() {}

  DummyClassForTest(String str1, int number) {}

  public void dummyMethodJustPrimitiveArgs(String str1, int number, Boolean myboo) {}

  public void dummyMethod(String str1, int number, List someList) {}
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

  private net.ittera.pal.common.runtime.Context createContextForConstructor() throws Exception {
    ConstructorSignature constructorSignature =
        new ConstructorSignature(
            DummyClassForTest.class.getDeclaredConstructor(new Class[] {String.class, int.class}));
    String sourceFile = "MessageBuilderTest.java";
    int lineNumber = 17;
    Class withinType = DummyClassForTest.class;
    return new net.ittera.pal.common.runtime.Context(
        sourceFile, lineNumber, withinType, constructorSignature);
  }

  private net.ittera.pal.common.runtime.Context createContextForInstanceMethod() throws Exception {
    MethodSignature methodSignature =
        new MethodSignature(
            DummyClassForTest.class.getDeclaredMethod(
                "dummyMethod", new Class[] {String.class, int.class, List.class}));
    String sourceFile = "MessageBuilderTest.java";
    int lineNumber = 20;
    Class withinType = DummyClassForTest.class;
    return new net.ittera.pal.common.runtime.Context(
        sourceFile, lineNumber, withinType, methodSignature);
  }

  private net.ittera.pal.common.runtime.Context createContextForClassMethod() throws Exception {
    // Arrays.deepEquals(Object[] a1, Object[] a2)
    MethodSignature methodSignature =
        new MethodSignature(
            Arrays.class.getDeclaredMethod(
                "deepEquals", new Class[] {Object[].class, Object[].class}));
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
    ExecMessageType execMessageType = ExecMessageType.values()[fieldOpMessage.execMessageType];
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
      default:
        fail("Unexpected ExecMessageType: " + fieldOpMessage.getExecMessageType());
    }
    return extractedFieldOpMessageInfo;
  }
  // </editor-fold>

  private MessageBuilder messageBuilder;
  private MessageBuilder messageBuilderWithContext;
  private net.ittera.pal.common.runtime.Context constructorContext;
  private net.ittera.pal.common.runtime.Context instanceMethodContext;

  @Before
  public void setUp() throws Exception {
    messageBuilder = new MessageBuilder();
    messageBuilderWithContext = new MessageBuilder(Boolean.toString(true));
    constructorContext = createContextForConstructor();
    instanceMethodContext = createContextForInstanceMethod();
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

  // <editor-fold desc="Header messages">
  @Test
  public void buildWriteAheadHeader_validUuid_writeAheadHeader() {
    UUID peerUuid = UUID.randomUUID();
    InternalHeader header = messageBuilder.buildWriteAheadHeader(peerUuid);
    assertNotNull(header);
    assertEquals(peerUuid.toString(), header.getValue());
    assertEquals((byte) InternalHeaderType.WRITE_AHEAD.ordinal(), header.getHeaderType());
  }
  // </editor-fold>

  // <editor-fold desc="Constructor messages">
  @Test
  public void buildEmptyConstructor_noArguments_constructorMessage() {
    UUID peerUuid = UUID.randomUUID();
    String className = this.getClass().getName();
    ExecMessage execMessage = messageBuilder.buildEmptyConstructor(peerUuid, className);
    assertNotNull(execMessage);
    assertEquals((byte) ExecMessageType.CONSTRUCTOR.ordinal(), execMessage.execMessageType);
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
    assertEquals((byte) ExecMessageType.CONSTRUCTOR.ordinal(), execMessage.execMessageType);
    assertNotNull(execMessage.getConstructorCall());
    assertEquals(peerUuid.toString(), execMessage.getPeerUuid());
    assertEquals(className, execMessage.constructorCall.getClazz().getName());
    assertNull(execMessage.getConstructorCall().getContext());
    assertEquals(parameterTypes.length, execMessage.getConstructorCall().getParameters().length);
  }

  @Test
  public void buildConstructor_withContext_constructorMessage() {
    UUID peerUuid = UUID.randomUUID();
    Object sender = this;
    ObjectRef senderObjRef = ObjectRef.randomRef();
    Object[] args = {"test", 123};
    ObjectRef[] argObjRefs = {null, null};
    ExecMessage execMessage =
        messageBuilderWithContext.buildConstructor(
            peerUuid, constructorContext, sender, senderObjRef, args, argObjRefs);
    assertNotNull(execMessage);
    assertEquals((byte) ExecMessageType.CONSTRUCTOR.ordinal(), execMessage.execMessageType);
    assertNotNull(execMessage.getConstructorCall());
    assertEquals(peerUuid.toString(), execMessage.getPeerUuid());
    assertEquals(
        DummyClassForTest.class.getName(), execMessage.getConstructorCall().getClazz().getName());
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
  public void buildConstructor_constructorCallWithNoArgs_constructorMessage() {
    UUID peerUuid = UUID.randomUUID();
    Object sender = this;
    ObjectRef senderObjRef = ObjectRef.randomRef();
    Class clazz = ArrayList.class;
    net.ittera.pal.common.api.rmi.ConstructorCall constructorCall =
        new net.ittera.pal.common.api.rmi.ConstructorCall(clazz);
    ExecMessage execMessage =
        messageBuilder.buildConstructor(peerUuid, sender, senderObjRef, constructorCall);
    assertNotNull(execMessage);
    assertEquals((byte) ExecMessageType.CONSTRUCTOR.ordinal(), execMessage.execMessageType);
    assertNotNull(execMessage.getConstructorCall());
    assertEquals(peerUuid.toString(), execMessage.getPeerUuid());
    assertEquals(clazz.getName(), execMessage.constructorCall.getClazz().getName());
    assertNull(execMessage.getConstructorCall().getContext());
    assertEquals(0, execMessage.getConstructorCall().getParameters().length);
  }

  @Test
  public void buildConstructor_constructorCallWithArgs_constructorMessage() {
    UUID peerUuid = UUID.randomUUID();
    Object sender = this;
    ObjectRef senderObjRef = ObjectRef.randomRef();
    Class clazz = ArrayList.class;
    int arrayListInitialCapacity = 23;
    Object[] args = {arrayListInitialCapacity};
    net.ittera.pal.common.api.rmi.ConstructorCall constructorCall =
        new net.ittera.pal.common.api.rmi.ConstructorCall(clazz)
            .withArgs(args)
            .withParameterTypes(new String[] {"int"});
    ExecMessage execMessage =
        messageBuilder.buildConstructor(peerUuid, sender, senderObjRef, constructorCall);
    assertNotNull(execMessage);
    assertEquals((byte) ExecMessageType.CONSTRUCTOR.ordinal(), execMessage.execMessageType);
    assertNotNull(execMessage.getConstructorCall());
    assertEquals(peerUuid.toString(), execMessage.getPeerUuid());
    assertEquals(clazz.getName(), execMessage.constructorCall.getClazz().getName());
    assertNull(execMessage.getConstructorCall().getContext());
    assertEquals(args.length, execMessage.getConstructorCall().getParameters().length);
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
            peerUuid,
            className,
            methodName,
            target,
            targetObjRef,
            parameterTypes,
            args,
            argObjRefs);
    assertNotNull(execMessage);
    assertEquals((byte) ExecMessageType.INSTANCE_METHOD.ordinal(), execMessage.execMessageType);
    assertNotNull(execMessage.getInstanceMethodCall());
    assertEquals(peerUuid.toString(), execMessage.getPeerUuid());
    assertEquals(className, execMessage.getInstanceMethodCall().getClazz().getName());
    assertEquals(methodName, execMessage.getInstanceMethodCall().getName());
    assertNull(execMessage.getInstanceMethodCall().getContext());
    assertEquals(parameterTypes.length, execMessage.getInstanceMethodCall().getParameters().length);
  }

  @Test
  public void buildInstanceMethod_withContext_instanceMethodCallMessage() {
    UUID peerUuid = UUID.randomUUID();
    Object sender = this;
    ObjectRef senderObjRef = ObjectRef.randomRef();
    Object target = new DummyClassForTest();
    ObjectRef targetObjRef = ObjectRef.randomRef();
    Object[] args = {"test", 123, new ArrayList()};
    ObjectRef[] argObjRefs = {null, null, ObjectRef.randomRef()};
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
    assertEquals((byte) ExecMessageType.INSTANCE_METHOD.ordinal(), execMessage.execMessageType);
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
  public void buildInstanceMethod_instanceMethodCall_instanceMethodCallMessage()
      throws ClassNotFoundException {
    UUID peerUuid = UUID.randomUUID();
    final String methodToCall = "dummyMethodJustPrimitiveArgs";
    InstanceMethodCall instanceMethodCall =
        new InstanceMethodCall(DummyClassForTest.class, methodToCall)
            .withInstance(ObjectRef.randomRef())
            .withParameterTypes(new String[] {"java.lang.String", "int", "java.lang.Boolean"})
            .withArgs(new Object[] {"test", 123, Boolean.FALSE});
    ExecMessage execMessage = messageBuilder.buildInstanceMethod(peerUuid, instanceMethodCall);
    assertNotNull(execMessage);
    assertEquals((byte) ExecMessageType.INSTANCE_METHOD.ordinal(), execMessage.execMessageType);
    assertNotNull(execMessage.getInstanceMethodCall());
    assertEquals(peerUuid.toString(), execMessage.getPeerUuid());
    assertEquals(
        DummyClassForTest.class.getName(),
        execMessage.getInstanceMethodCall().getClazz().getName());
    assertEquals(methodToCall, execMessage.getInstanceMethodCall().getName());
    assertNull(execMessage.getInstanceMethodCall().getContext());
    assertEquals(3, execMessage.getInstanceMethodCall().getParameters().length);
    // compare parameters
    for (int i = 0; i < execMessage.getInstanceMethodCall().getParameters().length; i++) {
      Parameter parameter = execMessage.getInstanceMethodCall().getParameters()[i];
      assertEquals(instanceMethodCall.getParameterTypes()[i], parameter.getType().getName());
      assertEquals(instanceMethodCall.getArgs()[i], Unwrapper.unwrapObject(parameter.getValue()));
    }
  }
  // </editor-fold>

  // <editor-fold desc="Class method messages">
  @Test
  public void buildClassMethod_validParams_classMethodMessage() throws ClassNotFoundException {
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
    assertEquals((byte) ExecMessageType.CLASS_METHOD.ordinal(), execMessage.execMessageType);
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

  @Ignore(
      "TODO: enable and complete once we implement wrapping of Object arrays in the Wrapper class")
  @Test
  public void buildClassMethod_withContext_classMethodMessage() throws Exception {
    UUID peerUuid = UUID.randomUUID();
    Context context = createContextForClassMethod();
    Object sender = this;
    ObjectRef senderObjRef = ObjectRef.randomRef();
    Object[] args = new Object[] {new Object[] {4.5f, 54.2f}, new Object[] {"float1", "float2"}};
    ObjectRef[] argObjRefs = new ObjectRef[] {null, null};
    ExecMessage execMessage =
        messageBuilder.buildClassMethod(peerUuid, context, sender, senderObjRef, args, argObjRefs);

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
    StaticMethodCall staticMethodCall =
        new StaticMethodCall(Arrays.class, method)
            .withParameterTypes(new String[] {"[Z", "boolean"})
            .withArgs(new Object[] {new boolean[] {false, false, false}, true});

    ExecMessage execMessage =
        messageBuilder.buildClassMethod(peerUuid, sender, senderObjRef, staticMethodCall);

    // assert expected properties of ExecMessage
    assertNotNull(execMessage);
    assertEquals((byte) ExecMessageType.CLASS_METHOD.ordinal(), execMessage.execMessageType);
    assertNotNull(execMessage.getClassMethodCall());
    assertEquals(peerUuid.toString(), execMessage.getPeerUuid());
    assertEquals(Arrays.class.getName(), execMessage.getClassMethodCall().getClazz().getName());
    assertEquals(method, execMessage.getClassMethodCall().getName());
    assertNull(execMessage.getClassMethodCall().getContext());
    assertEquals(
        staticMethodCall.getParameterTypes().length,
        execMessage.getClassMethodCall().getParameters().length);
    // compare parameter types
    for (int i = 0; i < execMessage.getClassMethodCall().getParameters().length; i++) {
      Parameter parameter = execMessage.getClassMethodCall().getParameters()[i];
      assertEquals(staticMethodCall.getParameterTypes()[i], parameter.getType().getName());
    }
    // compare parameter values
    assertArrayEquals(
        (boolean[]) staticMethodCall.getArgs()[0],
        (boolean[])
            Unwrapper.unwrapObject(execMessage.getClassMethodCall().getParameters()[0].getValue()));
    assertEquals(
        (boolean) staticMethodCall.getArgs()[1],
        (boolean)
            Unwrapper.unwrapObject(execMessage.getClassMethodCall().getParameters()[1].getValue()));
  }
  // </editor-fold>

  // <editor-fold desc="Field Ops generic">
  @Test
  public void buildFieldOp_allFourOps_newFieldOpMessages() throws Exception {

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
                put("targetObjectRef", ObjectRef.randomRef());
                put("arg", "an argument");
                put("argObjRef", ObjectRef.randomRef());
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
                put("argObjRef", ObjectRef.randomRef());
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

      assertEquals(peerUuid.toString(), execMessage.getPeerUuid());
      assertEquals((byte) execMessageType.ordinal(), execMessage.execMessageType);
      assertNotNull(execMessage);
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

      // for non-static field ops, assert that the sender and target are in the message
      if (extractedFieldOpMessageInfo.targetObject != null) {
        assertEquals(
            targetClass.getName(), extractedFieldOpMessageInfo.targetObject.getClazz().getName());
      }
      if (extractedFieldOpMessageInfo.targetObjectRef != null) {
        assertEquals(targetObjRef, extractedFieldOpMessageInfo.targetObjectRef);
      }
    }
  }

  // </editor-fold>

}
