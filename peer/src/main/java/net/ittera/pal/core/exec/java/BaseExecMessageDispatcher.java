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

package net.ittera.pal.core.exec.java;

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nonnull;
import net.ittera.pal.common.lang.reflect.ExecutableObjectType;
import net.ittera.pal.common.objects.ObjectRef;
import net.ittera.pal.common.runtime.Context;
import net.ittera.pal.common.runtime.Dispatcher;
import net.ittera.pal.common.runtime.ExecPhase;
import net.ittera.pal.common.util.Classes;
import net.ittera.pal.core.messages.SessionCmdMsg;
import net.ittera.pal.messages.colfer.ExecMessage;
import net.ittera.pal.messages.colfer.Obj;
import net.ittera.pal.messages.colfer.Parameter;
import net.ittera.pal.messages.types.ExecMessageType;
import net.ittera.pal.messages.types.SessionCommandType;
import net.ittera.pal.serdes.colfer.ColferUtils;
import net.ittera.pal.serdes.colfer.Unwrapper;

abstract class BaseExecMessageDispatcher extends AbstractDispatcher
    implements Dispatcher, ExecMessageDispatcher {

  // TODO load from config
  private static final boolean ENFORCE_JAVALANG_ACCESS = false;

  @Override
  public final Object dispatch(Context ctxt, Object sender, Object target, Object[] args)
      throws Throwable {

    if (logger.isTraceEnabled()) {
      logger.trace(
          "dispatch:in w/ signature: {}, sender: {}, target: {}, args: {}",
          ctxt.getSignature(),
          sender,
          target,
          args);
    }

    // 1. Wrap message
    final ExecMessage beforeExecMsg = wrapBeforeExecMessage(ctxt, sender, target, args);

    // 2. Send message
    final ExecMessage beforeExecReplyMsg =
        connector.sendExecMessage(beforeExecMsg, ExecPhase.BEFORE);

    // TODO if beforeExecReplyMsg != beforeExecMsg, unpack and exec reply msg

    // 3. Invoke
    Object returnValue = invoke(ctxt, sender, target, args);
    if (logger.isTraceEnabled()) {
      logger.trace("invoke() returned: {}", returnValue);
    }

    // 4. Store? object in object map
    ObjectRef objectRef = null;
    boolean returnsVoid = returnValue == Void.getInstance();

    if (!returnsVoid && returnValue != null) {
      objectRef = storeObject(returnValue);
    }

    // 5. Wrap object or exception
    final ExecMessage afterExecMsg =
        wrapAfterExecMessage(ctxt, returnValue, objectRef, returnsVoid);

    // 6. Send object or exception
    final ExecMessage afterExecReplyMsg = connector.sendExecMessage(afterExecMsg, ExecPhase.AFTER);

    // TODO if afterExecReplyMsg != afterExecMsg, unpack exception or return value

    // 7. Return object or re-raise exception
    if (returnValue instanceof InvocationExceptionWrapper) {
      if (logger.isTraceEnabled()) {
        logger.trace("dispatch:out re-raising exception: {}", returnValue);
      }
      Exception invocationException = ((InvocationExceptionWrapper) returnValue).getException();
      // we want to throw the cause exception
      if (invocationException instanceof InvocationTargetException) {
        throw invocationException.getCause();
      } else {
        throw invocationException;
      }
    }

    // TODO return Optional? for dispatch of voids, OR have our own Void class

    if (logger.isTraceEnabled()) {
      logger.trace("dispatch:out returning object: {}", returnValue);
    }
    return returnValue;
  }

  @Override
  public ExecMessage dispatchIncoming(ExecMessage incomingCall) {
    return dispatchIncoming(incomingCall, true);
  }

  @Override
  public ExecMessage dispatchIncoming(ExecMessage incomingCall, boolean isDirect) {
    if (logger.isTraceEnabled()) {
      logger.trace(
          "dispatchIncoming:in w/ message uuid: {}, isDirect: {}",
          incomingCall.getMessageUuid(),
          isDirect);
    }

    /**
     * TODO: Verify that message is invokable: - Class can be loaded/found - Method or field can be
     * found in class - Params can be unwrapped or loaded (if refs). What if they are remote?
     */

    /**
     * TODO: What if this message has intercepts (i.e. around or sequential pre-) ? We should call
     * an inner zmq service/connector and wait/get them, then execute that or go ahead and execute
     * this message.
     */

    // message doesn't come from log so we write-ahead before executing
    if (isDirect) {
      connector.writeAhead(incomingCall);
    }

    Throwable exceptionWhileLoading = null;
    Throwable exceptionWhileInvoking = null;
    Optional<AccessibleObject> accessibleObject = Optional.empty();
    Object target = null;
    Optional<Object> value = Optional.empty();
    List<Object> args = null;

    // Loading phase
    try {
      // 1. Extract and load parameter types from message
      List<Class> parameterTypes = getParameterTypesFromMessage(incomingCall);

      // 2. Unwrap and load arguments
      args = getArgsFromMessage(incomingCall, parameterTypes);

      // 3. Load constructor/method/field to call
      accessibleObject = Optional.of(loadAccessibleObject(incomingCall, parameterTypes, args));

      // 4. Load target for instance methods/field ops
      target = getTargetFromMessage(incomingCall, accessibleObject);

      // 5. Load value for assigning field ops
      value = getValueFromMessage(incomingCall, accessibleObject);

      // 6. (Optionally) Set field/method accessible, allowing to break Java access rules
      if (!ENFORCE_JAVALANG_ACCESS) {
        accessibleObject.ifPresent(aobj -> aobj.setAccessible(true));
      }
    } catch (Exception ex) {
      logger.error("Error during loading phase", ex);
      exceptionWhileLoading = ex;
    }

    // Invocation phase
    Object returnValue = null;
    ObjectRef objectRef = null;
    if (exceptionWhileLoading == null) {
      try {
        // 7. Invoke constructor/method/field
        returnValue = invokeIncoming(accessibleObject, target, args, value);
      } catch (Exception e) {
        logger.error("Error during invocation phase - invoke", e);
        if (e instanceof InvocationTargetException) {
          exceptionWhileInvoking = e.getCause();
        } else {
          exceptionWhileInvoking = e;
        }
      }
    }

    // 8. Map returnValue: add new entry in objectRef->object map
    if (exceptionWhileLoading == null && exceptionWhileInvoking == null) {
      try {
        if (!returnsVoid(accessibleObject) && returnValue != null) {
          objectRef = storeObject(returnValue);
        }
      } catch (Exception e) {
        logger.error("Error after invocation phase - mapping objectref -> return value", e);
      }

      // 9. Save returnValue to peer's session
      if (objectRef != null && incomingCall.getPeerUuid() != null) {
        try {
          final UUID peerUuid = UUID.fromString(incomingCall.getPeerUuid());
          storeObjectInSession(peerUuid, objectRef);
        } catch (Exception e) {
          logger.error("Error after invocation phase - saving return value to session", e);
        }
      }
    }

    // 10. Wrap object or exception
    final ExecMessage afterExecMsg =
        wrapAfterExecMessage(
            incomingCall,
            returnValue,
            objectRef,
            accessibleObject,
            exceptionWhileLoading,
            exceptionWhileInvoking);

    // 11. Send object or exception, and receive
    final ExecMessage afterExecReplyMsg = connector.sendExecMessage(afterExecMsg, ExecPhase.AFTER);

    // 12. Return received message
    if (logger.isTraceEnabled()) {
      logger.trace(
          "dispatchIncoming:out returning message: {}", ColferUtils.format(afterExecReplyMsg));
    }
    return afterExecReplyMsg;
  }

  final ObjectRef storeObject(Object object) {
    return object != null ? objectLookupStore.storeObject(object) : null;
  }

  /**
   * @param execMessage
   * @return List of loaded classes for each parameter, or null if execMessage is not a call to
   *     constructor/method.
   * @throws ClassNotFoundException
   */
  private List<Class> getParameterTypesFromMessage(ExecMessage execMessage)
      throws ClassNotFoundException {

    final List<Class> paramClasses = new ArrayList<>();
    List<Parameter> parameterList = getParameterList(execMessage);

    final ExecMessageType execMessageType =
        ExecMessageType.fromByte(execMessage.getExecMessageType());
    if (execMessageType.equals(ExecMessageType.CONSTRUCTOR)
        || execMessageType.equals(ExecMessageType.CLASS_METHOD)
        || execMessageType.equals(ExecMessageType.INSTANCE_METHOD)) {
      for (Parameter param : parameterList) {
        Class paramClass = Classes.getClassForPrimitive(param.getType().getName());
        if (paramClass == null) { // ie. not a primitive
          paramClass =
              Class.forName(
                  param.getType().getName(), true, Thread.currentThread().getContextClassLoader());
        }
        paramClasses.add(paramClass);
      }
    } else {
      return null;
    }

    return paramClasses;
  }

  private List<Object> getArgsFromMessage(ExecMessage execMessage, List<Class> parameterTypes) {

    final List<Object> args = new ArrayList<>();
    final List<Parameter> parameterList = getParameterList(execMessage);

    int i = 0;
    if (parameterList != null) {
      for (Parameter parameter : parameterList) {
        if (logger.isTraceEnabled()) {
          logger.trace("getting arg from param #{}: {}", i, ColferUtils.format(parameter));
        }
        Obj obj = parameter.getValue();
        if (obj.getIsNull()) {
          args.add(null);
        } else {
          Object lookedUpObj = null;
          final String objRef = obj.getRef();
          if (objRef != null && !objRef.isEmpty()) {
            // First try to fetch object by reference (works only with locally-instantiated/stored
            // objects)
            lookedUpObj = objectLookupStore.lookupObject(ObjectRef.from(objRef));
          }
          if (lookedUpObj != null) {
            args.add(lookedUpObj);
          } else {
            // If not found by reference, unwrap value
            args.add(Unwrapper.unwrapObject(obj, parameterTypes.get(i)));
          }
        }
        i++;
      }
    }

    return args;
  }

  /**
   * To be overridden by dispatchers that assign a value (SetFieldDispatcher)
   *
   * @return
   */
  Optional<Object> getValueFromMessage(
      ExecMessage execMessage, Optional<AccessibleObject> accessibleObject) {
    return Optional.empty();
  }

  /**
   * To be overridden by dispatchers that work on an instance method/variable
   *
   * @return
   */
  Object getTargetFromMessage(ExecMessage execMessage, Optional<AccessibleObject> accessibleObject)
      throws ClassNotFoundException, NullPointerException {
    return null;
  }

  /**
   * @param messageUuid
   * @param accessibleObject
   * @param executableObjectType
   * @param exceptionWhileLoading Either this or exceptionWhileInvoking must be non-null
   * @param exceptionWhileInvoking
   * @return
   */
  final ExecMessage wrapAfterExecThrowableMessage(
      String messageUuid,
      Optional<AccessibleObject> accessibleObject,
      ExecutableObjectType executableObjectType,
      Throwable exceptionWhileLoading,
      Throwable exceptionWhileInvoking) {

    Throwable throwable =
        exceptionWhileLoading != null ? exceptionWhileLoading : exceptionWhileInvoking;
    return messageBuilder.buildAccessibleObjectThrowable(
        peerUuid, accessibleObject, executableObjectType, throwable, messageUuid);
  }

  private void storeObjectInSession(@Nonnull UUID peerUuid, @Nonnull ObjectRef objectRef) {
    SessionCmdMsg sessionCmdMsg =
        new SessionCmdMsg(SessionCommandType.STORE_OBJECT, peerUuid, objectRef);
    connector.sendMessageToSessionService(sessionCmdMsg);
  }

  protected abstract ExecMessage wrapBeforeExecMessage(
      Context ctxt, Object sender, Object target, Object[] args);

  // TODO generalize this method, using a Builder method taking Executable's
  // TODO create a Builder.buildVoidReturnValue() method
  protected abstract ExecMessage wrapAfterExecMessage(
      Context ctxt, Object value, ObjectRef objectRef, boolean isVoid);

  protected abstract ExecMessage wrapAfterExecMessage(
      ExecMessage execMessage,
      Object valueObject,
      ObjectRef valueObjRef,
      Optional<AccessibleObject> accessibleObject,
      Throwable exceptionWhileLoading,
      Throwable exceptionWhileInvoking);

  protected abstract Object invoke(Context ctxt, Object sender, Object target, Object[] args);

  /**
   * @param accessibleObject
   * @param target Present only for instance methods/field ops
   * @param args
   * @param value Present only for value-assigning field ops.
   * @return
   * @throws Exception
   */
  protected abstract Object invokeIncoming(
      Optional<AccessibleObject> accessibleObject,
      Object target,
      List<Object> args,
      Optional<Object> value)
      throws Exception;

  protected abstract boolean returnsVoid(Optional<AccessibleObject> accessibleObject);

  protected abstract ExecMessageType getBeforeExecMessageType();

  /**
   * We need this method and the ExecutableObjectType enum for cases where a Field, Constructor or
   * Method fails to be loaded (i.e. exceptionWhileLoading), and we require at least information
   * about the type of accessible to include in the Throwable message
   *
   * @return
   */
  protected abstract ExecutableObjectType getExecutableObjectType();

  protected abstract List<Parameter> getParameterList(ExecMessage execMessage);

  /**
   * @param execMessage
   * @param parameterTypes Used only by constructor and method dispatchers
   * @param args Used only by method dispatchers
   * @return
   * @throws ReflectiveOperationException
   */
  protected abstract AccessibleObject loadAccessibleObject(
      ExecMessage execMessage, List<Class> parameterTypes, List<Object> args)
      throws ReflectiveOperationException;
}
