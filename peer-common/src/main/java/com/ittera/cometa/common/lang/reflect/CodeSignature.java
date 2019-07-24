package com.ittera.cometa.common.lang.reflect;

import java.lang.reflect.Parameter;

import java.util.Arrays;
import java.util.stream.IntStream;

public abstract class CodeSignature extends Signature {

	protected final Class[] exceptionTypes;
	protected final String[] parameterNames;
	protected final Class[] parameterTypes;
	protected final Parameter[] parameters;

	public CodeSignature(Class declaringType, String declaringTypeName, int modifiers, String name,
											 Class[] exceptionTypes, String[] parameterNames, Class[] parameterTypes, Parameter[] parameters) {
		super(declaringType, declaringTypeName, modifiers, name);
		this.exceptionTypes = Arrays.copyOf(exceptionTypes, exceptionTypes.length);
		this.parameterTypes = Arrays.copyOf(parameterTypes, parameterTypes.length);
		if (parameterNames == null) {
			this.parameterNames = IntStream.range(0, parameterTypes.length).mapToObj(i -> "arg" + i).toArray(String[]::new);
		} else {
			this.parameterNames = Arrays.copyOf(parameterNames, parameterNames.length);
		}
		this.parameters = Arrays.copyOf(parameters, parameters.length);
	}

	public Class[] getExceptionTypes() {
		return Arrays.copyOf(exceptionTypes, exceptionTypes.length);
	}

	public String[] getParameterNames() {
		return Arrays.copyOf(parameterNames, parameterNames.length);
	}

	public Class[] getParameterTypes() {
		return Arrays.copyOf(parameterTypes, parameterTypes.length);
	}

	public Parameter[] getParameters() {
		return Arrays.copyOf(parameters, parameters.length);
	}
}
