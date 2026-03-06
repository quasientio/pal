/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.core.execution.java;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.quasient.pal.common.lang.reflect.Void;
import io.quasient.pal.common.objects.ObjectRef;
import io.quasient.pal.messages.colfer.Obj;
import io.quasient.pal.serdes.Unwrapper;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.List;
import javax.annotation.Nullable;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.FieldSignature;

/**
 * Abstract dispatcher for setting a field value on a target object via reflection or a {@link
 * ProceedingJoinPoint}.
 *
 * <p>This class provides the core functionality to update a field of an object by interpreting a
 * field signature from the runtime context and handling incoming messages to perform the update. It
 * extends {@link FieldOpDispatcher} and overrides the necessary methods to support field-specific
 * operations.
 */
@SuppressFBWarnings(
    value = {"BC_UNCONFIRMED_CAST", "DP_DO_INSIDE_DO_PRIVILEGED"},
    justification = "Type validated before cast; setAccessible needed for field access in runtime")
public abstract class SetFieldDispatcher extends FieldOpDispatcher {

  /**
   * Processes an incoming field update by setting the designated field on the target object.
   *
   * <p>This method discards any additional message arguments and delegates the field setting
   * operation to an overloaded variant that accepts the value directly.
   *
   * @param accessibleObject the reflective object representing the target field.
   * @param target the object on which the field is to be updated.
   * @param args a list of message arguments, which are ignored in this context.
   * @param value the new value to assign to the field.
   * @return a {@code Void} instance indicating the completion of the operation.
   * @throws ReflectiveOperationException if a reflective operation fails during the field
   *     assignment.
   */
  @Override
  protected Object invokeIncoming(
      AccessibleObject accessibleObject, Object target, List<MessageArgument> args, Object value)
      throws ReflectiveOperationException {

    // discard args
    return invokeIncoming(accessibleObject, target, value);
  }

  /**
   * Handles the incoming field assignment by setting the specified field on the target object to
   * the provided value.
   *
   * <p>This method logs the operation at trace level if enabled, performs the field update, and
   * returns a {@code Void} instance once completed.
   *
   * @param accessibleObject the reflective field object representing the field to be updated.
   * @param target the object whose field is being modified.
   * @param value the value to assign to the field; may be {@code null}.
   * @return a {@code Void} instance to denote that the operation has been completed.
   * @throws IllegalAccessException if the field assignment fails.
   */
  private Object invokeIncoming(
      AccessibleObject accessibleObject, Object target, @Nullable Object value)
      throws IllegalAccessException {
    if (logger.isTraceEnabled()) {
      logger.trace(
          "invokeIncoming:in w/ accessibleObject: {}, target: {}, value: {}",
          accessibleObject,
          target,
          value);
    }
    Field field = (Field) accessibleObject;
    field.set(target, value);
    return Void.getInstance();
  }

  /**
   * Loads the {@code AccessibleObject} representing the field specified by the class name and field
   * name.
   *
   * <p>The method attempts to obtain a public field from the given class; if not found and if
   * non-public access is allowed, it attempts to retrieve the declared field.
   *
   * @param className the fully qualified name of the class that contains the field.
   * @param fieldName the name of the field to be accessed.
   * @return the {@code AccessibleObject} corresponding to the specified field.
   * @throws ReflectiveOperationException if the field cannot be located.
   */
  protected final AccessibleObject loadAccessibleObject(String className, String fieldName)
      throws ReflectiveOperationException {

    Class<?> clazz = Class.forName(className, true, Thread.currentThread().getContextClassLoader());

    try {
      return clazz.getField(fieldName);
    } catch (NoSuchFieldException e) {
      if (shouldAllowNonPublicAccess()) {
        return clazz.getDeclaredField(fieldName);
      }
      throw e;
    }
  }

  /**
   * Invokes the target accessible object (e.g., constructor, method, or field) using the provided
   * parameters.
   *
   * @param pjp the proceeding join point
   * @param args the arguments for the invocation
   * @return always null for field SET operations
   */
  @Override
  protected final Object invoke(ProceedingJoinPoint pjp, Object[] args) throws Throwable {

    FieldSignature fieldSignature = (FieldSignature) pjp.getStaticPart().getSignature();
    Field field = fieldSignature.getField();
    if (Modifier.isFinal(field.getModifiers())) {

      // we're in a final field set, inside a constructor call
      // final fields cannot be set outside <init> so we'll invoke by reflection
      field.setAccessible(true);
      Object fieldValue = args[0];
      field.set(pjp.getTarget(), fieldValue);
      return null;
    }
    return super.invoke(pjp, args);
  }

  /**
   * Extracts the field value from the given message object or via an object reference lookup.
   *
   * <p>If the message object contains valid type information, the value is unwrapped based on that
   * type; otherwise, the field's type is used for unwrapping. When the message object is {@code
   * null}, the value is retrieved from the lookup store using the provided reference.
   *
   * @param valueObject the message object that may encapsulate the field value; may be {@code
   *     null}.
   * @param objectRef the int reference used for object lookup when the message object is
   *     unavailable.
   * @param accessibleObject the reflective field object used to determine the type for unwrapping.
   * @return the unwrapped field value, or {@code null} if not available.
   */
  protected final @Nullable Object getValueFromMessage(
      Obj valueObject, int objectRef, final AccessibleObject accessibleObject) {

    final Object value;
    final Field field = (Field) accessibleObject;

    if (valueObject != null) {
      boolean typeIsAvailable =
          valueObject.getClazz() != null
              && valueObject.getClazz().getName() != null
              && !valueObject.getClazz().getName().isEmpty();
      if (typeIsAvailable) {
        try {
          value = Unwrapper.unwrapObject(valueObject);
        } catch (ClassNotFoundException e) {
          throw new IllegalArgumentException(
              "Class not found: " + valueObject.getClazz().getName(), e);
        }
      } else { // type is not available in valueObject, use the field type to unwrap
        value = Unwrapper.unwrapObject(valueObject, field.getType());
      }
      if (logger.isTraceEnabled()) {
        String valueClassName = value != null ? value.getClass().getName() : "null";
        logger.trace("Unwrapped value: {}, has class name: {}", value, valueClassName);
      }
    } else {
      value = objectLookupStore.lookupObject(ObjectRef.from(objectRef));
      if (logger.isTraceEnabled()) {
        logger.trace("Loaded value: {}", value);
      }
    }
    return value;
  }

  /**
   * Indicates that the field operation does not return a value.
   *
   * @return {@code true} always, reflecting the void-like outcome of the operation.
   */
  @Override
  protected final boolean returnsVoid() {
    return true;
  }

  /**
   * Indicates that the field operation corresponding to the given {@code AccessibleObject} does not
   * yield a value.
   *
   * @param accessibleObject the reflective object whose associated operation returns void.
   * @return {@code true} always, reflecting the void nature of the operation.
   */
  @Override
  protected boolean returnsVoid(AccessibleObject accessibleObject) {
    return true;
  }
}
