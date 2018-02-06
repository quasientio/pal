package com.ittera.cometa.concentrator.exec.java.reflect;

import java.lang.reflect.Method;

public class MethodSignature extends CodeSignature {

	private final Method method;
	private final Class returnType;

	public MethodSignature(Class declaringType, String declaringTypeName, int modifiers, String name,
												 Class[] exceptionTypes, String[] parameterNames, Class[] parameterTypes, Method method,
												 Class returnType) {
		super(declaringType, declaringTypeName, modifiers, name, exceptionTypes, parameterNames, parameterTypes);
		this.method = method;
		this.returnType = returnType;
	}

	public Method getMethod() {
		return method;
	}

	public Class getReturnType() {
		return returnType;
	}
}
