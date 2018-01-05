package com.ittera.cometa.concentrator;

import com.ittera.cometa.common.ObjectService;
import com.ittera.cometa.common.BiMapObjectService;

import com.ittera.cometa.LogInfo;
import com.ittera.cometa.cxn.PeerLogDirectory;
import com.ittera.cometa.cxn.ZkClient;

import com.ittera.cometa.messages.DataMessageBuilder;
import com.ittera.cometa.messages.protobuf.ProtobufDataMessageBuilder;
import com.ittera.cometa.messages.protobuf.Wrapper;
import com.ittera.cometa.messages.protobuf.Unwrapper;
import com.ittera.cometa.messages.protobuf.data.Primitives;
import com.ittera.cometa.messages.protobuf.data.Wrappers.DataMessage;
import com.ittera.cometa.messages.protobuf.data.Calls.ConstructorCall;
import com.ittera.cometa.messages.protobuf.data.Calls.ClassMethodCall;
import com.ittera.cometa.messages.protobuf.data.Calls.InstanceMethodCall;
import com.ittera.cometa.messages.protobuf.data.Fields.StaticFieldGet;
import com.ittera.cometa.messages.protobuf.data.Fields.StaticFieldPut;
import com.ittera.cometa.messages.protobuf.data.Fields.InstanceFieldGet;
import com.ittera.cometa.messages.protobuf.data.Fields.InstanceFieldPut;

import com.ittera.cometa.concentrator.exec.*;
import com.ittera.cometa.concentrator.messages.IncomingMessageDispatcher;
import com.ittera.cometa.concentrator.util.ReflectionHelper;

import org.aspectj.lang.reflect.ConstructorSignature;
import org.aspectj.lang.reflect.MethodSignature;
import org.aspectj.lang.JoinPoint.StaticPart;
import org.aspectj.runtime.reflect.FieldSignatureImpl;

import java.io.InputStream;

import java.lang.reflect.Field;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.InvocationTargetException;

import java.util.*;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

import com.google.inject.name.Names;
import com.google.inject.*;

import com.google.common.util.concurrent.Service;
import com.google.common.util.concurrent.ServiceManager;
import com.google.common.util.concurrent.MoreExecutors;

import org.zeromq.ZMQ;
import org.zeromq.ZMQ.Socket;
import org.zeromq.ZContext;
import org.zeromq.ZMQException;
import zmq.ZError;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

public class Concentrator {

	// <editor-fold defaultstate="collapsed" desc="STATIC VARS">
	protected static final Logger logger = LoggerFactory.getLogger(Concentrator.class);

	public static UUID uuid;

	private static final Properties properties = new Properties();

	// zmq context -- gets injected to all other threads
	protected static final ZContext zmqContext;

	protected static final String PROPERTIES_FILE = "/peer.properties";

	// defaults for properties
	private static final String ZMQ_LINGER_DEFAULT = "1000";
	private static final String ZMQ_RCVHWM_DEFAULT = "10000";
	private static final String ZMQ_SNDHWM_DEFAULT = "10000";

	protected static final String DEFAULT_BOOTSTRAP_SERVERS = "localhost:9092";

	protected static String outCellAddress;

	static {

		// load properties from file in classpath
		try (final InputStream stream = Concentrator.class.getResourceAsStream(PROPERTIES_FILE)) {
			properties.load(stream);
		} catch (Exception e) {
			logger.error("Could not load properties", e);
			System.err.println(String.format("Could not load properties. Make sure to have `%s` in the classpath",
				PROPERTIES_FILE));
			e.printStackTrace();
			System.exit(1);
		}

		// initialize zmq context
		zmqContext = new ZContext();
		zmqContext.setLinger(Integer.valueOf(properties.getProperty("ZMQ_LINGER", ZMQ_LINGER_DEFAULT)));
		zmqContext.setRcvHWM(Integer.valueOf(properties.getProperty("ZMQ_RCVHWM", ZMQ_RCVHWM_DEFAULT)));
		zmqContext.setSndHWM(Integer.valueOf(properties.getProperty("ZMQ_SNDHWM", ZMQ_SNDHWM_DEFAULT)));
	}

	// per-thread REP socket to send messages to dispatcher
	private static final ThreadLocal<Socket> threadSocket = new ThreadLocal<Socket>() {
		@Override
		protected Socket initialValue() {
			Socket worker = zmqContext.createSocket(ZMQ.REQ);
			worker.connect(outCellAddress);
			logger.debug("Created and connected REQ new socket to outCellAddress: {}", outCellAddress);
			threadSocketCreated.set(true);
			return worker;
		}
	};

	// flag to avoid creating the threadLocal socket when we're trying to close it and it hasn't been created yet.
	private static final ThreadLocal<Boolean> threadSocketCreated = new ThreadLocal<Boolean>() {
		@Override
		protected Boolean initialValue() {
			return false;
		}
	};

	// shared by threads - possible sources of contention

	@Inject
	protected static DataMessageBuilder dataMessageBuilder;

	@Inject
	protected static ObjectService objectService;

	// </editor-fold>

	/*********************** INVOKERS INTERFACE ***************************/

	//TODO: this method further ties this class to Protobuf, must decouple
	public static DataMessage incomingCall(DataMessage dataMessage) {
		if (dataMessage.hasConstructorCall()) {
			final ConstructorCall constructorCall = dataMessage.getConstructorCall();
			return incomingConstructor(dataMessage.getMessageUuid(), constructorCall, dataMessage.getMessageUuid());
		} else if (dataMessage.hasClassMethodCall()) {
			final ClassMethodCall methodCall = dataMessage.getClassMethodCall();
			return incomingClassMethod(dataMessage.getMessageUuid(), methodCall, dataMessage.getMessageUuid());
		} else if (dataMessage.hasInstanceMethodCall()) {
			final InstanceMethodCall methodCall = dataMessage.getInstanceMethodCall();
			return incomingInstanceMethod(dataMessage.getMessageUuid(), methodCall, dataMessage.getMessageUuid());
		} else if (dataMessage.hasStaticFieldGet()) {
			final StaticFieldGet staticFieldGetCall = dataMessage.getStaticFieldGet();
			return incomingGetStatic(dataMessage.getMessageUuid(), staticFieldGetCall, dataMessage.getMessageUuid());
		} else if (dataMessage.hasInstanceFieldGet()) {
			final InstanceFieldGet instanceFieldGet = dataMessage.getInstanceFieldGet();
			return incomingGetObject(dataMessage.getMessageUuid(), instanceFieldGet, dataMessage.getMessageUuid());
		} else if (dataMessage.hasStaticFieldPut()) {
			final StaticFieldPut staticFieldPut = dataMessage.getStaticFieldPut();
			return incomingPutStatic(dataMessage.getMessageUuid(), staticFieldPut, dataMessage.getMessageUuid());
		} else if (dataMessage.hasInstanceFieldPut()) {
			final InstanceFieldPut instanceFieldPut = dataMessage.getInstanceFieldPut();
			return incomingPutField(dataMessage.getMessageUuid(), instanceFieldPut, dataMessage.getMessageUuid());
		} else {
			throw new IllegalArgumentException(String.format("Incoming message with uuid {} ignored - no handler:\n{}",
				dataMessage.getMessageUuid()));
		}
	}

	// <editor-fold defaultstate="collapsed" desc="CONSTRUCTORS">

