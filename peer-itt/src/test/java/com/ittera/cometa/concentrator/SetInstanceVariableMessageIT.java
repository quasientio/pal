package com.ittera.cometa.concentrator;

import com.ittera.cometa.messages.protobuf.Unwrapper;
import com.ittera.cometa.messages.protobuf.data.Primitives;
import com.ittera.cometa.messages.protobuf.data.Values.ReturnValue;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Naming convention to use: methodName_stateUnderTest_expectedBehavior
 * <p>
 * TODO:
 * - private, protected, package-visible
 * - primitives
 * - arrays
 * - objectrefs
 */
public class SetInstanceVariableMessageIT extends AbstractPeerMessageIT {

	protected final String className = "com.ittera.cometa.apps.InstanceVars";

	@Test
	public void putField_integer_ok() throws Exception {

		String fieldName = "anInt";
		String fieldClassName = "java.lang.Integer";

		Integer originalValue = 4;
		Integer newValue = 500;

		// create new instance
		String newObjRef = callConstructor(className).getObject().getRef();

		// get instance variable, assert original value
		ReturnValue retValue = callGetInstanceVar(className, fieldName, newObjRef);
		Primitives.Object retObj = retValue.getObject();
		assertValueIsObjectOfType(retValue, fieldClassName);
		Object rawObj = Unwrapper.unwrapObject(retObj);
		assertTrue(rawObj instanceof Integer);
		assertEquals(originalValue, rawObj);

		// now call set to modify
		callPutField(className, fieldName, newObjRef, fieldClassName, newValue);

		// now get to test if set took place
		retValue = callGetInstanceVar(className, fieldName, newObjRef);
		rawObj = Unwrapper.unwrapObject(retValue.getObject());
		assertTrue(rawObj instanceof Integer);
		assertEquals(newValue, rawObj);
	}

	@Test
	public void putField_integerSetNull_ok() throws Exception {

		String fieldName = "anotherInt";
		String fieldClassName = "java.lang.Integer";

		Integer originalValue = 1;
		Integer newValue = null;

		// create new instance
		String newObjRef = callConstructor(className).getObject().getRef();

		// test with a non null integer
		ReturnValue retValue = callGetInstanceVar(className, fieldName, newObjRef);
		Primitives.Object retObj = retValue.getObject();
		assertValueIsObjectOfType(retValue, fieldClassName);
		Object rawObj = Unwrapper.unwrapObject(retObj);
		assertTrue(rawObj instanceof Integer);
		assertEquals(originalValue, rawObj);

		// set integer to null
		callPutField(className, fieldName, newObjRef, fieldClassName, newValue);

		// now get to test if set took place
		retValue = callGetInstanceVar(className, fieldName, newObjRef);
		assertValueIsNullObjectOfType(retValue, fieldClassName);
		rawObj = Unwrapper.unwrapObject(retValue.getObject());
		assertEquals(newValue, rawObj);
	}
}
