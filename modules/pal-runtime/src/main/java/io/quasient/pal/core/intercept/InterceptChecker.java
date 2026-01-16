/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.core.intercept;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.quasient.pal.common.runtime.ExecPhase;
import io.quasient.pal.messages.colfer.InterceptMessage;
import io.quasient.pal.messages.types.MessageType;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.aspectj.lang.reflect.CodeSignature;
import org.aspectj.lang.reflect.ConstructorSignature;
import org.aspectj.lang.reflect.FieldSignature;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Checks for matching intercepts without requiring Context or ExecMessage creation.
 *
 * <p>This class optimizes the hot-path by extracting necessary information directly from the
 * AspectJ {@link ProceedingJoinPoint} to match against registered intercepts. This avoids the
 * overhead of creating Context and ExecMessage objects when they are not needed (e.g., when only
 * intercepts are enabled but no matches are found, or when only local intercepts match).
 *
 * <p>The checker distinguishes between remote intercepts (requiring RPC callbacks) and local
 * intercepts (which can be handled in-process), allowing further optimization opportunities.
 */
@Singleton
@SuppressFBWarnings(
    value = {"EI_EXPOSE_REP2", "PZLA_PREFER_ZERO_LENGTH_ARRAYS"},
    justification =
        "Checker pattern with shared matcher; null return indicates no params vs empty params")
public class InterceptChecker {

  /** Logger instance for debugging intercept matching operations. */
  private static final Logger logger = LoggerFactory.getLogger(InterceptChecker.class);

  /** Matcher responsible for finding registered intercepts that match execution criteria. */
  private final InterceptMatcher interceptMatcher;

  /** UUID of this peer, used to distinguish local intercepts from remote intercepts. */
  private final UUID peerUuid;

  /**
   * Constructs a new InterceptChecker with the specified intercept matcher and peer UUID.
   *
   * <p>The peer UUID is used to determine whether an intercept callback should be handled locally
   * (when the intercept's callback peer matches this peer's UUID) or remotely (when they differ).
   *
   * @param interceptMatcher the matcher used to find registered intercepts
   * @param peerUuid the UUID of this peer
   */
  @Inject
  public InterceptChecker(InterceptMatcher interceptMatcher, UUID peerUuid) {
    this.interceptMatcher = interceptMatcher;
    this.peerUuid = peerUuid;
  }

  /**
   * Checks for matching intercepts using information extracted directly from the
   * ProceedingJoinPoint.
   *
   * <p>This method extracts the class name, executable name, and parameter types from the join
   * point's static part and uses them to query the intercept matcher. It avoids creating Context or
   * ExecMessage objects, reducing allocation overhead on the hot-path.
   *
   * @param pjp the proceeding join point containing execution context
   * @param messageType the type of execution message (e.g., EXEC_INSTANCE_METHOD, EXEC_CONSTRUCTOR)
   * @param phase the execution phase (BEFORE or AFTER)
   * @return an InterceptCheckResult containing matched remote and local intercepts
   */
  public InterceptCheckResult checkIntercepts(
      ProceedingJoinPoint pjp, MessageType messageType, ExecPhase phase) {

    // Extract matching info directly from PJP static part (no Context allocation)
    JoinPoint.StaticPart staticPart = pjp.getStaticPart();
    Signature ajSig = staticPart.getSignature();

    String className = ajSig.getDeclaringTypeName();
    String executableName = extractExecutableName(ajSig);
    String[] paramTypes = extractParamTypes(ajSig);

    return checkIntercepts(className, executableName, paramTypes, messageType, phase);
  }

