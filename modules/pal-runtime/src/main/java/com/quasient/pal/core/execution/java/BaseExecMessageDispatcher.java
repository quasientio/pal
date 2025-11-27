/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.core.execution.java;

import static com.quasient.pal.serdes.colfer.ExecMessageUtils.getMessageTypeOf;

import com.quasient.pal.common.objects.ObjectRef;
import com.quasient.pal.common.runtime.Context;
import com.quasient.pal.common.runtime.ContextFactory;
import com.quasient.pal.common.runtime.Dispatcher;
import com.quasient.pal.common.runtime.ExecPhase;
import com.quasient.pal.common.util.Classes;
import com.quasient.pal.common.weave.Proceed;
import com.quasient.pal.common.weave.VoidProceed;
import com.quasient.pal.core.intercept.InterceptCallbackDispatcher;
import com.quasient.pal.core.intercept.InterceptCheckResult;
import com.quasient.pal.core.intercept.InterceptChecker;
import com.quasient.pal.core.internal.messages.SessionCommandMsg;
import com.quasient.pal.core.service.RunOptions;
import com.quasient.pal.core.transport.MessageChannelType;
import com.quasient.pal.messages.colfer.ExecMessage;
import com.quasient.pal.messages.colfer.Obj;
import com.quasient.pal.messages.colfer.Parameter;
import com.quasient.pal.messages.types.MessageType;
import com.quasient.pal.messages.types.SessionCommandType;
import com.quasient.pal.serdes.Unwrapper;
import com.quasient.pal.serdes.colfer.ColferUtils;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nonnull;
import org.aspectj.lang.ProceedingJoinPoint;

/**
 * Base dispatcher for execution messages, providing the common logic for handling local and RPC
 * execution requests within the PAL runtime. This class orchestrates the wrapping, dispatching,
 * invocation, and response handling of execution messages. Subclasses must implement abstract
 * methods to create specific message wrappers and handle invocation details.
 */
