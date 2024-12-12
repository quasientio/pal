/*
   Copyright (c) 2020 Contributors listed in the AUTHORS file

   This file is part of PAL, the friendly java runtime.

   PAL is free software: you can redistribute it and/or modify
   it under the terms of the GNU General Public License as published by
   the Free Software Foundation, either version 3 of the License, or
   (at your option) any later version.

   PAL is distributed in the hope that it will be useful,
   but WITHOUT ANY WARRANTY; without even the implied warranty of
   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
   GNU General Public License for more details.

   You should have received a copy of the GNU General Public License
   along with this program. If not, see <http://www.gnu.org/licenses/>.
*/

package net.ittera.pal.serdes.colfer;

import static net.ittera.pal.serdes.colfer.ExecMessageUtils.getClassname;
import static net.ittera.pal.serdes.colfer.ExecMessageUtils.getExecutableName;
import static net.ittera.pal.serdes.colfer.ExecMessageUtils.getParameterTypes;
import static net.ittera.pal.serdes.colfer.Wrapper.getWrappedClass;
import static net.ittera.pal.serdes.colfer.Wrapper.getWrappedContext;
import static net.ittera.pal.serdes.colfer.Wrapper.getWrappedField;
import static net.ittera.pal.serdes.colfer.Wrapper.getWrappedObject;
import static net.ittera.pal.serdes.jsonrpc.JsonRpcMessageUtils.isMethodNotFoundError;

