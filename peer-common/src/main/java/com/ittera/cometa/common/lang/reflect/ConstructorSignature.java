package com.ittera.cometa.common.lang.reflect;

import java.lang.reflect.Constructor;

public class ConstructorSignature extends CodeSignature {

	private final Constructor constructor;

	public ConstructorSignature(Class declaringType, String declaringTypeName, int modifiers, String name,
															Class[] exceptionTypes, String[] parameterNames, Class[] parameterTypes,
															Constructor constructor) {
		super(declaringType, declaringTypeName, modifiers, name, exceptionTypes, parameterNames, parameterTypes);
		this.constructor = constructor;
	}

	public Constructor getConstructor() {
		return constructor;
	}
}