	public static boolean classConstructor(StaticPart staticPart, Object sender) throws ClassNotFoundException {
		logger.trace("in w/ staticPart: {}, sender: {}", staticPart.getSignature(), sender);

		/** 1. Wrap message **/
		final DataMessage msg = dataMessageBuilder.buildClassInitializer(uuid, staticPart, sender);

		/** 2. Send message **/
		DataMessage rcvdMsg = sendAndRecv(msg);

		/** 4. Load and initialize class  -  WARNING: For some reason the class is not being initialized! **/
		Class clazz = null;
		ClassNotFoundException exceptionWhileLoadingClass = null;
		try {
			clazz = Class.forName(staticPart.getSignature().getDeclaringType().getName());
			//Class.forName(codeSignature.getDeclaringTypeName(),true, Concentrator.class.getClassLoader());
		} catch (ClassNotFoundException cnfe) {
			exceptionWhileLoadingClass = cnfe;
			logger.error("Caught and assigned to exceptionWhileLoadingClass", cnfe);
		}

		/** 5. Wrap class/exception if any **/
		String objKey = null;
		final DataMessage invokedMsg;
		if (exceptionWhileLoadingClass != null) {
			invokedMsg = dataMessageBuilder.buildInitializerThrowable(uuid, staticPart, exceptionWhileLoadingClass);
		} else {
			invokedMsg = dataMessageBuilder.buildLoadedClass(uuid, clazz);
		}

		/** 6. Send object/exception **/
		rcvdMsg = sendAndRecv(invokedMsg);

		/** 8. Return or re-raise exception **/
		if (exceptionWhileLoadingClass != null) {
			throw exceptionWhileLoadingClass;
		}

		//Since class initialization is not working, we will return false if we want to aspectj to proceed(),
		// indicating class isn't initialized
		boolean returnValue = false;
		logger.trace("out w/ return bool: {}", returnValue);
		return returnValue;
	}

	/**
	 * @param constructorCall
	 * @throws Throwable
	 */
	static DataMessage incomingConstructor(String messageUuid, ConstructorCall constructorCall, String followingUuid) {
		logger.trace("in w/ constructorCall: {}, messageUuid: {}, following uuid: {}",
			constructorCall, messageUuid, followingUuid);

		/** 1. Unwrap message and load constructor **/
		Class clazz = null;
		final List<Class> paramClasses = new ArrayList<>();
		Constructor constructor = null;
		Exception exceptionWhileLoading = null;
		try {
			clazz = Class.forName(constructorCall.getClass_().getName());
			for (Primitives.Object param : constructorCall.getParameterList()) {
				paramClasses.add(Class.forName(param.getClass_().getName()));
			}
			constructor = clazz.getDeclaredConstructor((Class[]) paramClasses.toArray(new Class[paramClasses.size()]));
		} catch (Exception e) {
			exceptionWhileLoading = e;
			logger.error("Caught and assigned to exceptionWhileLoading", e);
		}


		/** 2. If class and constructor loaded, unwrap arguments and invoke constructor **/
		Exception exceptionWhileInvoking = null;
		Object newObject = null;
		String objKey = null;

		if (exceptionWhileLoading == null) {
			constructor.setAccessible(true);
			try {
				List<Object> args = new ArrayList<>();
				for (int i = 0; i < constructorCall.getParameterCount(); i++) {
					Primitives.Object obj = constructorCall.getParameter(i);
					if (obj.getIsNull()) {
						args.add(null);
					} else if (obj.hasRef()) {
						args.add(objectService.lookupObject(obj.getRef()));
					} else {
						args.add(Unwrapper.unwrapObject(obj, paramClasses.get(i)));
					}
				}
				newObject = constructor.newInstance(args.toArray(new Object[args.size()]));
			} catch (Exception ite) {
				exceptionWhileInvoking = ite;
				logger.error("Caught and assigned to exceptionWhileInvoking", ite);
			}
		}
		//store in object map
		if (newObject != null) {
			objKey = objectService.storeObject(newObject);
		}

		/** 3. Wrap new object or exception **/
		final DataMessage invokedMsg;

		if (exceptionWhileLoading != null) {
			invokedMsg = dataMessageBuilder.buildAccessibleObjectThrowable(uuid, constructor, exceptionWhileLoading,
				followingUuid);
		} else if (exceptionWhileInvoking != null) {
			invokedMsg = dataMessageBuilder.buildAccessibleObjectThrowable(uuid, constructor, exceptionWhileInvoking,
				followingUuid);
		} else {
			if (Wrapper.isWrappable(newObject)) {
				invokedMsg = dataMessageBuilder.buildReturnValue(uuid, newObject, clazz, null, false,
					followingUuid);
			} else {
				invokedMsg = dataMessageBuilder.buildReturnValue(uuid, null, clazz, objKey, false, followingUuid);
			}
		}


		/** 4. Send object/exception **/
		DataMessage rcvdMsg = sendAndRecv(invokedMsg);

		logger.trace("out w/ {}", rcvdMsg);
		return rcvdMsg;
	}

	public static Object constructor(StaticPart staticPart, Object sender, Object[] args) throws Throwable {
		logger.trace("in w/ staticPart: {}, sender: {}, args: {}", staticPart.getSignature(), sender, args);

		final ConstructorSignature constructorSignature = (ConstructorSignature) staticPart.getSignature();

		/** 1. Wrap message **/
		final DataMessage callMsg = dataMessageBuilder.buildConstructor(uuid, staticPart, sender, args);

		/** 2. Send message **/
		DataMessage rcvdMsg = sendAndRecv(callMsg);

		/** 4. Invoke constructor **/

		Constructor constructor = constructorSignature.getConstructor();

		Object newObject = null;
		Exception exceptionWhileInvoking = null;
		constructor.setAccessible(true);
		String objKey = null;
		try {
			newObject = constructor.newInstance(args);
		} catch (Exception ite) {
			exceptionWhileInvoking = ite;
			logger.error("Caught and assigned to exceptionWhileInvoking", ite);
		}

		//store in object map
		if (newObject != null) {
			objKey = objectService.storeObject(newObject);
		}

		/** 5. Wrap new object or exception **/
		DataMessage invokedMsg = null;

		if (exceptionWhileInvoking != null) {
			invokedMsg = dataMessageBuilder.buildAccessibleObjectThrowable(uuid, constructor, exceptionWhileInvoking,
				null);
		} else {
			if (Wrapper.isWrappable(newObject)) {
				invokedMsg = dataMessageBuilder.buildReturnValue(uuid, newObject, constructor.getClass(), null,
					false, null);
			} else {
				invokedMsg = dataMessageBuilder.buildReturnValue(uuid, null, constructor.getClass(), objKey,
					false, null);
			}
		}

		/** 6. Send object/exception **/
		rcvdMsg = sendAndRecv(invokedMsg);

		/** 8. Return object or re-raise exception **/
		if (exceptionWhileInvoking != null) {
			if (exceptionWhileInvoking instanceof InvocationTargetException) {
				throw exceptionWhileInvoking.getCause();
			} else {
				throw exceptionWhileInvoking;
			}
		}

		logger.trace("out w/ new object: {}", newObject);
		return newObject;
	}
	// </editor-fold>

	// <editor-fold defaultstate="collapsed" desc="METHOD CALLS">

