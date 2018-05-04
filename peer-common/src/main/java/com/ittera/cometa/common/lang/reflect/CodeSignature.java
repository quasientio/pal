package com.ittera.cometa.common.lang.reflect;

import java.lang.reflect.Parameter;

import java.util.stream.IntStream;

public abstract class CodeSignature extends Signature {

	protected Class[] exceptionTypes;
	protected String[] parameterNames;
	protected Class[] parameterTypes;
	protected Parameter[] parameters;

	public CodeSignature(Class declaringType, String declaringTypeName, int modifiers, String name,
											 Class[] exceptionTypes, String[] parameterNames, Class[] parameterTypes, Parameter[] parameters) {
		super(declaringType, declaringTypeName, modifiers, name);
		this.exceptionTypes = exceptionTypes;
		this.parameterTypes = parameterTypes;
		if (parameterNames == null) {
			this.parameterNames = IntStream.range(0, parameterTypes.length).mapToObj(i -> "arg" + i).toArray(String[]::new);
		} else {
			this.parameterNames = parameterNames;
		}
		this.parameters = parameters;
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

	public Parameter[] getParameters() {
		return parameters;
	}

}