  /**
   * Checks for matching intercepts using explicitly provided execution context.
   *
   * <p>This method is used by the incoming RPC path ({@code dispatchIncoming()}) where the
   * execution context is extracted from an {@code ExecMessage} rather than an AspectJ join point.
   * It queries the intercept matcher with the provided parameters to find matching intercepts.
   *
   * @param className the fully qualified name of the class being executed
   * @param executableName the name of the method, constructor ("new"), or field being executed
   * @param paramTypes the parameter type names (null for fields)
   * @param messageType the type of execution message (e.g., EXEC_INSTANCE_METHOD, EXEC_CONSTRUCTOR)
   * @param phase the execution phase (BEFORE or AFTER)
   * @return an InterceptCheckResult containing matched remote and local intercepts
   */
  public InterceptCheckResult checkIntercepts(
      String className,
      String executableName,
      String[] paramTypes,
      MessageType messageType,
      ExecPhase phase) {

    if (logger.isDebugEnabled()) {
      logger.debug(
          "Checking intercepts for: {}.{} with params: {}",
          className,
          executableName,
          paramTypes != null ? Arrays.toString(paramTypes) : "null");
    }

    // Match against registered intercepts
    List<InterceptMessage> matches =
        interceptMatcher.getMatchingIntercepts(
            className, executableName, paramTypes, messageType, phase);

    if (logger.isDebugEnabled()) {
      logger.debug("Found {} matching intercepts", matches.size());
    }

    // Separate local vs remote intercepts
    List<InterceptMessage> remoteIntercepts = filterRemoteIntercepts(matches);
    List<InterceptMessage> localIntercepts = filterLocalIntercepts(matches);

    return new InterceptCheckResult(remoteIntercepts, localIntercepts);
  }

  /**
   * Determines whether the provided message type is eligible for intercept processing.
   *
   * @param type the MessageType to evaluate
   * @return true if the type supports intercept handling; false otherwise
   */
  public static boolean isInterceptableType(MessageType type) {
    return switch (type) {
      case EXEC_CONSTRUCTOR,
              EXEC_INSTANCE_METHOD,
              EXEC_CLASS_METHOD,
              EXEC_GET_STATIC,
              EXEC_GET_FIELD,
              EXEC_PUT_STATIC,
              EXEC_PUT_FIELD ->
          true;
      default -> false;
    };
  }

  /**
   * Extracts the executable name from the AspectJ signature.
   *
   * <p>For methods and fields, this returns the signature name. For constructors, this returns the
   * special name "new" to match the convention used in message construction.
   *
   * @param ajSig the AspectJ signature
   * @return the executable name
   * @throws IllegalArgumentException if the signature type is unsupported
   */
  private String extractExecutableName(Signature ajSig) {
    if (ajSig instanceof MethodSignature) {
      return ajSig.getName();
    } else if (ajSig instanceof ConstructorSignature) {
      return "new";
    } else if (ajSig instanceof FieldSignature) {
      return ajSig.getName();
    }
    throw new IllegalArgumentException("Unsupported signature type: " + ajSig.getClass());
  }

  /**
   * Extracts parameter type names from the AspectJ signature.
   *
   * <p>For methods and constructors, this returns an array of fully qualified parameter type names.
   * For fields, this returns null since fields do not have parameters.
   *
   * @param ajSig the AspectJ signature
   * @return array of parameter type names, or null for fields
   */
  private String[] extractParamTypes(Signature ajSig) {
    if (ajSig instanceof CodeSignature codeSig) {
      return Arrays.stream(codeSig.getParameterTypes()).map(Class::getName).toArray(String[]::new);
    }
    return null; // Fields have no params
  }

  /**
   * Filters the list of matched intercepts to return only those that require remote peer callbacks.
   *
   * <p>An intercept is considered remote when its callback peer UUID differs from this peer's UUID.
   * Remote intercepts require ZMQ message passing to invoke the callback on the target peer.
   *
   * @param matches the list of all matched intercepts
   * @return the list of remote intercepts (callbacks on other peers)
   */
  private List<InterceptMessage> filterRemoteIntercepts(List<InterceptMessage> matches) {
    String thisPeerUuidString = peerUuid.toString();
    return matches.stream().filter(im -> !thisPeerUuidString.equals(im.getPeerUuid())).toList();
  }

  /**
   * Filters the list of matched intercepts to return only those that can be handled locally.
   *
   * <p>An intercept is considered local when its callback peer UUID matches this peer's UUID. Local
   * intercepts can be invoked directly without ZMQ message passing overhead, avoiding serialization
   * costs and network latency.
   *
   * @param matches the list of all matched intercepts
   * @return the list of local intercepts (callbacks on this peer)
   */
  private List<InterceptMessage> filterLocalIntercepts(List<InterceptMessage> matches) {
    String thisPeerUuidString = peerUuid.toString();
    return matches.stream().filter(im -> thisPeerUuidString.equals(im.getPeerUuid())).toList();
  }
}
