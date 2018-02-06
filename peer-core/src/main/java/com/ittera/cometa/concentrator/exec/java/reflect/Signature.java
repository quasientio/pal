package com.ittera.cometa.concentrator.exec.java.reflect;

public abstract class Signature {

	private final Class declaringType;
	private final String declaringTypeName;
	private final int modifiers;
	private final String name;

	protected Signature(Class declaringType, String declaringTypeName, int modifiers, String name) {
		this.declaringType = declaringType;
		this.declaringTypeName = declaringTypeName;
		this.modifiers = modifiers;
		this.name = name;
	}
}
