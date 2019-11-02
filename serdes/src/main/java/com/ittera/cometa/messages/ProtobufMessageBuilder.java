package com.ittera.cometa.messages;

import com.google.protobuf.Message.Builder;
import com.ittera.cometa.common.lang.Context;
import com.ittera.cometa.common.lang.ObjectRef;
import com.ittera.cometa.common.lang.reflect.*;
import com.ittera.cometa.messages.protobuf.*;
import com.ittera.cometa.messages.protobuf.Calls.*;
import com.ittera.cometa.messages.protobuf.Exec.ExecMessage;
import com.ittera.cometa.messages.protobuf.Exec.ExecMessageType;
import com.ittera.cometa.messages.protobuf.Fields.*;
import com.ittera.cometa.messages.protobuf.Headers.InternalHeader;
import com.ittera.cometa.messages.protobuf.Headers.InternalHeaderType;
import com.ittera.cometa.messages.protobuf.Intercepts.InterceptMessage;
import com.ittera.cometa.messages.protobuf.Intercepts.InterceptType;
import com.ittera.cometa.messages.protobuf.Values.*;
import com.ittera.cometa.messages.protobuf.Wrappers.Message;
import java.lang.reflect.*;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ProtobufMessageBuilder implements MessageBuilder {

  protected static final Logger logger = LoggerFactory.getLogger(ProtobufMessageBuilder.class);

  // ISO 8601 with millis (fraction-of-second) + TZ (no name, only offset)
  private final DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ");

  // <editor-fold desc="Thread-local sequence stamping methods">

  private final ThreadLocal<AtomicInteger> threadDispatchSequence =
      ThreadLocal.withInitial(() -> new AtomicInteger(1));

  private final ThreadLocal<AtomicInteger> threadBuilderSequence =
      ThreadLocal.withInitial(() -> new AtomicInteger(1));

  public ProtobufMessageBuilder() {}

  @Override
  public void resetThreadLocalSequence() {
    threadBuilderSequence.set(new AtomicInteger(1));
    threadDispatchSequence.get().getAndIncrement();
  }

  // </editor-fold>

  // <editor-fold desc="Private Auxiliary methods">

  private void addParameter(
      Builder callBuilder, String parameterType, Object arg, ObjectRef argObjRef) {

    // TODO : deal with objectRef
    Primitives.Parameter.Builder paramBuilder = Primitives.Parameter.newBuilder();
    paramBuilder.setType(getWrappedClass(parameterType));
    paramBuilder.setValue(getWrappedObject(arg, parameterType, argObjRef));

    if (callBuilder instanceof ConstructorCall.Builder) {
      ((ConstructorCall.Builder) callBuilder).addParameter(paramBuilder);
    } else if (callBuilder instanceof InstanceMethodCall.Builder) {
      ((InstanceMethodCall.Builder) callBuilder).addParameter(paramBuilder);
    } else if (callBuilder instanceof ClassMethodCall.Builder) {
      ((ClassMethodCall.Builder) callBuilder).addParameter(paramBuilder);
    } else {
      throw new UnsupportedOperationException(
          String.format("Unsupported Builder class: %s ", callBuilder.getClass().getName()));
    }
  }

  private void addParameters(
      Builder callBuilder, String[] parameterTypes, Object[] args, ObjectRef[] argObjRefs) {

    for (int i = 0; parameterTypes != null && i < parameterTypes.length; i++) {
      if (argObjRefs[i] != null) { // parameter is an objectref
        addParameter(callBuilder, parameterTypes[i], null, argObjRefs[i]);
      } else if (args[i] != null) { // parameter is string, primitive or wrapper
        addParameter(callBuilder, parameterTypes[i], args[i], null);
      } else { // parameter is null
        addParameter(callBuilder, parameterTypes[i], null, null);
      }
    }
  }

  private void addNamedParameter(
      Builder callBuilder,
      Parameter parameter,
      String paramName,
      String paramType,
      Object param,
      ObjectRef paramObjRef) {

    // TODO : deal with objectRef
    Primitives.Parameter.Builder paramBuilder = Primitives.Parameter.newBuilder();
    paramBuilder.setName(paramName == null ? parameter.getName() : paramName);
    paramBuilder.setType(
        getWrappedClass(paramType == null ? parameter.getType().getName() : paramType));
    paramBuilder.setIsVarArgs(parameter.isVarArgs());
    paramBuilder.setValue(getWrappedObject(param, paramType, paramObjRef));

    if (callBuilder instanceof ConstructorCall.Builder) {
      ((ConstructorCall.Builder) callBuilder).addParameter(paramBuilder);
    } else if (callBuilder instanceof InstanceMethodCall.Builder) {
      ((InstanceMethodCall.Builder) callBuilder).addParameter(paramBuilder);
    } else if (callBuilder instanceof ClassMethodCall.Builder) {
      ((ClassMethodCall.Builder) callBuilder).addParameter(paramBuilder);
    } else {
      throw new UnsupportedOperationException(
          String.format("Unsupported Builder class: %s ", callBuilder.getClass().getName()));
    }
  }

  private void addParameters(
      Builder callBuilder, Context context, Object[] args, ObjectRef[] argObjRefs) {
    final CodeSignature codeSignature = (CodeSignature) context.getSignature();
    Parameter[] parameters = codeSignature.getParameters();
    String paramName, paramTypeName;
    for (int i = 0; codeSignature.getParameterTypes() != null && i < args.length; i++) {
      paramName = codeSignature.getParameterNames()[i];
      paramTypeName = codeSignature.getParameterTypes()[i].getName();
      addNamedParameter(
          callBuilder, parameters[i], paramName, paramTypeName, args[i], argObjRefs[i]);
    }
  }

  private ExecMessage.Builder newWrapperBuilder(
      ExecMessageType msgType, UUID peerUuid, String followingUuid) {

    ExecMessage.Builder msgBuilder =
        ExecMessage.newBuilder()
            .setPeerUuid(peerUuid.toString())
            .setMessageUuid(UUID.randomUUID().toString())
            .setMsgType(msgType)
            .setThreadName(Thread.currentThread().getName())
            .setDispatchSeq(threadDispatchSequence.get().intValue())
            .setBuilderSeq(threadBuilderSequence.get().getAndIncrement())
            .setCurrentTime(dtf.format(ZonedDateTime.now()));

    if (followingUuid != null && !followingUuid.isEmpty()) {
      msgBuilder.setFollowingUuid(followingUuid);
    }

    return msgBuilder;
  }

  private ExecMessage.Builder newWrapperBuilder(ExecMessageType msgType, UUID peerUuid) {
    return newWrapperBuilder(msgType, peerUuid, null);
  }

  /** Methods delegating to Wrapper */
  private <T> Primitives.Object getWrappedObject(Object object, T t, ObjectRef objectRef) {
    return Wrapper.getWrappedObject(object, t, objectRef);
  }

  private Ctxt.Context getWrappedContext(Context context, Object sender, ObjectRef senderObjRef) {
    return Wrapper.getWrappedContext(context, sender, senderObjRef);
  }

  private Primitives.Field getWrappedField(Field field) {
    return Wrapper.getWrappedField(field);
  }

  private Primitives.Field getWrappedField(Class clazz, String fieldName) {
    return Wrapper.getWrappedField(clazz, fieldName);
  }

  private Primitives.Field getWrappedField(String className, String fieldName) {
    return Wrapper.getWrappedField(className, fieldName);
  }

  private Primitives.Class getWrappedClass(Class clazz) {
    return Wrapper.getWrappedClass(clazz);
  }

  private Primitives.Class getWrappedClass(String className) {
    return Wrapper.getWrappedClass(className);
  }

  // </editor-fold>

  // <editor-fold desc="Header messages">
  private InternalHeader.Builder buildInternalHeaderMessage(InternalHeaderType headerType) {
    return InternalHeader.newBuilder().setHeaderType(headerType);
  }

  @Override
  public InternalHeader buildWriteAheadHeader(UUID peerUuid) {
    return buildInternalHeaderMessage(InternalHeaderType.WRITE_AHEAD)
        .setValue(peerUuid.toString())
        .build();
  }

  @Override
  public InternalHeader buildIncomingInterceptRequestHeader() {
    return buildInternalHeaderMessage(InternalHeaderType.INCOMING_INTERCEPT_REQ).build();
  }

  // </editor-fold>

  // <editor-fold desc="Constructor messages">

  private ExecMessage buildConstructorMessage(
      UUID peerUuid,
      String className,
      Context context,
      Object sender,
      ObjectRef senderObjRef,
      String[] parameterTypes,
      Object[] args,
      ObjectRef[] argObjRefs) {

    final ConstructorCall.Builder constructorCallBuilder = ConstructorCall.newBuilder();
    if (context != null) {
      final ConstructorSignature codeSignature = (ConstructorSignature) context.getSignature();
      addParameters(constructorCallBuilder, context, args, argObjRefs);
      constructorCallBuilder.setModifiers(codeSignature.getModifiers());
      constructorCallBuilder.setContext(getWrappedContext(context, sender, senderObjRef));
      constructorCallBuilder.setClass_(getWrappedClass(codeSignature.getDeclaringTypeName()));
    } else {
      addParameters(constructorCallBuilder, parameterTypes, args, argObjRefs);
      constructorCallBuilder.setClass_(getWrappedClass(className));
    }

    final ExecMessage.Builder msgBuilder =
        newWrapperBuilder(ExecMessageType.CONSTRUCTOR, peerUuid)
            .setConstructorCall(constructorCallBuilder);

    return msgBuilder.build();
  }

  @Override
  public ExecMessage buildEmptyConstructor(UUID peerUuid, String className) {

    return buildConstructorMessage(peerUuid, className, null, null, null, null, null, null);
  }

  /**
   * Args must be set either in args or argObjRefs. If null in both, value is assumed to be null.
   *
   * @param peerUuid
   * @param className
   * @param parameterTypes
   * @param args Should be of same length as parameterTypes. For Strings, primitives and wrappers.
   * @param argObjRefs Should be of same length as parameterTypes. For objectrefs.
   * @return
   */
  @Override
  public ExecMessage buildNonEmptyConstructor(
      UUID peerUuid,
      String className,
      String[] parameterTypes,
      Object[] args,
      ObjectRef[] argObjRefs) {

    return buildConstructorMessage(
        peerUuid, className, null, null, null, parameterTypes, args, argObjRefs);
  }

  @Override
  public ExecMessage buildConstructor(
      UUID peerUuid,
      Context context,
      Object sender,
      ObjectRef senderObjRef,
      Object[] args,
      ObjectRef[] argObjRefs) {

    return buildConstructorMessage(
        peerUuid, null, context, sender, senderObjRef, null, args, argObjRefs);
  }
  // </editor-fold>

  // <editor-fold desc="Instance method messages">

  @Override
  public ExecMessage buildInstanceMethod(
      UUID peerUuid,
      String className,
      String methodName,
      Object target,
      ObjectRef targetObjRef,
      String[] parameterTypes,
      Object[] args,
      ObjectRef[] argObjRefs) {

    final InstanceMethodCall.Builder callBuilder = InstanceMethodCall.newBuilder();
    addParameters(callBuilder, parameterTypes, args, argObjRefs);

    final ExecMessage.Builder msgBuilder =
        newWrapperBuilder(ExecMessageType.INSTANCE_METHOD, peerUuid)
            .setInstanceMethodCall(
                callBuilder
                    .setClass_(getWrappedClass(className))
                    .setName(methodName)
                    .setObjectRef(targetObjRef.getRef()));

    return msgBuilder.build();
  }

  @Override
  public ExecMessage buildInstanceMethod(
      UUID peerUuid,
      Context context,
      Object sender,
      ObjectRef senderObjRef,
      Object target,
      ObjectRef targetObjRef,
      Object[] args,
      ObjectRef[] argObjRefs) {

    final MethodSignature codeSignature = (MethodSignature) context.getSignature();

    final InstanceMethodCall.Builder callBuilder = InstanceMethodCall.newBuilder();
    addParameters(callBuilder, context, args, argObjRefs);

    final ExecMessage.Builder msgBuilder =
        newWrapperBuilder(ExecMessageType.INSTANCE_METHOD, peerUuid)
            .setInstanceMethodCall(
                callBuilder
                    .setClass_(getWrappedClass(codeSignature.getDeclaringTypeName()))
                    .setName(codeSignature.getName())
                    .setObject(
                        getWrappedObject(
                            target, codeSignature.getDeclaringTypeName(), targetObjRef))
                    .setModifiers(codeSignature.getModifiers())
                    .setContext(getWrappedContext(context, sender, senderObjRef)));

    return msgBuilder.build();
  }
  // </editor-fold>

  // <editor-fold desc="Class method messages">

  @Override
  public ExecMessage buildClassMethod(
      UUID peerUuid,
      String className,
      String methodName,
      String[] parameterTypes,
      Object sender,
      ObjectRef senderObjRef,
      Object[] args,
      ObjectRef[] argObjRefs) {

    final ClassMethodCall.Builder callBuilder = ClassMethodCall.newBuilder();
    addParameters(callBuilder, parameterTypes, args, argObjRefs);

    final ExecMessage.Builder msgBuilder =
        newWrapperBuilder(ExecMessageType.CLASS_METHOD, peerUuid)
            .setClassMethodCall(
                callBuilder.setClass_(getWrappedClass(className)).setName(methodName));

    return msgBuilder.build();
  }

  @Override
  public ExecMessage buildClassMethod(
      UUID peerUuid,
      Context context,
      Object sender,
      ObjectRef senderObjRef,
      Object[] args,
      ObjectRef[] argObjRefs) {

    final MethodSignature codeSignature = (MethodSignature) context.getSignature();

    final ClassMethodCall.Builder callBuilder = ClassMethodCall.newBuilder();
    addParameters(callBuilder, context, args, argObjRefs);

    final ExecMessage.Builder msgBuilder =
        newWrapperBuilder(ExecMessageType.CLASS_METHOD, peerUuid)
            .setClassMethodCall(
                callBuilder
                    .setContext(getWrappedContext(context, sender, senderObjRef))
                    .setClass_(getWrappedClass(codeSignature.getDeclaringTypeName()))
                    .setName(codeSignature.getName())
                    .setModifiers(codeSignature.getModifiers()));

    return msgBuilder.build();
  }

  private void setParameters(
      ClassMethodCall.Builder callBuilder, List<Primitives.Parameter> parameters) {
    for (Primitives.Parameter param : parameters) {
      callBuilder.addParameter(param);
    }
  }

  // build ClassMethodCall with another message's parameter list
  private ExecMessage buildClassMethodWithMessageParameters(
      UUID peerUuid, String className, String methodName, ExecMessage otherMessage) {
    final ClassMethodCall.Builder callBuilder = ClassMethodCall.newBuilder();
    final String fieldParamType;
    switch (otherMessage.getMsgType()) {
      case CONSTRUCTOR:
        setParameters(callBuilder, otherMessage.getConstructorCall().getParameterList());
        break;
      case INSTANCE_METHOD:
        setParameters(callBuilder, otherMessage.getInstanceMethodCall().getParameterList());
        break;
      case CLASS_METHOD:
        setParameters(callBuilder, otherMessage.getClassMethodCall().getParameterList());
        break;
      case PUT_STATIC:
        // Q: is this right? Or should we use valueObject.class.name if present?
        fieldParamType = otherMessage.getStaticFieldPut().getField().getClass_().getName();
        Primitives.Object object = otherMessage.getStaticFieldPut().getValueObject();
        String objectRef = otherMessage.getStaticFieldPut().getValueObjectRef();
        addParameter(callBuilder, fieldParamType, object, ObjectRef.from(objectRef));
        break;
      case PUT_FIELD:
        fieldParamType = otherMessage.getInstanceFieldPut().getField().getClass_().getName();
        object = otherMessage.getInstanceFieldPut().getValueObject();
        objectRef = otherMessage.getInstanceFieldPut().getValueObjectRef();
        addParameter(callBuilder, fieldParamType, object, ObjectRef.from(objectRef));
        break;
      case GET_STATIC:
        fieldParamType = otherMessage.getStaticFieldGet().getField().getClass_().getName();
        addParameter(callBuilder, fieldParamType, null, null);
        break;
      case GET_FIELD:
        fieldParamType = otherMessage.getInstanceFieldGet().getField().getClass_().getName();
        addParameter(callBuilder, fieldParamType, null, null);
        break;
      default:
        logger.error("Unsupported msg type: {}", otherMessage.getMsgType());
    }

    final ExecMessage.Builder msgBuilder =
        newWrapperBuilder(ExecMessageType.CLASS_METHOD, peerUuid)
            .setClassMethodCall(
                callBuilder.setClass_(getWrappedClass(className)).setName(methodName));

    return msgBuilder.build();
  }

  // </editor-fold>

  // <editor-fold desc="Field Ops generic">
  @Override
  public ExecMessage buildFieldOp(
      UUID peerUuid,
      Context context,
      ExecMessageType type,
      Object sender,
      ObjectRef senderObjRef,
      Object target,
      ObjectRef targetObjRef,
      Object arg,
      ObjectRef argObjRef) {

    final FieldSignature fieldSignature = (FieldSignature) context.getSignature();

    Primitives.Class clazz = getWrappedClass(fieldSignature.getDeclaringType());
    Primitives.Object targetObj =
        getWrappedObject(target, fieldSignature.getDeclaringType(), targetObjRef);
    Primitives.Field field =
        getWrappedField(fieldSignature.getFieldType(), fieldSignature.getName());
    int modifiers = fieldSignature.getModifiers();
    Ctxt.Context ctxt = getWrappedContext(context, sender, senderObjRef);

    final ExecMessage.Builder msgBuilder = newWrapperBuilder(type, peerUuid);
    switch (type) {
      case GET_FIELD:
        msgBuilder.setInstanceFieldGet(
            InstanceFieldGet.newBuilder()
                .setClass_(clazz)
                .setObject(targetObj)
                .setField(field)
                .setModifiers(modifiers)
                .setContext(ctxt));
        break;
      case PUT_FIELD:
        msgBuilder.setInstanceFieldPut(
            InstanceFieldPut.newBuilder()
                .setClass_(clazz)
                .setObject(targetObj)
                .setField(field)
                .setValueObject(
                    getWrappedObject(arg, fieldSignature.getFieldType().getName(), argObjRef))
                .setModifiers(modifiers)
                .setContext(ctxt));
        break;
      case GET_STATIC:
        msgBuilder.setStaticFieldGet(
            StaticFieldGet.newBuilder()
                .setClass_(clazz)
                .setField(field)
                .setModifiers(modifiers)
                .setContext(ctxt));
        break;
      case PUT_STATIC:
        msgBuilder.setStaticFieldPut(
            StaticFieldPut.newBuilder()
                .setClass_(clazz)
                .setValueObject(getWrappedObject(arg, fieldSignature.getFieldType(), argObjRef))
                .setField(field)
                .setModifiers(modifiers)
                .setContext(ctxt));
        break;
      default:
        throw new IllegalArgumentException("Unexpected field op type: " + type);
    }

    return msgBuilder.build();
  }

  @Override
  public ExecMessage buildFieldOpDone(
      UUID peerUuid, AccessibleObject accessibleObject, Context context, ExecMessageType type) {

    final FieldSignature fieldSignature = (FieldSignature) context.getSignature();

    final ExecMessage.Builder msgBuilder = newWrapperBuilder(type, peerUuid);
    switch (type) {
      case PUT_FIELD_DONE:
        msgBuilder.setInstanceFieldPutDone(
            InstanceFieldPutDone.newBuilder()
                .setClass_(getWrappedClass(fieldSignature.getDeclaringType()))
                .setField(getWrappedField((Field) accessibleObject)));
        break;
      case PUT_STATIC_DONE:
        msgBuilder.setStaticFieldPutDone(
            StaticFieldPutDone.newBuilder()
                .setClass_(getWrappedClass(fieldSignature.getDeclaringType()))
                .setField(getWrappedField((Field) accessibleObject)));
        break;
      default:
        throw new IllegalArgumentException("Unexpected field op done type: " + type);
    }

    return msgBuilder.build();
  }
  // </editor-fold>

  // <editor-fold desc="Static field get messages">

  @Override
  public ExecMessage buildGetStatic(UUID peerUuid, String className, String fieldName) {

    final ExecMessage.Builder msgBuilder =
        newWrapperBuilder(ExecMessageType.GET_STATIC, peerUuid)
            .setStaticFieldGet(
                StaticFieldGet.newBuilder()
                    .setClass_(getWrappedClass(className))
                    .setField(getWrappedField(className, fieldName)));

    return msgBuilder.build();
  }

  // </editor-fold>

  // <editor-fold desc="Instance field get messages">

  @Override
  public ExecMessage buildGetObject(
      UUID peerUuid, String className, String fieldName, ObjectRef targetObjRef) {

    final ExecMessage.Builder msgBuilder =
        newWrapperBuilder(ExecMessageType.GET_FIELD, peerUuid)
            .setInstanceFieldGet(
                InstanceFieldGet.newBuilder()
                    .setClass_(getWrappedClass(className))
                    .setObjectRef(targetObjRef.getRef())
                    .setField(getWrappedField((String) null, fieldName)));

    return msgBuilder.build();
  }

  // </editor-fold>

  // <editor-fold desc="Static field put messages">

  @Override
  public ExecMessage buildPutStatic(
      UUID peerUuid, String className, String fieldName, String valueClassName, Object value) {

    final ExecMessage.Builder msgBuilder =
        newWrapperBuilder(ExecMessageType.PUT_STATIC, peerUuid)
            .setStaticFieldPut(
                StaticFieldPut.newBuilder()
                    .setClass_(getWrappedClass(className))
                    .setField(getWrappedField((String) null, fieldName))
                    .setValueObject(getWrappedObject(value, valueClassName, null)));

    return msgBuilder.build();
  }

  @Override
  public ExecMessage buildPutStatic(
      UUID peerUuid, String className, String fieldName, ObjectRef valueObjectRef) {

    final ExecMessage.Builder msgBuilder =
        newWrapperBuilder(ExecMessageType.PUT_STATIC, peerUuid)
            .setStaticFieldPut(
                StaticFieldPut.newBuilder()
                    .setClass_(getWrappedClass(className))
                    .setField(getWrappedField((String) null, fieldName))
                    .setValueObjectRef(valueObjectRef.getRef()));

    return msgBuilder.build();
  }

  @Override
  public ExecMessage buildPutStaticDone(
      UUID peerUuid,
      AccessibleObject accessibleObject,
      String staticFieldPutUuid,
      String followingUuid) {

    final StaticFieldPutDone.Builder fieldBuilder = StaticFieldPutDone.newBuilder();
    fieldBuilder
        .setField(getWrappedField((Field) accessibleObject))
        .setClass_(getWrappedClass(((Field) accessibleObject).getDeclaringClass()))
        .setStaticFieldPutUuid(staticFieldPutUuid);

    final ExecMessage.Builder msgBuilder =
        newWrapperBuilder(ExecMessageType.PUT_STATIC_DONE, peerUuid, followingUuid)
            .setStaticFieldPutDone(fieldBuilder);
    return msgBuilder.build();
  }

  // </editor-fold>

  // <editor-fold desc="Instance field put messages">

  @Override
  public ExecMessage buildPutObject(
      UUID peerUuid,
      String className,
      String fieldName,
      ObjectRef targetObjRef,
      String valueClassName,
      Object value) {

    final ExecMessage.Builder msgBuilder =
        newWrapperBuilder(ExecMessageType.PUT_FIELD, peerUuid, null)
            .setInstanceFieldPut(
                InstanceFieldPut.newBuilder()
                    .setClass_(getWrappedClass(className))
                    .setObjectRef(targetObjRef.getRef())
                    .setField(getWrappedField((String) null, fieldName))
                    .setValueObject(getWrappedObject(value, valueClassName, null)));

    return msgBuilder.build();
  }

  @Override
  public ExecMessage buildPutObject(
      UUID peerUuid,
      String className,
      String fieldName,
      ObjectRef targetObjRef,
      ObjectRef valueObjectRef) {

    final ExecMessage.Builder msgBuilder =
        newWrapperBuilder(ExecMessageType.PUT_FIELD, peerUuid, null)
            .setInstanceFieldPut(
                InstanceFieldPut.newBuilder()
                    .setClass_(getWrappedClass(className))
                    .setObjectRef(targetObjRef.getRef())
                    .setField(getWrappedField((String) null, fieldName))
                    .setValueObjectRef(valueObjectRef.getRef()));

    return msgBuilder.build();
  }

  @Override
  public ExecMessage buildPutObjectDone(
      UUID peerUuid,
      AccessibleObject accessibleObject,
      String instanceFieldPutUuid,
      String followingUuid) {

    final InstanceFieldPutDone.Builder fieldBuilder = InstanceFieldPutDone.newBuilder();
    fieldBuilder
        .setField(getWrappedField((Field) accessibleObject))
        .setClass_(getWrappedClass(((Field) accessibleObject).getDeclaringClass()))
        .setInstanceFieldPutUuid(instanceFieldPutUuid);

    final ExecMessage.Builder msgBuilder =
        newWrapperBuilder(ExecMessageType.PUT_FIELD_DONE, peerUuid, followingUuid)
            .setInstanceFieldPutDone(fieldBuilder);

    return msgBuilder.build();
  }

  // </editor-fold>

  // <editor-fold desc="Throwable messages">
  @Override
  public ExecMessage buildAccessibleObjectThrowable(
      UUID peerUuid,
      Optional<AccessibleObject> accessibleObject,
      ExecutableObjectType executableObjectType,
      Throwable exception,
      String followingUuid) {

    final Exceptions.RaisedThrowable.Builder thrBuilder = Exceptions.RaisedThrowable.newBuilder();
    if (accessibleObject.isPresent()) {
      if (accessibleObject.get() instanceof Constructor) {
        thrBuilder.setConstructor(
            ((Constructor) accessibleObject.get()).getDeclaringClass().getName());
        thrBuilder.setModifiers(((Constructor) accessibleObject.get()).getModifiers());
      } else if (accessibleObject.get() instanceof Method) {
        thrBuilder.setMethod(((Method) accessibleObject.get()).getName());
        thrBuilder.setModifiers(((Method) accessibleObject.get()).getModifiers());
      } else if (accessibleObject.get() instanceof Field) {
        thrBuilder.setField(((Field) accessibleObject.get()).getName());
        thrBuilder.setModifiers(((Field) accessibleObject.get()).getModifiers());
      } else {
        throw new UnsupportedOperationException(
            String.format(
                "Unsupported accessibleObject type: %s", accessibleObject.getClass().getName()));
      }
    } else {
      switch (executableObjectType) {
        case CONSTRUCTOR:
          thrBuilder.setConstructor("<info not available>");
          break;
        case METHOD:
          thrBuilder.setMethod("<info not available>");
          break;
        case FIELD:
          thrBuilder.setField("<info not available>");
          break;
      }
    }

    final ExecMessage.Builder msgBuilder =
        newWrapperBuilder(ExecMessageType.THROWABLE, peerUuid, followingUuid)
            .setRaisedThrowable(
                thrBuilder
                    .setClass_(getWrappedClass(exception.getClass().getName()))
                    .setThrowable(buildThrowableMessage(exception)));

    return msgBuilder.build();
  }

  private Exceptions.Throwable.Builder buildThrowableMessage(Throwable throwable) {

    final Exceptions.Throwable.Builder msgBuilder = Exceptions.Throwable.newBuilder();
    // type
    msgBuilder.setType(throwable.getClass().getName());
    // message
    if (throwable.getMessage() != null) {
      msgBuilder.setMessage(throwable.getMessage());
    }
    // stack trace
    StackTraceElement[] stackTrace = throwable.getStackTrace();
    if (stackTrace != null) {
      for (StackTraceElement ste : stackTrace) {
        msgBuilder.addStackTraceElement(ste.toString());
      }
    }
    // fill in cause(s) -- recursive
    if (throwable.getCause() != null) {
      msgBuilder.setCause(buildThrowableMessage(throwable.getCause()));
    }

    return msgBuilder;
  }

  // </editor-fold>

  // <editor-fold desc="Return value messages">
  @Override
  public ExecMessage buildReturnValue(
      UUID peerUuid,
      Object object,
      AccessibleObject accessibleObject,
      ObjectRef objectRef,
      boolean isVoid,
      String followingUuid) {

    final ReturnValue.Builder valueBuilder = Values.ReturnValue.newBuilder();

    Class declaringClass = ((Member) accessibleObject).getDeclaringClass();

    // set 'object'
    if (!isVoid) {
      Class objectClass;
      if (accessibleObject instanceof Constructor) {
        objectClass = declaringClass;
      } else if (accessibleObject instanceof Method) {
        objectClass = ((Method) accessibleObject).getReturnType();
      } else if (accessibleObject instanceof Field) {
        objectClass = ((Field) accessibleObject).getType();
      } else {
        throw new RuntimeException(
            String.format("Unable to handle accessible object of type: %s", accessibleObject));
      }
      valueBuilder.setObject(getWrappedObject(object, objectClass, objectRef));
    }

    // set 'from'
    if (accessibleObject instanceof Constructor) {
      valueBuilder.setFrom(
          Primitives.Reflectable.newBuilder()
              .setConstructor(
                  Primitives.Constructor.newBuilder()
                      .setRepr(((Executable) accessibleObject).toGenericString())
                      .build()));
    } else if (accessibleObject instanceof Method) {
      valueBuilder.setFrom(
          Primitives.Reflectable.newBuilder()
              .setMethod(
                  Primitives.Method.newBuilder()
                      .setRepr(((Executable) accessibleObject).toGenericString())
                      .build()));
    } else if (accessibleObject instanceof Field) {
      valueBuilder.setFrom(
          Primitives.Reflectable.newBuilder()
              .setField(
                  Primitives.Field.newBuilder()
                      .setName(((Field) accessibleObject).getName())
                      .setRepr(((Field) accessibleObject).toGenericString())
                      .build()));
    } else {
      throw new RuntimeException(
          String.format("Unable to handle accessible object of type: %s", accessibleObject));
    }

    // set 'class'
    final ExecMessage.Builder msgBuilder =
        newWrapperBuilder(ExecMessageType.RETURN_VALUE, peerUuid, followingUuid)
            .setReturnValue(
                valueBuilder.setIsVoid(isVoid).setClazz(getWrappedClass(declaringClass)));

    return msgBuilder.build();
  }
  // </editor-fold>

  // <editor-fold desc="Intercept messages">
  @Override
  public InterceptMessage buildInterceptMessage(
      UUID peerUuid,
      InterceptType type,
      String className,
      String methodName,
      List<String> parameterTypes,
      String callbackClassName,
      String callbackMethodName) {
    final Intercepts.InterceptMessage.Builder msgBuilder =
        InterceptMessage.newBuilder()
            .setPeerUuid(peerUuid.toString())
            .setType(type)
            .setMessageUuid(UUID.randomUUID().toString())
            .setClazz(className)
            .setMethod(
                Intercepts.InterceptableMethod.newBuilder()
                    .setName(methodName)
                    .addAllParameterType(parameterTypes)
                    .build())
            .setCallbackClass(callbackClassName)
            .setCallbackMethod(callbackMethodName);

    return msgBuilder.build();
  }

  @Override
  public Intercepts.InterceptMessage buildInterceptMessage(
      UUID peerUuid,
      InterceptType type,
      String className,
      String fieldName,
      Intercepts.FieldOpType fieldOpType,
      String callbackClassName,
      String callbackMethodName) {
    final InterceptMessage.Builder msgBuilder =
        Intercepts.InterceptMessage.newBuilder()
            .setPeerUuid(peerUuid.toString())
            .setType(type)
            .setMessageUuid(UUID.randomUUID().toString())
            .setClazz(className)
            .setField(
                Intercepts.InterceptableField.newBuilder()
                    .setName(fieldName)
                    .setType(fieldOpType)
                    .build())
            .setCallbackClass(callbackClassName)
            .setCallbackMethod(callbackMethodName);

    return msgBuilder.build();
  }

  @Override
  public ExecMessage buildCallbackForInterceptRequest(
      UUID peerUuid, ExecMessage interceptedMessage, Intercepts.InterceptMessage interceptMessage) {

    return buildClassMethodWithMessageParameters(
        peerUuid,
        interceptMessage.getCallbackClass(),
        interceptMessage.getCallbackMethod(),
        interceptedMessage);
  }
  // </editor-fold>

  // <editor-fold desc="Message Wrapper">
  @Override
  public Message wrap(ExecMessage execMessage) {
    return Message.newBuilder().setExecMessage(execMessage).build();
  }

  @Override
  public Message wrap(InterceptMessage interceptMessage) {
    return Message.newBuilder().setInterceptMessage(interceptMessage).build();
  }
  // </editor-fold>
}
