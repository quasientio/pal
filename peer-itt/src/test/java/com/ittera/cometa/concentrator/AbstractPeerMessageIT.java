package com.ittera.cometa.concentrator;

import com.ittera.cometa.cxn.ThinPeer;
import com.ittera.cometa.messages.protobuf.ProtobufDataMessageBuilder;
import com.ittera.cometa.messages.protobuf.data.Wrappers.DataMessage;
import com.ittera.cometa.messages.protobuf.data.Values.ReturnValue;
import com.ittera.cometa.messages.protobuf.data.Fields.*;
import com.ittera.cometa.messages.DataMessageBuilder;

import com.ittera.cometa.common.ObjectService;
import com.ittera.cometa.common.BiMapObjectService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;
import java.util.Set;
import java.util.HashSet;

import org.junit.AfterClass;
import org.junit.BeforeClass;

import com.google.inject.AbstractModule;
import com.google.inject.Injector;
import com.google.inject.Guice;

import com.google.common.util.concurrent.Service;
import com.google.common.util.concurrent.ServiceManager;

import static org.junit.Assert.*;

public abstract class AbstractPeerMessageIT extends DataMessageAssertions {

	protected final static Logger logger = LoggerFactory.getLogger("tests");

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
		dataMessageBuilder.dontStoreObjects();

		// configure services
		final Set<Service> services = new HashSet<>();
		services.add((Service) injector.getInstance(ObjectService.class));
		final ServiceManager manager = new ServiceManager(services);
		manager.startAsync();

		// we run tests read-writing exclusively from log (no p2p talk)
		boolean allowP2P = Boolean.parseBoolean(System.getProperty("peer.allowP2P", "false"));
		thinPeer = new ThinPeer("/tests.properties", allowP2P);
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
																				String[] argObjRefs) throws Exception {

		String[] parameterTypesNamesArray = new String[parameterTypes.length];
		for (int i = 0; i < parameterTypes.length; i++) {
			parameterTypesNamesArray[i] = parameterTypes[i].getName();
		}

		DataMessage replyMsg = sendAndReceive(dataMessageBuilder.buildNonEmptyConstructor(clientId, className,
			parameterTypesNamesArray, args, argObjRefs));

		// basic assertions
		assertTrue(replyMsg.hasReturnValue());
		assertValueIsObjectRefOfType(replyMsg.getReturnValue(), className);

		return replyMsg.getReturnValue();
	}

	protected ReturnValue callConstructor(String className) throws Exception {

		DataMessage replyMsg = sendAndReceive(dataMessageBuilder.buildEmptyConstructor(clientId, className));

		// basic assertions
		assertTrue(replyMsg.hasReturnValue());
		assertValueIsObjectRefOfType(replyMsg.getReturnValue(), className);

		return replyMsg.getReturnValue();
	}

	protected ReturnValue callGetStatic(String className, String fieldName) throws Exception {

		DataMessage requestMsg = dataMessageBuilder.buildGetStatic(clientId, className, fieldName);
		DataMessage replyMsg = sendAndReceive(requestMsg);

		// basic assertions
		assertTrue(replyMsg.hasReturnValue());

		return replyMsg.getReturnValue();
	}

	protected void callPutStatic(String className, String fieldName, String fieldClassName,
															 Object value) throws Exception {

		DataMessage requestMsg = dataMessageBuilder.buildPutStatic(clientId, className, fieldName, fieldClassName, value);
		DataMessage replyMsg = sendAndReceive(requestMsg);

		// basic assertions
		assertFalse(replyMsg.hasReturnValue());
		assertTrue(replyMsg.hasStaticFieldPutDone());
		StaticFieldPutDone staticFieldPutDone = replyMsg.getStaticFieldPutDone();
		assertEquals(staticFieldPutDone.getField().getName(), fieldName);
	}

	protected ReturnValue callGetInstanceVar(String className, String fieldName, String objRef) throws Exception {

		DataMessage requestMsg = dataMessageBuilder.buildGetObject(clientId, className, fieldName, objRef);
		DataMessage replyMsg = sendAndReceive(requestMsg);

		// basic assertions
		assertTrue(replyMsg.hasReturnValue());

		return replyMsg.getReturnValue();
	}

	protected void callPutField(String className, String fieldName, String targetObjRef,
															String valueClassName, Object value) throws Exception {

		DataMessage requestMsg = dataMessageBuilder.buildPutObject(clientId, className, fieldName, targetObjRef,
			valueClassName, value);
		DataMessage replyMsg = sendAndReceive(requestMsg);

		// basic assertions
		assertFalse(replyMsg.hasReturnValue());
		assertTrue(replyMsg.hasInstanceFieldPutDone());
		InstanceFieldPutDone fieldPutDone = replyMsg.getInstanceFieldPutDone();
		assertEquals(fieldPutDone.getField().getName(), fieldName);
	}

	protected ReturnValue callClassMethod(String className, String methodName, String[] parameterTypeNames,
																				Object[] parameters, String[] argObjRefs)
		throws Exception {

		DataMessage requestMsg = dataMessageBuilder.buildClassMethod(clientId, className, methodName,
			parameterTypeNames, parameters, argObjRefs);
		DataMessage replyMsg = sendAndReceive(requestMsg);

		// basic assertions
		assertTrue(replyMsg.hasReturnValue());

		return replyMsg.getReturnValue();
	}

	protected void callVoidClassMethod(String className, String methodName, String[] parameterTypeNames,
																		 Object[] parameters, String[] argObjRefs)
		throws Exception {

		DataMessage requestMsg = dataMessageBuilder.buildClassMethod(clientId, className, methodName,
			parameterTypeNames, parameters, argObjRefs);
		DataMessage replyMsg = sendAndReceive(requestMsg);

		// basic assertions
		assertTrue(replyMsg.hasReturnValue());
		assertNotNull(replyMsg.getReturnValue());
		assertTrue(replyMsg.getReturnValue().getIsVoid());
	}

	protected ReturnValue callInstanceMethod(String className, String methodName, String targetObjRef, String[]
		parameterTypeNames, Object[] parameters, String[] argObjRefs) throws Exception {

		DataMessage requestMsg = dataMessageBuilder.buildInstanceMethod(clientId, className, methodName, targetObjRef,
			parameterTypeNames, parameters, argObjRefs);
		DataMessage replyMsg = sendAndReceive(requestMsg);

		// basic assertions
		assertTrue(replyMsg.hasReturnValue());

		return replyMsg.getReturnValue();
	}

	protected void callVoidInstanceMethod(String className, String methodName, String targetObjRef, String[]
		parameterTypeNames, Object[] parameters, String[] argObjRefs) throws Exception {

		DataMessage requestMsg = dataMessageBuilder.buildInstanceMethod(clientId, className, methodName, targetObjRef,
			parameterTypeNames, parameters, argObjRefs);
		DataMessage replyMsg = sendAndReceive(requestMsg);

		// basic assertions
		assertTrue(replyMsg.hasReturnValue());
		assertNotNull(replyMsg.getReturnValue());
		assertTrue(replyMsg.getReturnValue().getIsVoid());
	}
}

