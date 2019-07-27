package com.ittera.cometa.concentrator;

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
		String[] parameterTypes = new String[]{};
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
		Object[] parameters = new Object[]{param};
		String[] parameterTypes = new String[]{param.getClass().getName()};
		callVoidInstanceMethod(className, methodName, newObjRef, parameterTypes, parameters,
			new ObjectRef[parameterTypes.length]);
	}

	@Test
	public void callInstanceMethod_protectedNoArgs_void() throws Exception {

		String methodName = "printDate";

		// create new instance
		ObjectRef newObjRef = ObjectRef.from(callEmptyConstructor(className).getObject().getRef());

		// now call the method
		String[] parameterTypes = new String[]{};
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
		Object[] parameters = new Object[]{param};
		String[] parameterTypes = new String[]{String.class.getName()};
		callVoidInstanceMethod(className, methodName, newObjRef, parameterTypes, parameters,
			new ObjectRef[parameterTypes.length], "java.lang.NullPointerException");
	}
}
