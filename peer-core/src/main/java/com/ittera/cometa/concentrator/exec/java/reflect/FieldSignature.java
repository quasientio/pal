package com.ittera.cometa.concentrator.exec.java.reflect;

import java.lang.reflect.Field;

public class FieldSignature extends Signature {

	private final Field field;
	private final Class fieldType;

	public FieldSignature(Class declaringType, String declaringTypeName, int modifiers, String name,
												Field field, Class fieldType) {
		super(declaringType, declaringTypeName, modifiers, name);
		this.field = field;
		this.fieldType = fieldType;
	}

	public Field getField() {
		return field;
	}

	public Class getFieldType() {
		return fieldType;
	}
}