	public static void voidInstanceMethod(StaticPart staticPart, Object sender, Object target, Object[] args)
		throws Throwable {
		logger.trace("in w/ staticPart: {}, sender: {}, target: {}, args: {}", staticPart.getSignature(), sender, target,
			args);

		final MethodSignature methodSignature = (MethodSignature) staticPart.getSignature();

		/** 1. Wrap message **/
		final DataMessage msg = dataMessageBuilder.buildInstanceMethod(uuid, staticPart, sender, target, args);


		/** 2. Send message **/
		DataMessage rcvdMsg = sendAndRecv(msg);

		/** 4. Invoke method **/

		Method method = methodSignature.getMethod();

		Exception exceptionWhileInvoking = null;
		method.setAccessible(true);
		try {
			method.invoke(target, args);
		} catch (Exception e) {
			exceptionWhileInvoking = e;
			logger.error("Caught and assigned to exceptionWhileInvoking", e);
		}

		/** 5. Wrap new object or exception **/
		final DataMessage invokedMsg;

		if (exceptionWhileInvoking != null) {
			invokedMsg = dataMessageBuilder.buildAccessibleObjectThrowable(uuid, method, exceptionWhileInvoking,
				null);
		} else {
			invokedMsg = dataMessageBuilder.buildReturnValue(uuid, Void.class, method.getReturnType(), null,
				true, null);
		}


		/** 6. Send object/exception **/
		rcvdMsg = sendAndRecv(invokedMsg);


		/** 8. Return object or re-raise exception **/
		if (exceptionWhileInvoking != null) {
			if (exceptionWhileInvoking instanceof InvocationTargetException) {
				throw exceptionWhileInvoking.getCause();
			} else {
				throw exceptionWhileInvoking;
			}
		}

		logger.trace("out");
		return;
	}

	public static Object nonVoidInstanceMethod(StaticPart staticPart, Object sender, Object target, Object[] args)
		throws Throwable {
		logger.trace("in w/ staticPart: {}, sender: {}, target: {}, args: {}", staticPart.getSignature(), sender, target,
			args);

		final MethodSignature methodSignature = (MethodSignature) staticPart.getSignature();

		/** 1. Wrap message **/
		final DataMessage msg = dataMessageBuilder.buildInstanceMethod(uuid, staticPart, sender, target, args);


		/** 2. Send message **/
		DataMessage rcvdMsg = sendAndRecv(msg);

		/** 4. Invoke method **/

		Method method = methodSignature.getMethod();
		Object returnValue = null;
		Exception exceptionWhileInvoking = null;
		method.setAccessible(true);
		String objKey = null;
		try {
			returnValue = method.invoke(target, args);
		} catch (Exception e) {
			exceptionWhileInvoking = e;
			logger.error("Caught and assigned to exceptionWhileInvoking", e);
		}

		//store in object map
		if (returnValue != null) {
			objKey = objectService.storeObject(returnValue);
		}

		/** 5. Wrap new object or exception **/
		final DataMessage invokedMsg;

		if (exceptionWhileInvoking != null) {
			invokedMsg = dataMessageBuilder.buildAccessibleObjectThrowable(uuid, method, exceptionWhileInvoking,
				null);
		} else {
			if (Wrapper.isWrappable(returnValue)) {
				invokedMsg = dataMessageBuilder.buildReturnValue(uuid, returnValue, method.getReturnType(), null,
					false, null);
			} else {
				invokedMsg = dataMessageBuilder.buildReturnValue(uuid, null, method.getReturnType(), objKey,
					false, null);
			}
		}


		/** 6. Send object/exception **/
		rcvdMsg = sendAndRecv(invokedMsg);

		/** 8. Return object or re-raise exception **/
		if (exceptionWhileInvoking != null) {
			if (exceptionWhileInvoking instanceof InvocationTargetException) {
				throw exceptionWhileInvoking.getCause();
			} else {
				throw exceptionWhileInvoking;
			}
		}

		logger.trace("out w/ return value: {}", returnValue);
		return returnValue;
	}

	/**
	 * This method currently only support calling method whose value is fully contained in the msg.
	 * i.e. --> primitives, and Strings that haven't been trimmed.
	 *
	 * @param instanceMethodCall
	 */
	static DataMessage incomingInstanceMethod(String messageUuid, InstanceMethodCall instanceMethodCall,
																						String followingUuid) {
		logger.trace("in w/ instanceMethodCall: {}, messageUuid: {}, following uuid: {}", instanceMethodCall, messageUuid,
			followingUuid);

		/** 1. Unwrap message and load class **/
		Class clazz = null;
		Method method = null;
		Exception exceptionWhileLoading = null;
		List<Class> paramClasses = new ArrayList<>();
		try {
			clazz = Class.forName(instanceMethodCall.getClass_().getName());
			for (Primitives.Object obj : instanceMethodCall.getParameterList()) {
				Class paramClass = Unwrapper.getClassForPrimitive(obj.getClass_().getName());
				if (paramClass == null) {
					paramClass = Class.forName(obj.getClass_().getName());
				}
				paramClasses.add(paramClass);
			}
		} catch (Exception e) {
			exceptionWhileLoading = e;
			logger.error("Caught and assigned to exceptionWhileLoading", e);
		}

		/** 2. If class loaded, unwrap/retrieve arguments and invoke method **/
		Exception exceptionWhileInvoking = null;
		Object returnValue = null;
		String objKey = null;
		if (exceptionWhileLoading == null) {
			List<Object> args = new ArrayList<>();
			for (int i = 0; i < instanceMethodCall.getParameterCount(); i++) {
				Primitives.Object obj = instanceMethodCall.getParameter(i);
				if (obj.getIsNull()) {
					args.add(null);
				} else if (obj.hasRef()) {
					args.add(objectService.lookupObject(obj.getRef()));
				} else {
					args.add(Unwrapper.unwrapObject(obj, paramClasses.get(i)));
				}
			}
			try {
				Object target = null;
				if (instanceMethodCall.hasObject()) {
					Class objClass = Class.forName(instanceMethodCall.getClass_().getName());
					target = Unwrapper.unwrapObject(instanceMethodCall.getObject(), objClass);
					logger.debug("Unwrapped target: {}", target);
				} else {
					target = objectService.lookupObject(instanceMethodCall.getObjectRef());
					logger.debug("Loaded target: {}", target);
				}
				if (target == null) {
					throw new RuntimeException("Invoking a method on null object will yield a NPE!");
				}
				method = ReflectionHelper.getMethodToInvoke(clazz, args.toArray(), instanceMethodCall.getParameterList(),
					instanceMethodCall.getName());
				if (method == null) {
					//TODO perhaps this should be thrown by ReflectionHelper instead of returning null
					throw new NoSuchMethodException(String.format("Can't find method:%s in class:%s with given parameter types",
						instanceMethodCall.getName(), clazz.getName()));
				}
				method.setAccessible(true);
				returnValue = method.invoke(target, args.toArray());
			} catch (Exception e) {
				exceptionWhileInvoking = e;
				logger.error("Caught and assigned to exceptionWhileInvoking", e);
			}
		}

		//store in object map
		final boolean isVoid = method.getReturnType() == void.class;
		if (returnValue != null && !isVoid) {
			objKey = objectService.storeObject(returnValue);
		}
		if (isVoid) {
			returnValue = Void.class;
		}

		/** 3. Wrap return value or exception **/
		final DataMessage invokedMsg;

		if (exceptionWhileLoading != null) {
			invokedMsg = dataMessageBuilder.buildAccessibleObjectThrowable(uuid, method, exceptionWhileLoading,
				followingUuid);
		} else if (exceptionWhileInvoking != null) {
			invokedMsg = dataMessageBuilder.buildAccessibleObjectThrowable(uuid, method, exceptionWhileInvoking,
				followingUuid);
		} else {
			if (Wrapper.isWrappable(returnValue)) {
				invokedMsg = dataMessageBuilder.buildReturnValue(uuid, returnValue, method.getReturnType(), null,
					isVoid, followingUuid);
			} else {
				invokedMsg = dataMessageBuilder.buildReturnValue(uuid, null, method.getReturnType(), objKey, isVoid,
					followingUuid);
			}
		}

		/** 4. Send object/exception **/
		DataMessage rcvdMsg = sendAndRecv(invokedMsg);

		logger.trace("out w/ {}", rcvdMsg);
		return rcvdMsg;
	}

