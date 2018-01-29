package com.ittera.cometa.concentrator;

import com.ittera.cometa.messages.protobuf.data.Values.ReturnValue;
import com.ittera.cometa.messages.protobuf.Unwrapper;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.*;

/**
 * Naming convention to use: methodName_stateUnderTest_expectedBehavior
 * <p>
 * TODO:
 * - returningObjectRefArray() commented out below
 */
public class NonVoidClassMethodMessageIT extends AbstractPeerMessageIT {

	protected final String className = "com.ittera.cometa.apps.NonVoidStaticMethods";

	@Test
	public void callClassMethod_privateWithArg_retValue() throws Exception {

		String methodName = "testNonVoidStatic";

		String param = "GIVE ME THIS IN LOWERCASE";
		Object[] parameters = new Object[]{param};
		String[] parameterTypes = new String[]{param.getClass().getName()};
		String[] paramObjRefs = new String[parameters.length];

		ReturnValue retValue = callClassMethod(className, methodName, parameterTypes, parameters, paramObjRefs);

		// test returned value
		String shouldReturn = param.toLowerCase();
		assertValueIsObjectOfType(retValue, shouldReturn.getClass().getName());
		Object rawObj = Unwrapper.unwrapObject(retValue.getObject());
		assertEquals(shouldReturn, rawObj);
	}

	@Test
	public void callClassMethod_protectedNoArgs_retValue() throws Exception {

		String methodName = "highFive";

		String[] parameterTypes = new String[]{};
		Object[] parameters = new Object[]{};
		String[] paramObjRefs = new String[parameters.length];
		ReturnValue retValue = callClassMethod(className, methodName, parameterTypes, parameters, paramObjRefs);

		// test returned value
		Integer shouldReturn = 5;
		assertValueIsObjectOfType(retValue, shouldReturn.getClass().getName());
		Object rawObj = Unwrapper.unwrapObject(retValue.getObject());
		assertEquals(shouldReturn, rawObj);
	}

	@Test
	public void callClassMethod_returnsIntegerSum_retValue() throws Exception {

		String methodName = "nonVoidSumUpList";

		// new ArrayList<Integer>
		String listObjRef = callConstructor("java.util.ArrayList").getObject().getRef();

		// add some int's
		int[] someInts = {39, 5, 58, 32, 70, 42};
		for (int i = 0; i < someInts.length; i++) {
			callInstanceMethod("java.util.ArrayList", "add", listObjRef,
				new String[]{"java.lang.Integer"}, new Object[]{someInts[i]}, new String[someInts.length]);
		}

		// call method
		String[] parameterTypes = new String[]{"java.util.ArrayList"};
		Object[] params = new Object[parameterTypes.length];
		String[] objRefs = new String[]{listObjRef};
		ReturnValue retValue = callClassMethod(className, methodName, parameterTypes, params, objRefs);

		// test returned value
		Integer shouldReturn = Arrays.stream(someInts).reduce(0, Integer::sum);
		assertValueIsObjectOfType(retValue, shouldReturn.getClass().getName());
		Object rawObj = Unwrapper.unwrapObject(retValue.getObject());
		assertEquals(shouldReturn, rawObj);
	}


	@Test
	public void callClassMethod_returningNullObject_nullRetValue() throws Exception {

		String methodName = "giveMeANull";

		// call method
		String[] parameterTypes = new String[]{};
		ReturnValue retValue = callClassMethod(className, methodName, parameterTypes,
			new Object[parameterTypes.length], new String[parameterTypes.length]);

		// test returned value
		assertValueIsNullObjectOfType(retValue, "java.lang.Object");
		Object rawObj = Unwrapper.unwrapObject(retValue.getObject());
		assertEquals(null, rawObj);
	}

	@Test
	public void callClassMethod_returningCharArray_retValue() throws Exception {

		String methodName = "toCharArray";

		// call method
		String param = "split me up";
		String[] parameterTypes = new String[]{param.getClass().getName()};
		Object[] parameters = new Object[]{param};
		ReturnValue retValue = callClassMethod(className, methodName, parameterTypes, parameters,
			new String[parameterTypes.length]);

		// test returned value
		char[] shouldReturn = param.toCharArray();
		assertValueIsArrayOfType(retValue, shouldReturn.getClass().getName());
		Object rawObj = Unwrapper.unwrapObject(retValue.getObject());
		assertArrayEquals(shouldReturn, (char[]) rawObj);
	}


	@Test
	public void callClassMethod_returningEmptyArray_retValue() throws Exception {

		String methodName = "giveMeAnEmptyLongArray";

		// call method
		String[] parameterTypes = new String[]{};
		Object[] parameters = new Object[]{};
		ReturnValue retValue = callClassMethod(className, methodName, parameterTypes, parameters,
			new String[parameterTypes.length]);

		// test returned value
		Long[] shouldReturn = new Long[]{};
		assertValueIsArrayOfType(retValue, shouldReturn.getClass().getName());
		Object rawObj = Unwrapper.unwrapObject(retValue.getObject());
		assertArrayEquals(shouldReturn, (Long[]) rawObj);
	}

	@Test
	public void callClassMethod_returningNullArray_nullRetValue() throws Exception {

		String methodName = "giveMeANullBoolArray";

		// call method
		String[] parameterTypes = new String[]{};
		ReturnValue retValue = callClassMethod(className, methodName, parameterTypes,
			new Object[parameterTypes.length], new String[parameterTypes.length]);

		// test returned value
		Boolean[] shouldReturn = null;
		assertValueIsNullArrayOfType(retValue, "[Ljava.lang.Boolean;");
		Object rawObj = Unwrapper.unwrapObject(retValue.getObject());
		assertArrayEquals(shouldReturn, (Boolean[]) rawObj);
	}

	@Test
	public void callClassMethod_returningObjectRef_refRetValue() throws Exception {

		String methodName = "fetchMeAThreadSingleton";

		String[] parameterTypes = new String[]{};
		Object[] parameters = new Object[]{};

		ReturnValue retValue = callClassMethod(className, methodName, parameterTypes, parameters,
			new String[parameterTypes.length]);

		// test returned value
		assertValueIsObjectRefOfType(retValue, "java.lang.Thread");

		// because field is a singleton, with a 2nd call we should get the same instance objectRef, let's make sure
		String appRef = retValue.getObject().getRef();

		retValue = callClassMethod(className, methodName, parameterTypes, parameters,
			new String[parameterTypes.length]);

		// test returned value
		assertValueIsObjectRefOfType(retValue, "java.lang.Thread");
		String secondAppRef = retValue.getObject().getRef();
		assertEquals(appRef, secondAppRef);
	}

	//	@Test
	public void callClassMethod_returningObjectRefArray_refRetValue() throws Exception {

		String methodName = "fetchMeAThreadArray";

		String[] parameterTypes = new String[]{};

		ReturnValue retValue = callClassMethod(className, methodName, parameterTypes,
			new Object[parameterTypes.length], new String[parameterTypes.length]);

		assertValueIsArrayOfType(retValue, String.format("[L%s;", "java.lang.Thread"));
	}
}

