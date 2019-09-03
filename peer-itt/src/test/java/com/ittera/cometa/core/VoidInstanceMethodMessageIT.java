package com.ittera.cometa.core;

import com.ittera.cometa.common.lang.ObjectRef;

import org.junit.Test;

/**
 * Naming convention to use: methodName_stateUnderTest_expectedBehavior
 * <p>
 * TODO
 * - arrays
 */
public class VoidInstanceMethodMessageIT extends AbstractPeerMessageIT {

	protected final String className = "com.ittera.cometa.apps.VoidInstanceMethods";

	@Test
	public void callInstanceMethod_packageVisibleNoArgs_void() throws Exception {

		String methodName = "doSomething";

		// create new instance
		ObjectRef newObjRef = ObjectRef.from(callEmptyConstructor(className).getObject().getRef());

		// now call the method
		String[] parameterTypes = {};
		callVoidInstanceMethod(className, methodName, newObjRef, parameterTypes, new Object[parameterTypes.length],
			new ObjectRef[parameterTypes.length]);
	}

	@Test
	public void callInstanceMethod_privateWithArg_void() throws Exception {

		String methodName = "testArg";

		// create new instance
		ObjectRef newObjRef = ObjectRef.from(callEmptyConstructor(className).getObject().getRef());

		// now call the method
		String param = "testing testing 1 2 3";
		Object[] parameters = {param};
		String[] parameterTypes = {param.getClass().getName()};
		callVoidInstanceMethod(className, methodName, newObjRef, parameterTypes, parameters,
			new ObjectRef[parameterTypes.length]);
	}

	@Test
	public void callInstanceMethod_protectedNoArgs_void() throws Exception {

		String methodName = "printDate";

		// create new instance
		ObjectRef newObjRef = ObjectRef.from(callEmptyConstructor(className).getObject().getRef());

		// now call the method
		String[] parameterTypes = {};
		callVoidInstanceMethod(className, methodName, newObjRef, parameterTypes, new Object[parameterTypes.length],
			new ObjectRef[parameterTypes.length]);
	}

	@Test
	public void callInstanceMethod_nullArg_throwsEx() throws Exception {

		String methodName = "testNonNullArg";

		// create new instance
		ObjectRef newObjRef = ObjectRef.from(callEmptyConstructor(className).getObject().getRef());

		// now call the method
		String param = null;
		Object[] parameters = {param};
		String[] parameterTypes = {String.class.getName()};
		callVoidInstanceMethod(className, methodName, newObjRef, parameterTypes, parameters,
			new ObjectRef[parameterTypes.length], "java.lang.NullPointerException");
	}

	@Test
	public void callInstanceMethod_noSuchClass_throwsEx() throws Exception {
		String nonExistingClass = "com.ittera.cometa.apps.IDontExist";
		String methodName = "testNonNullArg";

		// create new instance
		ObjectRef newObjRef = ObjectRef.from(callEmptyConstructor(className).getObject().getRef());

		// now call the method on a wrong class
		String param = null;
		Object[] parameters = {param};
		String[] parameterTypes = {String.class.getName()};
		callVoidInstanceMethod(nonExistingClass, methodName, newObjRef, parameterTypes, parameters,
			new ObjectRef[parameterTypes.length], "java.lang.ClassNotFoundException");
	}

	@Test
	public void callInstanceMethod_noSuchMethod_throwsEx() throws Exception {

		String methodName = "a_made_up_method";

		// create new instance
		ObjectRef newObjRef = ObjectRef.from(callEmptyConstructor(className).getObject().getRef());

		// now call the method
		String param = null;
		Object[] parameters = {param};
		String[] parameterTypes = {String.class.getName()};
		callVoidInstanceMethod(className, methodName, newObjRef, parameterTypes, parameters,
			new ObjectRef[parameterTypes.length], "java.lang.NoSuchMethodException");
	}

	@Test
	public void callInstanceMethod_noSuchInstance_throwsEx() throws Exception {

		String methodName = "printDate";

		// create new instance
		ObjectRef newObjRef = ObjectRef.from("Not_A_Real_ObjRef");

		// now call the method
		Object[] parameters = {};
		String[] parameterTypes = {};
		callVoidInstanceMethod(className, methodName, newObjRef, parameterTypes, parameters,
			new ObjectRef[parameterTypes.length], "com.ittera.cometa.common.lang.ObjectNotFoundException");
	}
}
