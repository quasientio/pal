package com.ittera.cometa.concentrator;

import org.junit.Test;

/**
 * Naming convention to use: methodName_stateUnderTest_expectedBehavior
 * <p>
 * TODO we should check the calls worked: As these methods are void, we should store some value in a field of the
 * target object and check it (+ revert it)
 */
public class VoidClassMethodMessageIT extends AbstractPeerMessageIT {

	protected final String className = "com.ittera.cometa.apps.VoidStaticMethods";

	@Test
	public void privateWithArg() throws Exception {

		String methodName = "testVoidStatic";

		String[] parameterTypes = new String[]{"java.lang.String"};
		Object[] parameters = new Object[]{"Hello from a unit test"};

		// test call
		callVoidClassMethod(className, methodName, parameterTypes, parameters, new String[parameterTypes.length]);
	}

	@Test
	public void privateWithPrimitiveAndWrapperArgs() throws Exception {

		String methodName = "printArg";

		String[] parameterTypes = new String[]{"int", "java.lang.String"};
		Object[] parameters = new Object[]{2, "more than an argument"};

		// test call
		callVoidClassMethod(className, methodName, parameterTypes, parameters, new String[parameterTypes.length]);
	}

	@Test
	public void packageWithNoArgs() throws Exception {

		String methodName = "doSomethingStatically";

		String[] parameterTypes = new String[]{};
		Object[] parameters = new Object[]{};

		// test call
		callVoidClassMethod(className, methodName, parameterTypes, parameters, new String[parameterTypes.length]);
	}


	@Test
	public void publicStaticVoidMain() throws Exception {

		//test main
		String methodName = "main";

		String[] parameterTypes = new String[]{"[Ljava.lang.String;"};
		Object[] parameters = new Object[]{new String[]{}};

		// test call
		callVoidClassMethod(className, methodName, parameterTypes, parameters, new String[parameterTypes.length]);
	}

	@Test
	public void withObjectrefAsArg() throws Exception {

		String methodName = "sumUpList";

		//new ArrayList<Integer>
		String listObjRef = callConstructor("java.util.ArrayList").getObject().getRef();

		//add some int's
		int[] someInts = {39, 5, 58, 32, 70, 42};
		for (int i = 0; i < someInts.length; i++) {
			callInstanceMethod("java.util.ArrayList", "add", listObjRef,
				new String[]{"java.lang.Integer"}, new Object[]{someInts[i]}, new String[someInts.length]);
		}


		String[] parameterTypes = new String[]{"java.util.ArrayList"};
		Object[] parameters = new Object[parameterTypes.length];
		String[] paramObjRefs = new String[]{listObjRef};

		// test call
		callVoidClassMethod(className, methodName, parameterTypes, parameters, paramObjRefs);
	}
}