	public static void voidClassMethod(StaticPart staticPart, Object sender, Object[] args) throws Throwable {
		logger.trace("in w/ staticPart: {}, sender: {}, args: {}", staticPart.getSignature(), sender, args);

		final MethodSignature methodSignature = (MethodSignature) staticPart.getSignature();

		/** 1. Wrap message **/
		final DataMessage msg = dataMessageBuilder.buildClassMethod(uuid, staticPart, sender, args);


		/** 2. Send message **/
		DataMessage rcvdMsg = sendAndRecv(msg);

		/** 4. Invoke method **/

		Method method = methodSignature.getMethod();
		Exception exceptionWhileInvoking = null;
		method.setAccessible(true);
		try {
			method.invoke(null, args);
		} catch (Exception e) {
			exceptionWhileInvoking = e;
			logger.error("Caught and assigned to exceptionWhileInvoking", e);
		}

		/** 5. Wrap new object or exception **/
		final DataMessage invokedMsg;

		if (exceptionWhileInvoking != null) {
			invokedMsg = dataMessageBuilder.buildAccessibleObjectThrowable(uuid, method, exceptionWhileInvoking,
				null);
		} else {
			invokedMsg = dataMessageBuilder.buildReturnValue(uuid, Void.class, method.getReturnType(), null,
				true, null);
		}


		/** 6. Send object/exception **/
		rcvdMsg = sendAndRecv(invokedMsg);

		/** 8. Return object or re-raise exception **/
		if (exceptionWhileInvoking != null) {
			if (exceptionWhileInvoking instanceof InvocationTargetException) {
				throw exceptionWhileInvoking.getCause();
			} else {
				throw exceptionWhileInvoking;
			}
		}

		logger.trace("out");
		return;
	}

	public static Object nonVoidClassMethod(StaticPart staticPart, Object sender, Object[] args) throws Throwable {
		logger.trace("in w/ staticPart: {}, sender: {}, args: {}", staticPart.getSignature(), sender, args);

		final MethodSignature methodSignature = (MethodSignature) staticPart.getSignature();

		/** 1. Wrap message **/
		final DataMessage msg = dataMessageBuilder.buildClassMethod(uuid, staticPart, sender, args);


		/** 2. Send message **/
		DataMessage rcvdMsg = sendAndRecv(msg);

		/** 4. Invoke method **/

		Method method = methodSignature.getMethod();
		Object returnValue = null;
		String objKey = null;
		Exception exceptionWhileInvoking = null;
		method.setAccessible(true);
		try {
			returnValue = method.invoke(null, args);
		} catch (Exception e) {
			exceptionWhileInvoking = e;
			logger.error("Caught and assigned to exceptionWhileInvoking", e);
		}

		//store in object map
		if (returnValue != null) {
			objKey = objectService.storeObject(returnValue);
		}

		/** 5. Wrap new object or exception **/
		final DataMessage invokedMsg;

		if (exceptionWhileInvoking != null) {
			invokedMsg = dataMessageBuilder.buildAccessibleObjectThrowable(uuid, method, exceptionWhileInvoking,
				null);
		} else {
			if (Wrapper.isWrappable(returnValue)) {
				invokedMsg = dataMessageBuilder.buildReturnValue(uuid, returnValue, method.getReturnType(), null,
					false, null);
			} else {
				invokedMsg = dataMessageBuilder.buildReturnValue(uuid, null, method.getReturnType(), objKey,
					false, null);
			}
		}


		/** 6. Send object/exception **/
		rcvdMsg = sendAndRecv(invokedMsg);

		/** 8. Return object or re-raise exception **/
		if (exceptionWhileInvoking != null) {
			if (exceptionWhileInvoking instanceof InvocationTargetException) {
				throw exceptionWhileInvoking.getCause();
			} else {
				throw exceptionWhileInvoking;
			}
		}

		logger.trace("out w/ return value: {}", returnValue);
		return returnValue;
	}


	/**
	 * This method currently only support calling method whose value is fully contained in the msg.
	 * i.e. --> primitives, and Strings that haven't been trimmed.
	 *
	 * @param classMethodCall
	 */
	static DataMessage incomingClassMethod(String messageUuid, ClassMethodCall classMethodCall, String followingUuid) {
		logger.trace("in w/ classMethodCall: {}, messageUuid: {}, following uuid: {}", classMethodCall, messageUuid,
			followingUuid);

		/** 1. Unwrap message and load class **/
		Class clazz = null;
		Method method = null;
		Exception exceptionWhileLoading = null;
		List<Class> paramClasses = new ArrayList<>();
		try {
			logger.debug("Attempting to load (initialize) class");
			clazz = Class.forName(classMethodCall.getClass_().getName());
			for (Primitives.Object obj : classMethodCall.getParameterList()) {
				Class paramClass = Unwrapper.getClassForPrimitive(obj.getClass_().getName());
				if (paramClass == null) {
					paramClass = Class.forName(obj.getClass_().getName());
				}
				paramClasses.add(paramClass);
			}
		} catch (Exception e) {
			exceptionWhileLoading = e;
			logger.error("Caught and assigned to exceptionWhileLoading", e);
		}

		/** 2. If class loaded, unwrap/retrieve arguments and invoke method **/
		Exception exceptionWhileInvoking = null;
		Object returnValue = null;
		String objKey = null;
		if (exceptionWhileLoading == null) {
			logger.debug("Unwrapping parameters");
			List<Object> args = new ArrayList<>();
			for (int i = 0; i < classMethodCall.getParameterCount(); i++) {
				Primitives.Object obj = classMethodCall.getParameter(i);
				if (obj.getIsNull()) {
					args.add(null);
				} else if (obj.hasRef()) {
					args.add(objectService.lookupObject(obj.getRef()));
				} else {
					args.add(Unwrapper.unwrapObject(obj, paramClasses.get(i)));
				}
			}
			try {
				method = ReflectionHelper.getMethodToInvoke(clazz, args.toArray(), classMethodCall.getParameterList(),
					classMethodCall.getName());
				if (method == null) {
					throw new NoSuchMethodException(String.format("Can't find method:%s in class:%s with given parameter types",
						classMethodCall.getName(), clazz.getName()));
				}
				method.setAccessible(true);
				returnValue = method.invoke(null, args.toArray());
			} catch (Exception e) {
				exceptionWhileInvoking = e;
				logger.error("Caught and assigned to exceptionWhileInvoking", e);
			}
		}

		//store in object map
		final boolean isVoid = method.getReturnType() == void.class;
		if (returnValue != null && !isVoid) {
			objKey = objectService.storeObject(returnValue);
		}
		if (isVoid) {
			returnValue = Void.class;
		}

		/** 3. Wrap return value or exception **/
		final DataMessage invokedMsg;

		if (exceptionWhileLoading != null) {
			invokedMsg = dataMessageBuilder.buildAccessibleObjectThrowable(uuid, method, exceptionWhileLoading,
				followingUuid);
		} else if (exceptionWhileInvoking != null) {
			invokedMsg = dataMessageBuilder.buildAccessibleObjectThrowable(uuid, method, exceptionWhileInvoking,
				followingUuid);
		} else {
			if (Wrapper.isWrappable(returnValue)) {
				invokedMsg = dataMessageBuilder.buildReturnValue(uuid, returnValue, method.getReturnType(), null,
					isVoid, followingUuid);
			} else {
				invokedMsg = dataMessageBuilder.buildReturnValue(uuid, null, method.getReturnType(), objKey, isVoid,
					followingUuid);
			}
		}

		/** 4. Send object/exception **/
		DataMessage rcvdMsg = sendAndRecv(invokedMsg);

		logger.trace("out w/ {}", rcvdMsg);
		return rcvdMsg;
	}


