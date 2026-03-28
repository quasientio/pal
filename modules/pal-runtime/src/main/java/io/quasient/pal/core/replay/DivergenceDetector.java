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

import io.quasient.pal.common.replay.WalEntry;
import io.quasient.pal.messages.colfer.ReturnValue;
import io.quasient.pal.serdes.Unwrapper;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Verification engine that compares actual return values against WAL-recorded values and
 * accumulates a {@link DivergenceReport} during deterministic replay.
 *
 * <p>The detector supports three {@link DivergencePolicy} modes:
 *
 * <ul>
 *   <li>{@link DivergencePolicy#HALT} — throws a {@link RuntimeException} immediately on any
 *       divergence
 *   <li>{@link DivergencePolicy#WARN} — logs the divergence and continues execution
 *   <li>{@link DivergencePolicy#IGNORE} — silently records the divergence without logging
 * </ul>
 */
public class DivergenceDetector {

  /** Logger for WARN-policy divergence reporting. */
  private static final Logger logger = LoggerFactory.getLogger(DivergenceDetector.class);

  /** Policy controlling how divergences are handled when detected. */
  public enum DivergencePolicy {
    /** Throw a {@link RuntimeException} immediately on divergence. */
    WARN,
    /** Log the divergence and continue execution. */
    HALT,
    /** Silently record the divergence without logging. */
    IGNORE
  }

  /** Category of divergence detected during replay. */
  public enum DivergenceType {
    /** The return value from live execution differs from the WAL-recorded value. */
    VALUE_MISMATCH,
    /** The return type from live execution differs from the WAL-recorded type. */
    TYPE_MISMATCH,
    /** The live operation does not match the WAL-expected operation signature. */
    OPERATION_MISMATCH,
    /** The WAL expects an operation that was not executed live. */
    MISSING_OPERATION,
    /** Live execution produced an operation that is not in the WAL. */
    EXTRA_OPERATION
  }

  /** Accumulated divergences detected during replay. */
  private final List<Divergence> divergences = new ArrayList<>();

  /** The policy controlling divergence handling behavior. */
  private final DivergencePolicy policy;

  /**
   * Constructs a new detector with the given policy.
   *
   * @param policy the divergence handling policy
   */
  public DivergenceDetector(DivergencePolicy policy) {
    this.policy = policy;
  }

  /**
   * Compares the actual return value from live execution against the WAL-recorded return value.
   *
   * <p>Extracts the expected value from the WAL entry's {@link ReturnValue} field using {@link
   * Unwrapper#unwrapObject}. Compares using {@link Objects#equals} for simple types. If the WAL
   * entry's return value is void, verifies the actual value is also {@code null}.
   *
   * @param walEntry the WAL entry containing the expected return value
   * @param actualValue the actual value from live execution
   * @param threadName the name of the thread on which the comparison is performed
   */
  public void compareReturnValue(WalEntry walEntry, Object actualValue, String threadName) {
    ReturnValue returnValue = walEntry.getRawMessage().getReturnValue();
    if (returnValue == null) {
      return;
    }

    if (returnValue.getIsVoid()) {
      // Void method — no value comparison needed
      return;
    }

    Object expectedValue;
    if (returnValue.getObject() == null) {
      expectedValue = null;
    } else {
      try {
        expectedValue = Unwrapper.unwrapObject(returnValue.getObject());
      } catch (UnsupportedOperationException e) {
        // Reference-only object — skip value comparison, type matching only
        return;
      } catch (Exception e) {
        // Cannot unwrap — skip comparison
        logger.debug("Cannot unwrap WAL return value at offset {}: {}", walEntry.getOffset(), e);
        return;
      }
    }

    // When the Wrapper falls back to identity hash codes for non-serializable arrays
    // (e.g. Thread[]), the unwrapped WAL value is int[] while the live value is the original
    // array type.  These cannot be meaningfully compared — skip like reference-only objects.
    if (expectedValue instanceof int[]
        && actualValue != null
        && actualValue.getClass().isArray()
        && !actualValue.getClass().getComponentType().isPrimitive()) {
      return;
    }

    if (!deepEquals(expectedValue, actualValue)) {
      recordDivergence(
          new Divergence(
              DivergenceType.VALUE_MISMATCH,
              walEntry.getOffset(),
              threadName,
              String.format(
                  "Return value mismatch for %s.%s",
                  walEntry.getClassName(), walEntry.getExecutableName()),
              expectedValue,
              actualValue));
    }
  }

  /**
   * Reports that the live operation does not match the WAL-expected operation.
   *
   * @param expected the WAL entry for the expected operation
   * @param actual the actual operation signature from live execution
   * @param threadName the name of the thread on which the mismatch was detected
   */
  public void reportOperationMismatch(
      WalEntry expected, OperationSignature actual, String threadName) {
    recordDivergence(
        new Divergence(
            DivergenceType.OPERATION_MISMATCH,
            expected.getOffset(),
            threadName,
            String.format(
                "Expected %s.%s but got %s.%s",
                expected.getClassName(),
                expected.getExecutableName(),
                actual.className(),
                actual.executableName()),
            OperationSignature.fromWalEntry(expected),
            actual));
  }

  /**
   * Reports that live execution produced an operation not present in the WAL.
   *
   * @param actual the extra operation signature from live execution
   * @param threadName the name of the thread on which the extra operation was detected
   */
  public void reportExtraOperation(OperationSignature actual, String threadName) {
    recordDivergence(
        new Divergence(
            DivergenceType.EXTRA_OPERATION,
            -1,
            threadName,
            String.format("Extra operation: %s.%s", actual.className(), actual.executableName()),
            null,
            actual));
  }

  /**
   * Reports that the WAL expects an operation that was not executed live.
   *
   * @param expected the WAL entry for the missing operation
   * @param threadName the name of the thread on which the missing operation was detected
   */
  public void reportMissingOperation(WalEntry expected, String threadName) {
    recordDivergence(
        new Divergence(
            DivergenceType.MISSING_OPERATION,
            expected.getOffset(),
            threadName,
            String.format(
                "Missing operation: %s.%s", expected.getClassName(), expected.getExecutableName()),
            OperationSignature.fromWalEntry(expected),
            null));
  }

  /**
   * Returns a report of all divergences accumulated so far.
   *
   * @return the divergence report
   */
  public DivergenceReport getReport() {
    return new DivergenceReport(divergences);
  }

  /**
   * Returns whether any divergences have been detected.
   *
   * @return {@code true} if at least one divergence has been recorded
   */
  public boolean hasDivergences() {
    return !divergences.isEmpty();
  }

  /**
   * Array-aware equality check. Uses {@link Arrays#deepEquals} for Object arrays, {@link
   * Arrays#equals} for primitive arrays, and {@link Objects#equals} for all other types.
   *
   * @param a the first value
   * @param b the second value
   * @return {@code true} if the values are deeply equal
   */
  private static boolean deepEquals(Object a, Object b) {
    if (a == b) {
      return true;
    }
    if (a == null || b == null) {
      return false;
    }
    if (a instanceof Object[] arrayA && b instanceof Object[] arrayB) {
      return Arrays.deepEquals(arrayA, arrayB);
    }
    if (a.getClass().isArray() && b.getClass().isArray()) {
      // Primitive arrays: delegate to Arrays.equals for each primitive type
      return Objects.deepEquals(a, b);
    }
    return Objects.equals(a, b);
  }

  /**
   * Records a divergence and applies the configured policy.
   *
   * @param divergence the divergence to record
   * @throws RuntimeException if the policy is {@link DivergencePolicy#HALT}
   */
  private void recordDivergence(Divergence divergence) {
    divergences.add(divergence);
    if (policy == DivergencePolicy.HALT) {
      throw new RuntimeException("Replay divergence (HALT policy): " + divergence.description());
    }
    if (policy == DivergencePolicy.WARN) {
      logger.warn(
          "Replay divergence at offset {} thread={}: {} (expected={}, actual={})",
          divergence.walOffset(),
          divergence.threadName(),
          divergence.description(),
          divergence.expected(),
          divergence.actual());
    }
  }
}
