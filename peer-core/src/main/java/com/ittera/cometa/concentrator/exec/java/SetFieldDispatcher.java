package com.ittera.cometa.concentrator.exec.java;

import com.ittera.cometa.common.lang.Context;
import com.ittera.cometa.common.lang.reflect.FieldSignature;

import java.lang.reflect.Field;
import java.lang.reflect.AccessibleObject;

import java.util.List;
import java.util.Optional;

abstract public class SetFieldDispatcher extends FieldOpDispatcher {

	@Override
	protected final Object invoke(Context ctxt, Object sender, Object target, Object[] args) {

		Field field = ((FieldSignature) ctxt.getSignature()).getField();
		field.setAccessible(true);

		Object fieldValue = args[0];
		try {
			field.set(target, fieldValue);
		} catch (Exception ex) {
			logger.error("Caught exception while invoking field operation. Will wrap and return it.", ex);
			return new InvocationException(ex);
		}

		return Void.getInstance();
	}


	@Override
	protected Object invokeIncoming(AccessibleObject accessibleObject, Optional<Object> target, List<Object> args,
																	Optional<Object> value) throws Exception {
		Field field = (Field) accessibleObject;
		field.set(target.isPresent() ? target.get() : null, value.get());
		return Void.getInstance();
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
