package com.ittera.cometa.concentrator;

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
	public void packageVisibleNoArgs() throws Exception {

		String methodName = "doSomething";

		// create new instance
		String newObjRef = callConstructor(className).getObject().getRef();

		// now call the method
		String[] parameterTypes = new String[]{};
		callVoidInstanceMethod(className, methodName, newObjRef, parameterTypes, new Object[parameterTypes.length],
			new String[parameterTypes.length]);
	}

	@Test
	public void privateWithArg() throws Exception {

		String methodName = "testArg";

		// create new instance
		String newObjRef = callConstructor(className).getObject().getRef();

		// now call the method
		String param = "testing testing 1 2 3";
		Object[] parameters = new Object[]{param};
		String[] parameterTypes = new String[]{param.getClass().getName()};
		callVoidInstanceMethod(className, methodName, newObjRef, parameterTypes, parameters,
			new String[parameterTypes.length]);
	}

	@Test
	public void protectedNoArgs() throws Exception {

		String methodName = "printDate";

		// create new instance
		String newObjRef = callConstructor(className).getObject().getRef();

		// now call the method
		String[] parameterTypes = new String[]{};
		callVoidInstanceMethod(className, methodName, newObjRef, parameterTypes, new Object[parameterTypes.length],
			new String[parameterTypes.length]);
	}
}