	// </editor-fold>

	// <editor-fold defaultstate="collapsed" desc="FIELD OPERATIONS">

	public static DataMessage incomingGetStatic(String messageUuid, StaticFieldGet staticFieldGet, String followingUuid) {
		logger.trace("in w/ staticFieldGet: {}, messageUuid: {}, following uuid: {}", staticFieldGet, messageUuid,
			followingUuid);

		/** 1. Get Object **/
		Class clazz = null;
		Field field = null;
		Exception exceptionWhileLoading = null;
		try {
			clazz = Class.forName(staticFieldGet.getClass_().getName());
			field = clazz.getDeclaredField(staticFieldGet.getField().getName());
			logger.debug("field {} is of type {}", field.getName(), field.getType());
		} catch (Exception e) {
			exceptionWhileLoading = e;
			logger.error("Caught and assigned to exceptionWhileLoading", e);
		}

		/** 2. If class and field loaded, invoke field get **/
		Exception exceptionWhileInvoking = null;

		String objKey = null;
		Object fieldValue = null;
		if (exceptionWhileLoading == null) {
			field.setAccessible(true);
			try {
				fieldValue = field.get(null);
			} catch (Exception e) {
				exceptionWhileInvoking = e;
				logger.error("Caught and assigned to exceptionWhileInvoking", e);
			}
		}
		//store in object map
		if (fieldValue != null) {
			objKey = objectService.storeObject(fieldValue);
		}

		/** 3. Wrap return value or exception **/
		DataMessage invokedMsg = null;

		if (exceptionWhileLoading != null) {
			invokedMsg = dataMessageBuilder.buildAccessibleObjectThrowable(uuid, field, exceptionWhileLoading, followingUuid);
		} else if (exceptionWhileInvoking != null) {
			invokedMsg = dataMessageBuilder.buildAccessibleObjectThrowable(uuid, field, exceptionWhileInvoking,
				followingUuid);
		} else {
			if (Wrapper.isWrappable(fieldValue)) {
				invokedMsg = dataMessageBuilder.buildReturnValue(uuid, fieldValue, field.getType(), null, false,
					followingUuid);
			} else {
				invokedMsg = dataMessageBuilder.buildReturnValue(uuid, null, field.getType(), objKey, false,
					followingUuid);
			}
		}


		/** 4. Send object/exception **/
		DataMessage rcvdMsg = sendAndRecv(invokedMsg);

		logger.trace("out w/ {}", rcvdMsg);
		return rcvdMsg;

	}

	public static Object getStatic(StaticPart staticPart, Object sender) throws IllegalAccessException {
		logger.trace("in w/ staticPart: {}, sender: {}", staticPart.getSignature(), sender);

		/** 1. Wrap message **/
		final DataMessage msg = dataMessageBuilder.buildGetStatic(uuid, staticPart, sender);

		/** 2. Send message **/
		DataMessage rcvdMsg = sendAndRecv(msg);

		/** 4. Get Object **/

		Field field = ((FieldSignatureImpl) staticPart.getSignature()).getField();
		field.setAccessible(true);

		IllegalAccessException exceptionGettingObject = null;
		Object fieldValue = null;
		String objKey = null;
		try {
			fieldValue = field.get(null);
		} catch (IllegalAccessException iae) {
			exceptionGettingObject = iae;
			logger.error("Caught and assigned to exceptionGettingObject", iae);
		}
		//store in object map
		if (fieldValue != null) {
			objKey = objectService.storeObject(fieldValue);
		}

		/** 5. Wrap exception if any **/
		DataMessage invokedMsg = null;
		if (exceptionGettingObject != null) {
			invokedMsg = dataMessageBuilder.buildAccessibleObjectThrowable(uuid, field, exceptionGettingObject,
				null);
		} else {
			if (Wrapper.isWrappable(fieldValue)) {
				invokedMsg = dataMessageBuilder.buildReturnValue(uuid, fieldValue, field.getType(), null, false,
					null);
			} else {
				invokedMsg = dataMessageBuilder.buildReturnValue(uuid, null, field.getType(), objKey, false,
					null);
			}
		}

		/** 6. Send object/exception **/
		rcvdMsg = sendAndRecv(invokedMsg);

		/** 8. Return or re-raise exception **/
		if (exceptionGettingObject != null) {
			throw exceptionGettingObject;
		}

		logger.trace("out w/ fieldValue: {}", fieldValue);
		return fieldValue;
	}

	public static DataMessage incomingGetObject(String messageUuid, InstanceFieldGet instanceFieldGet,
																							String followingUuid) {
		logger.trace("in w/ instanceFieldGet: {}, messageUuid: {}, following uuid: {}", instanceFieldGet, messageUuid,
			followingUuid);

		/** 1. Get Object **/
		Class clazz = null;
		Field field = null;
		Exception exceptionWhileLoading = null;
		try {
			clazz = Class.forName(instanceFieldGet.getClass_().getName());
			field = clazz.getDeclaredField(instanceFieldGet.getField().getName());
		} catch (Exception e) {
			exceptionWhileLoading = e;
			logger.error("Caught and assigned to exceptionWhileLoading", e);
		}

		/** 2. If class and field loaded, invoke field get **/
		Exception exceptionWhileInvoking = null;

		Object fieldValue = null;
		String objKey = null;
		if (exceptionWhileLoading == null) {
			field.setAccessible(true);
			try {
				Object target = null;
				if (instanceFieldGet.hasObject()) {
					Class objClass = Class.forName(instanceFieldGet.getClass_().getName());
					target = Unwrapper.unwrapObject(instanceFieldGet.getObject(), objClass);
					logger.debug("Unwrapped target: {}", target);
				} else {
					target = objectService.lookupObject(instanceFieldGet.getObjectRef());
					logger.debug("Loaded target: {}", target);
				}
				if (target == null) {
					throw new RuntimeException("Accessing a field on null object will yield a NPE!");
				}
				fieldValue = field.get(target);
			} catch (Exception e) {
				exceptionWhileInvoking = e;
				logger.error("Caught and assigned to exceptionWhileInvoking", e);
			}
		}
		//store in object map
		if (fieldValue != null) {
			objKey = objectService.storeObject(fieldValue);
		}

		/** 3. Wrap return value or exception **/
		DataMessage invokedMsg = null;

		if (exceptionWhileLoading != null) {
			invokedMsg = dataMessageBuilder.buildAccessibleObjectThrowable(uuid, field, exceptionWhileLoading, followingUuid);
		} else if (exceptionWhileInvoking != null) {
			invokedMsg = dataMessageBuilder.buildAccessibleObjectThrowable(uuid, field, exceptionWhileInvoking,
				followingUuid);
		} else {
			if (Wrapper.isWrappable(fieldValue)) {
				invokedMsg = dataMessageBuilder.buildReturnValue(uuid, fieldValue, field.getType(), null,
					false, followingUuid);
			} else {
				invokedMsg = dataMessageBuilder.buildReturnValue(uuid, null, field.getType(), objKey, false,
					followingUuid);
			}
		}

		/** 4. Send object/exception **/
		DataMessage rcvdMsg = sendAndRecv(invokedMsg);

		logger.trace("out w/ {}", rcvdMsg);
		return rcvdMsg;

	}

