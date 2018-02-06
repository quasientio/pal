package com.ittera.cometa.concentrator.exec.java;

import com.ittera.cometa.concentrator.exec.java.reflect.ConstructorSignature;
import com.ittera.cometa.concentrator.exec.java.reflect.FieldSignature;
import com.ittera.cometa.concentrator.exec.java.reflect.MethodSignature;
import com.ittera.cometa.concentrator.exec.java.reflect.Signature;
import org.aspectj.lang.JoinPoint;

import java.lang.reflect.Constructor;

/**
 * This class (and the ones under .reflect subpackage) partly hold info that will be extracted
 * from aspectj's JoinPoint.StaticPart, allowing us to construct instances for unit-testing,
 * and adding more useful contextual information.
 */
class Context {

	private final String sourceFilename;
	private final int sourceLine;
	private final Class withinType;
	private final Signature signature;

	Context(String sourceFilename, int sourceLine, Class withinType, Signature signature) {
		this.sourceFilename = sourceFilename;
		this.sourceLine = sourceLine;
		this.withinType = withinType;
		this.signature = signature;
	}

	String getFileName() {
		return sourceFilename;
	}

	int getSourceLine() {
		return sourceLine;
	}

	Class getWithinType() {
		return withinType;
	}

	Signature getSignature() {
		return signature;
	}

	Context parseFrom(final JoinPoint.StaticPart staticPart) {
		final String filename = staticPart.getSourceLocation().getFileName();
		final int sourceLine = staticPart.getSourceLocation().getLine();
		final Class withinType = staticPart.getSourceLocation().getWithinType();

		final Signature signature;
		org.aspectj.lang.Signature ajSig = staticPart.getSignature();

		// extract common Signature fields
		final Class declaringType = ajSig.getDeclaringType();
		final String declaringTypeName = ajSig.getDeclaringTypeName();
		final int modifiers = ajSig.getModifiers();
		final String name = ajSig.getName();

		// extract common CodeSignature fields
		if (ajSig instanceof org.aspectj.lang.reflect.CodeSignature) {
			org.aspectj.lang.reflect.CodeSignature ajCodeSig = (org.aspectj.lang.reflect.CodeSignature) ajSig;

			final Class[] exceptionTypes = ajCodeSig.getExceptionTypes();
			final String[] parameterNames = ajCodeSig.getParameterNames();
			final Class[] parameterTypes = ajCodeSig.getParameterTypes();

			// finally pull out specific fields of concrete types MethodSignature or ConstructorSignature
			if (ajSig instanceof org.aspectj.lang.reflect.MethodSignature) {

				org.aspectj.lang.reflect.MethodSignature ajMethodSig = (org.aspectj.lang.reflect.MethodSignature) ajSig;
				signature = new MethodSignature(declaringType, declaringTypeName, modifiers, name, exceptionTypes,
					parameterNames, parameterTypes, ajMethodSig.getMethod(), ajMethodSig.getReturnType());
			} else if (ajSig instanceof org.aspectj.lang.reflect.ConstructorSignature) {

				org.aspectj.lang.reflect.ConstructorSignature ajConsSig = (org.aspectj.lang.reflect.ConstructorSignature) ajSig;
				signature = new ConstructorSignature(declaringType, declaringTypeName, modifiers, name, exceptionTypes,
					parameterNames, parameterTypes, ajConsSig.getConstructor());
			} else {
				throw new IllegalArgumentException("Cannot handle signature of type: " + ajSig.getClass().getName());
			}
		}  // pull out specific fields of FieldSignature
		else if (ajSig instanceof org.aspectj.lang.reflect.FieldSignature) {
			org.aspectj.lang.reflect.FieldSignature ajFieldSig = (org.aspectj.lang.reflect.FieldSignature) ajSig;
			signature = new FieldSignature(declaringType, declaringTypeName, modifiers, name, ajFieldSig.getField(), ajFieldSig.getFieldType());
		} else {
			throw new IllegalArgumentException("Cannot handle signature of type: " + ajSig.getClass().getName());
		}

		return new Context(sourceFilename, sourceLine, withinType, signature);
	}
}
