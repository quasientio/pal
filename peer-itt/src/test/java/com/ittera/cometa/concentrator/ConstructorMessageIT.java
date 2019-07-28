package com.ittera.cometa.concentrator;

import com.ittera.cometa.common.lang.ObjectRef;

import com.ittera.cometa.apps.Constructors;

import org.junit.Test;

/**
 * Naming convention to use: methodName_stateUnderTest_expectedBehavior
 * <p>
 * TODO:
 * - varargs constructor
 * - constructor that takes Object, passing a Constructors instance. This will test ReflectionHelper,
 * as it should invoke the more specific constructor (the one taking Constructors type, not Object type)
 * - invoke constructor using constructor-ref (requires [ticket:15])
 * - inner constructor (commented out below), if it makes sense
 */
public class ConstructorMessageIT extends AbstractPeerMessageIT {

	protected final String className = "com.ittera.cometa.apps.Constructors";

	/**
	 * Explained here why won't pass
	 * https://stackoverflow.com/questions/32301892/nosuchmethodexception-for-public-no-argument-constructor-in-inner-class
	 */
	//@Test
	public void innerConstructor() throws Exception {

		String className = "com.ittera.cometa.apps.Constructors$Empty";
		callEmptyConstructor(className);
	}

	@Test
	public void constructor_publicNoArgs_newObjectReturned() throws Exception {

		callEmptyConstructor(className);
	}

	@Test
	public void constructor_publicOneArg_newObjectReturned() throws Exception {

		Object[] args = {5};
		ObjectRef[] argRefs = {null};
		Class[] parameterTypes = {Integer.class};

		callConstructor(className, parameterTypes, args, argRefs);
	}

	@Test
	public void constructor_packageVisibleTwoArgs_newObjectReturned() throws Exception {

		Object[] args = {"Constructing an app", 5};
		ObjectRef[] argRefs = {null, null};
		Class[] parameterTypes = {String.class, Integer.class};

		callConstructor(className, parameterTypes, args, argRefs);
	}

	@Test
	public void constructor_publicOneArgNull_newObjectReturned() throws Exception {

		Object[] args = {null};
		ObjectRef[] argRefs = {null};
		Class[] parameterTypes = {Integer.class};

		callConstructor(className, parameterTypes, args, argRefs);
	}

	@Test
	public void constructor_privateOneArgArray_newObjectReturned() throws Exception {

		Object[] args = {new String[]{"Aa", "Bb", "Cc"}};
		ObjectRef[] argRefs = {null};
		Class[] parameterTypes = {String[].class};

		callConstructor(className, parameterTypes, args, argRefs);
	}

	@Test
	public void constructor_protectedOneArgRef_newObjectReturned() throws Exception {

		//1. Construct an instance calling no-args constructor
		ObjectRef newObjRef = ObjectRef.from(callEmptyConstructor(className).getObject().getRef());

		//2. Construct an instance calling the constructor that takes another instance as arg
		Object[] args = {null};
		ObjectRef[] argRefs = {newObjRef};
		Class[] parameterTypes = {Constructors.class};

		callConstructor(className, parameterTypes, args, argRefs);
	}

	@Test
	public void constructor_constructor3DoublesDoesNotExist_exThrown() throws Exception {

		Object[] args = {239823d, 38723d, 2323d};
		ObjectRef[] argRefs = {null, null, null};
		Class[] parameterTypes = {Double.class, Double.class, Double.class};

		callConstructor(className, parameterTypes, args, argRefs, "java.lang.NoSuchMethodException");
	}

	@Test
	public void constructor_noSuchClass_exThrown() throws Exception {
		String nonExistingClass = "com.ittera.cometa.apps.IDontExist";

		Object[] args = {239823d, 38723d, 2323d};
		ObjectRef[] argRefs = {null, null, null};
		Class[] parameterTypes = {Double.class, Double.class, Double.class};

		callConstructor(nonExistingClass, parameterTypes, args, argRefs, "java.lang.ClassNotFoundException");
	}

	@Test
	public void constructor_publicOneBadArg_exThrown() throws Exception {

		Object[] args = {"not_a_number"};
		ObjectRef[] argRefs = {null};
		Class[] parameterTypes = {String.class};

		callConstructor(className, parameterTypes, args, argRefs, "java.lang.NumberFormatException");
	}
}
