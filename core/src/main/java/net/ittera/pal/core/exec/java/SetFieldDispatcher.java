package net.ittera.pal.core.exec.java;

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;
import net.ittera.pal.common.lang.Context;
import net.ittera.pal.common.lang.reflect.FieldSignature;

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
      Optional<AccessibleObject> accessibleObject,
      Object target,
      List<Object> args,
      Optional<Object> value)
      throws Exception {
    Field field = (Field) accessibleObject.get();
    field.set(target, value.orElse(null));
    return Void.getInstance();
  }

  @Override
  protected final boolean returnsVoid() {
    return true;
  }

  @Override
  protected boolean returnsVoid(Optional<AccessibleObject> accessibleObject) {
    return true;
  }
}
