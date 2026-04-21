/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
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
   * <p>For instance method operations, the class name is the runtime receiver class ({@code
   * pjp.getTarget().getClass().getName()}) — matching the runtime-class recording performed by the
   * message builder so replay comparisons align under virtual and interface dispatch. For all other
   * operations (static methods, constructors, field access) the class name falls back to {@code
   * pjp.getSignature().getDeclaringTypeName()}.
   *
   * <p>The executable name comes from {@code pjp.getSignature().getName()} (constructors are
   * normalized from {@code <init>} to {@code new}), and parameter types come from {@link
   * ParamTypeExtractor#extractFromArgs} to match the actual runtime types recorded in the WAL
   * rather than the declared signature types.
   *
   * @param pjp the proceeding join point from AspectJ dispatch
   * @param msgType the message type classifying this operation
   * @return a new {@code OperationSignature} reflecting the live operation
   */
  public static OperationSignature fromJoinPoint(ProceedingJoinPoint pjp, MessageType msgType) {
    final Object target = pjp.getTarget();
    final String declaredTypeName = pjp.getSignature().getDeclaringTypeName();
    String className = declaredTypeName;
    if (msgType == MessageType.EXEC_INSTANCE_METHOD && target != null) {
      // Prefer the runtime target class so the live signature matches the WAL-recorded
      // runtime class under virtual and interface dispatch. Fall back to the declared
      // type when the declared class cannot be loaded or the target is not an instance
      // of it — this path is only exercised by unit tests that stub join points with
      // non-existent class names and plain Object targets.
      try {
        Class<?> declaredType =
            Class.forName(declaredTypeName, false, Thread.currentThread().getContextClassLoader());
        if (declaredType.isInstance(target)) {
          className = target.getClass().getName();
        }
      } catch (ClassNotFoundException ignored) {
        // Declared class not loadable — retain declared name.
      }
    }
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
   * have different message types between recording and replay. Class names and parameter type names
   * are compared with lambda normalization so that synthetic lambda class names (which carry
   * non-deterministic suffixes across JVM runs) match when their enclosing class and {@code
   * $$Lambda} marker agree.
   *
   * @param other the other signature to compare against
   * @return {@code true} if class name, executable name, and parameter types are equal
   */
  public boolean matches(OperationSignature other) {
    return typeNamesMatch(this.className, other.className)
        && Objects.equals(this.executableName, other.executableName)
        && paramTypesMatch(this.paramTypes, other.paramTypes);
  }

  /**
   * Compares parameter type lists, treating {@code null} and empty list as equivalent. Fields have
   * no parameters and may be recorded as {@code null} in the WAL but as an empty list from the live
   * PJP.
   *
   * <p>Lambda class names are normalized before comparison since the JVM generates
   * non-deterministic synthetic class names of the form {@code EnclosingClass$$Lambda$N/0xaddress}
   * where N and the memory address change between JVM runs.
   *
   * @param a first parameter type list
   * @param b second parameter type list
   * @return {@code true} if both are effectively equal (with lambda normalization)
   */
  private static boolean paramTypesMatch(List<String> a, List<String> b) {
    boolean aEmpty = a == null || a.isEmpty();
    boolean bEmpty = b == null || b.isEmpty();
    if (aEmpty && bEmpty) {
      return true;
    }
    if (aEmpty || bEmpty) {
      return false;
    }
    if (a.size() != b.size()) {
      return false;
    }
    for (int i = 0; i < a.size(); i++) {
      if (!typeNamesMatch(a.get(i), b.get(i))) {
        return false;
      }
    }
    return true;
  }

  /**
   * Compares two type names, normalizing lambda class names.
   *
   * <p>Lambda classes have synthetic names like {@code com.example.Foo$$Lambda$123/0x7f...} where
   * the number and address suffix change between JVM runs. This method strips everything after
   * {@code $$Lambda} to make them comparable.
   *
   * @param a first type name
   * @param b second type name
   * @return {@code true} if the type names match (with lambda normalization)
   */
  private static boolean typeNamesMatch(String a, String b) {
    if (Objects.equals(a, b)) {
      return true;
    }
    // Normalize lambda class names: strip the $$Lambda$N/address suffix
    return Objects.equals(normalizeLambdaClassName(a), normalizeLambdaClassName(b));
  }

  /** Marker for lambda class names in the class name string. */
  private static final String LAMBDA_MARKER = "$$Lambda";

  /**
   * Normalizes a lambda class name by stripping the non-deterministic suffix.
   *
   * <p>Transforms {@code com.example.Foo$$Lambda$123/0x7fab...} to {@code com.example.Foo$$Lambda}.
   * Non-lambda class names are returned unchanged.
   *
   * @param className the class name to normalize
   * @return the normalized class name
   */
  private static String normalizeLambdaClassName(String className) {
    if (className == null) {
      return null;
    }
    int lambdaIdx = className.indexOf(LAMBDA_MARKER);
    if (lambdaIdx < 0) {
      return className;
    }
    // Return "EnclosingClass$$Lambda" (strip the $N/address part)
    return className.substring(0, lambdaIdx + LAMBDA_MARKER.length());
  }
}
