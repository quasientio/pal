package com.ittera.cometa.concentrator;

import com.ittera.cometa.cxn.ThinPeer;

import com.ittera.cometa.messages.protobuf.ProtobufDataMessageBuilder;
import com.ittera.cometa.messages.protobuf.data.Wrappers.DataMessage;
import com.ittera.cometa.messages.protobuf.data.Values.ReturnValue;
import com.ittera.cometa.messages.protobuf.data.Fields.*;
import com.ittera.cometa.messages.DataMessageBuilder;

import com.ittera.cometa.common.lang.ObjectRef;
import com.ittera.cometa.common.ObjectService;
import com.ittera.cometa.common.BiMapObjectService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;
import java.util.Properties;

import java.io.InputStream;

import org.junit.AfterClass;
import org.junit.BeforeClass;

import com.google.inject.AbstractModule;
import com.google.inject.Injector;
import com.google.inject.Guice;

import static org.junit.Assert.*;

public abstract class AbstractPeerMessageIT extends DataMessageAssertions {

	protected final static Logger logger = LoggerFactory.getLogger("tests");

	protected static final String TEST_PROPERTIES_PATH = "/tests.properties";

	protected final static UUID clientId = UUID.randomUUID();

	protected static DataMessageBuilder dataMessageBuilder;
	private static ThinPeer thinPeer;

	@BeforeClass
	public static void initialize() throws Exception {

		// configure wiring
		AbstractModule module = new AbstractModule() {
			@Override
			protected void configure() {
				bind(ObjectService.class).to(BiMapObjectService.class).asEagerSingleton();
				bind(DataMessageBuilder.class).to(ProtobufDataMessageBuilder.class).asEagerSingleton();
			}
		};

		final Injector injector = Guice.createInjector(module);
		dataMessageBuilder = injector.getInstance(DataMessageBuilder.class);

		final Properties properties = new Properties();
		try (final InputStream stream = AbstractPeerMessageIT.class.getResourceAsStream(TEST_PROPERTIES_PATH)) {
			properties.load(stream);
		}
		thinPeer = new ThinPeer(properties);
	}

	protected DataMessage sendAndReceive(DataMessage message) throws Exception {
		return thinPeer.sendAndReceive(message);
	}

	@AfterClass
	public static void finalizeStuff() {
		logger.debug("Finalizing after tests...");
		if (thinPeer != null) {
			thinPeer.close();
		}
	}

	/**
	 * Helper Methods
	 **/

	protected ReturnValue callConstructor(String className, Class[] parameterTypes, Object[] args,
																				ObjectRef[] argObjRefs) throws Exception {
		return callConstructor(className, parameterTypes, args, argObjRefs, null);
	}

	protected ReturnValue callConstructor(String className, Class[] parameterTypes, Object[] args,
																				ObjectRef[] argObjRefs, String expectedThrowableType) throws Exception {
		String[] parameterTypesNamesArray = new String[parameterTypes.length];
		for (int i = 0; i < parameterTypes.length; i++) {
			parameterTypesNamesArray[i] = parameterTypes[i].getName();
		}

		DataMessage replyMsg = sendAndReceive(dataMessageBuilder.buildNonEmptyConstructor(clientId, className,
			parameterTypesNamesArray, args, argObjRefs));

		// basic assertions
		if (expectedThrowableType != null) {
			assertHasThrowableOfType(replyMsg, expectedThrowableType);
		} else {
			assertTrue(replyMsg.hasReturnValue());
			assertValueIsObjectRefOfType(replyMsg.getReturnValue(), className);
		}

		return replyMsg.getReturnValue();
	}

	protected ReturnValue callEmptyConstructor(String className) throws Exception {
		return callEmptyConstructor(className, null);
	}

	protected ReturnValue callEmptyConstructor(String className, String expectedThrowableType) throws Exception {
		DataMessage replyMsg = sendAndReceive(dataMessageBuilder.buildEmptyConstructor(clientId, className));

		// basic assertions
		if (expectedThrowableType != null) {
			assertHasThrowableOfType(replyMsg, expectedThrowableType);
		} else {
			assertTrue(replyMsg.hasReturnValue());
			assertValueIsObjectRefOfType(replyMsg.getReturnValue(), className);
		}

		return replyMsg.getReturnValue();
	}

