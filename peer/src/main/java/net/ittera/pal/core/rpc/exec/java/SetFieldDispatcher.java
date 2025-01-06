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

package net.ittera.pal.core.rpc.exec.java;

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Field;
import java.util.List;
import javax.annotation.Nullable;
import net.ittera.pal.common.lang.reflect.FieldSignature;
import net.ittera.pal.common.objects.ObjectRef;
import net.ittera.pal.common.runtime.Context;
import net.ittera.pal.messages.colfer.Obj;
import net.ittera.pal.serdes.Unwrapper;

public abstract class SetFieldDispatcher extends FieldOpDispatcher {

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

  @Override
  protected Object invokeIncoming(
      AccessibleObject accessibleObject, Object target, List<MessageArgument> args, Object value)
      throws Exception {

    // discard args
    return invokeIncoming(accessibleObject, target, value);
  }

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

  @Override
  protected final boolean returnsVoid() {
    return true;
  }

  @Override
  protected boolean returnsVoid(AccessibleObject accessibleObject) {
    return true;
  }
}
