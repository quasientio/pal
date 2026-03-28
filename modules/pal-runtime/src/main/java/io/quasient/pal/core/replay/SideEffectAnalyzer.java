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

import io.quasient.pal.common.replay.Span;
import io.quasient.pal.common.replay.WalEntry;
import io.quasient.pal.common.replay.WalIndex;
import io.quasient.pal.core.replay.ReplayPolicy.ReplayAction;
import io.quasient.pal.messages.types.MessageType;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Analyzes a {@link WalIndex} against a {@link ReplayPolicy} to detect unsafe stubs.
 *
 * <p>A stub is unsafe when the stubbed span contains {@code PUT_FIELD} or {@code PUT_STATIC}
 * operations that mutate objects visible outside the span. Stubbing such a span would silently drop
 * those field mutations, leading to incorrect replay state.
 *
 * <p>This analyzer runs at replay startup (after {@link WalIndex#build} and {@link ReplayPolicy}
 * construction) and produces a list of {@link UnsafeStubWarning} instances. If warnings are found
 * and {@code --force-stub} is not set, the replay should exit with a non-zero code.
 */
public class SideEffectAnalyzer {

  /** Logger for emitting unsafe stub warnings. */
  private static final Logger logger = LoggerFactory.getLogger(SideEffectAnalyzer.class);

  /**
   * Analyzes the given WAL index against the replay policy to detect unsafe stubs.
   *
   * <p>For each span in the index, the policy is consulted. If the policy returns a {@code
   * STUB_FROM_WAL} or {@code STUB_FROM_WAL_VERIFIED} action, the entries within the span are
   * scanned for {@code PUT_FIELD} and {@code PUT_STATIC} operations. For each such PUT:
   *
   * <ul>
   *   <li>{@code PUT_STATIC} entries are always considered externally visible (static fields are
   *       globally accessible).
   *   <li>{@code PUT_FIELD} entries are checked for external references: if the target object ref
   *       appears in any operation outside the span, the mutation is externally visible.
   * </ul>
   *
   * <p>Spans whose policy action is {@code RE_EXECUTE}, {@code RE_EXECUTE_UNCHECKED}, or {@code
   * STUB_WITH_SIDE_EFFECTS} are not analyzed, since re-executed spans apply mutations naturally and
   * {@code STUB_WITH_SIDE_EFFECTS} replays field mutations explicitly.
   *
   * @param index the WAL index to analyze
   * @param policy the replay policy determining which spans are stubbed
   * @return a list of warnings for unsafe stubs; empty if no unsafe stubs are detected
   */
  public List<UnsafeStubWarning> analyze(WalIndex index, ReplayPolicy policy) {
    List<UnsafeStubWarning> warnings = new ArrayList<>();

    for (Map.Entry<Long, Span> spanEntry : index.getSpans().entrySet()) {
      long opOffset = spanEntry.getKey();
      Span span = spanEntry.getValue();
      WalEntry opEntry = index.getEntryAtOffset(opOffset);

      if (opEntry == null) {
        continue;
      }

      ReplayAction action =
          policy.getAction(
              opEntry.getClassName(), opEntry.getExecutableName(), opEntry.getMessageType());

      if (action != ReplayAction.STUB_FROM_WAL && action != ReplayAction.STUB_FROM_WAL_VERIFIED) {
        continue;
      }

      List<WalEntry> innerEntries = index.getEntriesInSpan(span);
      for (WalEntry innerEntry : innerEntries) {
        MessageType msgType = innerEntry.getMessageType();

        if (msgType == MessageType.EXEC_PUT_STATIC) {
          warnings.add(new UnsafeStubWarning(opEntry, innerEntry, span, -1));
          logger.warn("{}", warnings.get(warnings.size() - 1));
        } else if (msgType == MessageType.EXEC_PUT_FIELD) {
          long externalRefOffset = findExternalReference(index, innerEntry, span);
          if (externalRefOffset >= 0) {
            warnings.add(new UnsafeStubWarning(opEntry, innerEntry, span, externalRefOffset));
            logger.warn("{}", warnings.get(warnings.size() - 1));
          }
        }
      }
    }

    return warnings;
  }

  /**
   * Checks whether the target object ref of a PUT_FIELD entry is referenced by any operation
   * outside the given span.
   *
   * <p>Scans all entries in the index for references to the same object ref that occur at offsets
   * outside the span boundaries (i.e., before the operation offset or after the completion offset).
   *
   * @param index the WAL index to search
   * @param putEntry the PUT_FIELD entry whose target ref is being checked
   * @param span the span containing the PUT entry
   * @return the offset of the first external reference found, or {@code -1} if none
   */
  private long findExternalReference(WalIndex index, WalEntry putEntry, Span span) {
    int targetRef = putEntry.getObjectRef();
    if (targetRef == 0) {
      return -1;
    }

    for (WalEntry entry : index.getEntries()) {
      long offset = entry.getOffset();
      if (offset >= span.operationOffset() && offset <= span.completionOffset()) {
        continue;
      }
      if (entry.getObjectRef() == targetRef) {
        return offset;
      }
    }

    return -1;
  }

  /**
   * Describes an unsafe stub detected during side-effect analysis.
   *
   * <p>An unsafe stub occurs when a stubbed span contains a field mutation ({@code PUT_FIELD} or
   * {@code PUT_STATIC}) that affects state visible outside the span. Stubbing such a span would
   * silently drop the mutation.
   */
  public static final class UnsafeStubWarning {

    /** The operation entry at the start of the stubbed span. */
    private final WalEntry operationEntry;

    /** The PUT_FIELD or PUT_STATIC entry within the span that performs the unsafe mutation. */
    private final WalEntry putEntry;

    /** The span being stubbed. */
    private final Span span;

    /**
     * The offset of the first external reference to the PUT target, or {@code -1} for PUT_STATIC
     * (always externally visible).
     */
    private final long externalReferenceOffset;

    /**
     * Creates a new unsafe stub warning.
     *
     * @param operationEntry the operation entry at the start of the stubbed span
     * @param putEntry the PUT entry within the span
     * @param span the span being stubbed
     * @param externalReferenceOffset the offset of the first external reference, or {@code -1} for
     *     PUT_STATIC
     */
    public UnsafeStubWarning(
        WalEntry operationEntry, WalEntry putEntry, Span span, long externalReferenceOffset) {
      this.operationEntry = operationEntry;
      this.putEntry = putEntry;
      this.span = span;
      this.externalReferenceOffset = externalReferenceOffset;
    }

    /**
     * Returns the operation entry at the start of the stubbed span.
     *
     * @return the operation entry
     */
    public WalEntry getOperationEntry() {
      return operationEntry;
    }

    /**
     * Returns the PUT entry within the span.
     *
     * @return the PUT entry
     */
    public WalEntry getPutEntry() {
      return putEntry;
    }

    /**
     * Returns the span being stubbed.
     *
     * @return the span
     */
    public Span getSpan() {
      return span;
    }

    /**
     * Returns the offset of the first external reference to the PUT target, or {@code -1} for
     * PUT_STATIC entries (which are always externally visible).
     *
     * @return the external reference offset, or {@code -1}
     */
    public long getExternalReferenceOffset() {
      return externalReferenceOffset;
    }

    /**
     * Returns a human-readable warning message describing the unsafe stub.
     *
     * @return the warning message
     */
    @Override
    public String toString() {
      String opDesc = operationEntry.getClassName() + "." + operationEntry.getExecutableName();
      String putType =
          putEntry.getMessageType() == MessageType.EXEC_PUT_STATIC ? "PUT_STATIC" : "PUT_FIELD";
      String putDesc = putEntry.getClassName() + "." + putEntry.getExecutableName();

      StringBuilder sb = new StringBuilder();
      sb.append("Stubbing ")
          .append(opDesc)
          .append(" at offset ")
          .append(span.operationOffset())
          .append(" is unsafe. Span contains ")
          .append(putType)
          .append(" ")
          .append(putDesc);

      if (putEntry.getMessageType() == MessageType.EXEC_PUT_FIELD) {
        sb.append(" (ref ").append(putEntry.getObjectRef()).append(")");
        sb.append(" referenced at offset ")
            .append(externalReferenceOffset)
            .append(" (outside span)");
      } else {
        sb.append(" (static fields are always externally visible)");
      }

      sb.append(". Consider: RE_EXECUTE or STUB_WITH_SIDE_EFFECTS");
      return sb.toString();
    }
  }
}
