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

import io.quasient.pal.common.replay.Span;
import io.quasient.pal.common.replay.WalEntry;
import io.quasient.pal.common.replay.WalIndex;
import io.quasient.pal.messages.colfer.ExecMessage;
import io.quasient.pal.messages.colfer.InstanceFieldPut;
import io.quasient.pal.messages.colfer.Obj;
import io.quasient.pal.messages.colfer.StaticFieldPut;
import io.quasient.pal.serdes.Unwrapper;
import java.lang.reflect.Field;
import java.lang.reflect.InaccessibleObjectException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Replays {@code PUT_FIELD} and {@code PUT_STATIC} mutations from within a stubbed span.
 *
 * <p>When a method is stubbed via {@code STUB_WITH_SIDE_EFFECTS}, the method body is not executed,
 * but field mutations recorded in the WAL during the original execution must still be applied so
 * that externally-visible side effects are preserved. This class iterates the WAL entries within a
 * span and applies each field mutation via reflection.
 */
public class SpanFieldMutationReplayer {

  /** Logger for this class. */
  private static final Logger logger = LoggerFactory.getLogger(SpanFieldMutationReplayer.class);

  /**
   * Replays all {@code PUT_FIELD} and {@code PUT_STATIC} mutations within the given span.
   *
   * <p>Entries are processed in WAL offset order (the order returned by {@link
   * WalIndex#getEntriesInSpan(Span)}), so the last write to a field wins.
   *
   * @param index the WAL index providing span entry lookup
   * @param span the span whose inner mutations should be replayed
   * @param objectStore the replay object store for resolving WAL refs to live objects
   */
  public void replayMutations(WalIndex index, Span span, ReplayObjectStore objectStore) {
    List<WalEntry> entries = index.getEntriesInSpan(span);

    for (WalEntry entry : entries) {
      ExecMessage msg = entry.getRawMessage();

      if (msg.getInstanceFieldPut() != null) {
        replayInstanceFieldPut(msg.getInstanceFieldPut(), objectStore);
      } else if (msg.getStaticFieldPut() != null) {
        replayStaticFieldPut(msg.getStaticFieldPut(), objectStore);
      }
    }
  }

  /**
   * Applies a single instance field PUT via reflection.
   *
   * <p>Resolves the target object from the object store using the PUT's object ref. If the target
   * is null (phantom or unknown), the mutation is silently skipped with a DEBUG log. If the field
   * is inaccessible (e.g., due to JPMS restrictions), a warning is logged and processing continues.
   *
   * @param put the instance field PUT message from the WAL
   * @param objectStore the replay object store for resolving WAL refs
   */
  private void replayInstanceFieldPut(InstanceFieldPut put, ReplayObjectStore objectStore) {
    int targetRef = put.getObjectRef();
    Object target = objectStore.resolveOrNull(targetRef);
    if (target == null || objectStore.isPhantom(targetRef)) {
      logger.debug(
          "Skipping PUT_FIELD on ref {} (phantom or unresolvable): {}.{}",
          targetRef,
          put.getClazz() != null ? put.getClazz().getName() : "?",
          put.getField() != null ? put.getField().getName() : "?");
      return;
    }

    String className = put.getClazz().getName();
    String fieldName = put.getField().getName();
    Object value =
        reconstructFieldValue(put.getValueObject(), put.getValueObjectRef(), objectStore);

    try {
      Class<?> clazz =
          Class.forName(className, true, Thread.currentThread().getContextClassLoader());
      Field field = clazz.getDeclaredField(fieldName);
      field.setAccessible(true);
      field.set(target, value);
    } catch (ReflectiveOperationException | InaccessibleObjectException e) {
      logger.warn(
          "Failed to replay PUT_FIELD {}.{} on ref {}: {}",
          className,
          fieldName,
          targetRef,
          e.getMessage());
    }
  }

  /**
   * Applies a single static field PUT via reflection.
   *
   * <p>Similar to {@link #replayInstanceFieldPut(InstanceFieldPut, ReplayObjectStore)} but sets the
   * field on {@code null} (static context). If the field is inaccessible, a warning is logged and
   * processing continues.
   *
   * @param put the static field PUT message from the WAL
   * @param objectStore the replay object store for resolving value refs
   */
  private void replayStaticFieldPut(StaticFieldPut put, ReplayObjectStore objectStore) {
    String className = put.getClazz().getName();
    String fieldName = put.getField().getName();
    Object value =
        reconstructFieldValue(put.getValueObject(), put.getValueObjectRef(), objectStore);

    try {
      Class<?> clazz =
          Class.forName(className, true, Thread.currentThread().getContextClassLoader());
      Field field = clazz.getDeclaredField(fieldName);
      field.setAccessible(true);
      field.set(null, value);
    } catch (ReflectiveOperationException | InaccessibleObjectException e) {
      logger.warn("Failed to replay PUT_STATIC {}.{}: {}", className, fieldName, e.getMessage());
    }
  }

  /**
   * Reconstructs a field value from WAL data.
   *
   * <p>Resolution order:
   *
   * <ol>
   *   <li>If {@code valueObjRef != 0}, attempt to resolve from the object store.
   *   <li>If the object store lookup fails or the ref is zero, fall back to {@link
   *       Unwrapper#unwrapObject(Obj)}.
   *   <li>Return {@code null} for non-reconstructable values.
   * </ol>
   *
   * @param valueObj the serialized value object from the WAL (may be null)
   * @param valueObjRef the WAL object ref for the value (0 if none)
   * @param objectStore the replay object store for resolving refs
   * @return the reconstructed value, or {@code null} if not reconstructable
   */
  private Object reconstructFieldValue(
      Obj valueObj, int valueObjRef, ReplayObjectStore objectStore) {
    if (valueObjRef != 0) {
      Object resolved = objectStore.resolveOrNull(valueObjRef);
      if (resolved != null) {
        return resolved;
      }
    }
    if (valueObj == null || valueObj.getIsNull()) {
      return null;
    }
    try {
      return Unwrapper.unwrapObject(valueObj);
    } catch (Exception e) {
      logger.debug("Cannot reconstruct field value: {}", e.getMessage());
      return null;
    }
  }
}
