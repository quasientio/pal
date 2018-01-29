package com.ittera.cometa.concentrator;

import com.ittera.cometa.messages.protobuf.data.Values.ReturnValue;
import com.ittera.cometa.messages.protobuf.data.Primitives;
import com.ittera.cometa.messages.protobuf.Unwrapper;

import static org.junit.Assert.*;

public class DataMessageAssertions {

	/**
	 * Helper assertion methods. Encapsulates details of the protobuf serialization.
	 *
	 * @param returnValue
	 * @param className
	 * @param hasObjRef
	 * @param isNull
	 * @param isArray
	 */
	private void assertIsObjectOfType(ReturnValue returnValue, String className, boolean hasObjRef, boolean isNull,
																		boolean isArray) {
		assertFalse(returnValue.getIsVoid());
		assertFalse(returnValue.getIsClass());
		assertTrue(returnValue.hasClazz());
		assertEquals(className, returnValue.getClazz().getName());
		assertTrue(returnValue.hasObject());

		Primitives.Object retObj = returnValue.getObject();
		assertEquals(isArray, retObj.getIsArray());
		assertEquals(isNull, retObj.getIsNull());
		assertEquals(hasObjRef, retObj.hasRef());
		assertTrue(retObj.hasClass_());
		assertFalse(retObj.getClass_().getUnknown());
		assertEquals(className, retObj.getClass_().getName());

	}

	protected void assertValueIsObjectOfType(ReturnValue returnValue, String className) {
		assertIsObjectOfType(returnValue, className, true, false, false);
	}

	protected void assertValueIsObjectRefOfType(ReturnValue returnValue, String className) {
		assertIsObjectOfType(returnValue, className, true, false, false);
	}

	protected void assertValueIsArrayOfType(ReturnValue returnValue, String className) {
		assertIsObjectOfType(returnValue, className, true, false, true);
	}

	protected void assertValueIsNullObjectOfType(ReturnValue returnValue, String className) {
		assertIsObjectOfType(returnValue, className, false, true, false);
	}

	protected void assertValueIsNullArrayOfType(ReturnValue returnValue, String className) {
		assertIsObjectOfType(returnValue, className, false, true, true);
	}

	protected <T> void assertValueEqualsArray(T[] actualArray, ReturnValue retValue) throws Exception {

		// check array type
		Class classOfArray = actualArray.getClass();
		Object rawObj = Unwrapper.unwrapObject(retValue.getObject());
		assertTrue(classOfArray.isInstance(rawObj));

		// check length
		assertEquals(actualArray.length, ((T[]) rawObj).length);

		// check contents equal
		for (int i = 0; i < actualArray.length; i++) {
			assertEquals(actualArray[i], ((T[]) rawObj)[i]);
		}
	}

}
