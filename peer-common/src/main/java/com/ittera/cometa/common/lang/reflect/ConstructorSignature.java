package com.ittera.cometa.common.lang.reflect;

import java.lang.reflect.Constructor;

public class ConstructorSignature extends CodeSignature {

	private final Constructor constructor;

	public ConstructorSignature(Class declaringType, String declaringTypeName, int modifiers, String name,
															Class[] exceptionTypes, String[] parameterNames, Class[] parameterTypes,
															Constructor constructor) {
		super(declaringType, declaringTypeName, modifiers, name, exceptionTypes, parameterNames, parameterTypes,
			constructor.getParameters());
		this.constructor = constructor;
	}

	public ConstructorSignature(Constructor constructor) {
		this(constructor.getDeclaringClass(), constructor.getDeclaringClass().getTypeName(), constructor.getModifiers(),
			constructor.getName(), constructor.getExceptionTypes(), null, constructor.getParameterTypes(),
			constructor);
	}

	public Constructor getConstructor() {
		return constructor;
	}
}
