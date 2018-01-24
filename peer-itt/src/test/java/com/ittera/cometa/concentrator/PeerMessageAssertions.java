package com.ittera.cometa.concentrator;

import com.ittera.cometa.messages.protobuf.data.Values.ReturnValue;
import com.ittera.cometa.messages.protobuf.data.Primitives;

import static org.junit.Assert.*;

public class PeerMessageAssertions {

	/**
	 * Helper assertion methods
	 * This method is also useful as it encapsulates details of the protobuf serialization
	 *
	 * @param returnValue
	 * @param className
	 * @param hasObjRef
	 * @param isNull
	 * @param isArray
	 */
	private void isObjectOfRightType(ReturnValue returnValue, String className, boolean hasObjRef, boolean isNull, boolean isArray) {
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

	protected void assertValueIsObjectOfRightType(ReturnValue returnValue, String className) {
		isObjectOfRightType(returnValue, className, true, false, false);
	}

	protected void assertValueIsObjectRefOfRightType(ReturnValue returnValue, String className) {
		isObjectOfRightType(returnValue, className, true, false, false);
	}

	protected void assertValueIsArrayOfRightType(ReturnValue returnValue, String className) {
		isObjectOfRightType(returnValue, className, true, false, true);
	}

	protected void assertValueIsNullObjectOfRightType(ReturnValue returnValue, String className) {
		isObjectOfRightType(returnValue, className, false, true, false);
	}

	protected void assertValueIsNullArrayOfRightType(ReturnValue returnValue, String className) {
		isObjectOfRightType(returnValue, className, false, true, true);
	}

}
