/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package com.quasient.pal.core.intercept;

import com.quasient.pal.common.runtime.ExecPhase;
import com.quasient.pal.messages.colfer.InterceptMessage;
import com.quasient.pal.messages.types.MessageType;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
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
public class InterceptChecker {

  /** Logger instance for debugging intercept matching operations. */
  private static final Logger logger = LoggerFactory.getLogger(InterceptChecker.class);

  /** Matcher responsible for finding registered intercepts that match execution criteria. */
  private final InterceptMatcher interceptMatcher;

  /**
   * Constructs a new InterceptChecker with the specified intercept matcher.
   *
   * @param interceptMatcher the matcher used to find registered intercepts
   */
  @Inject
  public InterceptChecker(InterceptMatcher interceptMatcher) {
    this.interceptMatcher = interceptMatcher;
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
   * <p>Currently, all intercepts are considered remote. In the future, this method will check the
   * peer UUID to determine if the intercept is local to this JVM.
   *
   * @param matches the list of all matched intercepts
   * @return the list of remote intercepts
   */
  private List<InterceptMessage> filterRemoteIntercepts(List<InterceptMessage> matches) {
    // For now, all intercepts are remote
    // Future: check if interceptMessage.getPeerUuid() equals local peer UUID
    return matches;
  }

  /**
   * Filters the list of matched intercepts to return only those that can be handled locally.
   *
   * <p>Currently, local intercepts are not supported, so this always returns an empty list. This is
   * reserved for future functionality where intercepts can be registered and handled within the
   * same JVM without message passing overhead.
   *
   * @param ignoredMatches the list of all matched intercepts
   * @return the list of local intercepts (currently always empty)
   */
  @SuppressWarnings("unused")
  private List<InterceptMessage> filterLocalIntercepts(List<InterceptMessage> ignoredMatches) {
    // Future implementation: filter for intercepts with local peer UUID
    return Collections.emptyList();
  }
}