import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import javax.annotation.Nullable;
import net.ittera.pal.common.directory.nodes.InterceptRequest;
import net.ittera.pal.common.lang.FieldOpType;
import net.ittera.pal.common.lang.intercept.InterceptType;
import net.ittera.pal.common.lang.intercept.Interceptable.InterceptableType;
import net.ittera.pal.common.lang.intercept.InterceptableFieldOp;
import net.ittera.pal.common.lang.intercept.InterceptableMethodCall;
import net.ittera.pal.common.lang.reflect.CodeSignature;
import net.ittera.pal.common.lang.reflect.ConstructorSignature;
import net.ittera.pal.common.lang.reflect.FieldSignature;
import net.ittera.pal.common.lang.reflect.MethodSignature;
import net.ittera.pal.common.objects.ObjectRef;
import net.ittera.pal.common.runtime.Context;
import net.ittera.pal.messages.colfer.ClassMethodCall;
import net.ittera.pal.messages.colfer.ConstructorCall;
import net.ittera.pal.messages.colfer.ControlMessage;
import net.ittera.pal.messages.colfer.ExecMessage;
import net.ittera.pal.messages.colfer.InstanceFieldGet;
import net.ittera.pal.messages.colfer.InstanceFieldPut;
import net.ittera.pal.messages.colfer.InstanceFieldPutDone;
import net.ittera.pal.messages.colfer.InstanceMethodCall;
import net.ittera.pal.messages.colfer.InterceptKeyMessage;
import net.ittera.pal.messages.colfer.InterceptMessage;
import net.ittera.pal.messages.colfer.InterceptReply;
import net.ittera.pal.messages.colfer.InternalHeader;
import net.ittera.pal.messages.colfer.Message;
import net.ittera.pal.messages.colfer.Obj;
import net.ittera.pal.messages.colfer.Parameter;
import net.ittera.pal.messages.colfer.RaisedThrowable;
import net.ittera.pal.messages.colfer.Reflectable;
import net.ittera.pal.messages.colfer.ReturnValue;
import net.ittera.pal.messages.colfer.StaticFieldGet;
import net.ittera.pal.messages.colfer.StaticFieldPut;
import net.ittera.pal.messages.colfer.StaticFieldPutDone;
import net.ittera.pal.messages.jsonrpc.Argument;
import net.ittera.pal.messages.jsonrpc.JsonRpcError;
import net.ittera.pal.messages.jsonrpc.JsonRpcErrorData;
import net.ittera.pal.messages.jsonrpc.JsonRpcRequest;
import net.ittera.pal.messages.jsonrpc.JsonRpcResponse;
import net.ittera.pal.messages.jsonrpc.JsonRpcResponseReturnValue;
import net.ittera.pal.messages.types.ControlCommandType;
import net.ittera.pal.messages.types.ControlStatusType;
import net.ittera.pal.messages.types.ExecMessageType;
import net.ittera.pal.messages.types.InternalHeaderType;
import net.ittera.pal.messages.types.JsonRpcErrorCode;
import net.ittera.pal.messages.types.MessageType;
import net.ittera.pal.serdes.ConversionUtils;
import net.ittera.pal.serdes.jsonrpc.InvalidJsonRpcParamsException;
import net.ittera.pal.serdes.jsonrpc.InvalidJsonRpcRequestException;
import net.ittera.pal.serdes.jsonrpc.JsonRpcMessageUtils;
import net.ittera.pal.serdes.jsonrpc.JsonRpcParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class MessageBuilder {

  private static final Logger logger = LoggerFactory.getLogger(MessageBuilder.class);

  // ISO 8601 with millis (fraction-of-second) + TZ (no name, only offset)
  private final DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSZ");

  private final ThreadLocal<AtomicInteger> threadDispatchSequence =
      ThreadLocal.withInitial(() -> new AtomicInteger(1));

  private final ThreadLocal<AtomicInteger> threadBuilderSequence =
      ThreadLocal.withInitial(() -> new AtomicInteger(1));

  private boolean includeSourceContext;

  public MessageBuilder() {}

  @Inject
  public MessageBuilder(@Named("messages.with_src_context") String includeSourceContextStr) {
    this.includeSourceContext = Boolean.parseBoolean(includeSourceContextStr);
  }

  // <editor-fold desc="Thread-local sequence stamping methods">
  public void resetThreadLocalSequence() {
    threadBuilderSequence.set(new AtomicInteger(1));
    threadDispatchSequence.get().getAndIncrement();
  }

  // </editor-fold>

  // <editor-fold desc="Private Auxiliary methods">
  private Parameter createParameter(String parameterType, Object arg, ObjectRef argObjRef) {
    Object argValue = arg instanceof Obj ? ((Obj) arg).getValue() : arg;
    return new Parameter().withValue(getWrappedObject(argValue, parameterType, argObjRef));
  }

  private Parameter createNamedParameter(
      java.lang.reflect.Parameter parameter,
      String paramName,
      String paramType,
      Object param,
      ObjectRef paramObjRef) {

    return new Parameter()
        .withName(paramName == null ? parameter.getName() : paramName)
        .withValue(getWrappedObject(param, paramType, paramObjRef));
  }

  private Parameter[] createNamedParameters(
      Context context, Object[] args, ObjectRef[] argObjRefs) {
    final CodeSignature codeSignature = (CodeSignature) context.getSignature();
    final int paramsLen = codeSignature.getParameterTypes() == null ? 0 : args.length;
    final Parameter[] params = new Parameter[paramsLen];
    String paramName;
    String paramTypeName;
    for (int i = 0; i < paramsLen; i++) {
      paramName = codeSignature.getParameterNames()[i];
      paramTypeName = codeSignature.getParameterTypes()[i].getName();
      params[i] =
          createNamedParameter(
              codeSignature.getParameters()[i], paramName, paramTypeName, args[i], argObjRefs[i]);
    }
    return params;
  }

  // TODO we should be calling createNamedParameter instead of createParameter
  private Parameter[] createNamedParameters(
      String[] parameterTypes, Object[] args, ObjectRef[] argObjRefs) {
    final int paramsTypesLength = parameterTypes == null ? 0 : parameterTypes.length;
    final int argsLength = args == null ? 0 : args.length;
    final int argsObjRefsLength = argObjRefs == null ? 0 : argObjRefs.length;
    if (paramsTypesLength < argsLength || paramsTypesLength < argsObjRefsLength) {
      throw new IllegalArgumentException(
          "parameterTypes must be of same length as args and argObjRefs");
    }
    final Parameter[] params = new Parameter[paramsTypesLength];
    for (int i = 0; i < paramsTypesLength; i++) {
      assert argObjRefs != null;
      assert args != null;
      if (argObjRefs[i] != null) { // parameter is an objectref
        params[i] = createParameter(parameterTypes[i], null, argObjRefs[i]);
      } else if (args[i] != null) { // parameter is string, primitive or wrapper
        params[i] = createParameter(parameterTypes[i], args[i], null);
      } else { // parameter is null
        params[i] = createParameter(parameterTypes[i], null, null);
      }
    }

    return params;
  }

  private ExecMessage newWrapper(ExecMessageType msgType, UUID peerUuid, String responseToId) {
    ExecMessage msgWrapper =
        new ExecMessage()
            .withPeerUuid(peerUuid.toString())
            .withMessageId(UUID.randomUUID().toString())
            .withExecMessageType(msgType.toByte())
            .withThreadName(Thread.currentThread().getName())
            .withDispatchSeq(threadDispatchSequence.get().intValue())
            .withBuilderSeq(threadBuilderSequence.get().getAndIncrement())
            .withCurrentTime(dtf.format(ZonedDateTime.now(ZoneOffset.UTC)));

    if (responseToId != null && !responseToId.isEmpty()) {
      msgWrapper.setResponseToId(responseToId);
    }

    return msgWrapper;
  }

  private ExecMessage newWrapper(ExecMessageType msgType, UUID peerUuid) {
    return newWrapper(msgType, peerUuid, null);
  }

  private net.ittera.pal.messages.colfer.Throwable buildThrowableMessage(Throwable throwable) {

    final net.ittera.pal.messages.colfer.Throwable throwableMsg =
        new net.ittera.pal.messages.colfer.Throwable();
    // type
    throwableMsg.setType(throwable.getClass().getName());
    // message
    if (throwable.getMessage() != null) {
      throwableMsg.setMessage(throwable.getMessage());
    }
    // stack trace
    StackTraceElement[] stackTrace = throwable.getStackTrace();
    if (stackTrace != null) {
      throwableMsg.setStackTraceElements(
          Arrays.stream(stackTrace).map(StackTraceElement::toString).toArray(String[]::new));
    }
    // fill in cause(s) -- recursive
    if (throwable.getCause() != null) {
      throwableMsg.setCause(buildThrowableMessage(throwable.getCause()));
    }

    return throwableMsg;
  }

  // </editor-fold>

  // <editor-fold desc="Header messages">
  private InternalHeader buildInternalHeaderMessage(InternalHeaderType headerType) {
    return new InternalHeader().withHeaderType(headerType.toByte());
  }

  public InternalHeader buildWriteAheadHeader(UUID peerUuid) {
    logger.debug("Building write-ahead header message with peerUuid: {}", peerUuid.toString());
    return buildInternalHeaderMessage(InternalHeaderType.WRITE_AHEAD)
        .withValue(peerUuid.toString());
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

    final ConstructorCall constructorCall = new ConstructorCall();
    if (context != null) {
      final ConstructorSignature codeSignature = (ConstructorSignature) context.getSignature();
      constructorCall.setParameters(createNamedParameters(context, args, argObjRefs));
      constructorCall.setModifiers(codeSignature.getModifiers());
      if (includeSourceContext) {
        constructorCall.setContext(getWrappedContext(context, sender, senderObjRef));
      }
      constructorCall.setClazz(getWrappedClass(codeSignature.getDeclaringTypeName()));
    } else {
      constructorCall.setParameters(createNamedParameters(parameterTypes, args, argObjRefs));
      constructorCall.setClazz(getWrappedClass(className));
    }

    return newWrapper(ExecMessageType.CONSTRUCTOR, peerUuid).withConstructorCall(constructorCall);
  }

  public ExecMessage buildEmptyConstructor(UUID peerUuid, String className) {
    return buildConstructorMessage(peerUuid, className, null, null, null, null, null, null);
  }

  /**
   * Args must be set either in args or argObjRefs. If null in both, value is assumed to be null.
   *
   * @param peerUuid UUID of the peer
   * @param className Name of the class
   * @param parameterTypes Types of the parameters
   * @param args Should be of same length as parameterTypes. For Strings, primitives and wrappers.
   * @param argObjRefs Should be of same length as parameterTypes. For objectRefs.
   * @return ExecMessage
   */
  public ExecMessage buildNonEmptyConstructor(
      UUID peerUuid,
      String className,
      String[] parameterTypes,
      Object[] args,
      ObjectRef[] argObjRefs) {

    return buildConstructorMessage(
        peerUuid, className, null, null, null, parameterTypes, args, argObjRefs);
  }

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

  /**
   * Convenience method for building a constructor method message packing all arguments in a single
   * array, regardless of type (ObjectRef or not). TODO: other build methods should pack all
   * arguments and objRefs in a single array as well
   *
   * @param peerUuid UUID of this peer
   * @param className Name of the class
   * @param parameterTypes Types of the parameters
   * @param args Should be of same length as parameterTypes. For Strings, primitives and wrappers.
   * @param sender Sender object
   * @param senderObjRef Sender object reference
   * @return ExecMessage
   */
  public ExecMessage buildConstructor(
      UUID peerUuid,
      String className,
      String[] parameterTypes,
      Object[] args,
      Object sender,
      ObjectRef senderObjRef) {

    Object[] nonObjRefArgs = null;
    ObjectRef[] objRefArgs = null;
    if (args != null) {
      nonObjRefArgs = new Object[args.length];
      objRefArgs = new ObjectRef[args.length];
      for (int i = 0; i < args.length; i++) {
        Object arg = args[i];
        if (arg instanceof ObjectRef) {
          objRefArgs[i] = (ObjectRef) arg;
        } else {
          nonObjRefArgs[i] = arg;
        }
      }
    }

    return buildConstructorMessage(
        peerUuid, className, null, sender, senderObjRef, parameterTypes, nonObjRefArgs, objRefArgs);
  }

  // </editor-fold>

  // <editor-fold desc="Instance method messages">

  /**
   * Convenience method for building an instance method message packing all arguments in a single
   * array, regardless of type (ObjectRef or not). TODO: other build methods should pack all
   * arguments and objRefs in a single array as well
   *
   * @param peerUuid UUID of this peer
   * @param className Name of the class
   * @param methodName Name of the method
   * @param targetObjRef Object reference of the target object
   * @param parameterTypes Types of the parameters
   * @param args Should be of same length as parameterTypes.
   * @return ExecMessage
   */
  public ExecMessage buildInstanceMethod(
      UUID peerUuid,
      String className,
      String methodName,
      ObjectRef targetObjRef,
      String[] parameterTypes,
      Object[] args) {

    Object[] nonObjRefArgs = null;
    ObjectRef[] objRefArgs = null;
    if (args != null) {
      nonObjRefArgs = new Object[args.length];
      objRefArgs = new ObjectRef[args.length];
      for (int i = 0; i < args.length; i++) {
        Object arg = args[i];
        if (arg instanceof ObjectRef) {
          objRefArgs[i] = (ObjectRef) arg;
        } else {
          nonObjRefArgs[i] = arg;
        }
      }
    }

    return buildInstanceMethod(
        peerUuid, className, methodName, targetObjRef, parameterTypes, nonObjRefArgs, objRefArgs);
  }

  public ExecMessage buildInstanceMethod(
      UUID peerUuid,
      String className,
      String methodName,
      ObjectRef targetObjRef,
      String[] parameterTypes,
      Object[] args,
      ObjectRef[] argObjRefs) {

    return newWrapper(ExecMessageType.INSTANCE_METHOD, peerUuid)
        .withInstanceMethodCall(
            new InstanceMethodCall()
                .withParameters(createNamedParameters(parameterTypes, args, argObjRefs))
                .withClazz(getWrappedClass(className))
                .withName(methodName)
                .withObjectRef(String.valueOf(targetObjRef.getRef())));
  }

  public ExecMessage buildInstanceMethod(
      UUID peerUuid,
      Context context,
      Object sender,
      ObjectRef senderObjRef,
      ObjectRef targetObjRef,
      Object[] args,
      ObjectRef[] argObjRefs) {

    final MethodSignature codeSignature = (MethodSignature) context.getSignature();

    final InstanceMethodCall instanceMethodCall =
        new InstanceMethodCall()
            .withParameters(createNamedParameters(context, args, argObjRefs))
            .withClazz(getWrappedClass(codeSignature.getDeclaringTypeName()))
            .withName(codeSignature.getName())
            .withObjectRef(targetObjRef.asString())
            .withModifiers(codeSignature.getModifiers());

    if (includeSourceContext) {
      instanceMethodCall.setContext(getWrappedContext(context, sender, senderObjRef));
    }

    return newWrapper(ExecMessageType.INSTANCE_METHOD, peerUuid)
        .withInstanceMethodCall(instanceMethodCall);
  }

  // </editor-fold>

  // <editor-fold desc="Class method messages">

  public ExecMessage buildClassMethod(
      UUID peerUuid,
      String className,
      String methodName,
      String[] parameterTypes,
      Object sender,
      ObjectRef senderObjRef,
      Object[] args,
      ObjectRef[] argObjRefs) {

    final ClassMethodCall classMethodCall =
        new ClassMethodCall()
            .withParameters(createNamedParameters(parameterTypes, args, argObjRefs))
            .withClazz(getWrappedClass(className))
            .withName(methodName);

    if (includeSourceContext) {
      classMethodCall.setContext(getWrappedContext(null, sender, senderObjRef));
    }
    return newWrapper(ExecMessageType.CLASS_METHOD, peerUuid).withClassMethodCall(classMethodCall);
  }

  public ExecMessage buildClassMethod(
      UUID peerUuid,
      Context context,
      Object sender,
      ObjectRef senderObjRef,
      Object[] args,
      ObjectRef[] argObjRefs) {

    final MethodSignature codeSignature = (MethodSignature) context.getSignature();
    final ClassMethodCall classMethodCall =
        new ClassMethodCall()
            .withParameters(createNamedParameters(context, args, argObjRefs))
            .withClazz(getWrappedClass(codeSignature.getDeclaringTypeName()))
            .withName(codeSignature.getName())
            .withModifiers(codeSignature.getModifiers());

    if (includeSourceContext) {
      classMethodCall.setContext(getWrappedContext(context, sender, senderObjRef));
    }

    return newWrapper(ExecMessageType.CLASS_METHOD, peerUuid).withClassMethodCall(classMethodCall);
  }

  /**
   * Convenience method for building an instance method message packing all arguments in a single
   * array, regardless of type (ObjectRef or not). TODO: other build methods should pack all
   * arguments and objRefs in a single array as well.
   *
   * @param peerUuid UUID of this peer
   * @param className Name of the class
   * @param methodName Name of the method
   * @param parameterTypes Types of the parameters
   * @param sender Sender object
   * @param senderObjRef Sender object reference
   * @param args Should be of same length as parameterTypes.
   * @return ExecMessage
   */
  public ExecMessage buildClassMethod(
      UUID peerUuid,
      String className,
      String methodName,
      String[] parameterTypes,
      Object sender,
      ObjectRef senderObjRef,
      Object[] args) {

    Object[] noObjRefArgs = null;
    ObjectRef[] objectRefArgs = null;
    if (args != null) {
      noObjRefArgs = new Object[args.length];
      objectRefArgs = new ObjectRef[args.length];
      for (int i = 0; i < args.length; i++) {
        Object arg = args[i];
        if (arg instanceof ObjectRef) {
          objectRefArgs[i] = (ObjectRef) arg;
        } else {
          noObjRefArgs[i] = arg;
        }
      }
    }

    return buildClassMethod(
        peerUuid,
        className,
        methodName,
        parameterTypes,
        sender,
        senderObjRef,
        noObjRefArgs,
        objectRefArgs);
  }

  // build ClassMethodCall with another message's parameter list
  private ExecMessage buildClassMethodWithMessageParameters(
      UUID peerUuid, String className, String methodName, ExecMessage otherMessage) {

    final ClassMethodCall classMethodCall = new ClassMethodCall();
    final String fieldParamType;
    final ExecMessageType otherMessageType =
        ExecMessageType.fromByte(otherMessage.getExecMessageType());

    Obj valueObj;
    String valueObjectRef;
    switch (otherMessageType) {
      case CONSTRUCTOR:
        classMethodCall.setParameters(otherMessage.getConstructorCall().getParameters());
        break;
      case INSTANCE_METHOD:
        classMethodCall.setParameters(otherMessage.getInstanceMethodCall().getParameters());
        break;
      case CLASS_METHOD:
        classMethodCall.setParameters(otherMessage.getClassMethodCall().getParameters());
        break;
      case PUT_STATIC:
        fieldParamType = otherMessage.getStaticFieldPut().getField().getClazz().getName();
        valueObj = otherMessage.getStaticFieldPut().getValueObject();
        if (valueObj != null && !valueObj.getRef().isEmpty()) {
          valueObjectRef = valueObj.getRef();
        } else {
          // fallback to the ObjectRef set in the message
          valueObjectRef = otherMessage.getStaticFieldPut().getValueObjectRef();
        }
        classMethodCall.setParameters(
            new Parameter[] {
              createParameter(fieldParamType, valueObj, ObjectRef.from(valueObjectRef))
            });
        break;
      case PUT_FIELD:
        fieldParamType = otherMessage.getInstanceFieldPut().getField().getClazz().getName();
        valueObj = otherMessage.getInstanceFieldPut().getValueObject();
        if (valueObj != null && !valueObj.getRef().isEmpty()) {
          valueObjectRef = valueObj.getRef();
        } else {
          // fallback to the ObjectRef set in the message
          valueObjectRef = otherMessage.getInstanceFieldPut().getValueObjectRef();
        }
        classMethodCall.setParameters(
            new Parameter[] {
              createParameter(fieldParamType, valueObj, ObjectRef.from(valueObjectRef))
            });
        break;
      case GET_STATIC:
        fieldParamType = otherMessage.getStaticFieldGet().getField().getClazz().getName();
        classMethodCall.setParameters(
            new Parameter[] {createParameter(fieldParamType, null, null)});
        break;
      case GET_FIELD:
        fieldParamType = otherMessage.getInstanceFieldGet().getField().getClazz().getName();
        classMethodCall.setParameters(
            new Parameter[] {createParameter(fieldParamType, null, null)});
        break;
      default:
        logger.error("Unsupported msg type: {}", otherMessageType);
    }

    return newWrapper(ExecMessageType.CLASS_METHOD, peerUuid)
        .withClassMethodCall(
            classMethodCall.withClazz(getWrappedClass(className)).withName(methodName));
  }

  // </editor-fold>

  // <editor-fold desc="Field Ops generic">
  public ExecMessage buildFieldOp(
      UUID peerUuid,
      Context context,
      ExecMessageType execMessageType,
      Object sender,
      ObjectRef senderObjRef,
      ObjectRef targetObjRef,
      Object arg,
      ObjectRef argObjRef) {

    final FieldSignature fieldSignature = (FieldSignature) context.getSignature();

    net.ittera.pal.messages.colfer.Class clazz = getWrappedClass(fieldSignature.getDeclaringType());
    net.ittera.pal.messages.colfer.Field field =
        getWrappedField(fieldSignature.getFieldType(), fieldSignature.getName());
    int modifiers = fieldSignature.getModifiers();
    net.ittera.pal.messages.colfer.Context ctxt =
        includeSourceContext ? getWrappedContext(context, sender, senderObjRef) : null;

    final ExecMessage execMessage = newWrapper(execMessageType, peerUuid);

    switch (execMessageType) {
      case GET_FIELD:
        execMessage.setInstanceFieldGet(
            new InstanceFieldGet()
                .withClazz(clazz)
                .withObjectRef(String.valueOf(targetObjRef.getRef()))
                .withField(field)
                .withModifiers(modifiers)
                .withContext(ctxt));
        break;
      case PUT_FIELD:
        execMessage.setInstanceFieldPut(
            new InstanceFieldPut()
                .withClazz(clazz)
                .withObjectRef(String.valueOf(targetObjRef.getRef()))
                .withField(field)
                .withValueObject(
                    getWrappedObject(arg, fieldSignature.getFieldType().getName(), argObjRef))
                .withModifiers(modifiers)
                .withContext(ctxt));
        break;
      case GET_STATIC:
        execMessage.setStaticFieldGet(
            new StaticFieldGet()
                .withClazz(clazz)
                .withField(field)
                .withModifiers(modifiers)
                .withContext(ctxt));
        break;
      case PUT_STATIC:
        execMessage.setStaticFieldPut(
            new StaticFieldPut()
                .withClazz(clazz)
                .withValueObject(
                    getWrappedObject(arg, fieldSignature.getFieldType().getName(), argObjRef))
                .withField(field)
                .withModifiers(modifiers)
                .withContext(ctxt));
        break;
      default:
        throw new IllegalArgumentException("Unexpected field op type: " + execMessageType);
    }

    return execMessage;
  }

  public ExecMessage buildFieldOpDone(
      UUID peerUuid, AccessibleObject accessibleObject, Context context, ExecMessageType type) {

    final FieldSignature fieldSignature = (FieldSignature) context.getSignature();
    final ExecMessage execMessage = newWrapper(type, peerUuid);
    switch (type) {
      case PUT_FIELD_DONE:
        execMessage.setInstanceFieldPutDone(
            new InstanceFieldPutDone()
                .withClazz(getWrappedClass(fieldSignature.getDeclaringType()))
                .withField(getWrappedField((Field) accessibleObject)));
        break;
      case PUT_STATIC_DONE:
        execMessage.setStaticFieldPutDone(
            new StaticFieldPutDone()
                .withClazz(getWrappedClass(fieldSignature.getDeclaringType()))
                .withField(getWrappedField((Field) accessibleObject)));
        break;
      default:
        throw new IllegalArgumentException("Unexpected field op done type: " + type);
    }

    return execMessage;
  }

  // </editor-fold>

  // <editor-fold desc="Static field get messages">
  public ExecMessage buildGetStatic(UUID peerUuid, String className, String fieldName) {

    return newWrapper(ExecMessageType.GET_STATIC, peerUuid)
        .withStaticFieldGet(
            new StaticFieldGet()
                .withClazz(getWrappedClass(className))
                .withField(getWrappedField(className, fieldName)));
  }

  // </editor-fold>

  // <editor-fold desc="Instance field get messages">
  public ExecMessage buildGetObject(
      UUID peerUuid, String className, String fieldName, ObjectRef targetObjRef) {

    return newWrapper(ExecMessageType.GET_FIELD, peerUuid)
        .withInstanceFieldGet(
            new InstanceFieldGet()
                .withClazz(getWrappedClass(className))
                .withObjectRef(String.valueOf(targetObjRef.getRef()))
                .withField(getWrappedField((String) null, fieldName)));
  }

  // </editor-fold>

  // <editor-fold desc="Static field put messages">
  public ExecMessage buildPutStatic(
      UUID peerUuid, String className, String fieldName, String valueClassName, Object value) {

    return newWrapper(ExecMessageType.PUT_STATIC, peerUuid)
        .withStaticFieldPut(
            new StaticFieldPut()
                .withClazz(getWrappedClass(className))
                .withField(getWrappedField((String) null, fieldName))
                .withValueObject(getWrappedObject(value, valueClassName, null)));
  }

  public ExecMessage buildPutStatic(
      UUID peerUuid, String className, String fieldName, ObjectRef valueObjectRef) {

    return newWrapper(ExecMessageType.PUT_STATIC, peerUuid)
        .withStaticFieldPut(
            new StaticFieldPut()
                .withClazz(getWrappedClass(className))
                .withField(getWrappedField((String) null, fieldName))
                .withValueObjectRef(String.valueOf(valueObjectRef.getRef())));
  }

  public ExecMessage buildPutStaticDone(
      UUID peerUuid,
      AccessibleObject accessibleObject,
      String staticFieldPutId,
      String responseToId) {

    return newWrapper(ExecMessageType.PUT_STATIC_DONE, peerUuid, responseToId)
        .withStaticFieldPutDone(
            new StaticFieldPutDone()
                .withClazz(getWrappedClass(((Field) accessibleObject).getDeclaringClass()))
                .withField(getWrappedField((Field) accessibleObject))
                .withStaticFieldPutId(staticFieldPutId));
  }

  // </editor-fold>

  // <editor-fold desc="Instance field put messages">
  public ExecMessage buildPutObject(
      UUID peerUuid,
      String className,
      String fieldName,
      ObjectRef targetObjRef,
      String valueClassName,
      Object value) {

    return newWrapper(ExecMessageType.PUT_FIELD, peerUuid, null)
        .withInstanceFieldPut(
            new InstanceFieldPut()
                .withClazz(getWrappedClass(className))
                .withObjectRef(String.valueOf(targetObjRef.getRef()))
                .withField(getWrappedField((String) null, fieldName))
                .withValueObject(getWrappedObject(value, valueClassName, null)));
  }

  public ExecMessage buildPutObject(
      UUID peerUuid,
      String className,
      String fieldName,
      ObjectRef targetObjRef,
      ObjectRef valueObjectRef) {

    return newWrapper(ExecMessageType.PUT_FIELD, peerUuid, null)
        .withInstanceFieldPut(
            new InstanceFieldPut()
                .withClazz(getWrappedClass(className))
                .withObjectRef(String.valueOf(targetObjRef.getRef()))
                .withField(getWrappedField((String) null, fieldName))
                .withValueObjectRef(String.valueOf(valueObjectRef.getRef())));
  }

  public ExecMessage buildPutObjectDone(
      UUID peerUuid,
      AccessibleObject accessibleObject,
      String instanceFieldPutId,
      String responseToId) {

    return newWrapper(ExecMessageType.PUT_FIELD_DONE, peerUuid, responseToId)
        .withInstanceFieldPutDone(
            new InstanceFieldPutDone()
                .withClazz(getWrappedClass(((Field) accessibleObject).getDeclaringClass()))
                .withField(getWrappedField((Field) accessibleObject))
                .withInstanceFieldPutId(instanceFieldPutId));
  }

  // </editor-fold>

  // <editor-fold desc="Throwable messages">
  public ExecMessage buildAccessibleObjectThrowable(
      UUID peerUuid,
      @Nullable AccessibleObject accessibleObject,
      Throwable exception,
      String responseToId) {

    final RaisedThrowable raisedThrowable = new RaisedThrowable();
    if (accessibleObject != null) {
      if (accessibleObject instanceof Constructor) {
        raisedThrowable.setFrom(
            new Reflectable()
                .withConstructor(
                    new net.ittera.pal.messages.colfer.Constructor()
                        .withClazz(
                            getWrappedClass(
                                ((Constructor<?>) accessibleObject)
                                    .getDeclaringClass()
                                    .getName()))));
        raisedThrowable.setModifiers(((Constructor<?>) accessibleObject).getModifiers());
      } else if (accessibleObject instanceof Method) {
        raisedThrowable.setFrom(
            new Reflectable()
                .withMethod(
                    new net.ittera.pal.messages.colfer.Method()
                        .withClazz(
                            getWrappedClass(
                                ((Method) accessibleObject).getDeclaringClass().getName()))
                        .withName(((Method) accessibleObject).getName())));
        raisedThrowable.setModifiers(((Method) accessibleObject).getModifiers());
      } else if (accessibleObject instanceof Field) {
        raisedThrowable.setFrom(
            new Reflectable()
                .withField(
                    new net.ittera.pal.messages.colfer.Field()
                        .withClazz(
                            getWrappedClass(
                                ((Field) accessibleObject).getDeclaringClass().getName()))
                        .withName(((Field) accessibleObject).getName())));
        raisedThrowable.setModifiers(((Field) accessibleObject).getModifiers());
      } else {
        throw new UnsupportedOperationException(
            String.format(
                "Unsupported accessibleObject type: %s", accessibleObject.getClass().getName()));
      }
    }

    return newWrapper(ExecMessageType.THROWABLE, peerUuid, responseToId)
        .withRaisedThrowable(raisedThrowable.withThrowable(buildThrowableMessage(exception)));
  }

  // </editor-fold>

  // <editor-fold desc="Return value messages">
  public ExecMessage buildReturnValue(
      UUID peerUuid,
      Object object,
      AccessibleObject accessibleObject,
      ObjectRef objectRef,
      boolean isVoid,
      String responseToId) {

    final ReturnValue valueMessage = new ReturnValue();

    Class<?> declaringClass = ((Member) accessibleObject).getDeclaringClass();

    // set 'object'
    if (!isVoid) {
      Class<?> objectClass = getClassOfAccessible(accessibleObject, declaringClass);
      if (logger.isTraceEnabled()) {
        if (object != null) {
          logger.trace("object is of class: {}", object.getClass().getName());
        }
        logger.trace("objectClass.getName: {}", objectClass.getName());
      }
      valueMessage.setObject(getWrappedObject(object, objectClass.getName(), objectRef));
    }

    // set 'from'
    if (accessibleObject instanceof Constructor) {
      valueMessage.setFrom(
          new Reflectable()
              .withConstructor(
                  new net.ittera.pal.messages.colfer.Constructor()
                      .withClazz(getWrappedClass(declaringClass.getName()))));
    } else if (accessibleObject instanceof Method) {
      valueMessage.setFrom(
          new Reflectable()
              .withMethod(
                  new net.ittera.pal.messages.colfer.Method()
                      .withClazz(getWrappedClass(declaringClass.getName()))
                      .withName(((Method) accessibleObject).getName())));
    } else if (accessibleObject instanceof Field) {
      valueMessage.setFrom(
          new Reflectable()
              .withField(
                  new net.ittera.pal.messages.colfer.Field()
                      .withClazz(getWrappedClass(declaringClass.getName()))
                      .withName(((Field) accessibleObject).getName())));
    } else {
      throw new RuntimeException(
          String.format("Unable to handle accessible object of type: %s", accessibleObject));
    }

    // set class and getIsVoid
    return newWrapper(ExecMessageType.RETURN_VALUE, peerUuid, responseToId)
        .withReturnValue(valueMessage.withIsVoid(isVoid));
  }

  private static Class<?> getClassOfAccessible(
      AccessibleObject accessibleObject, Class<?> declaringClass) {
    Class<?> objectClass;
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
    return objectClass;
  }

  // </editor-fold>

  // <editor-fold desc="Intercept messages">
  public InterceptMessage buildInterceptMessage(
      UUID peerUuid,
      InterceptType type,
      String className,
      String methodName,
      List<String> parameterTypes,
      String callbackClassName,
      String callbackMethodName) {

    return new InterceptMessage()
        .withPeerUuid(peerUuid.toString())
        .withInterceptType(type.toByte())
        .withMessageId(UUID.randomUUID().toString())
        .withClazz(className)
        .withMethod(
            new net.ittera.pal.messages.colfer.InterceptableMethod()
                .withName(methodName)
                .withParameterTypes(parameterTypes.toArray(new String[0])))
        .withCallbackClass(callbackClassName)
        .withCallbackMethod(callbackMethodName);
  }

  public InterceptMessage buildInterceptMessage(
      UUID peerUuid,
      InterceptType type,
      String className,
      String fieldName,
      FieldOpType fieldOpType,
      String callbackClassName,
      String callbackMethodName) {

    return new InterceptMessage()
        .withPeerUuid(peerUuid.toString())
        .withInterceptType(type.toByte())
        .withMessageId(UUID.randomUUID().toString())
        .withClazz(className)
        .withField(
            new net.ittera.pal.messages.colfer.InterceptableField()
                .withName(fieldName)
                .withFieldOpType(fieldOpType.toByte()))
        .withCallbackClass(callbackClassName)
        .withCallbackMethod(callbackMethodName);
  }

  @SuppressWarnings("unchecked")
  public InterceptMessage buildInterceptMessage(InterceptRequest<?> intercept) {
    boolean isMethodInterceptable =
        intercept.getInterceptable().getType().equals(InterceptableType.METHOD_CALL);

    if (isMethodInterceptable) {
      return new InterceptMessage()
          .withPeerUuid(intercept.getPeer().toString())
          .withInterceptType(intercept.getType().toByte())
          .withMessageId(intercept.getUuid().toString())
          .withClazz(intercept.getClazz())
          .withMethod(
              new net.ittera.pal.messages.colfer.InterceptableMethod()
                  .withName(intercept.getInterceptable().getName())
                  .withParameterTypes(
                      ((InterceptRequest<InterceptableMethodCall>) intercept)
                          .getInterceptable()
                          .getParameterTypes()
                          .toArray(new String[0])))
          .withCallbackClass(intercept.getCallbackClass())
          .withCallbackMethod(intercept.getCallbackMethod());
    }

    return new InterceptMessage()
        .withPeerUuid(intercept.getPeer().toString())
        .withInterceptType(intercept.getType().toByte())
        .withMessageId(intercept.getUuid().toString())
        .withClazz(intercept.getClazz())
        .withField(
            new net.ittera.pal.messages.colfer.InterceptableField()
                .withName(intercept.getInterceptable().getName())
                .withFieldOpType(
                    ((InterceptRequest<InterceptableFieldOp>) intercept)
                        .getInterceptable()
                        .getFieldOpType()
                        .toByte()))
        .withCallbackClass(intercept.getCallbackClass())
        .withCallbackMethod(intercept.getCallbackMethod());
  }

  public InterceptReply buildInterceptReply(UUID peerUuid, UUID responseToId, boolean result) {
    return new InterceptReply()
        .withPeerUuid(peerUuid.toString())
        .withResponseToId(responseToId.toString())
        .withResult(result);
  }

  public InterceptKeyMessage buildInterceptKey(ExecMessage execMessage) {

    final InterceptKeyMessage keyMessage =
        new InterceptKeyMessage()
            .withClazz(getClassname(execMessage))
            .withExecutableName(getExecutableName(execMessage))
            .withExecMsgType(execMessage.getExecMessageType());
    final List<String> paramTypes = getParameterTypes(execMessage);
    if (paramTypes != null) {
      keyMessage.setParameterTypes(paramTypes.toArray(new String[0]));
    }
    return keyMessage;
  }

  public ExecMessage buildCallbackForInterceptRequest(
      UUID peerUuid, ExecMessage interceptedMessage, InterceptMessage interceptMessage) {

    return buildClassMethodWithMessageParameters(
        peerUuid,
        interceptMessage.getCallbackClass(),
        interceptMessage.getCallbackMethod(),
        interceptedMessage);
  }

  // </editor-fold>

  // <editor-fold desc="JSON-RPC messages">
  private Parameter[] jsonRpcParamsToBinaryRpcParams(List<Argument> jsonArgs) {
    if (jsonArgs == null || jsonArgs.isEmpty()) {
      return new Parameter[0];
    }

    Parameter[] binaryRpcParams = new Parameter[jsonArgs.size()];
    for (int i = 0; i < jsonArgs.size(); i++) {
      Argument arg = jsonArgs.get(i);
      Obj valueObj;
      if (arg.getRef() != null) {
        ObjectRef objectRef = ObjectRef.from(arg.getRef());
        valueObj = new Obj().withRef(String.valueOf(arg.getRef()));
        getWrappedObject(null, null, objectRef);
      } else {
        valueObj = getWrappedObject(arg.getValue(), arg.getType(), null);
      }
      binaryRpcParams[i] = new Parameter().withValue(valueObj);
    }
    return binaryRpcParams;
  }

  private InstanceMethodCall createInstanceMethodCall(JsonRpcRequest jsonRpcRequest) {
    var callParams = jsonRpcRequest.getParams();
    String className = JsonRpcMessageUtils.getClassName(jsonRpcRequest).orElseThrow();

    InstanceMethodCall instanceMethodCall = new InstanceMethodCall();
    instanceMethodCall.setClazz(getWrappedClass(className));
    instanceMethodCall.setName(callParams.getMethod());
    instanceMethodCall.setObjectRef(String.valueOf(callParams.getInstance()));
    instanceMethodCall.setParameters(jsonRpcParamsToBinaryRpcParams(callParams.getArgs()));
    return instanceMethodCall;
  }

  private ClassMethodCall createClassMethodCall(JsonRpcRequest jsonRpcRequest) {
    var callParams = jsonRpcRequest.getParams();
    String className = JsonRpcMessageUtils.getClassName(jsonRpcRequest).orElseThrow();

    ClassMethodCall classMethodCall = new ClassMethodCall();
    classMethodCall.setClazz(getWrappedClass(className));
    classMethodCall.setName(callParams.getMethod());
    classMethodCall.setParameters(jsonRpcParamsToBinaryRpcParams(callParams.getArgs()));
    return classMethodCall;
  }

  private InstanceFieldPut createInstanceFieldPut(JsonRpcRequest jsonRpcRequest) {
    var putParams = jsonRpcRequest.getParams();
    String className = JsonRpcMessageUtils.getClassName(jsonRpcRequest).orElseThrow();
    String fieldName = JsonRpcMessageUtils.getFieldName(jsonRpcRequest).orElseThrow();

    InstanceFieldPut instanceFieldPut = new InstanceFieldPut();
    instanceFieldPut.setClazz(getWrappedClass(className));
    instanceFieldPut.setField(getWrappedField(className, fieldName));
    instanceFieldPut.setObjectRef(String.valueOf(putParams.getInstance()));
    Argument value = putParams.getValue();
    assert value != null;
    if (value.getRef() != null) { // value is an object reference
      instanceFieldPut.setValueObjectRef(value.getRef().toString());
    } else {
      instanceFieldPut.setValueObject(getWrappedObject(value.getValue(), value.getType(), null));
    }
    return instanceFieldPut;
  }

  private StaticFieldPut createStaticFieldPut(JsonRpcRequest jsonRpcRequest) {
    var putParams = jsonRpcRequest.getParams();
    String className = JsonRpcMessageUtils.getClassName(jsonRpcRequest).orElseThrow();
    String fieldName = JsonRpcMessageUtils.getFieldName(jsonRpcRequest).orElseThrow();

    StaticFieldPut staticFieldPut = new StaticFieldPut();
    staticFieldPut.setClazz(getWrappedClass(className));
    staticFieldPut.setField(getWrappedField(className, fieldName));
    Argument value = putParams.getValue();
    assert value != null;
    if (value.getRef() != null) { // value is an object reference
      staticFieldPut.setValueObjectRef(value.getRef().toString());
    } else {
      staticFieldPut.setValueObject(getWrappedObject(value.getValue(), value.getType(), null));
    }
    return staticFieldPut;
  }

  private InstanceFieldGet createInstanceFieldGet(JsonRpcRequest jsonRpcRequest) {
    var getParams = jsonRpcRequest.getParams();
    String className = JsonRpcMessageUtils.getClassName(jsonRpcRequest).orElseThrow();
    String fieldName = JsonRpcMessageUtils.getFieldName(jsonRpcRequest).orElseThrow();

    InstanceFieldGet instanceFieldGet = new InstanceFieldGet();
    instanceFieldGet.setClazz(new net.ittera.pal.messages.colfer.Class().withName(className));
    instanceFieldGet.setField(new net.ittera.pal.messages.colfer.Field().withName(fieldName));
    instanceFieldGet.setObjectRef(String.valueOf(getParams.getInstance()));
    return instanceFieldGet;
  }

  private StaticFieldGet createStaticFieldGet(JsonRpcRequest jsonRpcRequest) {
    String className = JsonRpcMessageUtils.getClassName(jsonRpcRequest).orElseThrow();
    String fieldName = JsonRpcMessageUtils.getFieldName(jsonRpcRequest).orElseThrow();

    StaticFieldGet staticFieldGet = new StaticFieldGet();
    staticFieldGet.setClazz(new net.ittera.pal.messages.colfer.Class().withName(className));
    staticFieldGet.setField(new net.ittera.pal.messages.colfer.Field().withName(fieldName));
    return staticFieldGet;
  }

  private ConstructorCall createConstructorCall(JsonRpcRequest jsonRpcRequest) {
    var newParams = jsonRpcRequest.getParams();
    String className = JsonRpcMessageUtils.getClassName(jsonRpcRequest).orElseThrow();

    ConstructorCall constructorCall = new ConstructorCall();
    constructorCall.setClazz(new net.ittera.pal.messages.colfer.Class().withName(className));
    constructorCall.setParameters(jsonRpcParamsToBinaryRpcParams(newParams.getArgs()));
    return constructorCall;
  }

  public Message jsonRpcRequestToExecMessage(JsonRpcRequest jsonRpcRequest, UUID fromPeerUuid) {

    // Create an instance of ExecMessage and initialize required common fields
    ExecMessage execMessage = new ExecMessage();
    if (fromPeerUuid != null) {
      execMessage.setPeerUuid(fromPeerUuid.toString());
    }
    execMessage.setMessageId(jsonRpcRequest.getId());
    ExecMessageType execMessageType = JsonRpcMessageUtils.getExecMessageType(jsonRpcRequest);
    execMessage.setExecMessageType(execMessageType.toByte());

    // currentTime is meant for the client to indicate when then message is sent; as we don't have
    // it in a JSON-RPC request, we set it here to the time the message is received
    execMessage.setCurrentTime(dtf.format(ZonedDateTime.now(ZoneOffset.UTC)));

    // Create the appropriate ExecMessage call object based on the ExecMessageType
    switch (execMessageType) {
      case CONSTRUCTOR:
        execMessage.setConstructorCall(createConstructorCall(jsonRpcRequest));
        break;
      case GET_STATIC:
        execMessage.setStaticFieldGet(createStaticFieldGet(jsonRpcRequest));
        break;
      case GET_FIELD:
        execMessage.setInstanceFieldGet(createInstanceFieldGet(jsonRpcRequest));
        break;
      case PUT_STATIC:
        execMessage.setStaticFieldPut(createStaticFieldPut(jsonRpcRequest));
        break;
      case PUT_FIELD:
        execMessage.setInstanceFieldPut(createInstanceFieldPut(jsonRpcRequest));
        break;
      case CLASS_METHOD:
        execMessage.setClassMethodCall(createClassMethodCall(jsonRpcRequest));
        break;
      case INSTANCE_METHOD:
        execMessage.setInstanceMethodCall(createInstanceMethodCall(jsonRpcRequest));
        break;
      default:
        throw new IllegalArgumentException("Unsupported ExecMessageType: " + execMessageType);
    }
    return wrap(execMessage);
  }

  public JsonRpcResponse jsonRpcResponseFromExecMessageReply(ExecMessage execMessageResponse) {
    String requestId = execMessageResponse.getResponseToId();

    // Create a JSON-RPC response object
    final JsonRpcResponse jsonRpcResponse = new JsonRpcResponse();
    jsonRpcResponse.setId(requestId);

    switch (ExecMessageType.fromByte(execMessageResponse.getExecMessageType())) {
      case PUT_STATIC_DONE:
      case PUT_FIELD_DONE:
        jsonRpcResponse.setResult(
            new JsonRpcResponseReturnValue.Builder().withIsVoid(true).build());
        break;
      case RETURN_VALUE:
        jsonRpcResponse.setResult(
            ConversionUtils.toResponseReturnValue(execMessageResponse.getReturnValue()));
        break;
      case THROWABLE:
        net.ittera.pal.messages.colfer.Throwable raisedThrowable =
            execMessageResponse.getRaisedThrowable().getThrowable();
        JsonRpcErrorData errorData =
            new JsonRpcErrorData.Builder()
                .withRequestId(requestId)
                .withThrowableType(raisedThrowable.getType())
                .withMessage(raisedThrowable.getMessage())
                .withStackTrace(raisedThrowable.getStackTraceElements())
                .build();
        if (execMessageResponse.getRaisedThrowable() != null
            && isMethodNotFoundError(
                execMessageResponse.getRaisedThrowable().getThrowable().getType())) {
          jsonRpcResponse.setError(
              new JsonRpcError(
                  JsonRpcErrorCode.METHOD_NOT_FOUND.getCode(),
                  JsonRpcErrorCode.METHOD_NOT_FOUND.getMessage(),
                  errorData));
        } else {
          jsonRpcResponse.setError(
              new JsonRpcError(
                  JsonRpcErrorCode.SERVER_ERROR.getCode(),
                  JsonRpcErrorCode.SERVER_ERROR.getMessage(),
                  errorData));
        }
        break;
      default:
        throw new RuntimeException(
            "Unexpected response message type: "
                + ExecMessageType.fromByte(execMessageResponse.getExecMessageType()).name());
    }
    return jsonRpcResponse;
  }

  public JsonRpcResponse jsonRpcResponseFromError(Exception exception, String requestId) {

    final JsonRpcResponse jsonRpcResponse = new JsonRpcResponse();
    jsonRpcResponse.setId(requestId);
    final JsonRpcError error;
    JsonRpcErrorData errorData = new JsonRpcErrorData();

    // set request id
    errorData.setRequestId(requestId);

    // set throwable type
    if (exception instanceof JsonRpcParseException jsonRpcParseException) {
      errorData.setThrowableType(
          jsonRpcParseException.getJsonParsingException().getClass().getName());
    } else {
      errorData.setThrowableType(exception.getClass().getName());
    }

    // set other error data
    if (exception instanceof JsonRpcParseException jsonRpcParseException) {
      errorData.setMessage(jsonRpcParseException.getJsonParsingException().getMessage());
      error =
          new JsonRpcError(
              JsonRpcErrorCode.PARSE_ERROR.getCode(),
              JsonRpcErrorCode.PARSE_ERROR.getMessage(),
              errorData);
    } else if (exception instanceof InvalidJsonRpcParamsException) {
      errorData.setMessage(exception.getMessage());
      error =
          new JsonRpcError(
              JsonRpcErrorCode.INVALID_PARAMS.getCode(),
              JsonRpcErrorCode.INVALID_PARAMS.getMessage(),
              errorData);
    } else if (exception instanceof InvalidJsonRpcRequestException) {
      errorData.setMessage(exception.getMessage());
      error =
          new JsonRpcError(
              JsonRpcErrorCode.INVALID_REQUEST.getCode(),
              JsonRpcErrorCode.INVALID_REQUEST.getMessage(),
              errorData);
    } else {
      errorData.setMessage(exception.getMessage());
      error =
          new JsonRpcError(
              JsonRpcErrorCode.SERVER_ERROR.getCode(),
              JsonRpcErrorCode.SERVER_ERROR.getMessage(),
              errorData);
    }
    jsonRpcResponse.setError(error);
    return jsonRpcResponse;
  }

  // </editor-fold>

  // <editor-fold desc="Control messages">
  public ControlMessage buildDeleteObjectControlMessage(UUID fromPeer, String body) {
    final ControlMessage controlMessage =
        new ControlMessage()
            .withFromPeer(fromPeer.toString())
            .withMessageId(UUID.randomUUID().toString())
            .withCommand(ControlCommandType.DELETE_OBJECT.toByte());

    if (body != null && !body.isEmpty()) {
      controlMessage.setBody(body);
    }
    return controlMessage;
  }

  public ControlMessage buildDeleteSessionControlMessage(UUID fromPeer) {
    return new ControlMessage()
        .withFromPeer(fromPeer.toString())
        .withMessageId(UUID.randomUUID().toString())
        .withCommand(ControlCommandType.DELETE_SESSION.toByte());
  }

  public ControlMessage buildControlMessage(
      UUID fromPeerUuid, ControlStatusType statusType, @Nullable String body) {
    final ControlMessage controlMessage =
        new ControlMessage()
            .withFromPeer(fromPeerUuid.toString())
            .withMessageId(UUID.randomUUID().toString())
            .withStatus(statusType.toByte());

    if (body != null && !body.isEmpty()) {
      controlMessage.setBody(body);
    }
    return controlMessage;
  }

  public ControlMessage buildControlMessage(UUID fromPeerUuid, ControlStatusType statusType) {
    return buildControlMessage(fromPeerUuid, statusType, null);
  }

  // </editor-fold>

  // <editor-fold desc="Message Wrapper">
  public Message wrap(ExecMessage execMessage) {
    return new Message()
        .withMessageType(MessageType.EXEC_MESSAGE.toByte())
        .withExecMessage(execMessage);
  }

  public Message wrap(InterceptMessage interceptMessage) {
    return new Message()
        .withMessageType(MessageType.INTERCEPT_MESSAGE.toByte())
        .withInterceptMessage(interceptMessage);
  }

  public Message wrap(InterceptKeyMessage interceptKeyMessage) {
    return new Message()
        .withMessageType(MessageType.INTERCEPT_KEY.toByte())
        .withInterceptKeyMessage(interceptKeyMessage);
  }

  public Message wrap(InterceptReply interceptReply) {
    return new Message()
        .withMessageType(MessageType.INTERCEPT_REPLY.toByte())
        .withInterceptReply(interceptReply);
  }

  public Message wrap(ControlMessage controlMessage) {
    return new Message()
        .withMessageType(MessageType.CONTROL_MESSAGE.toByte())
        .withControlMessage(controlMessage);
  }
  // </editor-fold>
}
