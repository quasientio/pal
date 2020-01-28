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

package net.ittera.pal.messages;

import java.lang.reflect.AccessibleObject;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.ittera.pal.common.lang.Context;
import net.ittera.pal.common.lang.ObjectRef;
import net.ittera.pal.common.lang.reflect.ExecutableObjectType;
import net.ittera.pal.common.znodes.InterceptRequest;
import net.ittera.pal.messages.protobuf.Exec.ExecMessage;
import net.ittera.pal.messages.protobuf.Exec.ExecMessageType;
import net.ittera.pal.messages.protobuf.Headers.InternalHeader;
import net.ittera.pal.messages.protobuf.Intercepts.FieldOpType;
import net.ittera.pal.messages.protobuf.Intercepts.InterceptKeyMessage;
import net.ittera.pal.messages.protobuf.Intercepts.InterceptMessage;
import net.ittera.pal.messages.protobuf.Intercepts.InterceptReply;
import net.ittera.pal.messages.protobuf.Intercepts.InterceptType;
import net.ittera.pal.messages.protobuf.Wrappers.Message;

public interface MessageBuilder {

  /** header builders */
  InternalHeader buildWriteAheadHeader(UUID peerUuid);

  InternalHeader buildIncomingInterceptRequestHeader();

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
