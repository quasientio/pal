/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.serdes.colfer;

import static io.quasient.pal.messages.types.MessageType.CONTROL_MESSAGE_RESPONSE;
import static io.quasient.pal.messages.types.MessageType.META_MESSAGE_RESPONSE;
import static io.quasient.pal.serdes.colfer.ControlMessageUtils.getMessageTypeOf;
import static io.quasient.pal.serdes.colfer.ExecMessageUtils.getClassname;
import static io.quasient.pal.serdes.colfer.ExecMessageUtils.getExecutableName;
import static io.quasient.pal.serdes.colfer.ExecMessageUtils.getMessageTypeOf;
import static io.quasient.pal.serdes.colfer.ExecMessageUtils.getParameterTypes;
import static io.quasient.pal.serdes.colfer.MetaMessageUtils.getMessageTypeOf;
import static io.quasient.pal.serdes.colfer.Wrapper.getWrappedClass;
import static io.quasient.pal.serdes.colfer.Wrapper.getWrappedContext;
import static io.quasient.pal.serdes.colfer.Wrapper.getWrappedField;
import static io.quasient.pal.serdes.colfer.Wrapper.getWrappedObject;
import static io.quasient.pal.serdes.jsonrpc.JsonRpcMessageUtils.isMethodNotFoundError;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quasient.pal.common.directory.nodes.InterceptRequest;
import io.quasient.pal.common.lang.FieldOpType;
import io.quasient.pal.common.lang.intercept.InterceptType;
import io.quasient.pal.common.lang.intercept.Interceptable.InterceptableType;
import io.quasient.pal.common.lang.intercept.InterceptableFieldOp;
import io.quasient.pal.common.lang.intercept.InterceptableMethodCall;
import io.quasient.pal.common.lang.reflect.CodeSignature;
import io.quasient.pal.common.lang.reflect.ConstructorSignature;
import io.quasient.pal.common.lang.reflect.FieldSignature;
import io.quasient.pal.common.objects.ObjectRef;
import io.quasient.pal.common.runtime.Context;
import io.quasient.pal.common.util.FastIdGeneratorNonCrypto;
import io.quasient.pal.common.util.IdGenerator;
import io.quasient.pal.messages.colfer.ClassMethodCall;
import io.quasient.pal.messages.colfer.ConstructorCall;
import io.quasient.pal.messages.colfer.ControlMessage;
import io.quasient.pal.messages.colfer.ExecMessage;
import io.quasient.pal.messages.colfer.InstanceFieldGet;
import io.quasient.pal.messages.colfer.InstanceFieldPut;
import io.quasient.pal.messages.colfer.InstanceFieldPutDone;
import io.quasient.pal.messages.colfer.InstanceMethodCall;
import io.quasient.pal.messages.colfer.InterceptCallbackRequestMessage;
import io.quasient.pal.messages.colfer.InterceptCallbackResponseMessage;
import io.quasient.pal.messages.colfer.InterceptKeyMessage;
import io.quasient.pal.messages.colfer.InterceptMessage;
import io.quasient.pal.messages.colfer.InterceptResponse;
import io.quasient.pal.messages.colfer.InterceptableField;
import io.quasient.pal.messages.colfer.InterceptableMethod;
import io.quasient.pal.messages.colfer.InternalHeader;
import io.quasient.pal.messages.colfer.Message;
import io.quasient.pal.messages.colfer.MetaMessage;
import io.quasient.pal.messages.colfer.Obj;
import io.quasient.pal.messages.colfer.Parameter;
import io.quasient.pal.messages.colfer.RaisedThrowable;
import io.quasient.pal.messages.colfer.Reflectable;
import io.quasient.pal.messages.colfer.ReturnValue;
import io.quasient.pal.messages.colfer.StaticFieldGet;
import io.quasient.pal.messages.colfer.StaticFieldPut;
import io.quasient.pal.messages.colfer.StaticFieldPutDone;
import io.quasient.pal.messages.jsonrpc.Argument;
import io.quasient.pal.messages.jsonrpc.Executable;
import io.quasient.pal.messages.jsonrpc.JsonRpcError;
import io.quasient.pal.messages.jsonrpc.JsonRpcErrorData;
import io.quasient.pal.messages.jsonrpc.JsonRpcRequest;
import io.quasient.pal.messages.jsonrpc.JsonRpcResponse;
import io.quasient.pal.messages.jsonrpc.JsonRpcResponseReturnValue;
import io.quasient.pal.messages.jsonrpc.ResponseObject;
import io.quasient.pal.messages.types.ControlCommandType;
import io.quasient.pal.messages.types.ControlStatusType;
import io.quasient.pal.messages.types.InternalHeaderType;
import io.quasient.pal.messages.types.JsonRpcErrorCode;
import io.quasient.pal.messages.types.MessageType;
import io.quasient.pal.messages.types.MetaServiceType;
import io.quasient.pal.messages.types.MetaStatusType;
import io.quasient.pal.serdes.ConversionUtils;
import io.quasient.pal.serdes.NonWrappableObjectException;
import io.quasient.pal.serdes.colfer.scratches.TlMsgScratch;
import io.quasient.pal.serdes.colfer.scratches.TlScratchHolder;
import io.quasient.pal.serdes.jsonrpc.InvalidJsonRpcParamsException;
import io.quasient.pal.serdes.jsonrpc.InvalidJsonRpcRequestException;
import io.quasient.pal.serdes.jsonrpc.JsonRpcMessageUtils;
import io.quasient.pal.serdes.jsonrpc.JsonRpcParseException;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Constructs and serializes various types of PAL runtime messages using the Colfer serialization
 * format, and performs conversion to and from the JSON-RPC format.
 *
 * <p>A number of methods which create the main {@link ExecMessage} instances exist with two
 * variants, one with the 'Ephemeral' suffix, and the other without. The difference between these
 * two are:
 *
 * <ul>
 *   <li>The *Ephemeral methods build messages by filling up existing instances inside the {@link
 *       TlMsgScratch} through the {@link TlScratchHolder} utility, avoiding allocation and GC
 *       costs. These are only to be called from the hot-path, i.e. the methods invoked through the
 *       {@link io.quasient.pal.common.runtime.Dispatcher} interface entry point. Usage of these
 *       methods assumes that one thread will build a message, marshall it (usually by calling
 *       {@code OutboundMsg#new}) and neither share it nor make any more use of it.
 *   <li>The non-ephemeral method variants make no such assumptions about usage and always allocate
 *       new instances of the colfer beans, making them safe for use in unrestricted ways. These are
 *       to be invoked from outside the hot-path, such as in methods invoked through the {@code
 *       ExecMessageDispatcher} interface entry point, from tests and from external integrations
 *       through the API.
 * </ul>
 */
public final class MessageBuilder {

  /** Logger instance for logging message building operations. */
  private static final Logger logger = LoggerFactory.getLogger(MessageBuilder.class);

  /** Instance of UTC clock for timestamps */
  private static final Clock utcClock = Clock.systemUTC();

  /** ID of this peer (required through constructor for methods in hot-path) */
  private String peerId;

  /** Thread-local counter for dispatch sequencing of messages per thread. */
  private final ThreadLocal<int[]> threadDispatchSequence =
      ThreadLocal.withInitial(() -> new int[] {1});

  /** Thread-local counter for builder sequencing of messages per thread. */
  private final ThreadLocal<int[]> threadBuilderSequence =
      ThreadLocal.withInitial(() -> new int[] {1});

  /** Thread name (cached for efficiency). */
  private final ThreadLocal<String> tlThreadName =
      ThreadLocal.withInitial(() -> Thread.currentThread().getName());

  /** Flag indicating whether to include source context information in the built messages. */
  private boolean includeSourceContext;

  /** Generator for unique message identifiers. */
  private final IdGenerator idGenerator = new FastIdGeneratorNonCrypto();

  /**
   * Constructs a new {@code MessageBuilder} with default configuration.
   *
   * <p>Source context inclusion is disabled by default.
   */
  @Inject
  public MessageBuilder() {}

  /**
   * Constructs a new {@code MessageBuilder} with default configuration, and a reference to this
   * Peer's ID.
   *
   * <p>Source context inclusion is disabled by default.
   *
   * @param peerId ID of this peer, to avoid passing in every call.
   */
  public MessageBuilder(UUID peerId) {
    this.peerId = peerId.toString();
  }

  /**
   * Constructs a new {@code MessageBuilder} with source context configuration.
   *
   * @param peerId ID of this peer, to avoid passing in every call. May be null.
   * @param includeSourceContextStr a string representing whether to include source context
   *     information in the built messages. Expected values are "true" or "false".
   */
  public MessageBuilder(
      @Nullable UUID peerId, @Named("messages.with_src_context") String includeSourceContextStr) {
    if (peerId != null) {
      this.peerId = peerId.toString();
    }
    this.includeSourceContext = Boolean.parseBoolean(includeSourceContextStr);
  }

  // <editor-fold desc="Thread-local sequence stamping methods">

  /**
   * Resets the thread-local builder sequence counter and increments the dispatch sequence.
   *
   * <p>This method resets the {@code threadBuilderSequence} counter to 1 and increments the {@code
   * threadDispatchSequence} counter for the current thread, ensuring a fresh sequencing for new
   * message builds.
   */
  public void resetThreadLocalSequence() {
    threadBuilderSequence.set(new int[] {1});
    threadDispatchSequence.get()[0]++;
  }

  // </editor-fold>

  // <editor-fold desc="Private Auxiliary methods">

  /**
   * Converts a parameter map to an array of {@link Parameter} objects.
   *
   * @param params the map of parameter names to their respective values
   * @return an array of {@code Parameter} objects representing the key-value pairs, or an empty
   *     array if the input map is {@code null}
   */
  private static Parameter[] paramMapToParameters(Map<String, Object> params) {
    if (params == null) {
      return new Parameter[0];
    }

    Parameter[] keyValues = new Parameter[params.size()];
    int index = 0;

    for (Map.Entry<String, Object> entry : params.entrySet()) {
      Parameter keyValueParam =
          new Parameter()
              .withName(entry.getKey())
              .withValue(getWrappedObject(entry.getValue(), null, null, WrapPolicy.FORCE_BY_VALUE));
      keyValues[index++] = keyValueParam;
    }
    return keyValues;
  }

  /**
   * Generates the next unique message identifier.
   *
   * @return a unique identifier string
   */
  private String nextId() {
    return idGenerator.nextId();
  }

  /**
   * Creates a {@link Parameter} object based on the provided argument and type.
   *
   * @param parameterType the type of the parameter
   * @param arg the argument value, which may be {@code null} or an instance of {@link Obj}
   * @param argObjRef the reference to an object if the argument is an object reference, otherwise
   *     {@code null}
   * @return a {@code Parameter} representing the argument
   */
  private Parameter createParameter(String parameterType, Object arg, ObjectRef argObjRef) {
    if (arg instanceof Obj objArg) {
      return new Parameter().withValue(objArg);
    }
    return new Parameter()
        .withValue(getWrappedObject(arg, parameterType, argObjRef, WrapPolicy.PREFER_REFERENCE));
  }

  /**
   * Creates a named {@link Parameter} for a method or constructor parameter.
   *
   * @param parameter the reflective parameter information
   * @param paramName the name of the parameter, or {@code null} to use the reflective name
   * @param paramType the type name of the parameter
   * @param param the value of the parameter
   * @param paramObjRef the object reference for the parameter if applicable, otherwise {@code null}
   * @return a {@code Parameter} with the specified name and value
   */
  private Parameter createNamedParameter(
      java.lang.reflect.Parameter parameter,
      String paramName,
      String paramType,
      Object param,
      ObjectRef paramObjRef) {

    return new Parameter()
        .withName(paramName == null ? parameter.getName() : paramName)
        .withValue(getWrappedObject(param, paramType, paramObjRef, WrapPolicy.PREFER_REFERENCE));
  }

  /**
   * Creates an array of named {@link Parameter} objects based on the provided context and
   * arguments.
   *
   * @param context the execution context containing signature information
   * @param args the array of argument values
   * @param argObjRefs the array of object references corresponding to the arguments
   * @return an array of {@code Parameter} objects representing the named parameters
   */
  private Parameter[] createNamedParameters(
      Context context, Object[] args, ObjectRef[] argObjRefs) {
    final CodeSignature codeSignature = (CodeSignature) context.getSignature();
    final Class<?>[] paramTypes = codeSignature.getParameterTypes();
    final int paramCount = (paramTypes == null) ? 0 : paramTypes.length;
    final Parameter[] params = new Parameter[paramCount];
    final String[] paramNames = codeSignature.getParameterNames();
    final java.lang.reflect.Parameter[] reflParams = codeSignature.getParameters();

    for (int i = 0; i < paramCount; i++) {
      final String paramName = (paramNames != null && i < paramNames.length) ? paramNames[i] : null;
      // null-safe local var to silence static analysis warnings
      final String paramTypeName =
          (paramTypes != null && i < paramTypes.length && paramTypes[i] != null)
              ? paramTypes[i].getName()
              : null;
      final Object a = (args != null && i < args.length) ? args[i] : null;
      final ObjectRef r = (argObjRefs != null && i < argObjRefs.length) ? argObjRefs[i] : null;
      final java.lang.reflect.Parameter rp =
          (reflParams != null && i < reflParams.length) ? reflParams[i] : null;

      if (rp != null) {
        params[i] = createNamedParameter(rp, paramName, paramTypeName, a, r);
      } else {
        // Fallback if reflective parameter metadata is unavailable
        params[i] =
            new Parameter()
                .withName(paramName == null ? "" : paramName)
                .withValue(getWrappedObject(a, paramTypeName, r, WrapPolicy.PREFER_REFERENCE));
      }
    }
    return params;
  }

  /**
   * Creates an array of named {@link Parameter} objects based on parameter types and arguments.
   *
   * <p>All arrays must be non-null and of equal length.
   *
   * @param parameterTypes the array of parameter type names
   * @param args the array of argument values, corresponding to {@code parameterTypes}
   * @param argObjRefs the array of object references corresponding to {@code parameterTypes}
   * @return an array of {@code Parameter} objects representing the named parameters
   * @throws IllegalArgumentException if any array is {@code null} or their lengths differ
   */
  private Parameter[] createNamedParameters(
      String[] parameterTypes, Object[] args, ObjectRef[] argObjRefs) {
    if (parameterTypes == null || args == null || argObjRefs == null) {
      throw new IllegalArgumentException(
          "parameterTypes, args and argObjRefs must be non-null and of equal length");
    }
    final int n = parameterTypes.length;
    if (args.length != n || argObjRefs.length != n) {
      throw new IllegalArgumentException(
          "parameterTypes, args and argObjRefs must be non-null and of equal length");
    }

    final Parameter[] params = new Parameter[n];
    for (int i = 0; i < n; i++) {
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

  /**
   * Creates a new {@link ExecMessage} with the specified peer ID and response ID.
   *
   * @param peerId the ID of the peer sending the message
   * @param responseToId the message ID this {@code ExecMessage} is responding to, or {@code null}
   *     if not applicable
   * @return a new {@code ExecMessage} instance with initialized fields
   */
  private ExecMessage newExecMessage(String peerId, String responseToId) {
    ExecMessage msgWrapper =
        new ExecMessage()
            .withPeerUuid(peerId)
            .withMessageId(nextId())
            .withThreadName(tlThreadName.get())
            .withDispatchSeq(threadDispatchSequence.get()[0])
            .withBuilderSeq(threadBuilderSequence.get()[0]++)
            .withCurrentTime(Instant.now().toString());

    if (responseToId != null && !responseToId.isEmpty()) {
      msgWrapper.setResponseToId(responseToId);
    }

    return msgWrapper;
  }

  /**
   * Creates a new {@link ExecMessage} with the specified peer UUID and response ID.
   *
   * @param peerUuid the UUID of the peer sending the message
   * @param responseToId the message ID this {@code ExecMessage} is responding to, or {@code null}
   *     if not applicable
   * @return a new {@code ExecMessage} instance with initialized fields
   */
  private ExecMessage newExecMessage(UUID peerUuid, String responseToId) {
    return newExecMessage(peerUuid.toString(), responseToId);
  }

  /**
   * Creates a new {@link ExecMessage} with the specified peer UUID.
   *
   * @param peerUuid the UUID of the peer sending the message
   * @return a new {@code ExecMessage} instance with initialized fields
   */
  private ExecMessage newExecMessage(UUID peerUuid) {
    return newExecMessage(peerUuid, null);
  }

  /**
   * Builds a {@link io.quasient.pal.messages.colfer.Throwable} message from a {@code Throwable}.
   *
   * @param throwable the {@code Throwable} to be converted into a message
   * @return a {@code Throwable} message representing the provided exception
   */
  @SuppressWarnings("PMD.NoFullyQualifiedTypes")
  private io.quasient.pal.messages.colfer.Throwable buildThrowableMessage(Throwable throwable) {

    final io.quasient.pal.messages.colfer.Throwable throwableMsg =
        new io.quasient.pal.messages.colfer.Throwable();
    // type
    throwableMsg.setType(throwable.getClass().getName());
    // message
    if (throwable.getMessage() != null) {
      throwableMsg.setMessage(throwable.getMessage());
    }
    // stack trace
    StackTraceElement[] stackTrace = throwable.getStackTrace();
    throwableMsg.setStackTraceElements(
        Arrays.stream(stackTrace).map(StackTraceElement::toString).toArray(String[]::new));
    // fill in cause(s) -- recursive
    if (throwable.getCause() != null) {
      throwableMsg.setCause(buildThrowableMessage(throwable.getCause()));
    }

    return throwableMsg;
  }

  /**
   * Builds a {@link Parameter} array in-place using thread-local scratch holders.
   *
   * <p>Reuses {@link TlScratchHolder} Parameter/Obj flyweights and avoids allocations.
   *
   * @param stat precomputed message statics containing parameter type names
   * @param args argument values for wrapping; may be {@code null}
   * @param argObjRefs argument object references corresponding to {@code args}; may be {@code null}
   * @return the reused {@link Parameter} array for non-zero arity, or {@code null} if there are no
   *     parameters
   */
  private static Parameter[] buildParamsFlyweight(
      MessageStatics stat, Object[] args, ObjectRef[] argObjRefs) {
    final int n = (args == null) ? 0 : args.length;
    TlScratchHolder.ensureParamCapacity(n);
    final Parameter[] params = TlScratchHolder.paramsOut(n);
    for (int i = 0; i < n; i++) {
      final Parameter p = TlScratchHolder.paramAt(i);
      final Obj v = TlScratchHolder.valueAt(i);

      final String ptype =
          (stat.paramTypeNames != null && i < stat.paramTypeNames.length)
              ? stat.paramTypeNames[i]
              : null;
      final ObjectRef pref = (argObjRefs != null && i < argObjRefs.length) ? argObjRefs[i] : null;

      final Object a = (args != null && i < args.length) ? args[i] : null;
      Wrapper.wrapInto(v, a, ptype, pref, WrapPolicy.PREFER_REFERENCE);

      p.name = "";
      p.value = v;

      params[i] = p;
    }
    return n == 0 ? null : params;
  }

  /**
   * Builds and returns a reusable {@link io.quasient.pal.messages.colfer.Context} when source
   * context is enabled.
   *
   * <p>Fills fields from the provided {@link Context} and optional sender, otherwise returns {@code
   * null} if source context inclusion is disabled.
   *
   * @param context the execution context providing source location details
   * @param sender the sender instance, or {@code null}
   * @param senderObjRef the sender's object reference, or {@code null}
   * @return the thread-local context bean when enabled; {@code null} otherwise
   */
  @SuppressWarnings("PMD.NoFullyQualifiedTypes")
  private io.quasient.pal.messages.colfer.Context buildContextIfEnabled(
      Context context, Object sender, ObjectRef senderObjRef) {
    if (!includeSourceContext) {
      return null;
    }
    final io.quasient.pal.messages.colfer.Context c = TlScratchHolder.cctx();
    c.sourceLocationFile = context.getSourceFilename();
    c.sourceLocationLine = context.getSourceLine();
    c.sourceLocationType = context.getWithinType().getName();
    if (sender != null) {
      c.senderClass = getWrappedClass(sender.getClass());
      Obj senderObj = TlScratchHolder.senderObj();
      Wrapper.wrapInto(
          senderObj,
          sender,
          sender.getClass().getName(),
          senderObjRef,
          WrapPolicy.PREFER_REFERENCE);
      c.sender = senderObj;
    }
    return c;
  }

  /**
   * Stamps common header fields onto an {@link ExecMessage} using thread-local state.
   *
   * @param m the {@link ExecMessage} to fill
   */
  private void fillExecHeader(ExecMessage m) {
    m.peerUuid = peerId;
    m.messageId = idGenerator.nextId();
    m.threadName = tlThreadName.get();
    m.dispatchSeq = threadDispatchSequence.get()[0];
    m.builderSeq = threadBuilderSequence.get()[0]++;
    m.currentTime = Instant.now(utcClock).toString();
  }

  // </editor-fold>

  // <editor-fold desc="Header messages">

  /**
   * Builds an {@link InternalHeader} message with the specified header type.
   *
   * @param headerType the type of internal header to create
   * @return a new {@code InternalHeader} instance with the specified type
   */
  private InternalHeader buildInternalHeaderMessage(InternalHeaderType headerType) {
    return new InternalHeader().withHeaderType(headerType.toByte());
  }

  /**
   * Builds a write-ahead internal header message for the specified peer.
   *
   * @param peerUuid the UUID of the peer for whom the header is being built
   * @return an {@code InternalHeader} representing a write-ahead message
   */
  public InternalHeader buildWriteAheadHeader(UUID peerUuid) {
    logger.debug("Building write-ahead header message with peerUuid: {}", peerUuid.toString());
    return buildInternalHeaderMessage(InternalHeaderType.WRITE_AHEAD)
        .withValue(peerUuid.toString());
  }

  // </editor-fold>

  // <editor-fold desc="Constructor methods">

  /**
   * Builds an {@link ExecMessage} for invoking a class (static) method with specified arguments and
   * object references.
   *
   * @param context the execution context containing method signature information
   * @param sender the object sending the message
   * @param senderObjRef the reference to the sender object
   * @param args the array of argument values corresponding to the parameters
   * @param argObjRefs the array of object references corresponding to the arguments
   * @return an {@code ExecMessage} representing the class method invocation with context
   */
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
      // Only set parameters when all arrays are provided (strict contract); otherwise, omit.
      if (parameterTypes != null && args != null && argObjRefs != null) {
        constructorCall.setParameters(createNamedParameters(parameterTypes, args, argObjRefs));
      }
      constructorCall.setClazz(getWrappedClass(className));
    }
    return newExecMessage(peerUuid).withConstructorCall(constructorCall);
  }

  /**
   * Builds an {@link ExecMessage} for invoking a constructor using execution context.
   *
   * @param peerUuid the UUID of the peer invoking the constructor
   * @param context the execution context containing signature information
   * @param sender the object sending the message
   * @param senderObjRef the reference to the sender object
   * @param args the array of argument values
   * @param argObjRefs the array of object references corresponding to the arguments
   * @return an {@code ExecMessage} representing the constructor invocation with execution context
   */
  ExecMessage buildConstructor(
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

  // <editor-fold desc="Hot-path message builder methods">

  /**
   * Constructs an {@link ExecMessage} for invoking a constructor.
   *
   * @param context the execution context containing signature information
   * @param sender the object sending the message, or {@code null}
   * @param senderObjRef the reference to the sender object, or {@code null}
   * @param args the array of argument values, or {@code null}
   * @param argObjRefs the array of object references corresponding to the arguments, or {@code
   *     null}
   * @return an {@code ExecMessage} representing the constructor invocation
   */
  @SuppressWarnings("PMD.NoFullyQualifiedTypes")
  public ExecMessage buildConstructorMessageEphemeral(
      Context context,
      Object sender,
      ObjectRef senderObjRef,
      Object[] args,
      ObjectRef[] argObjRefs) {

    final ConstructorCall cc = TlScratchHolder.cc();

    // ----- payload (reuse) -----
    final ConstructorSignature sig = (ConstructorSignature) context.getSignature();
    // clazz + modifiers
    cc.clazz = getWrappedClass(sig.getDeclaringTypeName());
    cc.modifiers = sig.getModifiers();

    // parameters from context statics
    final MessageStatics stat = MessageStaticsFactory.forConstructor(context);
    final Parameter[] params = buildParamsFlyweight(stat, args, argObjRefs);
    if (params != null) {
      cc.parameters = params;
    }

    // optional source context
    {
      final io.quasient.pal.messages.colfer.Context c =
          buildContextIfEnabled(context, sender, senderObjRef);
      if (c != null) {
        cc.context = c;
      }
    }

    // ----- ExecMessage header (reuse) -----
    final ExecMessage m = TlScratchHolder.exec();

    fillExecHeader(m);

    m.constructorCall = cc;
    return m;
  }

  /**
   * Builds an {@link ExecMessage} for invoking a class (static) method with specified arguments and
   * object references.
   *
   * @param context the execution context containing method signature information
   * @param sender the object sending the message
   * @param senderObjRef the reference to the sender object
   * @param args the array of argument values corresponding to the parameters
   * @param argObjRefs the array of object references corresponding to the arguments
   * @return an {@code ExecMessage} representing the class method invocation with context
   */
  @SuppressWarnings("PMD.NoFullyQualifiedTypes")
  public ExecMessage buildClassMethodMessageEphemeral(
      Context context,
      Object sender,
      ObjectRef senderObjRef,
      Object[] args,
      ObjectRef[] argObjRefs) {

    // precomputed statics (no alloc)
    final MessageStatics stat = MessageStaticsFactory.forMethod(context);

    // ---- Fill the inner message in-place (no new) ----
    final ClassMethodCall call = TlScratchHolder.cmc();
    call.clazz = stat.clazz;
    call.name = stat.name;
    call.modifiers = stat.modifiers;

    // Parameters: reuse Parameter[] + Parameter + Obj elements
    final Parameter[] params = buildParamsFlyweight(stat, args, argObjRefs);
    if (params != null) {
      call.parameters = params;
    }

    {
      final io.quasient.pal.messages.colfer.Context c =
          buildContextIfEnabled(context, sender, senderObjRef);
      if (c != null) {
        call.context = c;
      }
    }

    // ---- Build the outer ExecMessage in-place (no new) ----
    final ExecMessage m = TlScratchHolder.exec();

    fillExecHeader(m);
    m.classMethodCall = call;

    return m;
  }

  /**
   * Builds an {@link ExecMessage} for invoking an instance method using execution context.
   *
   * @param context the execution context containing method signature information
   * @param sender the object sending the message
   * @param senderObjRef the reference to the sender object
   * @param targetObjRef the object reference of the target instance on which the method is invoked
   * @param args the array of argument values corresponding to the parameters
   * @param argObjRefs the array of object references corresponding to the arguments
   * @return an {@code ExecMessage} representing the instance method invocation with context
   */
  @SuppressWarnings("PMD.NoFullyQualifiedTypes")
  public ExecMessage buildInstanceMethodMessageEphemeral(
      Context context,
      Object sender,
      ObjectRef senderObjRef,
      ObjectRef targetObjRef,
      Object[] args,
      ObjectRef[] argObjRefs) {

    // precomputed statics (no alloc)
    final MessageStatics stat = MessageStaticsFactory.forMethod(context);

    // ---- Fill the inner message in-place (no new) ----
    final InstanceMethodCall call = TlScratchHolder.imc();
    call.clazz = stat.clazz;
    call.name = stat.name;
    call.modifiers = stat.modifiers;
    call.objectRef = targetObjRef.getRef();

    // Parameters: reuse Parameter[] + Parameter + Obj elements
    final Parameter[] params = buildParamsFlyweight(stat, args, argObjRefs);
    if (params != null) {
      call.parameters = params;
    }

    {
      final io.quasient.pal.messages.colfer.Context c =
          buildContextIfEnabled(context, sender, senderObjRef);
      if (c != null) {
        call.context = c;
      }
    }

    // ---- Build the outer ExecMessage in-place (no new) ----
    final ExecMessage m = TlScratchHolder.exec();
    fillExecHeader(m);
    m.instanceMethodCall = call;

    return m;
  }

  /**
   * Builds an {@link ExecMessage} for performing a field operation.
   *
   * @param context the execution context containing field signature information
   * @param messageType the type of field operation message to build
   * @param sender the object sending the message
   * @param senderObjRef the reference to the sender object
   * @param targetObjRef the reference to the target object for instance field operations
   * @param arg the argument value for the field operation, if applicable
   * @param argObjRef the object reference for the argument, if applicable
   * @return an {@code ExecMessage} representing the field operation
   * @throws IllegalArgumentException if the message type is unexpected
   */
  @SuppressWarnings("PMD.NoFullyQualifiedTypes")
  public ExecMessage buildFieldOpEphemeral(
      Context context,
      MessageType messageType,
      Object sender,
      ObjectRef senderObjRef,
      ObjectRef targetObjRef,
      Object arg,
      ObjectRef argObjRef) {

    final FieldSignature fs = (FieldSignature) context.getSignature();

    // ---- cached flyweights (no alloc) ----
    io.quasient.pal.messages.colfer.Class clazzFly = getWrappedClass(fs.getDeclaringType());
    io.quasient.pal.messages.colfer.Field fieldFly = getWrappedField(fs.getField());

    // ---- optional source context (reuse sc.cctx + sc.senderObj) ----
    io.quasient.pal.messages.colfer.Context cctxBean = TlScratchHolder.cctx();
    if (includeSourceContext) {
      cctxBean.sourceLocationFile = context.getSourceFilename();
      cctxBean.sourceLocationLine = context.getSourceLine();
      cctxBean.sourceLocationType = context.getWithinType().getName();
      if (sender != null) {
        cctxBean.senderClass = getWrappedClass(sender.getClass());
        Obj senderObj = TlScratchHolder.senderObj();
        Wrapper.wrapInto(
            senderObj,
            sender,
            sender.getClass().getName(),
            senderObjRef,
            WrapPolicy.PREFER_REFERENCE);
        cctxBean.sender = senderObj;
      }
    }

    // ---- ExecMessage header (reuse) ----
    final ExecMessage m = TlScratchHolder.exec();
    fillExecHeader(m);

    // ---- payload (reuse) ----
    switch (messageType) {
      case EXEC_GET_FIELD -> {
        final InstanceFieldGet ifg = TlScratchHolder.ifg();
        ifg.clazz = clazzFly;
        ifg.objectRef = (targetObjRef != null) ? targetObjRef.getRef() : 0;
        ifg.field = fieldFly;
        ifg.context = cctxBean;
        m.instanceFieldGet = ifg;
      }
      case EXEC_PUT_FIELD -> {
        final InstanceFieldPut ifp = TlScratchHolder.ifp();
        ifp.clazz = clazzFly;
        ifp.objectRef = (targetObjRef != null) ? targetObjRef.getRef() : 0;
        ifp.field = fieldFly;
        // reuse one Obj holder; prefer reference to avoid JSON
        Obj valObj = TlScratchHolder.valObj();
        Wrapper.wrapInto(valObj, arg, null, argObjRef, WrapPolicy.PREFER_REFERENCE);
        ifp.valueObject = valObj;
        ifp.context = cctxBean;
        m.instanceFieldPut = ifp;
      }
      case EXEC_GET_STATIC -> {
        final StaticFieldGet sfg = TlScratchHolder.sfg();
        sfg.clazz = clazzFly;
        sfg.field = fieldFly;
        sfg.context = cctxBean;
        m.staticFieldGet = sfg;
      }
      case EXEC_PUT_STATIC -> {
        final StaticFieldPut sfp = TlScratchHolder.sfp();
        sfp.clazz = clazzFly;
        sfp.field = fieldFly;
        Obj valObj = TlScratchHolder.valObj();
        Wrapper.wrapInto(valObj, arg, null, argObjRef, WrapPolicy.PREFER_REFERENCE);
        sfp.valueObject = valObj;
        sfp.context = cctxBean;
        m.staticFieldPut = sfp;
      }
      default -> throw new IllegalArgumentException("Unexpected field op type: " + messageType);
    }

    return m;
  }

  /**
   * Helps resolve the class name for the return value based on the AccessibleObject
   *
   * @param ao the accessible object
   * @return the class name of the returned type
   */
  private static String classNameForReturn(AccessibleObject ao) {
    if (ao instanceof Method m) return m.getReturnType().getName();
    if (ao instanceof Constructor<?> c) return c.getDeclaringClass().getName();
    if (ao instanceof Field f) return f.getType().getName();
    return "";
  }

  /**
   * Builds an {@link ExecMessage} representing the return value of an accessible object operation.
   * The message is built by resetting and reusing a {@link TlMsgScratch}, instead of using
   * allocation.
   *
   * @param object the return value object, or {@code null} if the method is void
   * @param accessibleObject the accessible object involved in the operation
   * @param objectRef the reference to the returned object, if applicable
   * @param isVoid {@code true} if the method has a void return type, otherwise {@code false}
   * @param responseToId the message ID this {@code ExecMessage} is responding to
   * @return an {@code ExecMessage} representing the return value
   */
  public ExecMessage buildReturnValueEphemeral(
      Object object,
      AccessibleObject accessibleObject,
      ObjectRef objectRef,
      boolean isVoid,
      String responseToId) {

    // ----- ReturnValue payload (reuse) -----
    final ReturnValue rv = TlScratchHolder.rv();

    if (!isVoid) {
      final String clazzName = classNameForReturn(accessibleObject);
      // Reuse the Obj holder and prefer reference to avoid JSON work
      Obj retObj = TlScratchHolder.retObj();
      Wrapper.wrapInto(retObj, object, clazzName, objectRef, WrapPolicy.PREFER_REFERENCE);
      rv.object = retObj;
    }
    rv.isVoid = isVoid;

    // Build the "from" reflectable using reusable nested beans
    final Reflectable refl = TlScratchHolder.refl();

    if (accessibleObject instanceof Constructor<?> c) {
      var constructor = TlScratchHolder.rc();
      constructor.clazz = getWrappedClass(c.getDeclaringClass());
      refl.constructor = constructor;
    } else if (accessibleObject instanceof Method m) {
      var method = TlScratchHolder.rm();
      method.clazz = getWrappedClass(m.getDeclaringClass());
      method.name = m.getName();
      method.modifiers = m.getModifiers();
      refl.method = method;
    } else if (accessibleObject instanceof Field f) {
      var field = TlScratchHolder.rf();
      field.clazz = getWrappedClass(f.getDeclaringClass());
      field.name = f.getName();
      field.modifiers = f.getModifiers();
      refl.field = field;
    } else {
      throw new RuntimeException("Unsupported AccessibleObject: " + accessibleObject);
    }

    rv.from = refl;

    // ----- ExecMessage header (reuse) -----
    final ExecMessage m = TlScratchHolder.exec();
    fillExecHeader(m);
    if (responseToId != null) {
      m.responseToId = responseToId;
    }

    // attach payload
    m.returnValue = rv;

    return m;
  }

  /**
   * Builds an {@link ExecMessage} representing a throwable thrown during an accessible object
   * operation.
   *
   * @param accessibleObject the accessible object involved in the operation, or {@code null}
   * @param exception the {@code Throwable} that was thrown
   * @param responseToId the message ID this {@code ExecMessage} is responding to
   * @return an {@code ExecMessage} representing the raised throwable
   */
  public ExecMessage buildAccessibleObjectThrowableEphemeral(
      @Nullable AccessibleObject accessibleObject, Throwable exception, String responseToId) {

    // ----- payload (reuse) -----
    final RaisedThrowable rt = TlScratchHolder.rt();

    // Clear previous 'from' branch
    final Reflectable refl = TlScratchHolder.refl();

    // Set 'from' + modifiers when we know the accessible
    if (accessibleObject instanceof Constructor<?> c) {
      var constructor = TlScratchHolder.rc();
      constructor.clazz = getWrappedClass(c.getDeclaringClass());
      refl.constructor = constructor;
      rt.modifiers = c.getModifiers();
    } else if (accessibleObject instanceof Method m) {
      var method = TlScratchHolder.rm();
      method.clazz = getWrappedClass(m.getDeclaringClass());
      method.name = m.getName();
      method.modifiers = m.getModifiers();
      refl.method = method;
      rt.modifiers = m.getModifiers();
    } else if (accessibleObject instanceof Field f) {
      var field = TlScratchHolder.rf();
      field.clazz = getWrappedClass(f.getDeclaringClass());
      field.name = f.getName();
      field.modifiers = f.getModifiers();
      refl.field = field;
      rt.modifiers = f.getModifiers();
    } else {
      // Unknown or null: no 'from', zero modifiers
      rt.modifiers = 0;
    }

    rt.from = (accessibleObject != null) ? refl : null;
    rt.throwable = buildThrowableMessage(exception);

    // ----- ExecMessage header (reuse) -----
    final ExecMessage m = TlScratchHolder.exec();
    fillExecHeader(m);
    if (responseToId != null) {
      m.responseToId = responseToId;
    }

    // attach payload
    m.raisedThrowable = rt;

    return m;
  }

  /**
   * Builds an {@link ExecMessage} indicating the completion of a field operation.
   *
   * @param accessibleObject the accessible object involved in the field operation
   * @param context the execution context containing field signature information
   * @param type the type of field operation completion message to build
   * @return an {@code ExecMessage} representing the field operation completion
   * @throws IllegalArgumentException if accessibleObject is not a Field, or if the completion type
   *     is unexpected
   */
  public ExecMessage buildFieldOpDoneEphemeral(
      AccessibleObject accessibleObject, Context context, MessageType type) {

    final ExecMessage m = TlScratchHolder.exec();
    fillExecHeader(m);

    final FieldSignature fs = (FieldSignature) context.getSignature();
    if (!(accessibleObject instanceof Field field)) {
      throw new IllegalArgumentException(
          "Expected java.lang.reflect.Field, got: " + accessibleObject.getClass());
    }
    switch (type) {
      case EXEC_PUT_FIELD_DONE -> {
        final InstanceFieldPutDone ifpd = TlScratchHolder.ifpd();
        ifpd.clazz = getWrappedClass(fs.getDeclaringType());
        ifpd.field = getWrappedField(field);
        m.instanceFieldPutDone = ifpd;
      }
      case EXEC_PUT_STATIC_DONE -> {
        final StaticFieldPutDone sfpd = TlScratchHolder.sfpd();
        sfpd.clazz = getWrappedClass(fs.getDeclaringType());
        sfpd.field = getWrappedField(field);
        m.staticFieldPutDone = sfpd;
      }
      default -> throw new IllegalArgumentException("Unexpected field op done type: " + type);
    }

    return m;
  }

  // </editor-fold>

  // <editor-fold desc="Constructor messages">

  /**
   * Builds an {@link ExecMessage} for invoking an empty (no-argument) constructor.
   *
   * @param peerUuid the UUID of the peer invoking the constructor
   * @param className the name of the class whose constructor is being invoked
   * @return an {@code ExecMessage} representing the empty constructor invocation
   */
  public ExecMessage buildEmptyConstructor(UUID peerUuid, String className) {
    return buildConstructorMessage(peerUuid, className, null, null, null, null, null, null);
  }

  /**
   * Builds an {@link ExecMessage} for invoking a constructor with parameters.
   *
   * <p>Args must be set either in {@code args} or {@code argObjRefs}. If {@code null} in both, the
   * value is assumed to be {@code null}.
   *
   * @param peerUuid the UUID of the peer invoking the constructor
   * @param className the name of the class whose constructor is being invoked
   * @param parameterTypes the array of parameter type names
   * @param args the array of argument values corresponding to the parameters. For Strings,
   *     primitives, and wrappers.
   * @param argObjRefs the array of object references corresponding to the parameters.
   * @return an {@code ExecMessage} representing the constructor invocation with parameters
   */
  public ExecMessage buildNonEmptyConstructor(
      UUID peerUuid,
      String className,
      String[] parameterTypes,
      Object[] args,
      ObjectRef[] argObjRefs) {
    // Normalize arrays to strict contract: non-null and equal length
    if (parameterTypes == null) {
      parameterTypes = new String[0];
    }
    final int n = parameterTypes.length;
    Object[] normArgs = new Object[n];
    ObjectRef[] normArgRefs = new ObjectRef[n];
    if (args != null) {
      int copyLen = Math.min(args.length, n);
      System.arraycopy(args, 0, normArgs, 0, copyLen);
    }
    if (argObjRefs != null) {
      int copyLen = Math.min(argObjRefs.length, n);
      System.arraycopy(argObjRefs, 0, normArgRefs, 0, copyLen);
    }

    return buildConstructorMessage(
        peerUuid, className, null, null, null, parameterTypes, normArgs, normArgRefs);
  }

  /**
   * Convenience method for building a constructor method message packing all arguments in a single
   * array, regardless of type (ObjectRef or not).
   *
   * @param peerUuid the UUID of this peer
   * @param className the name of the class whose constructor is being invoked
   * @param parameterTypes the array of parameter type names
   * @param args the array of argument values. For Strings, primitives, and wrappers.
   * @param sender the object sending the message
   * @param senderObjRef the reference to the sender object
   * @return an {@code ExecMessage} representing the constructor invocation with mixed argument
   *     types
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
        if (arg instanceof ObjectRef objRefArg) {
          objRefArgs[i] = objRefArg;
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
   * Builds an {@link ExecMessage} for invoking an instance method with arguments.
   *
   * <p>Convenience method for building an instance method message packing all arguments in a single
   * array, regardless of type (ObjectRef or not).
   *
   * @param peerUuid the UUID of this peer
   * @param className the name of the class containing the method
   * @param methodName the name of the method to be invoked
   * @param targetObjRef the object reference of the target instance on which the method is invoked
   * @param parameterTypes the array of parameter type names
   * @param args the array of argument values corresponding to the parameters
   * @return an {@code ExecMessage} representing the instance method invocation
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
        if (arg instanceof ObjectRef objRefArg) {
          objRefArgs[i] = objRefArg;
        } else {
          nonObjRefArgs[i] = arg;
        }
      }
    }

    return buildInstanceMethod(
        peerUuid, className, methodName, targetObjRef, parameterTypes, nonObjRefArgs, objRefArgs);
  }

  /**
   * Builds an {@link ExecMessage} for invoking an instance method with specified arguments and
   * object references.
   *
   * @param peerUuid the UUID of this peer
   * @param className the name of the class containing the method
   * @param methodName the name of the method to be invoked
   * @param targetObjRef the object reference of the target instance on which the method is invoked
   * @param parameterTypes the array of parameter type names
   * @param args the array of argument values
   * @param argObjRefs the array of object references corresponding to the arguments
   * @return an {@code ExecMessage} representing the instance method invocation
   */
  public ExecMessage buildInstanceMethod(
      UUID peerUuid,
      String className,
      String methodName,
      ObjectRef targetObjRef,
      String[] parameterTypes,
      Object[] args,
      ObjectRef[] argObjRefs) {
    final InstanceMethodCall call =
        new InstanceMethodCall()
            .withClazz(getWrappedClass(className))
            .withName(methodName)
            .withObjectRef(targetObjRef.getRef());
    if (parameterTypes != null && args != null && argObjRefs != null) {
      call.setParameters(createNamedParameters(parameterTypes, args, argObjRefs));
    }
    return newExecMessage(peerUuid).withInstanceMethodCall(call);
  }

  // </editor-fold>

  // <editor-fold desc="Return value messages">

  /**
   * Determines the class of the object associated with an accessible object.
   *
   * @param accessibleObject the accessible object involved in the operation
   * @param declaringClass the declaring class of the accessible object
   * @return the {@link Class} of the object associated with the accessible object
   * @throws RuntimeException if the accessible object type is unsupported
   */
  private static Class<?> getClassOfAccessible(
      AccessibleObject accessibleObject, Class<?> declaringClass) {
    Class<?> objectClass;
    if (accessibleObject instanceof Constructor) {
      objectClass = declaringClass;
    } else if (accessibleObject instanceof Method method) {
      objectClass = method.getReturnType();
    } else if (accessibleObject instanceof Field field) {
      objectClass = field.getType();
    } else {
      throw new RuntimeException(
          String.format("Unable to handle accessible object of type: %s", accessibleObject));
    }
    return objectClass;
  }

  /**
   * Builds an {@link ExecMessage} representing the return value of an accessible object operation.
   * The message is built anew, using allocation.
   *
   * @param object the return value object, or {@code null} if the method is void
   * @param accessibleObject the accessible object involved in the operation
   * @param objectRef the reference to the returned object, if applicable
   * @param isVoid {@code true} if the method has a void return type, otherwise {@code false}
   * @param responseToId the message ID this {@code ExecMessage} is responding to
   * @return an {@code ExecMessage} representing the return value
   * @throws IllegalArgumentException if accessibleObject is not a Member (Field, Method, or
   *     Constructor)
   */
  @SuppressWarnings("PMD.NoFullyQualifiedTypes")
  public ExecMessage buildReturnValue(
      Object object,
      AccessibleObject accessibleObject,
      ObjectRef objectRef,
      boolean isVoid,
      String responseToId) {

    final ReturnValue valueMessage = new ReturnValue();

    if (!(accessibleObject instanceof Member member)) {
      throw new IllegalArgumentException(
          "Expected Member (Field, Method, or Constructor), got: " + accessibleObject.getClass());
    }
    Class<?> declaringClass = member.getDeclaringClass();

    // set 'object'
    if (!isVoid) {
      Class<?> objectClass = getClassOfAccessible(accessibleObject, declaringClass);
      if (logger.isTraceEnabled()) {
        if (object != null) {
          logger.trace("object is of class: {}", object.getClass().getName());
        }
        logger.trace("objectClass.getName: {}", objectClass.getName());
      }
      valueMessage.setObject(
          getWrappedObject(object, objectClass.getName(), objectRef, WrapPolicy.PREFER_REFERENCE));
    }

    // set 'from'
    if (accessibleObject instanceof Constructor) {
      valueMessage.setFrom(
          new Reflectable()
              .withConstructor(
                  new io.quasient.pal.messages.colfer.Constructor()
                      .withClazz(getWrappedClass(declaringClass.getName()))));
    } else if (accessibleObject instanceof Method method) {
      valueMessage.setFrom(
          new Reflectable()
              .withMethod(
                  new io.quasient.pal.messages.colfer.Method()
                      .withClazz(getWrappedClass(declaringClass.getName()))
                      .withName(method.getName())
                      .withModifiers(method.getModifiers())));
    } else if (accessibleObject instanceof Field field) {
      valueMessage.setFrom(
          new Reflectable()
              .withField(
                  new io.quasient.pal.messages.colfer.Field()
                      .withClazz(getWrappedClass(declaringClass.getName()))
                      .withName(field.getName())));
    } else {
      throw new RuntimeException(
          String.format("Unable to handle accessible object of type: %s", accessibleObject));
    }

    // set class and getIsVoid
    return newExecMessage(peerId, responseToId).withReturnValue(valueMessage.withIsVoid(isVoid));
  }

  // </editor-fold>

  // <editor-fold desc="Throwable messages">

  /**
   * Builds an {@link ExecMessage} representing a throwable thrown during an accessible object
   * operation.
   *
   * @param peerUuid the UUID of the peer involved in the operation
   * @param accessibleObject the accessible object involved in the operation, or {@code null}
   * @param exception the {@code Throwable} that was thrown
   * @param responseToId the message ID this {@code ExecMessage} is responding to
   * @return an {@code ExecMessage} representing the raised throwable
   */
  @SuppressWarnings("PMD.NoFullyQualifiedTypes")
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
                    new io.quasient.pal.messages.colfer.Constructor()
                        .withClazz(
                            getWrappedClass(
                                ((Constructor<?>) accessibleObject)
                                    .getDeclaringClass()
                                    .getName()))));
        raisedThrowable.setModifiers(((Constructor<?>) accessibleObject).getModifiers());
      } else if (accessibleObject instanceof Method method) {
        raisedThrowable.setFrom(
            new Reflectable()
                .withMethod(
                    new io.quasient.pal.messages.colfer.Method()
                        .withClazz(getWrappedClass(method.getDeclaringClass().getName()))
                        .withName(method.getName())
                        .withModifiers(method.getModifiers())));
        raisedThrowable.setModifiers(method.getModifiers());
      } else if (accessibleObject instanceof Field field) {
        raisedThrowable.setFrom(
            new Reflectable()
                .withField(
                    new io.quasient.pal.messages.colfer.Field()
                        .withClazz(getWrappedClass(field.getDeclaringClass().getName()))
                        .withName(field.getName())));
        raisedThrowable.setModifiers(field.getModifiers());
      } else {
        throw new UnsupportedOperationException(
            String.format(
                "Unsupported accessibleObject type: %s", accessibleObject.getClass().getName()));
      }
    }
    return newExecMessage(peerUuid, responseToId)
        .withRaisedThrowable(raisedThrowable.withThrowable(buildThrowableMessage(exception)));
  }

  // </editor-fold>

  // <editor-fold desc="Class method messages">

  /**
   * Builds an {@link ExecMessage} for invoking a class (static) method with arguments.
   *
   * @param peerUuid the UUID of this peer
   * @param className the name of the class containing the method
   * @param methodName the name of the method to be invoked
   * @param parameterTypes the array of parameter type names
   * @param sender the object sending the message
   * @param senderObjRef the reference to the sender object
   * @param args the array of argument values corresponding to the parameters
   * @param argObjRefs the array of objectRefs corresponding to the parameters
   * @return an {@code ExecMessage} representing the class method invocation
   */
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
        new ClassMethodCall().withClazz(getWrappedClass(className)).withName(methodName);
    if (parameterTypes != null && args != null && argObjRefs != null) {
      classMethodCall.setParameters(createNamedParameters(parameterTypes, args, argObjRefs));
    }

    if (includeSourceContext) {
      classMethodCall.setContext(getWrappedContext(null, sender, senderObjRef));
    }
    return newExecMessage(peerUuid).withClassMethodCall(classMethodCall);
  }

  /**
   * Builds an {@link ExecMessage} for invoking a class (static) method with arguments.
   *
   * <p>Convenience method for building a class method message packing all arguments in a single
   * array, regardless of type (ObjectRef or not).
   *
   * @param peerUuid the UUID of this peer
   * @param className the name of the class containing the method
   * @param methodName the name of the method to be invoked
   * @param parameterTypes the array of parameter type names
   * @param sender the object sending the message
   * @param senderObjRef the reference to the sender object
   * @param args the array of argument values corresponding to the parameters
   * @return an {@code ExecMessage} representing the class method invocation
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
        if (arg instanceof ObjectRef objRefArg) {
          objectRefArgs[i] = objRefArg;
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

  // </editor-fold>

  // <editor-fold desc="Field op messages">

  /**
   * Builds an {@link ExecMessage} for performing a field operation.
   *
   * @param peerUuid the UUID of the peer performing the operation
   * @param context the execution context containing field signature information
   * @param messageType the type of field operation message to build
   * @param sender the object sending the message
   * @param senderObjRef the reference to the sender object
   * @param targetObjRef the reference to the target object for instance field operations
   * @param arg the argument value for the field operation, if applicable
   * @param argObjRef the object reference for the argument, if applicable
   * @return an {@code ExecMessage} representing the field operation
   * @throws IllegalArgumentException if the message type is unexpected
   */
  @SuppressWarnings("PMD.NoFullyQualifiedTypes")
  public ExecMessage buildFieldOp(
      UUID peerUuid,
      Context context,
      MessageType messageType,
      Object sender,
      ObjectRef senderObjRef,
      ObjectRef targetObjRef,
      Object arg,
      ObjectRef argObjRef) {
    final FieldSignature fieldSignature = (FieldSignature) context.getSignature();
    io.quasient.pal.messages.colfer.Class clazz =
        getWrappedClass(fieldSignature.getDeclaringType());
    io.quasient.pal.messages.colfer.Field field =
        getWrappedField(
            fieldSignature.getFieldType(), fieldSignature.getName(), fieldSignature.getModifiers());
    io.quasient.pal.messages.colfer.Context ctxt =
        includeSourceContext ? getWrappedContext(context, sender, senderObjRef) : null;
    final ExecMessage execMessage = newExecMessage(peerUuid);
    switch (messageType) {
      case EXEC_GET_FIELD ->
          execMessage.setInstanceFieldGet(
              new InstanceFieldGet()
                  .withClazz(clazz)
                  .withObjectRef(targetObjRef.getRef())
                  .withField(field)
                  .withContext(ctxt));
      case EXEC_PUT_FIELD ->
          execMessage.setInstanceFieldPut(
              new InstanceFieldPut()
                  .withClazz(clazz)
                  .withObjectRef(targetObjRef.getRef())
                  .withField(field)
                  .withValueObject(
                      getWrappedObject(arg, null, argObjRef, WrapPolicy.PREFER_REFERENCE))
                  .withContext(ctxt));
      case EXEC_GET_STATIC ->
          execMessage.setStaticFieldGet(
              new StaticFieldGet().withClazz(clazz).withField(field).withContext(ctxt));
      case EXEC_PUT_STATIC ->
          execMessage.setStaticFieldPut(
              new StaticFieldPut()
                  .withClazz(clazz)
                  .withValueObject(
                      getWrappedObject(arg, null, argObjRef, WrapPolicy.PREFER_REFERENCE))
                  .withField(field)
                  .withContext(ctxt));
      default -> throw new IllegalArgumentException("Unexpected field op type: " + messageType);
    }
    return execMessage;
  }

  /**
   * Builds an {@link ExecMessage} indicating the completion of a field operation.
   *
   * @param peerUuid the UUID of the peer indicating the operation completion
   * @param accessibleObject the accessible object involved in the field operation
   * @param context the execution context containing field signature information
   * @param type the type of field operation completion message to build
   * @return an {@code ExecMessage} representing the field operation completion
   * @throws IllegalArgumentException if accessibleObject is not a Field, or if the completion type
   *     is unexpected
   */
  public ExecMessage buildFieldOpDone(
      UUID peerUuid, AccessibleObject accessibleObject, Context context, MessageType type) {
    if (!(accessibleObject instanceof Field field)) {
      throw new IllegalArgumentException(
          "Expected java.lang.reflect.Field, got: " + accessibleObject.getClass());
    }
    final FieldSignature fieldSignature = (FieldSignature) context.getSignature();
    final ExecMessage execMessage = newExecMessage(peerUuid);
    switch (type) {
      case EXEC_PUT_FIELD_DONE ->
          execMessage.setInstanceFieldPutDone(
              new InstanceFieldPutDone()
                  .withClazz(getWrappedClass(fieldSignature.getDeclaringType()))
                  .withField(getWrappedField(field)));
      case EXEC_PUT_STATIC_DONE ->
          execMessage.setStaticFieldPutDone(
              new StaticFieldPutDone()
                  .withClazz(getWrappedClass(fieldSignature.getDeclaringType()))
                  .withField(getWrappedField(field)));
      default -> throw new IllegalArgumentException("Unexpected field op done type: " + type);
    }
    return execMessage;
  }

  /**
   * Builds an {@link ExecMessage} indicating the completion of an instance field put operation.
   *
   * @param peerUuid the UUID of the peer indicating the operation completion
   * @param accessibleObject the accessible object involved in the field operation
   * @param instanceFieldPutId the ID of the instance field put operation
   * @param responseToId the message ID this {@code ExecMessage} is responding to
   * @return an {@code ExecMessage} representing the completion of the instance field put operation
   * @throws IllegalArgumentException if accessibleObject is not a Field
   */
  public ExecMessage buildPutObjectDone(
      UUID peerUuid,
      AccessibleObject accessibleObject,
      String instanceFieldPutId,
      String responseToId) {
    if (!(accessibleObject instanceof Field field)) {
      throw new IllegalArgumentException(
          "Expected java.lang.reflect.Field, got: " + accessibleObject.getClass());
    }
    return newExecMessage(peerUuid, responseToId)
        .withInstanceFieldPutDone(
            new InstanceFieldPutDone()
                .withClazz(getWrappedClass(field.getDeclaringClass()))
                .withField(getWrappedField(field))
                .withInstanceFieldPutId(instanceFieldPutId));
  }

  /**
   * Builds an {@link ExecMessage} indicating the completion of a static field put operation.
   *
   * @param peerUuid the UUID of the peer indicating the operation completion
   * @param accessibleObject the accessible object involved in the field operation
   * @param staticFieldPutId the ID of the static field put operation
   * @param responseToId the message ID this {@code ExecMessage} is responding to
   * @return an {@code ExecMessage} representing the completion of the static field put operation
   * @throws IllegalArgumentException if accessibleObject is not a Field
   */
  public ExecMessage buildPutStaticDone(
      UUID peerUuid,
      AccessibleObject accessibleObject,
      String staticFieldPutId,
      String responseToId) {
    if (!(accessibleObject instanceof Field field)) {
      throw new IllegalArgumentException(
          "Expected java.lang.reflect.Field, got: " + accessibleObject.getClass());
    }
    return newExecMessage(peerUuid, responseToId)
        .withStaticFieldPutDone(
            new StaticFieldPutDone()
                .withClazz(getWrappedClass(field.getDeclaringClass()))
                .withField(getWrappedField(field))
                .withStaticFieldPutId(staticFieldPutId));
  }

  // </editor-fold>

  // <editor-fold desc="Static field get messages">

  /**
   * Builds an {@link ExecMessage} for getting the value of a static field.
   *
   * @param peerUuid the UUID of the peer performing the operation
   * @param className the name of the class containing the static field
   * @param fieldName the name of the static field to get
   * @return an {@code ExecMessage} representing the static field get operation
   */
  public ExecMessage buildGetStatic(UUID peerUuid, String className, String fieldName) {
    int unknownModifiers = 0;
    return newExecMessage(peerUuid)
        .withStaticFieldGet(
            new StaticFieldGet()
                .withClazz(getWrappedClass(className))
                .withField(getWrappedField(className, fieldName, unknownModifiers)));
  }

  // </editor-fold>

  // <editor-fold desc="Instance field get messages">

  /**
   * Builds an {@link ExecMessage} for getting the value of an instance field.
   *
   * @param peerUuid the UUID of the peer performing the operation
   * @param className the name of the class containing the instance field
   * @param fieldName the name of the instance field to get
   * @param targetObjRef the reference to the target object whose field value is to be retrieved
   * @return an {@code ExecMessage} representing the instance field get operation
   */
  public ExecMessage buildGetObject(
      UUID peerUuid, String className, String fieldName, ObjectRef targetObjRef) {
    int unknownModifiers = 0;
    return newExecMessage(peerUuid)
        .withInstanceFieldGet(
            new InstanceFieldGet()
                .withClazz(getWrappedClass(className))
                .withObjectRef(targetObjRef.getRef())
                .withField(getWrappedField((String) null, fieldName, unknownModifiers)));
  }

  // </editor-fold>

  // <editor-fold desc="Static field put messages">

  /**
   * Builds an {@link ExecMessage} for setting the value of a static field.
   *
   * @param peerUuid the UUID of the peer performing the operation
   * @param className the name of the class containing the static field
   * @param fieldName the name of the static field to set
   * @param valueClassName the class name of the value being set
   * @param value the value to set in the static field
   * @return an {@code ExecMessage} representing the static field put operation
   */
  public ExecMessage buildPutStatic(
      UUID peerUuid, String className, String fieldName, String valueClassName, Object value) {
    int unknownModifiers = 0;
    return newExecMessage(peerUuid)
        .withStaticFieldPut(
            new StaticFieldPut()
                .withClazz(getWrappedClass(className))
                .withField(getWrappedField((String) null, fieldName, unknownModifiers))
                .withValueObject(
                    getWrappedObject(value, valueClassName, null, WrapPolicy.PREFER_REFERENCE)));
  }

  /**
   * Builds an {@link ExecMessage} for setting the value of a static field using an object
   * reference.
   *
   * @param peerUuid the UUID of the peer performing the operation
   * @param className the name of the class containing the static field
   * @param fieldName the name of the static field to set
   * @param valueObjectRef the object reference of the value to set in the static field
   * @return an {@code ExecMessage} representing the static field put operation
   */
  public ExecMessage buildPutStatic(
      UUID peerUuid, String className, String fieldName, ObjectRef valueObjectRef) {
    int unknownModifiers = 0;
    return newExecMessage(peerUuid)
        .withStaticFieldPut(
            new StaticFieldPut()
                .withClazz(getWrappedClass(className))
                .withField(getWrappedField((String) null, fieldName, unknownModifiers))
                .withValueObjectRef(valueObjectRef.getRef()));
  }

  // </editor-fold>

  // <editor-fold desc="Instance field put messages">

  /**
   * Builds an {@link ExecMessage} for setting the value of an instance field.
   *
   * @param peerUuid the UUID of the peer performing the operation
   * @param className the name of the class containing the instance field
   * @param fieldName the name of the instance field to set
   * @param targetObjRef the reference to the target object whose field value is to be set
   * @param valueClassName the class name of the value being set
   * @param value the value to set in the instance field
   * @return an {@code ExecMessage} representing the instance field put operation
   */
  public ExecMessage buildPutObject(
      UUID peerUuid,
      String className,
      String fieldName,
      ObjectRef targetObjRef,
      String valueClassName,
      Object value) {
    int unknownModifiers = 0;
    return newExecMessage(peerUuid, null)
        .withInstanceFieldPut(
            new InstanceFieldPut()
                .withClazz(getWrappedClass(className))
                .withObjectRef(targetObjRef.getRef())
                .withField(getWrappedField((String) null, fieldName, unknownModifiers))
                .withValueObject(
                    getWrappedObject(value, valueClassName, null, WrapPolicy.PREFER_REFERENCE)));
  }

  /**
   * Builds an {@link ExecMessage} for setting the value of an instance field using an object
   * reference.
   *
   * @param peerUuid the UUID of the peer performing the operation
   * @param className the name of the class containing the instance field
   * @param fieldName the name of the instance field to set
   * @param targetObjRef the reference to the target object whose field value is to be set
   * @param valueObjectRef the object reference of the value to set in the instance field
   * @return an {@code ExecMessage} representing the instance field put operation
   */
  public ExecMessage buildPutObject(
      UUID peerUuid,
      String className,
      String fieldName,
      ObjectRef targetObjRef,
      ObjectRef valueObjectRef) {
    int unknownModifiers = 0;
    return newExecMessage(peerUuid, null)
        .withInstanceFieldPut(
            new InstanceFieldPut()
                .withClazz(getWrappedClass(className))
                .withObjectRef(targetObjRef.getRef())
                .withField(getWrappedField((String) null, fieldName, unknownModifiers))
                .withValueObjectRef(valueObjectRef.getRef()));
  }

  // </editor-fold>

  // <editor-fold desc="Intercept messages">
  /**
   * Builds an {@link InterceptCallbackRequestMessage} from intercept metadata and execution
   * context.
   *
   * <p>This method constructs a callback request message that will be sent to the callback peer.
   * The request contains all necessary information for the callback handler to execute, including:
   *
   * <ul>
   *   <li>Callback routing information (class, method, peer UUID)
   *   <li>The original execution message
   *   <li>Phase-specific data (arguments for BEFORE, return value/exception for AFTER)
   * </ul>
   *
   * <p><b>IMPORTANT - ExecMessage Cloning:</b> The {@code execMessage} parameter is cloned before
   * being stored in the request. This is critical because {@link ExecMessage} objects may be
   * obtained from {@link io.quasient.pal.serdes.colfer.scratches.TlScratchHolder#exec()
   * TlScratchHolder.exec()}, which returns thread-local reusable objects. During return value
   * serialization (e.g., calling {@link #serializeObjectForCallback(Object)}), field accesses on
   * the return value object can trigger nested dispatch operations that reuse and mutate the same
   * {@code ExecMessage} instance. By cloning first, we preserve the original execution context for
   * the callback.
   *
   * @param peerUuid the UUID of the peer being intercepted
   * @param interceptMessage the intercept message containing callback routing info
   * @param execMessage the execution message with operation metadata (will be cloned)
   * @param phase the callback phase (BEFORE or AFTER)
   * @param returnValue the return value (AFTER phase only, may be null)
   * @param isVoid whether the method is void
   * @param thrownException the thrown exception (AFTER phase only, may be null)
   * @return the constructed callback request
   */
  @SuppressWarnings("PMD.NoFullyQualifiedTypes")
  public InterceptCallbackRequestMessage buildInterceptCallbackRequest(
      UUID peerUuid,
      InterceptMessage interceptMessage,
      ExecMessage execMessage,
      io.quasient.pal.common.lang.intercept.InterceptPhase phase,
      Object returnValue,
      boolean isVoid,
      Throwable thrownException) {

    InterceptCallbackRequestMessage request = new InterceptCallbackRequestMessage();

    // Set unique callback ID
    request.setCallbackId(UUID.randomUUID().toString());

    // Set phase and type
    request.setPhase(phase.toByte());
    request.setInterceptType(interceptMessage.getInterceptType());

    // Set peer info
    request.setInterceptedPeer(peerUuid.toString());

    // Set callback routing info from intercept message
    request.setCallbackClass(interceptMessage.getCallbackClass());
    request.setCallbackMethod(interceptMessage.getCallbackMethod());

    // CRITICAL: Clone the execMessage BEFORE any operations that might trigger nested dispatches.
    // The execMessage may be a thread-local reusable object from TlScratchHolder.exec().
    // Serializing the return value (below) can access object fields, triggering field-get
    // dispatches that reuse and mutate the same ExecMessage instance. Cloning preserves
    // the original execution context.
    ExecMessage clonedExec = cloneExecMessage(execMessage);
    request.setExec(clonedExec);

    // Set phase-specific fields
    if (phase == io.quasient.pal.common.lang.intercept.InterceptPhase.AFTER) {
      request.setIsVoid(isVoid);
      if (!isVoid && returnValue != null) {
        request.setReturnValue(serializeObjectForCallback(returnValue));
      }
      if (thrownException != null) {
        request.setThrownException(ExceptionSerdes.serializeException(thrownException));
      }

      // For AROUND AFTER callbacks, the exec message may be from BEFORE phase and lack
      // return value. Only set if not already present (regular AFTER callbacks have it).
      if (clonedExec.getReturnValue() == null) {
        ReturnValue rv = new ReturnValue();
        rv.isVoid = isVoid;
        if (!isVoid && returnValue != null) {
          rv.object = serializeObjectForCallback(returnValue);
        }
        clonedExec.setReturnValue(rv);
      }

      // For field PUT operations, convert BEFORE phase message to AFTER phase format.
      // Use the exec message ID as the PUT ID for correlation.
      if (clonedExec.getInstanceFieldPut() != null
          && clonedExec.getInstanceFieldPutDone() == null) {
        InstanceFieldPut put = clonedExec.getInstanceFieldPut();
        clonedExec.setInstanceFieldPutDone(
            new InstanceFieldPutDone()
                .withClazz(put.getClazz())
                .withField(put.getField())
                .withInstanceFieldPutId(clonedExec.getMessageId()));
        clonedExec.setInstanceFieldPut(null);
      }
      if (clonedExec.getStaticFieldPut() != null && clonedExec.getStaticFieldPutDone() == null) {
        StaticFieldPut put = clonedExec.getStaticFieldPut();
        clonedExec.setStaticFieldPutDone(
            new StaticFieldPutDone()
                .withClazz(put.getClazz())
                .withField(put.getField())
                .withStaticFieldPutId(clonedExec.getMessageId()));
        clonedExec.setStaticFieldPut(null);
      }
    }

    return request;
  }

  /**
   * Creates a deep copy of an {@link ExecMessage} by marshaling and unmarshaling.
   *
   * <p>This method is used to preserve the state of an ExecMessage before operations that might
   * trigger nested dispatches which could reuse and mutate thread-local ExecMessage instances.
   *
   * @param original the ExecMessage to clone
   * @return a new ExecMessage instance with the same data
   */
  private ExecMessage cloneExecMessage(ExecMessage original) {
    byte[] buf = new byte[original.marshalFit()];
    int len = original.marshal(buf, 0);
    ExecMessage clone = new ExecMessage();
    clone.unmarshal(buf, 0, len);
    return clone;
  }

  /**
   * Builds an {@link InterceptMessage} for intercepting a method or field operation.
   *
   * @param peerUuid the UUID of the peer initiating the interception
   * @param type the type of interception
   * @param className the name of the class containing the interceptable
   * @param methodName the name of the method to intercept, or {@code null} if intercepting a field
   * @param parameterTypes the list of parameter type names for method interception, or {@code null}
   *     if intercepting a field
   * @param callbackClassName the name of the callback class to notify upon interception
   * @param callbackMethodName the name of the callback method to invoke
   * @return an {@code InterceptMessage} representing the interception request
   */
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
        .withMessageId(nextId())
        .withClazz(className)
        .withMethod(
            new InterceptableMethod()
                .withName(methodName)
                .withParameterTypes(parameterTypes.toArray(new String[0])))
        .withCallbackClass(callbackClassName)
        .withCallbackMethod(callbackMethodName);
  }

  /**
   * Builds an {@link InterceptMessage} for intercepting a field operation.
   *
   * @param peerUuid the UUID of the peer initiating the interception
   * @param type the type of interception
   * @param className the name of the class containing the field to intercept
   * @param fieldName the name of the field to intercept
   * @param fieldOpType the type of field operation to intercept
   * @param callbackClassName the name of the callback class to notify upon interception
   * @param callbackMethodName the name of the callback method to invoke
   * @return an {@code InterceptMessage} representing the interception request
   */
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
        .withMessageId(nextId())
        .withClazz(className)
        .withField(
            new InterceptableField().withName(fieldName).withFieldOpType(fieldOpType.toByte()))
        .withCallbackClass(callbackClassName)
        .withCallbackMethod(callbackMethodName);
  }

  /**
   * Builds an {@link InterceptMessage} based on an {@link InterceptRequest}.
   *
   * @param intercept the intercept request containing interception details
   * @return an {@code InterceptMessage} representing the interception request
   */
  @SuppressWarnings("unchecked")
  public InterceptMessage buildInterceptMessage(InterceptRequest<?> intercept) {
    boolean isMethodInterceptable =
        intercept.getInterceptable().getType().equals(InterceptableType.METHOD_CALL);

    // Convert exception policies to bytes: 255 = null (defer to global), 0-3 = enum value
    byte exceptionPropagationPolicyByte =
        intercept.getExceptionPropagationPolicy() != null
            ? intercept.getExceptionPropagationPolicy().toByte()
            : (byte) 255;
    byte checkedExceptionPolicyByte =
        intercept.getCheckedExceptionPolicy() != null
            ? intercept.getCheckedExceptionPolicy().toByte()
            : (byte) 255;

    if (isMethodInterceptable) {
      return new InterceptMessage()
          .withPeerUuid(intercept.getPeer().toString())
          .withInterceptType(intercept.getType().toByte())
          .withMessageId(intercept.getUuid().toString())
          .withClazz(intercept.getClazz())
          .withMethod(
              new InterceptableMethod()
                  .withName(intercept.getInterceptable().getName())
                  .withParameterTypes(
                      ((InterceptRequest<InterceptableMethodCall>) intercept)
                          .getInterceptable()
                          .getParameterTypes()
                          .toArray(new String[0])))
          .withCallbackClass(intercept.getCallbackClass())
          .withCallbackMethod(intercept.getCallbackMethod())
          .withForceImmediate(intercept.isForceImmediate())
          .withExceptionPropagationPolicy(exceptionPropagationPolicyByte)
          .withCheckedExceptionPolicy(checkedExceptionPolicyByte);
    }

    return new InterceptMessage()
        .withPeerUuid(intercept.getPeer().toString())
        .withInterceptType(intercept.getType().toByte())
        .withMessageId(intercept.getUuid().toString())
        .withClazz(intercept.getClazz())
        .withField(
            new InterceptableField()
                .withName(intercept.getInterceptable().getName())
                .withFieldOpType(
                    ((InterceptRequest<InterceptableFieldOp>) intercept)
                        .getInterceptable()
                        .getFieldOpType()
                        .toByte()))
        .withCallbackClass(intercept.getCallbackClass())
        .withCallbackMethod(intercept.getCallbackMethod())
        .withForceImmediate(intercept.isForceImmediate())
        .withExceptionPropagationPolicy(exceptionPropagationPolicyByte)
        .withCheckedExceptionPolicy(checkedExceptionPolicyByte);
  }

  /**
   * Builds an {@link InterceptResponse} after processing an interception request.
   *
   * @param peerUuid the UUID of the peer responding to the interception
   * @param responseToId the message ID this response is addressing
   * @param result the result of the interception processing
   * @return an {@code InterceptResponse} representing the outcome of the interception
   */
  public InterceptResponse buildInterceptResponse(
      UUID peerUuid, String responseToId, boolean result) {
    return new InterceptResponse()
        .withPeerUuid(peerUuid.toString())
        .withResponseToId(responseToId)
        .withResult(result);
  }

  /**
   * Builds an {@link InterceptKeyMessage} based on an existing {@link ExecMessage}.
   *
   * @param execMessage the execution message to extract key information from
   * @return an {@code InterceptKeyMessage} representing the key details of the execution message
   */
  public InterceptKeyMessage buildInterceptKey(ExecMessage execMessage) {
    MessageType execMessageType = getMessageTypeOf(execMessage);
    final InterceptKeyMessage keyMessage =
        new InterceptKeyMessage()
            .withClazz(getClassname(execMessage))
            .withExecutableName(getExecutableName(execMessage))
            .withExecMsgType(execMessageType.getId());
    final List<String> paramTypes = getParameterTypes(execMessage);
    if (paramTypes != null) {
      keyMessage.setParameterTypes(paramTypes.toArray(new String[0]));
    }
    return keyMessage;
  }

  // </editor-fold>

  // <editor-fold desc="JSON-RPC messages">

  /**
   * Converts a list of JSON-RPC {@link Argument} objects to an array of colfer {@link Parameter}
   * objects.
   *
   * @param jsonArgs the list of JSON-RPC arguments
   * @return an array of {@code Parameter} objects representing the binary RPC parameters
   */
  private Parameter[] jsonRpcParamsToColferParams(List<Argument> jsonArgs) {
    if (jsonArgs == null || jsonArgs.isEmpty()) {
      return new Parameter[0];
    }

    Parameter[] colferParams = new Parameter[jsonArgs.size()];
    for (int i = 0; i < jsonArgs.size(); i++) {
      Argument arg = jsonArgs.get(i);
      Obj valueObj;
      // null-safe local var to silence static analysis warnings
      Integer ref = arg.getRef();
      if (ref != null) {
        int refInt = ref;
        ObjectRef objectRef = ObjectRef.from(refInt);
        valueObj = new Obj().withRef(refInt);
        getWrappedObject(null, null, objectRef, WrapPolicy.FORCE_BY_VALUE);
      } else {
        valueObj = getWrappedObject(arg.getValue(), arg.getType(), null, WrapPolicy.FORCE_BY_VALUE);
      }
      String paramName = arg.getName() != null ? arg.getName() : "";
      Parameter param = new Parameter().withName(paramName).withValue(valueObj);
      if (logger.isDebugEnabled()) {
        logger.debug(
            "Converted jsonrpc Argument: {} to binary Parameter: {}",
            arg,
            ColferUtils.toJson(param));
      }
      colferParams[i] = param;
    }
    return colferParams;
  }

  /**
   * Creates an {@link InstanceMethodCall} based on a JSON-RPC {@link JsonRpcRequest}.
   *
   * @param jsonRpcRequest the JSON-RPC request to convert
   * @return an {@code InstanceMethodCall} representing the instance method invocation
   */
  private InstanceMethodCall createInstanceMethodCall(JsonRpcRequest jsonRpcRequest) {
    var callParams = jsonRpcRequest.getParams();
    String className = JsonRpcMessageUtils.getClassName(jsonRpcRequest).orElseThrow();

    InstanceMethodCall instanceMethodCall = new InstanceMethodCall();
    instanceMethodCall.setClazz(getWrappedClass(className));
    instanceMethodCall.setName(callParams.getMethod());
    // null-safe local var to silence static analysis warnings
    Integer inst = callParams.getInstance();
    if (inst != null) {
      instanceMethodCall.setObjectRef(inst);
    }
    instanceMethodCall.setParameters(jsonRpcParamsToColferParams(callParams.getArgs()));
    return instanceMethodCall;
  }

  /**
   * Creates a {@link ClassMethodCall} based on a JSON-RPC {@link JsonRpcRequest}.
   *
   * @param jsonRpcRequest the JSON-RPC request to convert
   * @return a {@code ClassMethodCall} representing the class method invocation
   */
  private ClassMethodCall createClassMethodCall(JsonRpcRequest jsonRpcRequest) {
    var callParams = jsonRpcRequest.getParams();
    String className = JsonRpcMessageUtils.getClassName(jsonRpcRequest).orElseThrow();

    ClassMethodCall classMethodCall = new ClassMethodCall();
    classMethodCall.setClazz(getWrappedClass(className));
    classMethodCall.setName(callParams.getMethod());
    classMethodCall.setParameters(jsonRpcParamsToColferParams(callParams.getArgs()));
    return classMethodCall;
  }

  /**
   * Creates an {@link InstanceFieldPut} based on a JSON-RPC {@link JsonRpcRequest}.
   *
   * @param jsonRpcRequest the JSON-RPC request to convert
   * @return an {@code InstanceFieldPut} representing the instance field set operation
   */
  private InstanceFieldPut createInstanceFieldPut(JsonRpcRequest jsonRpcRequest) {
    var putParams = jsonRpcRequest.getParams();
    String className = JsonRpcMessageUtils.getClassName(jsonRpcRequest).orElseThrow();
    String fieldName = JsonRpcMessageUtils.getFieldName(jsonRpcRequest).orElseThrow();

    int unknownModifiers = 0;
    InstanceFieldPut instanceFieldPut = new InstanceFieldPut();
    instanceFieldPut.setClazz(getWrappedClass(className));
    instanceFieldPut.setField(getWrappedField(className, fieldName, unknownModifiers));
    // null-safe local var to silence static analysis warnings
    Integer inst = putParams.getInstance();
    if (inst != null) {
      instanceFieldPut.setObjectRef(inst);
    }
    Argument value = putParams.getValue();
    // null-safe local var to silence static analysis warnings
    Integer ref = (value != null) ? value.getRef() : null;
    if (ref != null) { // value is an object reference
      instanceFieldPut.setValueObjectRef(ref);
    } else {
      instanceFieldPut.setValueObject(
          getWrappedObject(
              value != null ? value.getValue() : null,
              value != null ? value.getType() : null,
              null,
              WrapPolicy.FORCE_BY_VALUE));
    }
    return instanceFieldPut;
  }

  /**
   * Creates a {@link StaticFieldPut} based on a JSON-RPC {@link JsonRpcRequest}.
   *
   * @param jsonRpcRequest the JSON-RPC request to convert
   * @return a {@code StaticFieldPut} representing the static field set operation
   */
  private StaticFieldPut createStaticFieldPut(JsonRpcRequest jsonRpcRequest) {
    var putParams = jsonRpcRequest.getParams();
    String className = JsonRpcMessageUtils.getClassName(jsonRpcRequest).orElseThrow();
    String fieldName = JsonRpcMessageUtils.getFieldName(jsonRpcRequest).orElseThrow();

    int unknownModifiers = 0;
    StaticFieldPut staticFieldPut = new StaticFieldPut();
    staticFieldPut.setClazz(getWrappedClass(className));
    staticFieldPut.setField(getWrappedField(className, fieldName, unknownModifiers));
    Argument value = putParams.getValue();
    // null-safe local var to silence static analysis warnings
    Integer ref = (value != null) ? value.getRef() : null;
    if (ref != null) { // value is an object reference
      staticFieldPut.setValueObjectRef(ref);
    } else {
      staticFieldPut.setValueObject(
          getWrappedObject(
              value != null ? value.getValue() : null,
              value != null ? value.getType() : null,
              null,
              WrapPolicy.FORCE_BY_VALUE));
    }
    return staticFieldPut;
  }

  /**
   * Creates an {@link InstanceFieldGet} based on a JSON-RPC {@link JsonRpcRequest}.
   *
   * @param jsonRpcRequest the JSON-RPC request to convert
   * @return an {@code InstanceFieldGet} representing the instance field get operation
   */
  @SuppressWarnings("PMD.NoFullyQualifiedTypes")
  private InstanceFieldGet createInstanceFieldGet(JsonRpcRequest jsonRpcRequest) {
    var getParams = jsonRpcRequest.getParams();
    String className = JsonRpcMessageUtils.getClassName(jsonRpcRequest).orElseThrow();
    String fieldName = JsonRpcMessageUtils.getFieldName(jsonRpcRequest).orElseThrow();

    InstanceFieldGet instanceFieldGet = new InstanceFieldGet();
    instanceFieldGet.setClazz(new io.quasient.pal.messages.colfer.Class().withName(className));
    instanceFieldGet.setField(new io.quasient.pal.messages.colfer.Field().withName(fieldName));
    // null-safe local var to silence static analysis warnings
    Integer inst = getParams.getInstance();
    if (inst != null) {
      instanceFieldGet.setObjectRef(inst);
    }
    return instanceFieldGet;
  }

  /**
   * Creates a {@link StaticFieldGet} based on a JSON-RPC {@link JsonRpcRequest}.
   *
   * @param jsonRpcRequest the JSON-RPC request to convert
   * @return a {@code StaticFieldGet} representing the static field get operation
   */
  @SuppressWarnings("PMD.NoFullyQualifiedTypes")
  private StaticFieldGet createStaticFieldGet(JsonRpcRequest jsonRpcRequest) {
    String className = JsonRpcMessageUtils.getClassName(jsonRpcRequest).orElseThrow();
    String fieldName = JsonRpcMessageUtils.getFieldName(jsonRpcRequest).orElseThrow();

    StaticFieldGet staticFieldGet = new StaticFieldGet();
    staticFieldGet.setClazz(new io.quasient.pal.messages.colfer.Class().withName(className));
    staticFieldGet.setField(new io.quasient.pal.messages.colfer.Field().withName(fieldName));
    return staticFieldGet;
  }

  /**
   * Creates a {@link ConstructorCall} based on a JSON-RPC {@link JsonRpcRequest}.
   *
   * @param jsonRpcRequest the JSON-RPC request to convert
   * @return a {@code ConstructorCall} representing the constructor invocation
   */
  @SuppressWarnings("PMD.NoFullyQualifiedTypes")
  private ConstructorCall createConstructorCall(JsonRpcRequest jsonRpcRequest) {
    var newParams = jsonRpcRequest.getParams();
    String className = JsonRpcMessageUtils.getClassName(jsonRpcRequest).orElseThrow();

    ConstructorCall constructorCall = new ConstructorCall();
    constructorCall.setClazz(new io.quasient.pal.messages.colfer.Class().withName(className));
    constructorCall.setParameters(jsonRpcParamsToColferParams(newParams.getArgs()));
    return constructorCall;
  }

  /**
   * Converts a JSON-RPC {@link JsonRpcRequest} to a binary {@link ExecMessage}.
   *
   * @param jsonRpcRequest the JSON-RPC request to convert
   * @param fromPeerUuid the UUID of the peer sending the request
   * @return a binary {@code Message} representing the executable message
   * @throws IllegalArgumentException if the message type of the JSON-RPC request is unsupported
   */
  public Message jsonRpcRequestToExecMessage(JsonRpcRequest jsonRpcRequest, UUID fromPeerUuid) {

    // Create an instance of ExecMessage and initialize required common fields
    ExecMessage execMessage = new ExecMessage();
    if (fromPeerUuid != null) {
      execMessage.setPeerUuid(fromPeerUuid.toString());
    }
    execMessage.setMessageId(jsonRpcRequest.getId());
    MessageType messageType = JsonRpcMessageUtils.getMessageType(jsonRpcRequest);

    // currentTime is meant for the client to indicate when then message is sent; as we don't have
    // it in a JSON-RPC request, we set it here to the time the message is received
    execMessage.setCurrentTime(Instant.now().toString());

    // Create the appropriate ExecMessage call object based on the ExecMessageType
    switch (messageType) {
      case EXEC_CONSTRUCTOR ->
          execMessage.setConstructorCall(createConstructorCall(jsonRpcRequest));
      case EXEC_GET_STATIC -> execMessage.setStaticFieldGet(createStaticFieldGet(jsonRpcRequest));
      case EXEC_GET_FIELD ->
          execMessage.setInstanceFieldGet(createInstanceFieldGet(jsonRpcRequest));
      case EXEC_PUT_STATIC -> execMessage.setStaticFieldPut(createStaticFieldPut(jsonRpcRequest));
      case EXEC_PUT_FIELD ->
          execMessage.setInstanceFieldPut(createInstanceFieldPut(jsonRpcRequest));
      case EXEC_CLASS_METHOD ->
          execMessage.setClassMethodCall(createClassMethodCall(jsonRpcRequest));
      case EXEC_INSTANCE_METHOD ->
          execMessage.setInstanceMethodCall(createInstanceMethodCall(jsonRpcRequest));
      default -> throw new IllegalArgumentException("Unsupported ExecMessageType: " + messageType);
    }
    return wrap(execMessage);
  }

  /**
   * Converts a JSON-RPC {@link JsonRpcRequest} to a binary {@link MetaMessage}.
   *
   * @param jsonRpcRequest the JSON-RPC request to convert
   * @param fromPeerUuid the UUID of the peer sending the request
   * @return a binary {@code Message} representing the meta message
   * @throws IllegalArgumentException if the meta service type of the JSON-RPC request is unknown
   */
  public Message jsonRpcRequestToMetaMessage(JsonRpcRequest jsonRpcRequest, UUID fromPeerUuid) {
    if (logger.isDebugEnabled()) {
      logger.debug(
          "in jsonRpcRequestToMetaMessage with request: {} from peer: {}",
          jsonRpcRequest,
          fromPeerUuid);
    }

    // get service
    String serviceName = jsonRpcRequest.getParams().getMethod();
    MetaServiceType metaServiceType = MetaServiceType.fromJsonName(serviceName);
    if (metaServiceType == null) {
      throw new IllegalArgumentException("Unknown meta service type: " + serviceName);
    }

    // get params
    List<Argument> args = jsonRpcRequest.getParams().getArgs();
    Parameter[] params = jsonRpcParamsToColferParams(args);

    return wrap(
        buildMetaMessageRequest(fromPeerUuid, jsonRpcRequest.getId(), metaServiceType, params));
  }

  /**
   * Converts a JSON-RPC {@link JsonRpcRequest} to a binary {@link ControlMessage}.
   *
   * @param jsonRpcRequest the JSON-RPC request to convert
   * @param fromPeerUuid the UUID of the peer sending the request
   * @return a binary {@code Message} representing the control message
   * @throws IllegalArgumentException if the control command type of the JSON-RPC request is unknown
   */
  public Message jsonRpcRequestToControlMessage(JsonRpcRequest jsonRpcRequest, UUID fromPeerUuid) {
    if (logger.isDebugEnabled()) {
      logger.debug(
          "in jsonRpcRequestToControlMessage with request: {} from peer: {}",
          jsonRpcRequest,
          fromPeerUuid);
    }

    // get command
    String commandName = jsonRpcRequest.getParams().getMethod();
    ControlCommandType command = ControlCommandType.fromJsonName(commandName);
    if (command == null) {
      throw new IllegalArgumentException("Unknown control command type: " + commandName);
    }

    // get params
    List<Argument> args = jsonRpcRequest.getParams().getArgs();
    Parameter[] params = jsonRpcParamsToColferParams(args);

    // create control message
    ControlMessage controlMessage = buildControlCommandMessage(fromPeerUuid, command, params);

    // set original message id
    controlMessage.setMessageId(jsonRpcRequest.getId());
    return wrap(controlMessage);
  }

  /**
   * Builds a {@link JsonRpcResponse} based on an {@link ExecMessage} response.
   *
   * @param execMessageResponse the {@code ExecMessage} containing the response details
   * @return a {@code JsonRpcResponse} representing the executable message response
   * @throws RuntimeException if the response message type is unexpected
   */
  @SuppressWarnings("PMD.NoFullyQualifiedTypes")
  public JsonRpcResponse jsonRpcResponseFromExecMessageResponse(ExecMessage execMessageResponse) {
    String requestId = execMessageResponse.getResponseToId();

    // Create a JSON-RPC response object
    final JsonRpcResponse jsonRpcResponse = new JsonRpcResponse();
    jsonRpcResponse.setId(requestId);
    MessageType responseMessageType = getMessageTypeOf(execMessageResponse);

    switch (responseMessageType) {
      case EXEC_PUT_STATIC_DONE ->
          jsonRpcResponse.setResult(
              new JsonRpcResponseReturnValue.Builder()
                  .withIsVoid(true)
                  .withFrom(
                      new Executable.Builder()
                          .withClassName(
                              execMessageResponse
                                  .getStaticFieldPutDone()
                                  .getField()
                                  .getClazz()
                                  .getName())
                          .withFieldName(
                              execMessageResponse.getStaticFieldPutDone().getField().getName())
                          .withModifiers(
                              execMessageResponse.getStaticFieldPutDone().getField().getModifiers())
                          .build())
                  .build());
      case EXEC_PUT_FIELD_DONE ->
          jsonRpcResponse.setResult(
              new JsonRpcResponseReturnValue.Builder()
                  .withIsVoid(true)
                  .withFrom(
                      new Executable.Builder()
                          .withClassName(
                              execMessageResponse
                                  .getInstanceFieldPutDone()
                                  .getField()
                                  .getClazz()
                                  .getName())
                          .withFieldName(
                              execMessageResponse.getInstanceFieldPutDone().getField().getName())
                          .withModifiers(
                              execMessageResponse
                                  .getInstanceFieldPutDone()
                                  .getField()
                                  .getModifiers())
                          .build())
                  .build());
      case EXEC_RETURN_VALUE ->
          jsonRpcResponse.setResult(
              ConversionUtils.toResponseReturnValue(execMessageResponse.getReturnValue()));
      case EXEC_THROWABLE -> {
        RaisedThrowable raisedThrowable = execMessageResponse.getRaisedThrowable();
        io.quasient.pal.messages.colfer.Throwable throwable = raisedThrowable.getThrowable();
        Reflectable fromAccessible = raisedThrowable.getFrom();
        JsonRpcErrorData errorData =
            new JsonRpcErrorData.Builder()
                .withRequestId(requestId)
                .withThrowableType(throwable.getType())
                .withMessage(throwable.getMessage())
                .withStackTrace(throwable.getStackTraceElements())
                .build();
        if (fromAccessible != null) {
          errorData.setFrom(ConversionUtils.toJsonRpcFromExecutable(fromAccessible));
        }
        if (isMethodNotFoundError(
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
      }
      default ->
          throw new RuntimeException(
              "Unexpected response message type: " + responseMessageType.name());
    }
    return jsonRpcResponse;
  }

  /**
   * Builds a {@link JsonRpcResponse} based on a {@link MetaMessage} response.
   *
   * @param metaMessageResponse the {@code MetaMessage} containing the response details
   * @return a {@code JsonRpcResponse} representing the meta message response
   * @throws IllegalArgumentException if the response message type is incorrect
   */
  public JsonRpcResponse jsonRpcResponseFromMetaMessageResponse(MetaMessage metaMessageResponse) {
    if (logger.isDebugEnabled()) {
      logger.debug(
          "in jsonRpcResponseFromMetaMessageResponse with MetaMessage w/id: {}",
          metaMessageResponse.getMessageId());
    }

    // Create a JSON-RPC response object
    final JsonRpcResponse jsonRpcResponse = new JsonRpcResponse();

    // the json-rpc response id must be the original request's id
    jsonRpcResponse.setId(metaMessageResponse.getResponseToId());

    MessageType responseMessageType = getMessageTypeOf(metaMessageResponse);
    if (!responseMessageType.equals(META_MESSAGE_RESPONSE)) {
      throw new IllegalArgumentException(
          "Unexpected response message type: " + responseMessageType);
    }

    MetaStatusType statusType = MetaStatusType.fromId(metaMessageResponse.getStatus());
    switch (statusType) {
      case OK:
        jsonRpcResponse.setResult(
            new JsonRpcResponseReturnValue.Builder()
                .withValue(
                    ResponseObject.builder().withValue(metaMessageResponse.getBody()).build())
                .build());
        break;
      case UNSUPPORTED:
        jsonRpcResponse.setError(
            new JsonRpcError(
                JsonRpcErrorCode.METHOD_NOT_FOUND.getCode(),
                JsonRpcErrorCode.METHOD_NOT_FOUND.getMessage()));
        break;
      case ERROR:
        String errorMessage = metaMessageResponse.getBody();
        jsonRpcResponse.setError(
            new JsonRpcError(
                JsonRpcErrorCode.SERVER_ERROR.getCode(),
                JsonRpcErrorCode.SERVER_ERROR.getMessage(),
                JsonRpcErrorData.builder().withMessage(errorMessage).build()));
        break;
      case UNAUTHORIZED:
      default:
        throw new IllegalArgumentException("Response with unsupported status type: " + statusType);
    }

    return jsonRpcResponse;
  }

  /**
   * Builds a {@link JsonRpcResponse} based on a {@link ControlMessage} response.
   *
   * @param controlMessageResponse the {@code ControlMessage} containing the response details
   * @return a {@code JsonRpcResponse} representing the control message response
   * @throws IllegalArgumentException if the response message type is incorrect
   */
  public JsonRpcResponse jsonRpcResponseFromControlMessageResponse(
      ControlMessage controlMessageResponse) {
    if (logger.isDebugEnabled()) {
      logger.debug(
          "in jsonRpcResponseFromControlMessageResponse with control message w/id: {}"
              + " in response to message w/id: {}",
          controlMessageResponse.getMessageId(),
          controlMessageResponse.getResponseToId());
    }

    // Create a JSON-RPC response object
    final JsonRpcResponse jsonRpcResponse = new JsonRpcResponse();

    // the json-rpc response id must be the original request's id
    jsonRpcResponse.setId(controlMessageResponse.getResponseToId());
    MessageType responseMessageType = getMessageTypeOf(controlMessageResponse);
    if (!responseMessageType.equals(CONTROL_MESSAGE_RESPONSE)) {
      throw new IllegalArgumentException(
          "Unexpected response message type: " + responseMessageType);
    }

    ControlStatusType statusType = ControlStatusType.fromId(controlMessageResponse.getStatus());
    String body = controlMessageResponse.getBody();
    switch (statusType) {
      case OK:
        if (body == null || body.isBlank()) {
          jsonRpcResponse.setResult(JsonRpcResponseReturnValue.builder().withIsVoid(true).build());
        } else {
          jsonRpcResponse.setResult(
              JsonRpcResponseReturnValue.builder()
                  .withValue(ResponseObject.builder().withValue(body).build())
                  .build());
        }
        break;
      case UNSUPPORTED:
        jsonRpcResponse.setError(
            new JsonRpcError(
                JsonRpcErrorCode.METHOD_NOT_FOUND.getCode(),
                JsonRpcErrorCode.METHOD_NOT_FOUND.getMessage()));
        break;
      case NO_SUCH_SESSION:
        jsonRpcResponse.setError(
            new JsonRpcError(
                JsonRpcErrorCode.SERVER_ERROR.getCode(),
                JsonRpcErrorCode.SERVER_ERROR.getMessage(),
                JsonRpcErrorData.builder().withMessage("No such session").build()));
        break;
      case NO_SUCH_OBJECT:
        jsonRpcResponse.setError(
            new JsonRpcError(
                JsonRpcErrorCode.SERVER_ERROR.getCode(),
                JsonRpcErrorCode.SERVER_ERROR.getMessage(),
                JsonRpcErrorData.builder().withMessage("No such object").build()));
        break;
      case ERROR:
        JsonRpcError jsonRpcError =
            new JsonRpcError(
                JsonRpcErrorCode.SERVER_ERROR.getCode(),
                JsonRpcErrorCode.SERVER_ERROR.getMessage());
        if (body == null || body.isBlank()) {
          jsonRpcError.setData(JsonRpcErrorData.builder().withMessage(body).build());
        }
        jsonRpcResponse.setError(jsonRpcError);
        break;
      case UNAUTHORIZED:
      default:
        throw new IllegalArgumentException("Response with unsupported status type: " + statusType);
    }

    return jsonRpcResponse;
  }

  /**
   * Builds a {@link JsonRpcResponse} representing an error based on an {@link Exception}.
   *
   * @param exception the exception that occurred
   * @param requestId the ID of the original JSON-RPC request
   * @return a {@code JsonRpcResponse} representing the error
   */
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

  /**
   * Builds an {@link ControlMessage} representing a control command.
   *
   * @param fromPeerUuid the UUID of the peer sending the control command
   * @param command the type of control command to build
   * @param params the array of parameters for the control command, or {@code null} if none
   * @return a {@code ControlMessage} representing the control command
   */
  public ControlMessage buildControlCommandMessage(
      UUID fromPeerUuid, ControlCommandType command, @Nullable Parameter[] params) {
    ControlMessage controlMessage =
        new ControlMessage()
            .withFromPeer(fromPeerUuid.toString())
            .withMessageId(nextId())
            .withCommand(command.getId());

    if (params != null && params.length > 0) {
      controlMessage.setParams(params);
    }
    return controlMessage;
  }

  /**
   * Builds an {@link ControlMessage} representing a control command. This is a convenience method
   * for control commands that take no params.
   *
   * @param fromPeerUuid the UUID of the peer sending the control command
   * @param command the type of control command to build
   * @return a {@code ControlMessage} representing the control command
   */
  public ControlMessage buildControlCommandMessage(UUID fromPeerUuid, ControlCommandType command) {
    return buildControlCommandMessage(fromPeerUuid, command, null);
  }

  /**
   * Builds a {@link ControlMessage} for deleting an object.
   *
   * @param fromPeer the UUID of the peer issuing the delete command
   * @param objectRef the reference of the object to delete
   * @return a {@code ControlMessage} representing the delete object command
   */
  public ControlMessage buildDeleteObjectCommandMessage(UUID fromPeer, ObjectRef objectRef) {
    Parameter[] params =
        new Parameter[] {new Parameter().withValue(new Obj().withRef(objectRef.getRef()))};
    return buildControlCommandMessage(fromPeer, ControlCommandType.DELETE_OBJECT, params);
  }

  /**
   * Builds a {@link ControlMessage} for deleting the current session.
   *
   * @param fromPeer the UUID of the peer issuing the delete session command
   * @return a {@code ControlMessage} representing the delete session command
   */
  public ControlMessage buildDeleteSessionCommandMessage(UUID fromPeer) {
    return buildControlCommandMessage(fromPeer, ControlCommandType.DELETE_SESSION, null);
  }

  /**
   * Builds a {@link ControlMessage} for triggering garbage collection.
   *
   * @param fromPeer the UUID of the peer issuing the garbage collection command
   * @return a {@code ControlMessage} representing the garbage collection command
   */
  public ControlMessage buildGcCommandMessage(UUID fromPeer) {
    return buildControlCommandMessage(fromPeer, ControlCommandType.GC, null);
  }

  /**
   * Builds an {@link ControlMessage} representing a control status (i.e. response) message.
   *
   * @param fromPeerUuid the UUID of the peer sending the status message
   * @param statusType the type of control status
   * @param responseToId the message ID this status message is responding to
   * @param body additional body content of the status message, or {@code null} if none
   * @return a {@code ControlMessage} representing the control status message
   */
  public ControlMessage buildControlStatusMessage(
      UUID fromPeerUuid, ControlStatusType statusType, String responseToId, @Nullable String body) {
    final ControlMessage controlMessage =
        new ControlMessage()
            .withFromPeer(fromPeerUuid.toString())
            .withMessageId(nextId())
            .withResponseToId(responseToId)
            .withStatus(statusType.toId());

    if (body != null && !body.isEmpty()) {
      controlMessage.setBody(body);
    }
    return controlMessage;
  }

  /**
   * Builds an {@link ControlMessage} representing a control status (i.e. response) message without
   * a body.
   *
   * @param fromPeerUuid the UUID of the peer sending the status message
   * @param statusType the type of control status
   * @param responseToId the message ID this status message is responding to
   * @return a {@code ControlMessage} representing the control status message
   */
  public ControlMessage buildControlStatusMessage(
      UUID fromPeerUuid, ControlStatusType statusType, String responseToId) {
    return buildControlStatusMessage(fromPeerUuid, statusType, responseToId, null);
  }

  // </editor-fold>

  // <editor-fold desc="Meta Messages">

  /**
   * Builds a {@link MetaMessage} representing a meta service request.
   *
   * @param fromPeerUuid the UUID of the peer sending the meta message
   * @param requestId the unique ID of the meta message request
   * @param serviceType the type of meta service to request
   * @return a {@code MetaMessage} representing the meta service request
   */
  public MetaMessage buildMetaMessageRequest(
      UUID fromPeerUuid, String requestId, MetaServiceType serviceType) {
    return buildMetaMessageRequest(fromPeerUuid, requestId, serviceType, (Parameter[]) null);
  }

  /**
   * Builds a {@link MetaMessage} representing a meta service request with parameters.
   *
   * <p>Convenience method to pass parameters as a key-value map.
   *
   * @param fromPeerUuid the UUID of the peer sending the meta message
   * @param requestId the unique ID of the meta message request
   * @param serviceType the type of meta service to request
   * @param params the map of parameters to include in the meta message, or {@code null} if none
   * @return a {@code MetaMessage} representing the meta service request with parameters
   */
  public MetaMessage buildMetaMessageRequest(
      UUID fromPeerUuid,
      String requestId,
      MetaServiceType serviceType,
      @Nullable Map<String, Object> params) {
    return buildMetaMessageRequest(
        fromPeerUuid, requestId, serviceType, paramMapToParameters(params));
  }

  /**
   * Builds a {@link MetaMessage} representing a meta service request with parameters.
   *
   * @param fromPeerUuid the UUID of the peer sending the meta message
   * @param requestId the unique ID of the meta message request
   * @param serviceType the type of meta service to request
   * @param params the array of parameters to include in the meta message, or {@code null} if none
   * @return a {@code MetaMessage} representing the meta service request with parameters
   */
  public MetaMessage buildMetaMessageRequest(
      UUID fromPeerUuid,
      String requestId,
      MetaServiceType serviceType,
      @Nullable Parameter[] params) {
    final MetaMessage metaMessage =
        new MetaMessage()
            .withFromPeer(fromPeerUuid.toString())
            .withMessageId(requestId)
            .withService(serviceType.getId());

    if (params != null) {
      metaMessage.setParams(params);
    }
    return metaMessage;
  }

  /**
   * Builds a {@link MetaMessage} representing a meta service response.
   *
   * @param fromPeerUuid the UUID of the peer sending the meta message response
   * @param statusType the status type of the response
   * @param body the body content of the response, or {@code null} if none
   * @param responseToId the message ID this response is addressing
   * @return a {@code MetaMessage} representing the meta service response
   */
  public MetaMessage buildMetaMessageResponse(
      UUID fromPeerUuid,
      MetaServiceType serviceType,
      MetaStatusType statusType,
      @Nullable String body,
      String responseToId) {
    final MetaMessage metaMessage =
        new MetaMessage()
            .withFromPeer(fromPeerUuid.toString())
            .withMessageId(nextId())
            .withResponseToId(responseToId)
            .withService(serviceType.getId())
            .withStatus(statusType.getId());
    ObjectMapper objectMapper = new ObjectMapper();

    if (body != null && !body.isEmpty()) {
      Map<String, String> responseMap = new HashMap<>();
      responseMap.put("service", serviceType.getJsonName());
      responseMap.put("response", body);
      String bodyAsJSON;
      try {
        bodyAsJSON = objectMapper.writeValueAsString(responseMap);
      } catch (JsonProcessingException e) {
        throw new RuntimeException(e);
      }
      metaMessage.setBody(bodyAsJSON);
    }
    return metaMessage;
  }

  // </editor-fold>

  // <editor-fold desc="Message Wrapper">

  /**
   * Wraps an {@link ExecMessage} into a generic {@link Message}.
   *
   * @param execMessage the executable message to wrap
   * @return a {@code Message} containing the executable message
   */
  public Message wrap(ExecMessage execMessage) {
    final MessageType messageType = getMessageTypeOf(execMessage);
    return new Message().withMessageType(messageType.getId()).withExecMessage(execMessage);
  }

  /**
   * Wraps an {@link InterceptMessage} into a generic {@link Message}.
   *
   * @param interceptMessage the intercept message to wrap
   * @return a {@code Message} containing the intercept message
   */
  public Message wrap(InterceptMessage interceptMessage) {
    return new Message()
        .withMessageType(MessageType.INTERCEPT_MESSAGE.getId())
        .withInterceptMessage(interceptMessage);
  }

  /**
   * Wraps an {@link InterceptKeyMessage} into a generic {@link Message}.
   *
   * @param interceptKeyMessage the intercept key message to wrap
   * @return a {@code Message} containing the intercept key message
   */
  public Message wrap(InterceptKeyMessage interceptKeyMessage) {
    return new Message()
        .withMessageType(MessageType.INTERCEPT_KEY.getId())
        .withInterceptKeyMessage(interceptKeyMessage);
  }

  /**
   * Wraps an {@link InterceptResponse} into a generic {@link Message}.
   *
   * @param interceptResponse the intercept response to wrap
   * @return a {@code Message} containing the intercept response
   */
  public Message wrap(InterceptResponse interceptResponse) {
    return new Message()
        .withMessageType(MessageType.INTERCEPT_RESPONSE.getId())
        .withInterceptResponse(interceptResponse);
  }

  /**
   * Serializes an object to Colfer {@link Obj} format with force-by-value semantics.
   *
   * <p>This method is used specifically for intercept callbacks to ensure that actual values are
   * serialized rather than just references. This is important because the callback may be on a
   * different peer that doesn't have access to the same object references.
   *
   * @param value the object to serialize
   * @return the serialized Obj
   */
  private Obj serializeObjectForCallback(Object value) {
    Obj obj = new Obj();
    if (value == null) {
      obj.setIsNull(true);
      return obj;
    }
    String className = value.getClass().getName();
    try {
      return Wrapper.wrapInto(obj, value, className, null, WrapPolicy.FORCE_BY_VALUE);
    } catch (NonWrappableObjectException e) {
      // For non-serializable objects (e.g., newly constructed objects in AFTER callbacks),
      // we still send the callback but mark the value as "not available" by setting
      // only the class name. The callback can still be useful for monitoring/logging.
      logger.warn(
          "Cannot serialize value of type {} for callback, sending without value: {}",
          className,
          e.getMessage());
      obj.setClazz(getWrappedClass(className));
      return obj;
    }
  }

  /**
   * Wraps an {@link InterceptCallbackRequestMessage} into a generic {@link Message}.
   *
   * @param interceptCallbackRequestMessage the intercept callback request to wrap
   * @return a {@code Message} containing the intercept callback request
   */
  public Message wrap(InterceptCallbackRequestMessage interceptCallbackRequestMessage) {
    return new Message()
        .withMessageType(MessageType.INTERCEPT_CALLBACK_REQUEST.getId())
        .withInterceptCallbackRequestMessage(interceptCallbackRequestMessage);
  }

  /**
   * Wraps an {@link InterceptCallbackResponseMessage} into a generic {@link Message}.
   *
   * @param interceptCallbackResponseMessage the intercept callback response to wrap
   * @return a {@code Message} containing the intercept callback response
   */
  public Message wrap(InterceptCallbackResponseMessage interceptCallbackResponseMessage) {
    return new Message()
        .withMessageType(MessageType.INTERCEPT_CALLBACK_RESPONSE.getId())
        .withInterceptCallbackResponseMessage(interceptCallbackResponseMessage);
  }

  /**
   * Wraps a {@link ControlMessage} into a generic {@link Message}.
   *
   * @param controlMessage the control message to wrap
   * @return a {@code Message} containing the control message
   */
  public Message wrap(ControlMessage controlMessage) {
    final MessageType messageType = getMessageTypeOf(controlMessage);
    return new Message().withMessageType(messageType.getId()).withControlMessage(controlMessage);
  }

  /**
   * Wraps a {@link MetaMessage} into a generic {@link Message}.
   *
   * @param metaMessage the meta message to wrap
   * @return a {@code Message} containing the meta message
   */
  public Message wrap(MetaMessage metaMessage) {
    final MessageType messageType = getMessageTypeOf(metaMessage);
    return new Message().withMessageType(messageType.getId()).withMetaMessage(metaMessage);
  }
  // </editor-fold>
}