	protected ReturnValue callGetStatic(String className, String fieldName) throws Exception {
		return callGetStatic(className, fieldName, null);
	}

	protected ReturnValue callGetStatic(String className, String fieldName, String expectedThrowableType) throws Exception {
		DataMessage requestMsg = dataMessageBuilder.buildGetStatic(clientId, className, fieldName);
		DataMessage replyMsg = sendAndReceive(requestMsg);

		// basic assertions
		if (expectedThrowableType != null) {
			assertHasThrowableOfType(replyMsg, expectedThrowableType);
		} else {
			assertTrue(replyMsg.hasReturnValue());
		}

		return replyMsg.getReturnValue();
	}

	protected void callPutStatic(String className, String fieldName, String fieldClassName,
															 Object value) throws Exception {
		callPutStatic(className, fieldName, fieldClassName, value, null);
	}

	protected void callPutStatic(String className, String fieldName, String fieldClassName,
															 Object value, String expectedThrowableType) throws Exception {
		DataMessage requestMsg = dataMessageBuilder.buildPutStatic(clientId, className, fieldName, fieldClassName, value);
		DataMessage replyMsg = sendAndReceive(requestMsg);

		// basic assertions
		if (expectedThrowableType != null) {
			assertHasThrowableOfType(replyMsg, expectedThrowableType);
		} else {
			assertFalse(replyMsg.hasReturnValue());
			assertTrue(replyMsg.hasStaticFieldPutDone());
			StaticFieldPutDone staticFieldPutDone = replyMsg.getStaticFieldPutDone();
			assertEquals(staticFieldPutDone.getField().getName(), fieldName);
		}
	}

	protected ReturnValue callGetInstanceVar(String className, String fieldName, ObjectRef objRef) throws Exception {
		return callGetInstanceVar(className, fieldName, objRef, null);
	}

	protected ReturnValue callGetInstanceVar(String className, String fieldName, ObjectRef objRef,
																					 String expectedThrowableType) throws Exception {
		DataMessage requestMsg = dataMessageBuilder.buildGetObject(clientId, className, fieldName, objRef);
		DataMessage replyMsg = sendAndReceive(requestMsg);

		// basic assertions
		if (expectedThrowableType != null) {
			assertHasThrowableOfType(replyMsg, expectedThrowableType);
		} else {
			assertTrue(replyMsg.hasReturnValue());
		}

		return replyMsg.getReturnValue();
	}

	protected void callPutField(String className, String fieldName, ObjectRef targetObjRef,
															String valueClassName, Object value) throws Exception {
		callPutField(className, fieldName, targetObjRef, valueClassName, value, null);
	}

	protected void callPutField(String className, String fieldName, ObjectRef targetObjRef,
															String valueClassName, Object value, String expectedThrowableType) throws Exception {

		DataMessage requestMsg = dataMessageBuilder.buildPutObject(clientId, className, fieldName, targetObjRef,
			valueClassName, value);
		DataMessage replyMsg = sendAndReceive(requestMsg);

		// basic assertions
		if (expectedThrowableType != null) {
			assertHasThrowableOfType(replyMsg, expectedThrowableType);
		} else {
			assertFalse(replyMsg.hasReturnValue());
			assertTrue(replyMsg.hasInstanceFieldPutDone());
			InstanceFieldPutDone fieldPutDone = replyMsg.getInstanceFieldPutDone();
			assertEquals(fieldPutDone.getField().getName(), fieldName);
		}
	}

	protected ReturnValue callClassMethod(String className, String methodName, String[] parameterTypeNames,
																				Object[] parameters, ObjectRef[] paramObjRefs)
		throws Exception {
		return callClassMethod(className, methodName, parameterTypeNames, parameters, paramObjRefs, null);
	}