	public static Object getObject(StaticPart staticPart, Object sender, Object target) throws IllegalAccessException {
		logger.trace("in w/ staticPart: {}, sender: {}, target: {}", staticPart.getSignature(), sender, target);

		/** 1. Wrap message **/
		final DataMessage msg = dataMessageBuilder.buildGetObject(uuid, staticPart, sender, target);

		/** 2. Send message **/
		DataMessage rcvdMsg = sendAndRecv(msg);

		/** 4. Get Object **/

		Field field = ((FieldSignatureImpl) staticPart.getSignature()).getField();
		field.setAccessible(true);

		IllegalAccessException exceptionGettingObject = null;
		Object fieldValue = null;
		String objKey = null;
		try {
			fieldValue = field.get(target);
		} catch (IllegalAccessException iae) {
			exceptionGettingObject = iae;
			logger.error("Caught and assigned to exceptionGettingObject", iae);
		}
		//store in object map
		if (fieldValue != null) {
			objKey = objectService.storeObject(fieldValue);
		}

		/** 5. Wrap exception if any **/
		DataMessage invokedMsg = null;
		if (exceptionGettingObject != null) {
			invokedMsg = dataMessageBuilder.buildAccessibleObjectThrowable(uuid, field, exceptionGettingObject,
				null);
		} else {
			if (Wrapper.isWrappable(fieldValue)) {
				invokedMsg = dataMessageBuilder.buildReturnValue(uuid, fieldValue, field.getType(), null, false,
					null);
			} else {
				invokedMsg = dataMessageBuilder.buildReturnValue(uuid, null, field.getType(), objKey, false,
					null);
			}
		}

		/** 6. Send object/exception **/
		rcvdMsg = sendAndRecv(invokedMsg);

		/** 8. Return or re-raise exception **/
		if (exceptionGettingObject != null) {
			throw exceptionGettingObject;
		}

		logger.trace("out w/ fieldValue: {}", fieldValue);
		return fieldValue;
	}

	public static DataMessage incomingPutStatic(String messageUuid, StaticFieldPut staticFieldPut, String followingUuid) {
		logger.trace("in w/ staticFieldPut: {}, messageUuid: {}, following uuid: {}", staticFieldPut, messageUuid,
			followingUuid);

		/** 1. Load class and field **/
		final Class clazz;
		Field field = null;
		Exception exceptionWhileLoading = null;
		try {
			clazz = Class.forName(staticFieldPut.getClass_().getName());
			field = clazz.getDeclaredField(staticFieldPut.getField().getName());
		} catch (Exception e) {
			exceptionWhileLoading = e;
			logger.error("Caught and assigned to exceptionWhileLoading", e);
		}

		/** 2. If class and field loaded, unwrap value and invoke field set **/
		//TODO unwrap or load object before and have a separate exception for this step

		Exception exceptionWhileInvoking = null;

		if (exceptionWhileLoading == null) {
			field.setAccessible(true);
			try {
				final Object value;
				if (staticFieldPut.hasObject()) {
					value = Unwrapper.unwrapObject(staticFieldPut.getObject(), field.getType());
					logger.debug("Unwrapped value: {}", value);
				} else {
					value = objectService.lookupObject(staticFieldPut.getObjectRef());
					logger.debug("Loaded value: {}", value);
				}
				//invoke set
				field.set(null, value);
			} catch (Exception e) {
				exceptionWhileInvoking = e;
				logger.error("Caught and assigned to exceptionWhileInvoking", e);
			}
		}


		/** 3. Wrap return value or exception **/
		DataMessage invokedMsg = null;

		if (exceptionWhileLoading != null) {
			invokedMsg = dataMessageBuilder.buildAccessibleObjectThrowable(uuid, field, exceptionWhileLoading, followingUuid);
		} else if (exceptionWhileInvoking != null) {
			invokedMsg = dataMessageBuilder.buildAccessibleObjectThrowable(uuid, field, exceptionWhileInvoking,
				followingUuid);
		} else {
			invokedMsg = dataMessageBuilder.buildPutStaticDone(uuid, messageUuid, staticFieldPut, field.getType(),
				followingUuid);
		}


		/** 4. Send object/exception **/
		DataMessage rcvdMsg = sendAndRecv(invokedMsg);

		logger.trace("out w/ {}", rcvdMsg);
		return rcvdMsg;

	}

	public static void putStatic(StaticPart staticPart, Object sender, Object[] args) throws IllegalAccessException {
		logger.trace("in w/ staticPart: {}, sender: {}, args: {}", staticPart.getSignature(), sender, args);

		/** 1. Wrap message **/
		final DataMessage msg = dataMessageBuilder.buildPutStatic(uuid, staticPart, sender, args[0]);

		/** 2. Send message **/
		DataMessage rcvdMsg = sendAndRecv(msg);

		/** 4. Put Object **/

		Field field = ((FieldSignatureImpl) staticPart.getSignature()).getField();
		field.setAccessible(true);

		IllegalAccessException exceptionSettingObject = null;
		try {
			//invoke set
			field.set(null, args[0]);
		} catch (IllegalAccessException iae) {
			exceptionSettingObject = iae;
			logger.error("Caught and assigned to exceptionSettingObject", iae);
		}

		/** 5. Wrap exception if any **/
		DataMessage invokedMsg = null;
		if (exceptionSettingObject != null) {
			invokedMsg = dataMessageBuilder.buildAccessibleObjectThrowable(uuid, field, exceptionSettingObject,
				null);
		} else {
			invokedMsg = dataMessageBuilder.buildPutStaticDone(uuid, staticPart, sender, args[0]);
		}

		/** 6. Send object/exception **/
		rcvdMsg = sendAndRecv(invokedMsg);

		/** 8. Return or re-raise exception **/
		if (exceptionSettingObject != null) {
			throw exceptionSettingObject;
		}

		logger.trace("out");
		return;
	}

	public static void putField(StaticPart staticPart, Object sender, Object target, Object[] args)
		throws IllegalAccessException {
		logger.trace("in w/ staticPart: {}, sender: {}, target: {}, args: {}", staticPart.getSignature(), sender, target,
			args);

		/** 1. Wrap message **/
		final DataMessage msg = dataMessageBuilder.buildPutObject(uuid, staticPart, sender, target, args[0]);

		/** 2. Send message **/
		DataMessage rcvdMsg = sendAndRecv(msg);

		/** 4. Put Object **/

		Field field = ((FieldSignatureImpl) staticPart.getSignature()).getField();
		field.setAccessible(true);

		IllegalAccessException exceptionSettingObject = null;
		try {
			//invoke set
			field.set(target, args[0]);
		} catch (IllegalAccessException iae) {
			exceptionSettingObject = iae;
			logger.error("Caught and assigned to exceptionSettingObject", iae);
		}

		/** 5. Wrap exception if any **/
		DataMessage invokedMsg = null;
		if (exceptionSettingObject != null) {
			invokedMsg = dataMessageBuilder.buildAccessibleObjectThrowable(uuid, field, exceptionSettingObject,
				null);
		} else {
			invokedMsg = dataMessageBuilder.buildPutObjectDone(uuid, staticPart, sender, target, args[0]);
		}

		/** 6. Send object/exception **/
		rcvdMsg = sendAndRecv(invokedMsg);

		/** 8. Return or re-raise exception **/
		if (exceptionSettingObject != null) {
			throw exceptionSettingObject;
		}

		logger.trace("out");
		return;
	}