abstract class BaseExecMessageDispatcher extends AbstractDispatcher
    implements Dispatcher, ExecMessageDispatcher {

  /**
   * Dispatches an execution request by performing pre-invocation messaging, invoking the target
   * accessible object, and sending post-invocation messaging.
   *
   * <p>The process involves creating a before-execution message, sending it, invoking the target,
   * storing the return value if applicable, and then wrapping and sending an after-execution
   * message. In case an invocation exception is encountered, this method rethrows the underlying
   * cause.
   *
   * @param pjp the {@link ProceedingJoinPoint} handle
   * @param proceed the {@link Proceed} callback handle
   * @return the result of the executed operation, or a special void instance if no result is
   *     produced
   * @throws Throwable if an error occurs during invocation or processing phases
   */
  @Override
  public final <T> T dispatch(ProceedingJoinPoint pjp, Proceed<T> proceed) throws Throwable {

    final Object sender = pjp.getThis();
    final Object[] args = pjp.getArgs();
    final Object target = pjp.getTarget();

    if (logger.isTraceEnabled()) {
      logger.trace("JoinPoint: {}", pjp.toLongString());
      logger.trace("dispatch:in w/sender: {}, target: {}, args: {}", sender, target, args);
    }

    // Check intercepts BEFORE creating Context/ExecMessage
    final MessageType beforeExecMsgType = getBeforeExecMessageType();
    final boolean isMessageInterceptable = InterceptChecker.isInterceptableType(beforeExecMsgType);

    InterceptCheckResult beforeInterceptCheck = null;
    if (runOptions.contains(RunOptions.WITH_INTERCEPTS) && isMessageInterceptable) {
      beforeInterceptCheck =
          interceptChecker.checkIntercepts(pjp, beforeExecMsgType, ExecPhase.BEFORE);
    }

    // Decide if we need to create messages
    boolean withPubOrWal =
        runOptions.contains(RunOptions.WITH_WAL) || runOptions.contains(RunOptions.WITH_TCP_PUB);
    boolean needsBeforeMessages =
        withPubOrWal || (beforeInterceptCheck != null && beforeInterceptCheck.needsExecMessage());

    Context ctx = null;
    ExecMessage beforeExecMsg = null;

    if (needsBeforeMessages) {
      // Create (or get cached) context instance from join point
      ctx = ContextFactory.forJoinPoint(pjp.getStaticPart());

      // 1. Wrap message
      beforeExecMsg = createBeforeExecMessage(ctx, sender, target, args);

      // 2. Send message (WAL/PUB only - intercepts handled separately)
      @SuppressWarnings("unused")
      final ExecMessage beforeExecResponseMsg =
          messageGateway.sendExecMessage(messageBuilder.wrap(beforeExecMsg), ExecPhase.BEFORE);
    } else if (beforeInterceptCheck != null && beforeInterceptCheck.hasLocalIntercepts()) {
      // Future: handle local intercepts without creating ExecMessage
      // handleLocalIntercepts(beforeInterceptCheck.getLocalIntercepts(), pjp, args);
    }

    // Send BEFORE intercept callbacks and apply argument mutations
    Object[] finalArgs = args;
    if (beforeInterceptCheck != null
        && beforeInterceptCheck.hasRemoteIntercepts()
        && beforeExecMsg != null) {
      InterceptCallbackDispatcher.ConsolidatedCallbackResponse callbackResponse =
          interceptCallbackDispatcher.sendBeforeCallbacks(
              beforeInterceptCheck, beforeExecMsg, args);

      // Check if callback wants to throw an exception
      if (callbackResponse.shouldThrowException()) {
        throw callbackResponse.getExceptionToThrow();
      }

      // Apply argument mutations
      if (callbackResponse.hasArgMutations()) {
        Object[] mutatedArgs = args.clone();
        for (Map.Entry<Integer, Object> entry : callbackResponse.getMutatedArgs().entrySet()) {
          int index = entry.getKey();
          Object newValue = entry.getValue();
          if (index >= 0 && index < mutatedArgs.length) {
            mutatedArgs[index] = newValue;
          }
        }
        finalArgs = mutatedArgs;
      }
    }

    // 4. Invoke
    T returnValue = null;
    InvocationThrowableWrapper throwableWrapper = null;
    try {
      returnValue = invoke(pjp, proceed, finalArgs);
    } catch (Throwable th) {
      logger.error("Caught throwable while invoking field operation. Will wrap and return it.", th);
      throwableWrapper = new InvocationThrowableWrapper(th);
    }

    // Check intercepts for AFTER phase
    InterceptCheckResult afterInterceptCheck = null;
    if (runOptions.contains(RunOptions.WITH_INTERCEPTS) && isMessageInterceptable) {
      afterInterceptCheck =
          interceptChecker.checkIntercepts(pjp, getBeforeExecMessageType(), ExecPhase.AFTER);
    }

    boolean needsAfterMessages =
        withPubOrWal || (afterInterceptCheck != null && afterInterceptCheck.needsExecMessage());

    if (needsAfterMessages) {
      // Reuse context if already created
      if (ctx == null) {
        ctx = ContextFactory.forJoinPoint(pjp.getStaticPart());
      }

      // 5. Store? object in object map
      ObjectRef objectRef = null;
      if (returnValue != null) {
        objectRef = generateObjectRef(returnValue);
      }

      boolean returnsVoid = proceed instanceof VoidProceed;

      // 6. Wrap object or exception
      final ExecMessage afterExecMsg =
          createAfterExecMessage(ctx, returnValue, objectRef, returnsVoid);

      // 7. Send object or exception (WAL/PUB only)
      @SuppressWarnings("unused")
      final ExecMessage afterExecResponseMsg =
          messageGateway.sendExecMessage(messageBuilder.wrap(afterExecMsg), ExecPhase.AFTER);

      // 8. Send intercept callbacks (if any remote intercepts matched) and apply return value
      // override
      if (afterInterceptCheck != null && afterInterceptCheck.hasRemoteIntercepts()) {
        InterceptCallbackDispatcher.ConsolidatedCallbackResponse afterCallbackResponse =
            interceptCallbackDispatcher.sendAfterCallbacks(
                afterInterceptCheck,
                afterExecMsg,
                returnValue,
                returnsVoid,
                throwableWrapper != null ? throwableWrapper.throwable() : null);

        // Check if callback wants to throw an exception
        if (afterCallbackResponse.shouldThrowException()) {
          throwableWrapper =
              new InvocationThrowableWrapper(afterCallbackResponse.getExceptionToThrow());
        }

        // Apply return value override
        if (afterCallbackResponse.hasReturnValueOverride()) {
          @SuppressWarnings("unchecked")
          T overriddenReturnValue = (T) afterCallbackResponse.getOverriddenReturnValue();
          returnValue = overriddenReturnValue;
        }
      }
    } else if (afterInterceptCheck != null && afterInterceptCheck.hasLocalIntercepts()) {
      // Future: handle local intercepts without creating ExecMessage
      // handleLocalIntercepts(afterInterceptCheck.getLocalIntercepts(), pjp, returnValue);
    }

    // 9. Return object or re-raise exception
    if (throwableWrapper != null) {
      if (logger.isTraceEnabled()) {
        logger.trace("dispatch:out re-raising exception: {}", throwableWrapper);
      }
      Throwable invocationThr = throwableWrapper.throwable();
      // we want to throw the cause exception
      if (invocationThr instanceof InvocationTargetException) {
        throw invocationThr.getCause();
      } else {
        throw invocationThr;
      }
    }

    if (logger.isTraceEnabled()) {
      logger.trace("dispatch:out returning object: {}", returnValue);
    }
    return returnValue;
  }

  /**
   * Dispatches an incoming (via RPC or Log) execution message.
   *
   * <p>This method validates the message type, writes ahead if not coming from Log, and performs
   * the loading and invocation phases including argument extraction, accessible object loading, and
   * target retrieval. It then sends an after-execution message to complete the processing.
   *
   * @param incomingCall the execution message to process
   * @param messageChannel the transport channel through which the message was received
   * @return the execution message received in response after processing the incoming call
   * @throws IllegalArgumentException if the message type is not supported by this dispatcher
   */
  @Override
  public ExecMessage dispatchIncoming(ExecMessage incomingCall, MessageChannelType messageChannel) {
    if (logger.isTraceEnabled()) {
      logger.trace(
          "dispatchIncoming:in w/ message id: {}, from peer w/id:{}, channel: {}",
          incomingCall.getMessageId(),
          incomingCall.getPeerUuid(),
          messageChannel);
    }

    // get type
    final MessageType messageType = getMessageTypeOf(incomingCall);

    // check if this dispatcher supports the message type
    if (!getSupportedMessageType().equals(messageType)) {
      throw new IllegalArgumentException(
          "Unsupported message type: " + messageType + " for dispatcher: " + this.getClass());
    }

    Throwable exceptionWhileLoading = null;
    Throwable exceptionWhileInvoking = null;
    AccessibleObject accessibleObject = null;
    Object target = null;
    Object value = null;
    List<MessageArgument> args = null;

    // Loading phase
    try {
      // 1. Extract and load parameter types from message
      List<Class<?>> parameterTypes = getParameterTypesFromMessage(incomingCall, messageType);

      // 2. Unwrap and load arguments
      args = getArgsFromMessage(incomingCall, parameterTypes);

      // 3. Load constructor/method/field to call
      accessibleObject =
          loadAccessibleObject(
              incomingCall, parameterTypes, args.stream().map(MessageArgument::object).toList());

      // 4. Load target for instance methods/field ops
      target = getTargetFromMessage(incomingCall);

      // 5. Load value for assigning field ops
      value = getValueFromMessage(incomingCall, accessibleObject);

      // 6. (Optionally) Set field/method accessible, allowing to break Java access rules
      if (allowNonPublicAccess) { // extra-check, since already checked in loadAccessibleObject
        accessibleObject.setAccessible(true);
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
        if (logger.isTraceEnabled()) {
          String returnedClass =
              returnValue == null ? "unavailable" : returnValue.getClass().toString();
          logger.trace("invokeIncoming returnValue: {} of class: {}", returnValue, returnedClass);
        }
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
        createAfterExecMessage(
            incomingCall,
            returnValue,
            objectRef,
            accessibleObject,
            exceptionWhileLoading,
            exceptionWhileInvoking);

    // 11. Send object or exception, and receive
    final ExecMessage afterExecResponseMsg =
        messageGateway.sendExecMessage(messageBuilder.wrap(afterExecMsg), ExecPhase.AFTER);

    // 12. Return received message
    if (logger.isTraceEnabled()) {
      logger.trace(
          "dispatchIncoming:out returning message: {}", ColferUtils.format(afterExecResponseMsg));
    }
    return afterExecResponseMsg;
  }

  /**
   * Generates a unique {@link ObjectRef} for the given object based on its identity hash code.
   *
   * @param object the object for which to generate a reference
   * @return a new {@link ObjectRef} instance encapsulating the object's identity
   */
  protected ObjectRef generateObjectRef(Object object) {
    return ObjectRef.from(System.identityHashCode(object));
  }

  /**
   * Stores the provided object in the lookup store and returns its reference.
   *
   * @param object the object to be stored; if null, no action is taken
   * @return an ObjectRef representing the stored object, or null if the object is null
   */
  final ObjectRef storeObject(Object object) {
    return object != null ? objectLookupStore.storeObject(object) : null;
  }

  /**
   * Extracts parameter types from the message, and loads the classes for each parameter.
   *
   * @param execMessage The message to extract parameter types from
   * @param messageType The type of the message
   * @return List of loaded classes for each parameter, or null if execMessage is not a call to
   *     constructor/method.
   * @throws ClassNotFoundException if a parameter class cannot be found
   */
  private List<Class<?>> getParameterTypesFromMessage(
      ExecMessage execMessage, MessageType messageType) throws ClassNotFoundException {

    final List<Class<?>> paramClasses = new ArrayList<>();
    List<Parameter> parameterList = getParameterList(execMessage);

    if (messageType.equals(MessageType.EXEC_CONSTRUCTOR)
        || messageType.equals(MessageType.EXEC_CLASS_METHOD)
        || messageType.equals(MessageType.EXEC_INSTANCE_METHOD)) {
      for (Parameter param : parameterList) {
        if (param.getValue().getClazz() == null
            || param.getValue().getClazz().getName().isEmpty()) {
          paramClasses.add(null);
        } else {
          Class<?> primitiveClass =
              Classes.getClassForPrimitive(param.getValue().getClazz().getName());
          if (primitiveClass != null) { // param is primitive
            paramClasses.add(primitiveClass);
          } else { // i.e. not a primitive
            paramClasses.add(
                Class.forName(
                    param.getValue().getClazz().getName(),
                    true,
                    Thread.currentThread().getContextClassLoader()));
          }
        }
      }
    } else {
      return null;
    }

    return paramClasses;
  }

  /**
   * Extracts and unwraps arguments from the execution message based on the provided parameter
   * types.
   *
   * <p>This method iterates over the parameters contained in the message, attempts to look up
   * objects by reference, and, if not found, unwraps the object to its expected type.
   *
   * @param execMessage the execution message containing parameters
   * @param parameterTypes the list of expected parameter classes for proper unwrapping
   * @return a list of MessageArgument instances encapsulating the unwrapped arguments along with a
   *     flag indicating whether the argument was retrieved by reference
   */
  private List<MessageArgument> getArgsFromMessage(
      ExecMessage execMessage, List<Class<?>> parameterTypes) {

    final List<MessageArgument> args = new ArrayList<>();
    final List<Parameter> parameterList = getParameterList(execMessage);

    int i = 0;
    if (parameterList != null) {
      for (Parameter parameter : parameterList) {
        if (logger.isTraceEnabled()) {
          logger.trace("getting arg from param #{}: {}", i, ColferUtils.format(parameter));
        }
        Obj obj = parameter.getValue();
        if (obj.getIsNull()) {
          args.add(new MessageArgument(null, true));
        } else {
          Object lookedUpObj = null;
          final int objRef = obj.getRef();
          if (objRef != 0) {
            // First try to fetch object by reference (works only with locally-instantiated/stored
            // objects)
            lookedUpObj = objectLookupStore.lookupObject(ObjectRef.from(objRef));
          }
          if (lookedUpObj != null) {
            args.add(new MessageArgument(lookedUpObj, true));
          } else {
            // If not found by reference, unwrap value
            args.add(
                new MessageArgument(Unwrapper.unwrapObject(obj, parameterTypes.get(i)), false));
          }
        }
        i++;
      }
    }

    return args;
  }

  /**
   * Retrieves the value to assign for field operations from the execution message.
   *
   * <p>Dispatchers overriding this method should extract and return the value object for assignment
   * operations.
   *
   * @param execMessage the execution message containing the value
   * @param accessibleObject the field related accessible object
   * @return the value to be assigned, or null if not applicable
   */
  Object getValueFromMessage(ExecMessage execMessage, AccessibleObject accessibleObject) {
    return null;
  }

  /**
   * Retrieves the target object for instance method or field operations from the execution message.
   *
   * <p>Overriding dispatchers should extract and return the appropriate target object upon which
   * the execution should be performed.
   *
   * @param execMessage the execution message containing target information
   * @return the target object on which a method or field operation is to be invoked
   * @throws NullPointerException if the target object cannot be determined
   */
  Object getTargetFromMessage(ExecMessage execMessage) throws NullPointerException {
    return null;
  }

  /**
   * Wraps a Throwable message to be sent back to the client, after an exception occurred during
   * loading or invoking an accessible object.
   *
   * @param messageId The id of the message that this is a response to
   * @param accessibleObject The accessible object that failed to be loaded or invoked
   * @param exceptionWhileLoading Either this or exceptionWhileInvoking must be non-null
   * @param exceptionWhileInvoking Either this or exceptionWhileLoading must be non-null
   * @return The wrapped ExecMessage with the Throwable
   */
  final ExecMessage wrapAfterExecThrowableMessage(
      String messageId,
      AccessibleObject accessibleObject,
      Throwable exceptionWhileLoading,
      Throwable exceptionWhileInvoking) {

    Throwable throwable =
        exceptionWhileLoading != null ? exceptionWhileLoading : exceptionWhileInvoking;
    return messageBuilder.buildAccessibleObjectThrowable(
        peerUuid, accessibleObject, throwable, messageId);
  }

  /**
   * Stores the given object reference in the session associated with the specified peer UUID.
   *
   * <p>This method sends a session command message to the session service to persist the object
   * reference.
   *
   * @param peerUuid the UUID identifying the peer whose session is to be updated
   * @param objectRef the object reference to store in the session
   */
  private void storeObjectInSession(@Nonnull UUID peerUuid, @Nonnull ObjectRef objectRef) {
    SessionCommandMsg sessionCommandMsg =
        new SessionCommandMsg(SessionCommandType.STORE_OBJECT, peerUuid, objectRef);
    messageGateway.sendMessageToSessionService(sessionCommandMsg);
  }

  /**
   * Creates an execution message to be sent before the actual invocation.
   *
   * <p>Subclasses should construct an appropriate ExecMessage that encapsulates context and
   * invocation details, to be sent during the pre-execution phase.
   *
   * @param ctxt the execution context holding metadata for the invocation
   * @param sender the originator of the call
   * @param target the target on which the accessible object will be invoked
   * @param args the arguments to be used in the invocation
   * @return the constructed before-execution ExecMessage
   */
  protected abstract ExecMessage createBeforeExecMessage(
      Context ctxt, Object sender, Object target, Object[] args);

  /**
   * Creates an execution message to be sent after the invocation has been performed.
   *
   * <p>This message encapsulates the result of the invocation, including any object reference if
   * applicable, and indicates whether the invocation returned void.
   *
   * @param ctxt the execution context for the call
   * @param value the return value of the invocation (or exception if occurred)
   * @param objectRef the reference to the stored result object, if applicable
   * @param isVoid flag indicating whether the execution result is void
   * @return the constructed after-execution ExecMessage
   */
  protected abstract ExecMessage createAfterExecMessage(
      Context ctxt, Object value, ObjectRef objectRef, boolean isVoid);

  /**
   * Creates an execution response message after processing an incoming execution call.
   *
   * <p>This method packages the outcome of the invocation, including any return value, object
   * reference, or exception encountered during loading or invocation phases.
   *
   * @param execMessage the original execution message received
   * @param valueObject the result of the invocation if successful
   * @param valueObjRef the object reference corresponding to the result, if available
   * @param accessibleObject the accessible object that was subject to invocation
   * @param exceptionWhileLoading the exception encountered during the loading phase, if any
   * @param exceptionWhileInvoking the exception encountered during the invocation phase, if any
   * @return the constructed ExecMessage representing the after-execution response
   */
  protected abstract ExecMessage createAfterExecMessage(
      ExecMessage execMessage,
      Object valueObject,
      ObjectRef valueObjRef,
      AccessibleObject accessibleObject,
      Throwable exceptionWhileLoading,
      Throwable exceptionWhileInvoking);

  /**
   * Invokes the target accessible object (e.g., constructor, method, or field) using the provided
   * parameters.
   *
   * <p>This method may be overridden by specialized dispatchers based on the type of the operation.
   *
   * <p>If the args parameter differs from the original join point arguments (e.g., due to BEFORE
   * intercept callback mutations), this method uses {@link ProceedingJoinPoint#proceed(Object[])}
   * to pass the modified arguments to the intercepted operation.
   *
   * @param pjp the proceeding join point
   * @param proceed handle to the {@link Proceed} callback
   * @param args the arguments for the invocation (which may be mutated by intercept callbacks)
   * @return the result of the accessible object invocation, null if the operation is a setter or
   *     void call
   */
  protected <T> T invoke(ProceedingJoinPoint pjp, Proceed<T> proceed, Object[] args)
      throws Throwable {
    // Use pjp.proceed(args) to pass the arguments (which may have been mutated by intercept
    // callbacks) to the intercepted operation. AspectJ's proceed(Object[]) allows us to replace
    // the original arguments with modified ones.
    @SuppressWarnings("unchecked")
    T result = (T) pjp.proceed(args);
    return result;
  }

  /**
   * Invokes the loaded accessible object (constructor, method, or field) using the unwrapped
   * arguments.
   *
   * <p>Subclasses must implement the logic to perform the invocation, handling any value assignment
   * for field operations.
   *
   * @param accessibleObject the accessible object to invoke
   * @param target the target object for instance operations; may be null for static members
   * @param args the list of arguments encapsulated in MessageArgument instances
   * @param value the value to assign for field operations, if applicable
   * @return the result of the invocation, which may include a wrapped exception
   * @throws Exception if an error occurs during the invocation process
   */
  protected abstract Object invokeIncoming(
      AccessibleObject accessibleObject, Object target, List<MessageArgument> args, Object value)
      throws Exception;

  /**
   * Determines whether invoking the specified accessible object results in a void return type.
   *
   * @param accessibleObject the method, constructor, or field to evaluate
   * @return true if the invocation yields no meaningful return value; false otherwise
   */
  protected abstract boolean returnsVoid(AccessibleObject accessibleObject);

  /**
   * Retrieves the MessageType corresponding to the before-execution phase.
   *
   * <p>This type is used to identify the nature of the operation (constructor, instance method, or
   * class method) during pre-execution processing.
   *
   * @return the MessageType for the before-execution message
   */
  protected abstract MessageType getBeforeExecMessageType();

  /**
   * Extracts the list of parameters from the given execution message.
   *
   * <p>This method should return the parameters that will be used to determine argument types and
   * values during the loading phase.
   *
   * @param execMessage the execution message containing the parameters
   * @return a list of Parameter objects extracted from the execution message
   */
  protected abstract List<Parameter> getParameterList(ExecMessage execMessage);

  /**
   * Loads the accessible object (Constructor, Method, Field) from the message.
   *
   * @param execMessage The message to extract the accessible object from
   * @param parameterTypes Used only by constructor and method dispatchers
   * @param args Arguments. Only by constructor and method dispatchers
   * @return The loaded accessible object
   * @throws ReflectiveOperationException if the accessible object cannot be loaded
   * @throws AmbiguousCallException if the call is ambiguous
   */
  protected abstract AccessibleObject loadAccessibleObject(
      ExecMessage execMessage, List<Class<?>> parameterTypes, List<Object> args)
      throws ReflectiveOperationException, AmbiguousCallException;
}
