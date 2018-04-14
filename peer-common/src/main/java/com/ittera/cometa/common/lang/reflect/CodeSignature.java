package com.ittera.cometa.common.lang.reflect;

import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public abstract class CodeSignature extends Signature {

	protected Class[] exceptionTypes;
	protected String[] parameterNames;
	protected Class[] parameterTypes;

	public CodeSignature(Class declaringType, String declaringTypeName, int modifiers, String name,
											 Class[] exceptionTypes, String[] parameterNames, Class[] parameterTypes) {
		super(declaringType, declaringTypeName, modifiers, name);
		this.exceptionTypes = exceptionTypes;
		this.parameterTypes = parameterTypes;
		if (parameterNames == null) {
			this.parameterNames = IntStream.range(0, parameterTypes.length).mapToObj(i -> "arg" + i).toArray(String[]::new);
		} else {
			this.parameterNames = parameterNames;
		}
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
