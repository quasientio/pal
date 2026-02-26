/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.core.replay;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.quasient.pal.common.replay.WalEntry;
import io.quasient.pal.core.intercept.ParamTypeExtractor;
import io.quasient.pal.messages.types.MessageType;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import org.aspectj.lang.ProceedingJoinPoint;

/**
 * Immutable signature identifying an operation by its class name, executable name, parameter types,
 * and message type.
 *
 * <p>Used to match live execution (from {@link ProceedingJoinPoint}) against WAL-recorded entries
 * (from {@link WalEntry}) during deterministic replay. The {@link #matches(OperationSignature)}
 * method compares class name, executable name, and parameter types (message type is intentionally
 * excluded from matching since the same operation may be recorded with a different completion
 * type).
 *
 * @param className the fully qualified class name of the operation target
 * @param executableName the method, constructor, or field name
 * @param paramTypes the parameter type names (may be empty for no-arg methods, {@code null} for
 *     fields)
 * @param messageType the EXEC message type classifying the operation
 */
@SuppressFBWarnings(
    value = {"EI_EXPOSE_REP", "EI_EXPOSE_REP2"},
    justification =
        "paramTypes is always stored as an unmodifiable list via the compact constructor")
public record OperationSignature(
    String className, String executableName, List<String> paramTypes, MessageType messageType) {

  /**
   * Compact constructor that defensively wraps {@code paramTypes} as unmodifiable.
   *
   * @param className the fully qualified class name
   * @param executableName the method, constructor, or field name
   * @param paramTypes the parameter types (wrapped as unmodifiable, may be {@code null})
   * @param messageType the EXEC message type
   */
  public OperationSignature {
    paramTypes = paramTypes != null ? Collections.unmodifiableList(paramTypes) : null;
  }

  /**
   * Creates an {@code OperationSignature} from a WAL entry by extracting its recorded fields.
   *
   * @param entry the WAL entry to extract from
   * @return a new {@code OperationSignature} reflecting the entry's class, executable, parameter
   *     types, and message type
   */
  public static OperationSignature fromWalEntry(WalEntry entry) {
    return new OperationSignature(
        entry.getClassName(),
        entry.getExecutableName(),
        entry.getParamTypes(),
        entry.getMessageType());
  }

  /**
   * Creates an {@code OperationSignature} from a live {@link ProceedingJoinPoint} and its
   * corresponding message type.
   *
   * <p>Uses {@code pjp.getSignature().getDeclaringTypeName()} for the class name, {@code
   * pjp.getSignature().getName()} for the executable name, and {@link
   * ParamTypeExtractor#extractFromArgs} for the parameter types (matching the actual runtime types
   * recorded in the WAL rather than the declared signature types).
   *
   * @param pjp the proceeding join point from AspectJ dispatch
   * @param msgType the message type classifying this operation
   * @return a new {@code OperationSignature} reflecting the live operation
   */
  public static OperationSignature fromJoinPoint(ProceedingJoinPoint pjp, MessageType msgType) {
    String className = pjp.getSignature().getDeclaringTypeName();
    String executableName = pjp.getSignature().getName();
    // Normalize constructor names: AspectJ returns "<init>" but the WAL records "new"
    if ("<init>".equals(executableName)) {
      executableName = "new";
    }
    // Use actual argument types (not declared signature types) to match WAL-recorded types.
    // The WAL records runtime types (e.g., String, Double for HashMap.put("milk", 2.49)),
    // while the declared signature types would be (Object, Object).
    String[] paramTypeNames = ParamTypeExtractor.extractFromArgs(pjp.getArgs(), pjp.getSignature());
    List<String> paramTypes = ParamTypeExtractor.asList(paramTypeNames);
    return new OperationSignature(className, executableName, paramTypes, msgType);
  }

  /**
   * Checks whether this signature matches another by comparing class name, executable name, and
   * parameter types.
   *
   * <p>Message type is intentionally excluded from matching since the same logical operation may
   * have different message types between recording and replay.
   *
   * @param other the other signature to compare against
   * @return {@code true} if class name, executable name, and parameter types are equal
   */
  public boolean matches(OperationSignature other) {
    return Objects.equals(this.className, other.className)
        && Objects.equals(this.executableName, other.executableName)
        && paramTypesMatch(this.paramTypes, other.paramTypes);
  }

  /**
   * Compares parameter type lists, treating {@code null} and empty list as equivalent. Fields have
   * no parameters and may be recorded as {@code null} in the WAL but as an empty list from the live
   * PJP.
   *
   * @param a first parameter type list
   * @param b second parameter type list
   * @return {@code true} if both are effectively equal
   */
  private static boolean paramTypesMatch(List<String> a, List<String> b) {
    boolean aEmpty = a == null || a.isEmpty();
    boolean bEmpty = b == null || b.isEmpty();
    if (aEmpty && bEmpty) {
      return true;
    }
    return Objects.equals(a, b);
  }
}