	protected ReturnValue callClassMethod(String className, String methodName, String[] parameterTypeNames,
																				Object[] parameters, ObjectRef[] paramObjRefs, String expectedThrowableType)
		throws Exception {
		DataMessage requestMsg = dataMessageBuilder.buildClassMethod(clientId, className, methodName,
			parameterTypeNames, this, null, parameters, paramObjRefs);
		DataMessage replyMsg = sendAndReceive(requestMsg);

		// basic assertions
		if (expectedThrowableType != null) {
			assertHasThrowableOfType(replyMsg, expectedThrowableType);
		} else {
			assertTrue(replyMsg.hasReturnValue());
		}

		return replyMsg.getReturnValue();
	}

	protected void callVoidClassMethod(String className, String methodName, String[] parameterTypeNames,
																		 Object[] parameters, ObjectRef[] paramObjRefs)
		throws Exception {
		callVoidClassMethod(className, methodName, parameterTypeNames, parameters, paramObjRefs, null);
	}

	protected void callVoidClassMethod(String className, String methodName, String[] parameterTypeNames,
																		 Object[] parameters, ObjectRef[] paramObjRefs, String expectedThrowableType)
		throws Exception {
		DataMessage requestMsg = dataMessageBuilder.buildClassMethod(clientId, className, methodName,
			parameterTypeNames, this, null, parameters, paramObjRefs);
		DataMessage replyMsg = sendAndReceive(requestMsg);

		// basic assertions
		if (expectedThrowableType != null) {
			assertHasThrowableOfType(replyMsg, expectedThrowableType);
		} else {
			assertTrue(replyMsg.hasReturnValue());
			assertNotNull(replyMsg.getReturnValue());
			assertTrue(replyMsg.getReturnValue().getIsVoid());
		}
	}

	protected ReturnValue callInstanceMethod(String className, String methodName, ObjectRef targetObjRef, String[]
		parameterTypeNames, Object[] parameters, ObjectRef[] paramObjRefs) throws Exception {
		return callInstanceMethod(className, methodName, targetObjRef, parameterTypeNames, parameters, paramObjRefs, null);
	}

	protected ReturnValue callInstanceMethod(String className, String methodName, ObjectRef targetObjRef, String[]
		parameterTypeNames, Object[] parameters, ObjectRef[] paramObjRefs, String expectedThrowableType) throws Exception {
		DataMessage requestMsg = dataMessageBuilder.buildInstanceMethod(clientId, className, methodName, null,
			targetObjRef, parameterTypeNames, parameters, paramObjRefs);
		DataMessage replyMsg = sendAndReceive(requestMsg);

		// basic assertions
		if (expectedThrowableType != null) {
			assertHasThrowableOfType(replyMsg, expectedThrowableType);
		} else {
			assertTrue(replyMsg.hasReturnValue());
		}

		return replyMsg.getReturnValue();
	}

	protected void callVoidInstanceMethod(String className, String methodName, ObjectRef targetObjRef, String[]
		parameterTypeNames, Object[] parameters, ObjectRef[] paramObjRefs) throws Exception {
		callVoidInstanceMethod(className, methodName, targetObjRef, parameterTypeNames, parameters, paramObjRefs, null);
	}

	protected void callVoidInstanceMethod(String className, String methodName, ObjectRef targetObjRef, String[]
		parameterTypeNames, Object[] parameters, ObjectRef[] paramObjRefs, String expectedThrowableType) throws Exception {

		DataMessage requestMsg = dataMessageBuilder.buildInstanceMethod(clientId, className, methodName, null,
			targetObjRef, parameterTypeNames, parameters, paramObjRefs);
		DataMessage replyMsg = sendAndReceive(requestMsg);

		// basic assertions
		if (expectedThrowableType != null) {
			assertHasThrowableOfType(replyMsg, expectedThrowableType);
		} else {
			assertTrue(replyMsg.hasReturnValue());
			assertNotNull(replyMsg.getReturnValue());
			assertTrue(replyMsg.getReturnValue().getIsVoid());
		}
	}
}

