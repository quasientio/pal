package com.ittera.cometa.core;

import com.ittera.cometa.common.lang.ObjectRef;

import com.ittera.cometa.messages.protobuf.Unwrapper;
import com.ittera.cometa.messages.protobuf.data.Values.ReturnValue;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Naming convention to use: methodName_stateUnderTest_expectedBehavior
 * <p>
 * TODO
 * arrays
 * objectrefs
 * rest of primitive types (?)
 */
public class GetInstanceVariableMessageIT extends AbstractPeerMessageIT {

	protected final String className = "com.ittera.cometa.apps.InstanceVars";

	@Test
	public void getInstanceVariable_publicIntegerNotNull_intReturned() throws Exception {

		// create new instance
		ObjectRef newObjRef = ObjectRef.from(callEmptyConstructor(className).getObject().getRef());

		// now get instance variable
		ReturnValue retValue = callGetInstanceVar(className, "anInt", newObjRef);

		assertValueIsObjectOfType(retValue, "java.lang.Integer");
		Object rawObj = Unwrapper.unwrapObject(retValue.getObject());
		assertTrue(rawObj instanceof Integer);
		assertEquals(4, rawObj);
	}

	@Test
	public void getInstanceVariable_privateNullInteger_nullIntReturned() throws Exception {

		// create new instance
		ObjectRef newObjRef = ObjectRef.from(callEmptyConstructor(className).getObject().getRef());

		// now get instance variable
		ReturnValue retValue = callGetInstanceVar(className, "aNullInt", newObjRef);

		assertValueIsNullObjectOfType(retValue, "java.lang.Integer");
	}

	@Test
	public void getInstanceVariable_protectedStringNotNull_stringReturned() throws Exception {

		// create new instance
		ObjectRef newObjRef = ObjectRef.from(callEmptyConstructor(className).getObject().getRef());

		// now get instance variable
		ReturnValue retValue = callGetInstanceVar(className, "someString", newObjRef);

		assertValueIsObjectOfType(retValue, "java.lang.String");
		Object rawObj = Unwrapper.unwrapObject(retValue.getObject());
		assertTrue(rawObj instanceof String);
		assertEquals("I'm not blank", rawObj);
	}

	@Test
	public void getInstanceVariable_getPublicStringNull_nullStringReturned() throws Exception {

		// create new instance
		ObjectRef newObjRef = ObjectRef.from(callEmptyConstructor(className).getObject().getRef());

		// now get instance variable
		ReturnValue retValue = callGetInstanceVar(className, "aNullStr", newObjRef);

		assertValueIsNullObjectOfType(retValue, "java.lang.String");
	}

	@Test
	public void getInstanceVariable_packageVisibleBooleanNull_nullBoolReturned() throws Exception {

		// create new instance
		ObjectRef newObjRef = ObjectRef.from(callEmptyConstructor(className).getObject().getRef());

		// now get instance variable
		ReturnValue retValue = callGetInstanceVar(className, "aNullBool", newObjRef);

		assertValueIsNullObjectOfType(retValue, "java.lang.Boolean");
	}

	@Test
	public void getInstanceVariable_publicBoolNotNull_boolReturned() throws Exception {

		// create new instance
		ObjectRef newObjRef = ObjectRef.from(callEmptyConstructor(className).getObject().getRef());

		// now get instance variable
		ReturnValue retValue = callGetInstanceVar(className, "aBool", newObjRef);

		assertValueIsObjectOfType(retValue, "boolean");
		Object rawObj = Unwrapper.unwrapObject(retValue.getObject());
		assertTrue(rawObj instanceof Boolean);
		assertEquals(true, rawObj);
	}

	@Test
	public void getInstanceVariable_privateShortNotZero_shortReturned() throws Exception {

		// create new instance
		ObjectRef newObjRef = ObjectRef.from(callEmptyConstructor(className).getObject().getRef());

		// now get instance variable
		ReturnValue retValue = callGetInstanceVar(className, "someShort", newObjRef);

		assertValueIsObjectOfType(retValue, "short");
		Object rawObj = Unwrapper.unwrapObject(retValue.getObject());
		assertTrue(rawObj instanceof Short);
		assertEquals((short) 233, rawObj);
	}

	@Test
	public void getInstanceVariable_noSuchClass_exThrown() throws Exception {
		// create new instance
		ObjectRef newObjRef = ObjectRef.from(callEmptyConstructor(className).getObject().getRef());

		String nonExistingClass = "com.ittera.cometa.apps.IDontExist";
		callGetInstanceVar(nonExistingClass, "someShort", newObjRef,
			"java.lang.ClassNotFoundException");
	}

	@Test
	public void getInstanceVariable_noSuchInstance_exThrown() throws Exception {
		// create new instance
		ObjectRef newObjRef = ObjectRef.from("Not_A_Real_ObjRef");

		callGetInstanceVar(className, "someShort", newObjRef,
			"com.ittera.cometa.common.lang.ObjectNotFoundException");
	}

	@Test
	public void getInstanceVariable_noSuchField_exThrown() throws Exception {

		// create new instance
		ObjectRef newObjRef = ObjectRef.from(callEmptyConstructor(className).getObject().getRef());

		// now get instance variable
		callGetInstanceVar(className, "aMadeUpField", newObjRef,
			"java.lang.NoSuchFieldException");
	}
}