	public static DataMessage incomingPutField(String messageUuid, InstanceFieldPut instanceFieldPut,
																						 String followingUuid) {
		logger.trace("in w/ instanceFieldPut: {}, messageUuid: {}, following uuid: {}", instanceFieldPut, messageUuid,
			followingUuid);

		/** 1. Load class and field **/
		final Class clazz;
		Field field = null;
		Exception exceptionWhileLoading = null;
		try {
			clazz = Class.forName(instanceFieldPut.getClass_().getName());
			field = clazz.getDeclaredField(instanceFieldPut.getField().getName());
		} catch (Exception e) {
			exceptionWhileLoading = e;
			logger.error("Caught and assigned to exceptionWhileLoading", e);
		}

		/** 2. If class and field loaded, unwrap/load target object and value and invoke field set **/
		//TODO unwrap or load object before and have a separate exception for this step

		Exception exceptionWhileInvoking = null;

		final Object target;
		if (exceptionWhileLoading == null) {
			field.setAccessible(true);
			try {
				//unwrap or load target object
				if (instanceFieldPut.hasObject()) {
					target = Unwrapper.unwrapObject(instanceFieldPut.getObject(), field.getType());
					logger.debug("Unwrapped target: {}", target);
				} else {
					target = objectService.lookupObject(instanceFieldPut.getObjectRef());
					logger.debug("Loaded target: {}", target);
				}
				//unwrap or load value
				final Object value;
				if (instanceFieldPut.hasValueObject()) {
					value = Unwrapper.unwrapObject(instanceFieldPut.getValueObject(), field.getType());
					logger.debug("Unwrapped value: {}", value);
				} else {
					value = objectService.lookupObject(instanceFieldPut.getValueObjectRef());
					logger.debug("Loaded value: {}", value);
				}
				//invoke set
				field.set(target, value);
			} catch (Exception e) {
				exceptionWhileInvoking = e;
				logger.error("Caught and assigned to exceptionWhileInvoking", e);
			}
		}

		/** 3. Wrap return value or exception **/
		DataMessage invokedMsg = null;

		if (exceptionWhileLoading != null) {
			invokedMsg = dataMessageBuilder.buildAccessibleObjectThrowable(uuid, field, exceptionWhileLoading,
				followingUuid);
		} else if (exceptionWhileInvoking != null) {
			invokedMsg = dataMessageBuilder.buildAccessibleObjectThrowable(uuid, field, exceptionWhileInvoking,
				followingUuid);
		} else {
			invokedMsg = dataMessageBuilder.buildPutObjectDone(uuid, messageUuid, instanceFieldPut, field.getType(),
				followingUuid);
		}


		/** 4. Send object/exception **/
		DataMessage rcvdMsg = sendAndRecv(invokedMsg);

		logger.trace("out w/ {}", rcvdMsg);
		return rcvdMsg;

	}

	// </editor-fold>

	// <editor-fold defaultstate="collapsed" desc="SOCKET METHODS">
	private static DataMessage sendAndRecv(DataMessage dataMessage) {
		logger.trace("in w/ dataMessage with uuid: {}", dataMessage.getMessageUuid());
		Socket outSocket = threadSocket.get();
		outSocket.send(dataMessage.toByteArray());

		String rcvdString = null;
		try {
			rcvdString = outSocket.recvStr();
		} catch (ZMQException ex) {
			int errorCode = ex.getErrorCode();
			if (errorCode == ZError.ETERM) {
				logger.warn("Caught ETERM during blocking read. Will close socket");
				outSocket.close();
				return null;
			} else if (errorCode == ZError.EINTR) {
				logger.warn("Caught EINTR during blocking read. Will close socket.");
				outSocket.close();
				return null;
			}
		}

		DataMessage returnValue;
		if ("0".equals(rcvdString)) {
			logger.debug("0 means return same message");
			returnValue = dataMessage;
		} else {
			//TODO should get it and return it
			logger.warn("We should not get here");
			returnValue = null;
		}

		logger.trace("out w/ {}", returnValue);
		return returnValue;
	}

	public static void closeThreadLocalSocket() {
		if (threadSocketCreated.get()) {
			Socket outSocket = threadSocket.get();
			if (outSocket != null) {
				outSocket.close();
				logger.debug("Thread local socket closed");
			}
		}
	}

	private static void closeZmqContext() {
		logger.info("Destroying zmq context");
		zmqContext.destroy();
		logger.info("Destroyed zmq context");
	}
	// </editor-fold>

	// <editor-fold defaultstate="collapsed" desc="PEER INIT METHODS">
	private static void registerSelfAsPeer(Properties properties, Injector injector) {

		final PeerLogDirectory registry = injector.getInstance(PeerLogDirectory.class);

		// connect to directory
		try {
			registry.connect(properties.getProperty("zookeeper.url"));
		} catch (Exception ex) {
			logger.error("Error connecting to directory", ex);
			ex.printStackTrace();
			System.exit(3);
		}

		// register self as new peer
		try {
			final Properties peerProperties = new Properties();
			peerProperties.put("listenAddress", properties.getProperty("in.router"));
			registry.registerPeer(uuid, peerProperties);
		} catch (Exception ex) {
			logger.error("Error registering peer", ex);
			ex.printStackTrace();
			System.exit(4);
		}
	}

	private static LogInfo registerNewLog(Properties properties, Injector injector) {

		final PeerLogDirectory registry = injector.getInstance(PeerLogDirectory.class);
		final String kafkaTopicPrefix = properties.getProperty("kafkaTopic");
		LogInfo newLogInfo = null;

		// register new log
		try {
			newLogInfo = registry.createLog(kafkaTopicPrefix, DEFAULT_BOOTSTRAP_SERVERS);
		} catch (Exception ex) {
			logger.error("Error registering new log", ex);
			ex.printStackTrace();
			System.exit(5);
		}

		return newLogInfo;
	}

	private static LogInfo registerGivenLog(String logName, Injector injector) {

		final PeerLogDirectory registry = injector.getInstance(PeerLogDirectory.class);
		LogInfo logInfo = null;

		// register given log if not registered
		try {
			if (registry.logExists(logName)) {
				logInfo = registry.getLogInfo(logName);
			} else {
				logInfo = registry.addGivenLog(logName, DEFAULT_BOOTSTRAP_SERVERS);
			}
		} catch (Exception ex) {
			logger.error("Error registering given log", ex);
			ex.printStackTrace();
			System.exit(5);
		}

		return logInfo;
	}

	private static void readFromLog(String logName, Injector injector, Long offset) {
		IncomingMessageDispatcher incomingMessageDispatcher = injector.getInstance(IncomingMessageDispatcher.class);
		try {
			incomingMessageDispatcher.readFromLog(logName, offset);
		} catch (Exception ex) {
			logger.error("Could not initialize log reader. Aborting ...", ex);
			ex.printStackTrace();
			System.exit(6);
		}
	}

	private static void writeToLog(String logName, Injector injector) {
		KafkaMessageWriter kafkaMessageWriter = injector.getInstance(KafkaMessageWriter.class);
		try {
			kafkaMessageWriter.writeToLog(logName);
		} catch (Exception ex) {
			logger.error("Could not initialize log writer. Aborting ...", ex);
			ex.printStackTrace();
			System.exit(6);
		}
	}

