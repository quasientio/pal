package com.ittera.cometa.common.lang.reflect;

import java.lang.reflect.Method;

public class MethodSignature extends CodeSignature {

	protected final Method method;
	protected final Class returnType;

	public MethodSignature(Class declaringType, String declaringTypeName, int modifiers, String name,
												 Class[] exceptionTypes, String[] parameterNames, Class[] parameterTypes, Method method,
												 Class returnType) {
		super(declaringType, declaringTypeName, modifiers, name, exceptionTypes, parameterNames, parameterTypes,
			method.getParameters());
		this.method = method;
		this.returnType = returnType;
	}

	public MethodSignature(Method method) throws NoSuchMethodException {
		this(method.getDeclaringClass(), method.getDeclaringClass().getTypeName(), method.getModifiers(), method.getName(),
			method.getExceptionTypes(), null, method.getParameterTypes(), method, method.getReturnType());
	}

	public Method getMethod() {
		return method;
	}

	public Class getReturnType() {
		return returnType;
	}
}
