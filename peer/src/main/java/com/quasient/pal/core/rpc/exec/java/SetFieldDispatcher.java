/*
   Copyright (c) 2020 Contributors listed in the AUTHORS file

   This file is part of PAL, the friendly java runtime.

   PAL is free software: you can redistribute it and/or modify
   it under the terms of the GNU General Public License as published by
   the Free Software Foundation, either version 3 of the License, or
   (at your option) any later version.

   PAL is distributed in the hope that it will be useful,
   but WITHOUT ANY WARRANTY; without even the implied warranty of
   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
   GNU General Public License for more details.

   You should have received a copy of the GNU General Public License
   along with this program. If not, see <http://www.gnu.org/licenses/>.
*/

package com.quasient.pal.core.rpc.exec.java;

import com.quasient.pal.common.lang.reflect.FieldSignature;
import com.quasient.pal.common.objects.ObjectRef;
import com.quasient.pal.common.runtime.Context;
import com.quasient.pal.messages.colfer.Obj;
import com.quasient.pal.serdes.Unwrapper;
import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Field;
import java.util.List;
import javax.annotation.Nullable;

/**
 * An abstract dispatcher for setting a field value on a target object using reflection.
 *
 * <p>This class provides the core functionality to update a field of an object by interpreting a
 * field signature from the runtime context and handling incoming messages to perform the update. It
 * extends {@link FieldOpDispatcher} and overrides the necessary methods to support field-specific
 * operations.
 */
public abstract class SetFieldDispatcher extends FieldOpDispatcher {

  /**
   * Sets a field value on the target object using the field specified in the provided context.
   *
   * <p>The method retrieves the {@link Field} from the {@link FieldSignature} contained in the
   * context, makes it accessible, and attempts to assign the value (first element in {@code args})
   * to the field of the target object. If an exception occurs during the assignment, it is caught,
   * logged, and wrapped in an {@code InvocationExceptionWrapper}.
   *
   * @param ctxt the runtime context that holds the field signature for the target field.
   * @param sender the entity initiating the field update.
   * @param target the object whose field is to be updated.
   * @param args an array whose first element is the value to assign to the field.
   * @return a {@code Void} instance if the operation is successful, or an {@code
   *     InvocationExceptionWrapper} if an exception is encountered.
   */
  @Override
  protected final Object invoke(Context ctxt, Object sender, Object target, Object[] args) {

    Field field = ((FieldSignature) ctxt.getSignature()).getField();
    field.setAccessible(true);

    Object fieldValue = args[0];
    try {
      field.set(target, fieldValue);
    } catch (Exception ex) {
      logger.error("Caught exception while invoking field operation. Will wrap and return it.", ex);
      return new InvocationExceptionWrapper(ex);
    }

    return Void.getInstance();
  }

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
   * @throws Exception if a reflective operation fails during the field assignment.
   */
  @Override
  protected Object invokeIncoming(
      AccessibleObject accessibleObject, Object target, List<MessageArgument> args, Object value)
      throws Exception {

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
   * @throws Exception if the field assignment fails.
   */
  private Object invokeIncoming(
      AccessibleObject accessibleObject, Object target, @Nullable Object value) throws Exception {
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
   * @param parameterTypes a list of parameter types; currently not utilized in the lookup.
   * @param args a list of arguments; currently not utilized in the lookup.
   * @return the {@code AccessibleObject} corresponding to the specified field.
   * @throws ReflectiveOperationException if the field cannot be located.
   */
  protected final AccessibleObject loadAccessibleObject(
      String className, String fieldName, List<Class<?>> parameterTypes, List<Object> args)
      throws ReflectiveOperationException {

    Class<?> clazz = Class.forName(className, true, Thread.currentThread().getContextClassLoader());

    try {
      return clazz.getField(fieldName);
    } catch (NoSuchFieldException e) {
      if (allowNonPublicAccess) {
        return clazz.getDeclaredField(fieldName);
      }
      throw e;
    }
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
   * @param objectRef a string reference used for object lookup when the message object is
   *     unavailable.
   * @param accessibleObject the reflective field object used to determine the type for unwrapping.
   * @return the unwrapped field value, or {@code null} if not available.
   */
  protected final @Nullable Object getValueFromMessage(
      Obj valueObject, String objectRef, final AccessibleObject accessibleObject) {

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
