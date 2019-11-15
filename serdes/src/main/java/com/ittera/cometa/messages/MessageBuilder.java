package com.ittera.cometa.messages;

import com.ittera.cometa.common.lang.Context;
import com.ittera.cometa.common.lang.ObjectRef;
import com.ittera.cometa.common.lang.reflect.ExecutableObjectType;
import com.ittera.cometa.common.znodes.InterceptRequest;
import com.ittera.cometa.messages.protobuf.Exec.ExecMessage;
import com.ittera.cometa.messages.protobuf.Exec.ExecMessageType;
import com.ittera.cometa.messages.protobuf.Headers;
import com.ittera.cometa.messages.protobuf.Intercepts.FieldOpType;
import com.ittera.cometa.messages.protobuf.Intercepts.InterceptKeyMessage;
import com.ittera.cometa.messages.protobuf.Intercepts.InterceptMessage;
import com.ittera.cometa.messages.protobuf.Intercepts.InterceptReply;
import com.ittera.cometa.messages.protobuf.Intercepts.InterceptType;
import com.ittera.cometa.messages.protobuf.Wrappers.Message;
import java.lang.reflect.AccessibleObject;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MessageBuilder {

  /** header builders */
  Headers.InternalHeader buildWriteAheadHeader(UUID peerUuid);

  Headers.InternalHeader buildIncomingInterceptRequestHeader();

  /** constructor builders */
  ExecMessage buildEmptyConstructor(UUID peerUuid, String className);

  ExecMessage buildNonEmptyConstructor(
      UUID peerUuid,
      String className,
      String[] parameterTypes,
      Object[] args,
      ObjectRef[] argObjRefs);

  ExecMessage buildConstructor(
      UUID peerUuid,
      Context context,
      Object sender,
      ObjectRef senderObjRef,
      Object[] args,
      ObjectRef[] argObjRefs);

  /** instance method builders */
  ExecMessage buildInstanceMethod(
      UUID peerUuid,
      String className,
      String methodName,
      Object target,
      ObjectRef targetObjRef,
      String[] parameterTypes,
      Object[] args,
      ObjectRef[] argObjRefs);

  ExecMessage buildInstanceMethod(
      UUID peerUuid,
      Context context,
      Object sender,
      ObjectRef senderObjRef,
      Object target,
      ObjectRef targetObjRef,
      Object[] args,
      ObjectRef[] argObjRefs);

  /** class method builders */
  ExecMessage buildClassMethod(
      UUID peerUuid,
      String className,
      String methodName,
      String[] parameterTypes,
      Object sender,
      ObjectRef senderObjRef,
      Object[] args,
      ObjectRef[] argObjRefs);

  ExecMessage buildClassMethod(
      UUID peerUuid,
      Context context,
      Object sender,
      ObjectRef senderObjRef,
      Object[] args,
      ObjectRef[] argObjRefs);

  /** field op builders */
  ExecMessage buildFieldOp(
      UUID peerUuid,
      Context context,
      ExecMessageType type,
      Object sender,
      ObjectRef senderObjRef,
      Object target,
      ObjectRef targetObjRef,
      Object arg,
      ObjectRef argObjRef);

  ExecMessage buildGetStatic(UUID peerUuid, String className, String fieldName);

  ExecMessage buildGetObject(
      UUID peerUuid, String className, String fieldName, ObjectRef targetObjRef);

  ExecMessage buildPutStatic(
      UUID peerUuid, String className, String fieldName, String valueClassName, Object value);

  ExecMessage buildPutStatic(
      UUID peerUuid, String className, String fieldName, ObjectRef valueObjectRef);

  ExecMessage buildPutObject(
      UUID peerUuid,
      String className,
      String fieldName,
      ObjectRef targetObjRef,
      String valueClassName,
      Object value);

  ExecMessage buildPutObject(
      UUID peerUuid,
      String className,
      String fieldName,
      ObjectRef targetObjRef,
      ObjectRef valueObjectRef);

  /** field op done builders */
  ExecMessage buildFieldOpDone(
      UUID peerUuid, AccessibleObject accessibleObject, Context context, ExecMessageType type);

  ExecMessage buildPutStaticDone(
      UUID peerUuid,
      AccessibleObject accessibleObject,
      String staticFieldPutUuid,
      String followingUuid);

  ExecMessage buildPutObjectDone(
      UUID peerUuid,
      AccessibleObject accessibleObject,
      String instanceFieldPutUuid,
      String followingUuid);

  /** return builders */
  ExecMessage buildAccessibleObjectThrowable(
      UUID peerUuid,
      Optional<AccessibleObject> accessibleObject,
      ExecutableObjectType type,
      Throwable throwable,
      String followingUuid);

  ExecMessage buildReturnValue(
      UUID peerUuid,
      Object object,
      AccessibleObject accessibleObject,
      ObjectRef objectRef,
      boolean isVoid,
      String followingUuid);

  /** intercept requests */
  InterceptMessage buildInterceptMessage(
      UUID peerUuid,
      InterceptType type,
      String className,
      String methodName,
      List<String> parameterTypes,
      String callbackClassName,
      String callbackMethodName);

  InterceptMessage buildInterceptMessage(
      UUID peerUuid,
      InterceptType type,
      String className,
      String fieldName,
      FieldOpType fieldOpType,
      String callbackClassName,
      String callbackMethodName);

  InterceptMessage buildInterceptMessage(InterceptRequest interceptRequest);

  InterceptReply buildInterceptReply(UUID peerUuid, UUID followingUuid, boolean result);

  InterceptKeyMessage buildInterceptKey(ExecMessage execMessage);

  /** callbacks from intercept requests */
  ExecMessage buildCallbackForInterceptRequest(
      UUID peerUuid, ExecMessage interceptedMessage, InterceptMessage interceptMessage);

  /** wrappers * */
  Message wrap(ExecMessage execMessage);

  Message wrap(InterceptMessage interceptMessage);

  Message wrap(InterceptKeyMessage interceptKeyMessage);

  Message wrap(InterceptReply interceptReply);

  /** other */
  void resetThreadLocalSequence();
}
