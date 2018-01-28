package com.ittera.cometa.concentrator;

import com.ittera.cometa.messages.protobuf.Unwrapper;
import com.ittera.cometa.messages.protobuf.data.Values.ReturnValue;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Naming convention to use: methodName_stateUnderTest_expectedBehavior
 * <p>
 * TODO:
 * - null value
 * - private, protected, package-visible
 * - primitives
 * - arrays
 * - objectrefs
 */
public class SetClassVariableMessageIT extends AbstractPeerMessageIT {

	protected final String className = "com.ittera.cometa.apps.StaticVars";

	@Test
	public void testPutStaticIntegerNotNull() throws Exception {

		String fieldName = "aStaticInteger";
		String fieldClassName = "int";

		Integer originalValue = 3000;
		Integer newValue = 3200;

		// get field
		ReturnValue retValue = callGetStatic(className, fieldName);

		// test returned (original) value
		Object rawObj = Unwrapper.unwrapObject(retValue.getObject());
		assertTrue(rawObj instanceof Integer);
		assertEquals(originalValue, rawObj);

		// set a new value
		callPutStatic(className, fieldName, fieldClassName, newValue);

		// get again and test
		retValue = callGetStatic(className, fieldName);
		assertValueIsObjectOfType(retValue, fieldClassName);
		rawObj = Unwrapper.unwrapObject(retValue.getObject());
		assertTrue(rawObj instanceof Integer);
		assertEquals(newValue, rawObj);

		//END OF TEST

		//now revert changed value to original (otherwise other tests may fail after a 1st run)
		callPutStatic(className, fieldName, fieldClassName, originalValue);
	}

	@Test
	public void testPutStaticStringNotNull() throws Exception {

		//test with a non null String
		String fieldName = "aClassString";
		String fieldClassName = "java.lang.String";

		String originalValue = "I'm classy";
		String newValue = "New dummy str";

		// get field
		ReturnValue retValue = callGetStatic(className, fieldName);

		// test returned (original) value
		Object rawObj = Unwrapper.unwrapObject(retValue.getObject());
		assertTrue(rawObj instanceof String);
		assertEquals(originalValue, rawObj);

		// set a new value
		callPutStatic(className, fieldName, fieldClassName, newValue);

		//test that the field has now the new value
		retValue = callGetStatic(className, fieldName);
		assertValueIsObjectOfType(retValue, fieldClassName);
		rawObj = Unwrapper.unwrapObject(retValue.getObject());
		assertTrue(rawObj instanceof String);
		assertEquals(newValue, rawObj);

		//END OF TEST

		//now revert changed value to original (otherwise other tests may fail after a 1st run)
		callPutStatic(className, fieldName, fieldClassName, originalValue);
	}

}
