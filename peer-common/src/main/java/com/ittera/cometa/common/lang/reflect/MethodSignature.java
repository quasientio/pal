package com.ittera.cometa.common.lang.reflect;

import java.lang.reflect.Method;

public class MethodSignature extends CodeSignature {

	protected Method method;
	protected Class returnType;

	public MethodSignature(Class declaringType, String declaringTypeName, int modifiers, String name,
												 Class[] exceptionTypes, String[] parameterNames, Class[] parameterTypes, Method method,
												 Class returnType) {
		super(declaringType, declaringTypeName, modifiers, name, exceptionTypes, parameterNames, parameterTypes);
		this.method = method;
		this.returnType = returnType;
	}

	public MethodSignature(Class declaringType, String name, Class[] parameterTypes) throws NoSuchMethodException {
		super(declaringType, declaringType.getTypeName(), 0, name, null, null,
			parameterTypes);

		method = declaringType.getMethod(name, parameterTypes);
		modifiers = method.getModifiers();
		exceptionTypes = method.getExceptionTypes();
	}

	public Method getMethod() {
		return method;
	}

	public Class getReturnType() {
		return returnType;
	}
}