	private static CommandLine parseOptions(String[] args) {
		CommandLineParser parser = new DefaultParser();
		Options options = new Options();
		options.addOption(Option.builder("u").required(false).longOpt("use-uuid").hasArg()
			.desc("use given uuid").build());
		options.addOption(Option.builder("rl").required(false).longOpt("read-log").hasArg()
			.desc("read from given log").build());
		options.addOption(Option.builder("wl").required(false).longOpt("write-log").hasArg()
			.desc("write to given log").build());
		options.addOption(Option.builder("l").required(false).longOpt("log").hasArg()
			.desc("read and write from/to given log").build());
		options.addOption(Option.builder("os").required(false).longOpt("offset-start").hasArg()
			.desc("read from given offset (requires -l or -rl)").build());
		options.addOption(Option.builder("h").required(false).longOpt("help").desc("print usage").build());

		CommandLine cmdLine = null;
		try {
			cmdLine = parser.parse(options, args);
		} catch (ParseException exp) {
			System.err.println(exp.getMessage());
			System.exit(1);
		}

		if (cmdLine.hasOption("help")) {
			HelpFormatter formatter = new HelpFormatter();
			formatter.printHelp("peer", options);
			System.exit(0);
		}

		// this must be done here or the guice injector will fail
		if (cmdLine.hasOption("use-uuid")) {
			uuid = UUID.fromString(cmdLine.getOptionValue("u").trim());
		} else {
			uuid = UUID.randomUUID();
		}
		properties.put("id", uuid.toString());

		return cmdLine;
	}
	// </editor-fold>

	public static void main(final String[] args) {

		if (logger.isInfoEnabled()) {
			StringBuilder sb = new StringBuilder();
			for (String arg: args) {
				sb.append(arg).append(" ");
			}
			logger.info("::main called w/args: {}", sb);
		}

		CommandLine cmdLine = parseOptions(args);

		AbstractModule module = new AbstractModule() {
			@Override
			protected void configure() {

				Names.bindProperties(binder(), properties);

				// bind implementations
				bind(PeerThreadFactory.class).to(PeerExecThreadFactory.class);
				bind(PeerExecutor.class).to(PeerMessageExecutor.class);
				bind(LogThreadFactory.class).to(LogExecThreadFactory.class);
				bind(LogExecutor.class).to(LogMessageExecutor.class);
				bind(KafkaMessageWriter.class).to(KafkaDataMessageWriter.class);
				bind(IncomingMessageDispatcher.class).to(KafkaDataMessageReader.class);
				bind(OutgoingMessageDispatcher.class).to(JeromqOutMessageDispatcher.class);
				bind(InRequestMessageDispatcher.class).to(JeromqInRequestDispatcher.class);

				// common and cxn library classes are not annotated with @Singleton
				bind(ObjectService.class).to(BiMapObjectService.class).asEagerSingleton();
				bind(DataMessageBuilder.class).to(ProtobufDataMessageBuilder.class).asEagerSingleton();
				bind(PeerLogDirectory.class).to(ZkClient.class).asEagerSingleton();

				// fields to be injected in Concentrator are static
				Concentrator.outCellAddress = properties.getProperty("out.cell");
				requestStaticInjection(Concentrator.class);
			}

			@Provides
			ZContext getZmqContext() {
				return Concentrator.zmqContext;
			}
		};

		final Injector injector = Guice.createInjector(module);

		// register peer
		registerSelfAsPeer(properties, injector);

		// init log IO
		boolean readLog = cmdLine.hasOption("read-log") || cmdLine.hasOption("log");
		boolean writeLog = cmdLine.hasOption("write-log") || cmdLine.hasOption("log");
		boolean offsetGiven = cmdLine.hasOption("offset-start");
		String offsetStr = cmdLine.getOptionValue("os");
		if (offsetGiven && !readLog) {
			System.err.println("Offset given but no log to read from. Try `runner -h`.");
			System.exit(1);
		}

		// parse initial offset
		Long offset = null;
		if (offsetGiven && offsetStr != null) {
			offset = Long.valueOf(offsetStr);
		}

		// init log reader
		LogInfo newLog = null;
		if (readLog) {
			String logName = cmdLine.getOptionValue("l");
			if (logName == null) {
				logName = cmdLine.getOptionValue("rl");
			}
			registerGivenLog(logName, injector);
			readFromLog(logName, injector, offset);
		} else { // no log given, create new
			newLog = registerNewLog(properties, injector);
			readFromLog(newLog.getName(), injector, offset);
		}

		// init log writer
		if (writeLog) {
			String logName = cmdLine.getOptionValue("l");
			if (logName == null) {
				logName = cmdLine.getOptionValue("wl");
			}
			registerGivenLog(logName, injector);
			writeToLog(logName, injector);
		} else { // no log given, create new if not done already
			if (newLog == null) {
				newLog = registerNewLog(properties, injector);
			}
			writeToLog(newLog.getName(), injector);
		}


		// managed services
		final Set<Service> services = new HashSet<>();
		services.add((Service) injector.getInstance(IncomingMessageDispatcher.class));
		services.add((Service) injector.getInstance(OutgoingMessageDispatcher.class));
		services.add(injector.getInstance(KafkaDataMessageWriter.class));
		services.add((Service) injector.getInstance(ObjectService.class));
		services.add(injector.getInstance(JeromqInRequestDispatcher.class));

		final ServiceManager manager = new ServiceManager(services);

		manager.addListener(new ServiceManager.Listener() {
			public void stopped() {
				logger.info("Service manager stopped.");
			}

			public void healthy() {
				// start accepting requests...
				logger.info("Service manager is healthy.");
				IncomingMessageDispatcher incomingMessageDispatcher = injector.getInstance(IncomingMessageDispatcher.class);
				incomingMessageDispatcher.acceptConnections(true);

				// We must prestart threads to create the REP sockets, and this must be done after DEALER
				ExtendedThreadPoolExecutor executor = (PeerMessageExecutor) injector.getInstance(PeerExecutor.class);
				executor.prestartAllCoreThreads();
				executor = (LogMessageExecutor) injector.getInstance(LogExecutor.class);
				executor.prestartAllCoreThreads();
			}

			public void failure(Service service) {
				logger.error("Service manager failed. Exiting ...", service.failureCause());
				System.exit(7);
			}
		}, MoreExecutors.directExecutor());

		/** Add shutdown hook **/
		Runtime.getRuntime().addShutdownHook(new Thread() {
			@Override
			public void run() {
				try {
					// destroy context
					Concentrator.closeZmqContext();

					// stop peer executor (interrupts all peer exec threads)
					final ExtendedThreadPoolExecutor peerMessageExecutor =
						(PeerMessageExecutor) injector.getInstance(PeerExecutor.class);
					logger.info("shutting down peer threads");
					peerMessageExecutor.shutdownNow();

					// stop log executor (interrupts all log exec threads)
					final ExtendedThreadPoolExecutor logMessageExecutor =
						(LogMessageExecutor) injector.getInstance(LogExecutor.class);
					logger.info("shutting down log threads");
					logMessageExecutor.shutdownNow();

					// stop all services
					manager.stopAsync().awaitStopped(3, TimeUnit.SECONDS);

				} catch (TimeoutException ie) {
					logger.error("Timeout exception in shutdown hook", ie);
				} finally {
					logger.info("This peer is done! bye");
				}
			}
		});

		//start services
		manager.startAsync();

	}
}
