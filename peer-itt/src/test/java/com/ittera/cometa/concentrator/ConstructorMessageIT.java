package com.ittera.cometa.concentrator;

import com.ittera.cometa.messages.protobuf.data.Values;
import com.ittera.cometa.messages.protobuf.data.Wrappers.DataMessage;

import com.ittera.cometa.apps.Constructors;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Naming convention to use: methodName_stateUnderTest_expectedBehavior
 *
 * <p>
 * TODO
 * - varargs constructor
 * - constructor that takes Object, passing a Constructors instance. This will test ReflectionHelper,
 *   as it should invoke the more specific constructor (the one taking Constructors type, not Object type)
 * - invoke constructor using constructor-ref (requires [ticket:15])
 * - constructor throwing exception
 */
public class ConstructorMessageIT extends AbstractPeerIntegrationTest {

	protected final String className = "com.ittera.cometa.apps.Constructors";

	/**
	 * Explained here why won't pass
	 * https://stackoverflow.com/questions/32301892/nosuchmethodexception-for-public-no-argument-constructor-in-inner-class
	 */
	//@Test
	public void innerConstructor() throws Exception {

		String className = "com.ittera.cometa.apps.Constructors$Empty";
		DataMessage requestMsg = dataMessageBuilder.buildEmptyConstructor(clientId, className);
		DataMessage replyMsg = sendAndReceive(requestMsg);

		assertTrue(replyMsg.hasReturnValue());
		assertValueIsObjectRefOfRightType(replyMsg.getReturnValue(), className);
	}

	@Test
	public void constructor_publicNoArgs_newObjectReturned() throws Exception {

		DataMessage requestMsg = dataMessageBuilder.buildEmptyConstructor(clientId, className);
		DataMessage replyMsg = sendAndReceive(requestMsg);

		assertTrue(replyMsg.hasReturnValue());
		assertValueIsObjectRefOfRightType(replyMsg.getReturnValue(), className);
	}

	@Test
	public void constructor_publicOneArg_newObjectReturned() throws Exception {

		Object[] args = {Integer.valueOf(5)};
		String[] argRefs = {null};
		Class[] parameterTypes = new Class[]{Integer.class};

		String[] parameterTypesNamesArray = new String[parameterTypes.length];
		for (int i = 0; i < parameterTypes.length; i++) {
			parameterTypesNamesArray[i] = parameterTypes[i].getName();
		}

		DataMessage requestMsg = dataMessageBuilder.buildNonEmptyConstructor(clientId, className, parameterTypesNamesArray, args, argRefs);
		DataMessage replyMsg = sendAndReceive(requestMsg);

		assertTrue(replyMsg.hasReturnValue());
		assertValueIsObjectRefOfRightType(replyMsg.getReturnValue(), className);
	}

	@Test
	public void constructor_packageVisibleTwoArgs_newObjectReturned() throws Exception {

		Object[] args = {"Constructing an app", Integer.valueOf(5)};
		String[] argRefs = {null, null};
		Class[] parameterTypes = new Class[]{String.class, Integer.class};

		String[] parameterTypesNamesArray = new String[parameterTypes.length];
		for (int i = 0; i < parameterTypes.length; i++) {
			parameterTypesNamesArray[i] = parameterTypes[i].getName();
		}

		DataMessage requestMsg = dataMessageBuilder.buildNonEmptyConstructor(clientId, className, parameterTypesNamesArray, args, argRefs);
		DataMessage replyMsg = sendAndReceive(requestMsg);

		assertTrue(replyMsg.hasReturnValue());
		assertValueIsObjectRefOfRightType(replyMsg.getReturnValue(), className);
	}

	@Test
	public void constructor_publicOneArgNull_newObjectReturned() throws Exception {

		Object[] args = {null};
		String[] argRefs = {null};
		Class[] parameterTypes = new Class[]{Integer.class};

		String[] parameterTypesNamesArray = new String[parameterTypes.length];

		for (int i = 0; i < parameterTypes.length; i++) {
			parameterTypesNamesArray[i] = parameterTypes[i].getName();
		}

		DataMessage requestMsg = dataMessageBuilder.buildNonEmptyConstructor(clientId, className, parameterTypesNamesArray, args, argRefs);
		DataMessage replyMsg = sendAndReceive(requestMsg);

		assertTrue(replyMsg.hasReturnValue());
		assertValueIsObjectRefOfRightType(replyMsg.getReturnValue(), className);
	}

	@Test
	public void constructor_privateOneArgArray_newObjectReturned() throws Exception {

		Object[] args = {new String[]{"Aa", "Bb", "Cc"}};
		String[] argRefs = {null};
		Class[] parameterTypes = new Class[]{String[].class};

		String[] parameterTypesNamesArray = new String[parameterTypes.length];
		for (int i = 0; i < parameterTypes.length; i++) {
			parameterTypesNamesArray[i] = parameterTypes[i].getName();
		}

		DataMessage requestMsg = dataMessageBuilder.buildNonEmptyConstructor(clientId, className, parameterTypesNamesArray, args, argRefs);
		DataMessage replyMsg = sendAndReceive(requestMsg);

		assertTrue(replyMsg.hasReturnValue());
		assertValueIsObjectRefOfRightType(replyMsg.getReturnValue(), className);
	}

	@Test
	public void constructor_protectedOneArgRef_newObjectReturned() throws Exception {

		//1. Construct an instance calling no-args constructor
		DataMessage requestMsg = dataMessageBuilder.buildEmptyConstructor(clientId, className);
		DataMessage replyMsg = sendAndReceive(requestMsg);

		assertTrue(replyMsg.hasReturnValue());
		assertValueIsObjectRefOfRightType(replyMsg.getReturnValue(), className);
		Values.ReturnValue retValue = replyMsg.getReturnValue();
		String newObjRef = retValue.getObject().getRef();

		//2. Construct an instance calling the constructor that takes another instance as arg

		Object[] args = {null};
		String[] argRefs = {newObjRef};
		Class[] parameterTypes = new Class[]{Constructors.class};

		String[] parameterTypesNamesArray = new String[parameterTypes.length];
		for (int i = 0; i < parameterTypes.length; i++) {
			parameterTypesNamesArray[i] = parameterTypes[i].getName();
		}

		requestMsg = dataMessageBuilder.buildNonEmptyConstructor(clientId, className, parameterTypesNamesArray, args, argRefs);
		replyMsg = sendAndReceive(requestMsg);

		assertTrue(replyMsg.hasReturnValue());
		assertValueIsObjectRefOfRightType(replyMsg.getReturnValue(), className);
	}

}
