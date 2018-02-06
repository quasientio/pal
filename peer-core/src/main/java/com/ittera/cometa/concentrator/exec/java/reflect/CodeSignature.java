package com.ittera.cometa.concentrator.exec.java.reflect;

public abstract class CodeSignature extends Signature {

	private final Class[] exceptionTypes;
	private final String[] parameterNames;
	private final Class[] parameterTypes;

	public CodeSignature(Class declaringType, String declaringTypeName, int modifiers, String name,
											 Class[] exceptionTypes, String[] parameterNames, Class[] parameterTypes) {
		super(declaringType, declaringTypeName, modifiers, name);
		this.exceptionTypes = exceptionTypes;
		this.parameterNames = parameterNames;
		this.parameterTypes = parameterTypes;
	}

	public Class[] getExceptionTypes() {
		return exceptionTypes;
	}

	public String[] getParameterNames() {
		return parameterNames;
	}

	public Class[] getParameterTypes() {
		return parameterTypes;
	}
}
