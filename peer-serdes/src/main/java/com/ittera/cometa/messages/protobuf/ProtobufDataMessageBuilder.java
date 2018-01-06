package com.ittera.cometa.messages.protobuf;

import com.ittera.cometa.messages.protobuf.data.Wrappers.DataMessage;
import com.ittera.cometa.messages.protobuf.data.Wrappers.Type;
import com.ittera.cometa.messages.protobuf.data.Primitives;
import com.ittera.cometa.messages.protobuf.data.Exceptions;
import com.ittera.cometa.messages.protobuf.data.Fields;
import com.ittera.cometa.messages.protobuf.data.Values;
import com.ittera.cometa.messages.protobuf.data.Ctxt;
import com.ittera.cometa.messages.protobuf.data.Fields.*;
import com.ittera.cometa.messages.protobuf.data.Calls.*;
import com.ittera.cometa.messages.protobuf.data.Values.*;
import com.ittera.cometa.messages.DataMessageBuilder;

import com.ittera.cometa.common.ObjectService;

import org.aspectj.runtime.reflect.FieldSignatureImpl;
import org.aspectj.lang.reflect.CodeSignature;
import org.aspectj.lang.reflect.ConstructorSignature;
import org.aspectj.lang.reflect.MethodSignature;
import org.aspectj.lang.reflect.InitializerSignature;
import org.aspectj.lang.JoinPoint.StaticPart;

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Field;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.protobuf.Message.Builder;

import javax.inject.Inject;

/**
 * Methods of this class receive aspectj objects (i.e. StaticPart) as arguments as convenience.
 */
public final class ProtobufDataMessageBuilder implements DataMessageBuilder {

	protected static final Logger logger = LoggerFactory.getLogger(ProtobufDataMessageBuilder.class);

	@Inject
	private ObjectService objectService;

	private boolean storeUncachedObjects = true;

	//<editor-fold desc="Thread-local sequence stamping methods">

	private final ThreadLocal<AtomicLong> threadDispatchSequence = new ThreadLocal<AtomicLong>() {
		@Override
		protected AtomicLong initialValue() {
			return new AtomicLong(1);
		}
	};

	private final ThreadLocal<AtomicLong> threadBuilderSequence = new ThreadLocal<AtomicLong>() {
		@Override
		protected AtomicLong initialValue() {
			return new AtomicLong(1);
		}
	};

	@Override
	public void resetThreadLocalSequence() {
		threadBuilderSequence.set(new AtomicLong(1));
		threadDispatchSequence.get().getAndIncrement();
	}

	//</editor-fold>

	//<editor-fold desc="Private Auxiliary methods">

	private Builder addParameter(Builder callBuilder, String parameterType, Object arg, String argObjRef) {

		if (callBuilder instanceof ConstructorCall.Builder) {
			((ConstructorCall.Builder) callBuilder).addParameter(getWrapped(arg, parameterType, argObjRef));
		} else if (callBuilder instanceof InstanceMethodCall.Builder) {
			((InstanceMethodCall.Builder) callBuilder).addParameter(getWrapped(arg, parameterType, argObjRef));
		} else if (callBuilder instanceof ClassMethodCall.Builder) {
			((ClassMethodCall.Builder) callBuilder).addParameter(getWrapped(arg, parameterType, argObjRef));
		} else {
			throw new UnsupportedOperationException(String.format("Unsupported Builder class: %s ",
				callBuilder.getClass().getName()));
		}

		return callBuilder;
	}

	private Builder addParameters(Builder callBuilder, String[] parameterTypes, Object[] args, String[] argObjRefs) {

		for (int i = 0; parameterTypes != null && i < parameterTypes.length; i++) {
			if (argObjRefs[i] != null) { //parameter is an objectref
				addParameter(callBuilder, parameterTypes[i], (Object) null, argObjRefs[i]);
			} else if (args[i] != null) { //parameter is string, primitive or wrapper
				addParameter(callBuilder, parameterTypes[i], args[i], (String) null);
			} else { //parameter is null
				addParameter(callBuilder, parameterTypes[i], (Object) null, (String) null);
			}
		}

		return callBuilder;
	}

	private Builder addNamedParameter(Builder callBuilder, String paramName, String paramType, Object param) {
		if (callBuilder instanceof ConstructorCall.Builder) {
			((ConstructorCall.Builder) callBuilder).addParameterName(paramName);
			((ConstructorCall.Builder) callBuilder).addParameter(getWrapped(param, paramType, null));
		} else if (callBuilder instanceof InstanceMethodCall.Builder) {
			((InstanceMethodCall.Builder) callBuilder).addParameterName(paramName);
			((InstanceMethodCall.Builder) callBuilder).addParameter(getWrapped(param, paramType,
				null));
		} else if (callBuilder instanceof ClassMethodCall.Builder) {
			((ClassMethodCall.Builder) callBuilder).addParameterName(paramName);
			((ClassMethodCall.Builder) callBuilder).addParameter(getWrapped(param, paramType, null));
		} else {
			throw new UnsupportedOperationException(String.format("Unsupported Builder class: %s ",
				callBuilder.getClass().getName()));
		}

		return callBuilder;
	}

	private Builder addParameters(Builder callBuilder, StaticPart staticPart, Object[] args) {

		final CodeSignature codeSignature = (CodeSignature) staticPart.getSignature();
		for (int i = 0; codeSignature.getParameterTypes() != null && i < args.length; i++) {
			//TODO what about objectRefs?
			addNamedParameter(callBuilder, codeSignature.getParameterNames()[i],
				codeSignature.getParameterTypes()[i].getName(), args[i]);
		}

		return callBuilder;
	}


	private DataMessage.Builder newWrapperBuilder(Type msgType, UUID concentratorUuid, String followingUuid) {

		DataMessage.Builder msgBuilder = DataMessage.newBuilder()
			.setConcentratorUuid(concentratorUuid.toString())
			.setMessageUuid(UUID.randomUUID().toString())
			.setMsgType(msgType)
			.setThreadId(Thread.currentThread().getId())
			.setDispatchSeq(threadDispatchSequence.get().longValue())
			.setBuilderSeq(threadBuilderSequence.get().getAndIncrement())
			.setCurrentTime(System.currentTimeMillis());

		if (followingUuid != null && !followingUuid.isEmpty()) {
			msgBuilder.setFollowingUuid(followingUuid);
		}

		return msgBuilder;
	}

	private DataMessage.Builder newWrapperBuilder(Type msgType, UUID concentratorUuid) {
		return newWrapperBuilder(msgType, concentratorUuid, null);
	}

	/**
	 * Methods delegating to Wrapper
	 **/
	private <T> Primitives.Object getWrapped(Object object, T t, String objectRef) {

		if (objectRef == null && object != null) {
			return Wrapper.getWrappedObject(object, t, storeUncachedObjects ? objectService.storeObject(object) : null);
		} else {
			return Wrapper.getWrappedObject(object, t, objectRef);
		}
	}

	private Ctxt.Context getWrapped(StaticPart staticPart, Object sender) {
		if (sender != null) {
			return Wrapper.getWrappedContext(staticPart, sender,
				storeUncachedObjects ? objectService.storeObject(sender) : null);
		} else {
			return Wrapper.getWrappedContext(staticPart, sender, null);
		}
	}

	private Fields.Field getWrapped(Class clazz, String fieldName) {
		return Wrapper.getWrappedField(clazz, fieldName);
	}

	private Fields.Field getWrapped(String className, String fieldName) {
		return Wrapper.getWrappedField(className, fieldName);
	}

	private Primitives.Class getWrapped(Class clazz) {
		return Wrapper.getWrappedClass(clazz);
	}

	private Primitives.Class getWrapped(String className) {
		return Wrapper.getWrappedClass(className);
	}

	//</editor-fold>

	//<editor-fold desc="Class initialization messages">
	public DataMessage buildClassInitializer(UUID concentratorUuid, StaticPart staticPart, Object sender) {

		final InitializerSignature codeSignature = (InitializerSignature) staticPart.getSignature();

		final DataMessage.Builder msgBuilder = newWrapperBuilder(Type.STATIC_CONSTRUCTOR, concentratorUuid)
			.setClinitCall(ClInitCall.newBuilder()
				.setClass_(getWrapped(codeSignature.getDeclaringTypeName()))
				.setModifiers(codeSignature.getModifiers())
				.setContext(getWrapped(staticPart, sender)));

		return msgBuilder.build();
	}

	public DataMessage buildLoadedClass(UUID concentratorUuid, Class clazz) {

		final DataMessage.Builder msgBuilder = newWrapperBuilder(Type.RETURN_CLASS, concentratorUuid)
			.setReturnValue(ReturnValue.newBuilder()
				.setIsClass(true)
				.setClazz(getWrapped(clazz.getName())));

		return msgBuilder.build();
	}

	//</editor-fold>

	//<editor-fold desc="Constructor messages">


	private final DataMessage buildConstructorMessage(UUID concentratorUuid, String className,
																										StaticPart staticPart, Object sender, String[] parameterTypes, Object[] args, String[] argObjRefs) {

		final ConstructorCall.Builder constructorCallBuilder = ConstructorCall.newBuilder();
		if (staticPart != null) {
			final ConstructorSignature codeSignature = (ConstructorSignature) staticPart.getSignature();
			addParameters(constructorCallBuilder, staticPart, args);
			constructorCallBuilder.setModifiers(codeSignature.getModifiers());
			constructorCallBuilder.setContext(getWrapped(staticPart, sender));
			constructorCallBuilder.setClass_(getWrapped(codeSignature.getDeclaringTypeName()));
		} else {
			addParameters(constructorCallBuilder, parameterTypes, args, argObjRefs);
			constructorCallBuilder.setClass_(getWrapped(className));
		}

		final DataMessage.Builder msgBuilder = newWrapperBuilder(Type.CONSTRUCTOR, concentratorUuid)
			.setConstructorCall(constructorCallBuilder);

		return msgBuilder.build();
	}

	/**
	 * This method is to be called when no joinpoint context is available.
	 */
	public DataMessage buildEmptyConstructor(UUID concentratorUuid, String className) {

		return buildConstructorMessage(concentratorUuid, className,
			null, null, null, null, null);
	}

	/**
	 * Args must be set either in args or argObjRefs. If null in both, value is assumed to be null.
	 *
	 * @param concentratorUuid
	 * @param className
	 * @param parameterTypes
	 * @param args             Should be of same length as parameterTypes. For Strings, primitives and wrappers.
	 * @param argObjRefs       Should be of same length as parameterTypes. For objectrefs.
	 * @return
	 */
	public DataMessage buildNonEmptyConstructor(UUID concentratorUuid, String className, String[] parameterTypes,
																							Object[] args, String[] argObjRefs) {

		return buildConstructorMessage(concentratorUuid, className, null, null, parameterTypes, args,
			argObjRefs);
	}

	public DataMessage buildConstructor(UUID concentratorUuid, StaticPart staticPart, Object sender, Object[] args) {

		return buildConstructorMessage(concentratorUuid, null, staticPart, sender, null, args,
			null);
	}
	//</editor-fold>

	//<editor-fold desc="Instance method messages">

	/**
	 * This method is to be called when no joinpoint context is available.
	 */
	public DataMessage buildInstanceMethod(UUID concentratorUuid, String className, String methodName, String objRef,
																				 String[] parameterTypes, Object[] args, String[] argObjRefs) {

		final InstanceMethodCall.Builder callBuilder = InstanceMethodCall.newBuilder();
		addParameters(callBuilder, parameterTypes, args, argObjRefs);

		final DataMessage.Builder msgBuilder = newWrapperBuilder(Type.INSTANCE_METHOD, concentratorUuid)
			.setInstanceMethodCall(callBuilder
				.setClass_(getWrapped(className))
				.setName(methodName)
				.setObjectRef(objRef));

		return msgBuilder.build();
	}


	public DataMessage buildInstanceMethod(UUID concentratorUuid, StaticPart staticPart, Object sender, Object target,
																				 Object[] args) {

		final MethodSignature codeSignature = (MethodSignature) staticPart.getSignature();

		final InstanceMethodCall.Builder callBuilder = InstanceMethodCall.newBuilder();
		addParameters(callBuilder, staticPart, args);

		final DataMessage.Builder msgBuilder = newWrapperBuilder(Type.INSTANCE_METHOD, concentratorUuid)
			.setInstanceMethodCall(callBuilder
				.setClass_(getWrapped(codeSignature.getDeclaringTypeName()))
				.setName(codeSignature.getName())
				.setObject(getWrapped(target, codeSignature.getDeclaringTypeName(), null))
				.setModifiers(codeSignature.getModifiers())
				.setContext(getWrapped(staticPart, sender)));

		return msgBuilder.build();
	}
	//</editor-fold>

	//<editor-fold desc="Class method messages">

	/**
	 * This method is to be called when no joinpoint context is available.
	 */
	public DataMessage buildClassMethod(UUID concentratorUuid, String className, String methodName,
																			String[] parameterTypes, Object[] args, String[] argObjRefs) {

		final ClassMethodCall.Builder callBuilder = ClassMethodCall.newBuilder();
		addParameters(callBuilder, parameterTypes, args, argObjRefs);

		final DataMessage.Builder msgBuilder = newWrapperBuilder(Type.CLASS_METHOD, concentratorUuid)
			.setClassMethodCall(callBuilder
				.setClass_(getWrapped(className))
				.setName(methodName));

		return msgBuilder.build();

	}

	public DataMessage buildClassMethod(UUID concentratorUuid, StaticPart staticPart, Object sender, Object[] args) {

		final MethodSignature codeSignature = (MethodSignature) staticPart.getSignature();

		final ClassMethodCall.Builder callBuilder = ClassMethodCall.newBuilder();
		addParameters(callBuilder, staticPart, args);

		final DataMessage.Builder msgBuilder = newWrapperBuilder(Type.CLASS_METHOD, concentratorUuid)
			.setClassMethodCall(callBuilder
				.setContext(getWrapped(staticPart, sender))
				.setClass_(getWrapped(codeSignature.getDeclaringTypeName()))
				.setName(codeSignature.getName())
				.setModifiers(codeSignature.getModifiers()));

		return msgBuilder.build();
	}
	//</editor-fold>

	//<editor-fold desc="Static field get messages">

	/**
	 * This method is to be called when no joinpoint context is available.
	 */
	public DataMessage buildGetStatic(UUID concentratorUuid, String className, String fieldName) {

		final DataMessage.Builder msgBuilder = newWrapperBuilder(Type.GET_STATIC, concentratorUuid)
			.setStaticFieldGet(StaticFieldGet.newBuilder()
				.setClass_(getWrapped(className))
				.setField(getWrapped(className, fieldName)));

		return msgBuilder.build();
	}

	public DataMessage buildGetStatic(UUID concentratorUuid, StaticPart staticPart, Object sender) {

		final FieldSignatureImpl fieldSignature = (FieldSignatureImpl) staticPart.getSignature();

		final DataMessage.Builder msgBuilder = newWrapperBuilder(Type.GET_STATIC, concentratorUuid)
			.setStaticFieldGet(StaticFieldGet.newBuilder()
				.setClass_(getWrapped(fieldSignature.getDeclaringTypeName()))
				.setField(getWrapped(fieldSignature.getFieldType(), fieldSignature.getName()))
				.setModifiers(fieldSignature.getModifiers())
				.setContext(getWrapped(staticPart, sender)));

		return msgBuilder.build();
	}
	//</editor-fold>

	//<editor-fold desc="Instance field get messages">

	/**
	 * This method is to be called when no joinpoint context is available.
	 */
	public DataMessage buildGetObject(UUID concentratorUuid, String className, String fieldName, String targetObjRef) {

		final DataMessage.Builder msgBuilder = newWrapperBuilder(Type.GET_FIELD, concentratorUuid)
			.setInstanceFieldGet(InstanceFieldGet.newBuilder()
				.setClass_(getWrapped(className))
				.setObjectRef(targetObjRef)
				.setField(getWrapped((String) null, fieldName)));

		return msgBuilder.build();
	}

	public DataMessage buildGetObject(UUID concentratorUuid, StaticPart staticPart, Object sender, Object target) {

		final FieldSignatureImpl fieldSignature = (FieldSignatureImpl) staticPart.getSignature();

		final DataMessage.Builder msgBuilder = newWrapperBuilder(Type.GET_FIELD, concentratorUuid)
			.setInstanceFieldGet(InstanceFieldGet.newBuilder()
				.setClass_(getWrapped(fieldSignature.getDeclaringTypeName()))
				.setObject(getWrapped(target, fieldSignature.getDeclaringTypeName(), null))
				.setField(getWrapped(fieldSignature.getFieldType(), fieldSignature.getName()))
				.setModifiers(fieldSignature.getModifiers())
				.setContext(getWrapped(staticPart, sender)));

		return msgBuilder.build();
	}
	//</editor-fold>

	//<editor-fold desc="Static field put messages">

	/**
	 * This method is to be called when no joinpoint context is available.
	 */
	public DataMessage buildPutStatic(UUID concentratorUuid, String className, String fieldName, String valueClassName,
																		Object value) {

		final DataMessage.Builder msgBuilder = newWrapperBuilder(Type.PUT_STATIC, concentratorUuid)
			.setStaticFieldPut(StaticFieldPut.newBuilder()
				.setClass_(getWrapped(className))
				.setField(getWrapped((String) null, fieldName))
				.setObject(getWrapped(value, valueClassName, null)));

		return msgBuilder.build();
	}

	/**
	 * This method is to be called when no joinpoint context is available.
	 * Equivalent to the above, for objectRefs
	 */
	public DataMessage buildPutStatic(UUID concentratorUuid, String className, String fieldName, String objectRef) {

		final DataMessage.Builder msgBuilder = newWrapperBuilder(Type.PUT_STATIC, concentratorUuid)
			.setStaticFieldPut(StaticFieldPut.newBuilder()
				.setClass_(getWrapped(className))
				.setField(getWrapped((String) null, fieldName))
				.setObjectRef(objectRef));

		return msgBuilder.build();
	}

	public DataMessage buildPutStatic(UUID concentratorUuid, StaticPart staticPart, Object sender, Object arg) {

		final FieldSignatureImpl fieldSignature = (FieldSignatureImpl) staticPart.getSignature();

		final DataMessage.Builder msgBuilder = newWrapperBuilder(Type.PUT_STATIC, concentratorUuid)
			.setStaticFieldPut(StaticFieldPut.newBuilder()
				.setClass_(getWrapped(fieldSignature.getDeclaringType()))
				.setField(getWrapped(fieldSignature.getFieldType(), fieldSignature.getName()))
				.setObject(getWrapped(arg, fieldSignature.getFieldType(), null))
				.setModifiers(fieldSignature.getModifiers())
				.setContext(getWrapped(staticPart, sender)));

		return msgBuilder.build();
	}

	/**
	 * This method is to be called when no joinpoint context is available.
	 * Equivalent to the above, for objectRefs
	 */
	public DataMessage buildPutStaticDone(UUID concentratorUuid, String staticFieldPutUuid,
																				Fields.StaticFieldPut staticFieldPut, Class fieldType, String followingUuid) {

		final StaticFieldPutDone.Builder fieldBuilder = StaticFieldPutDone.newBuilder();
		if (staticFieldPut.getField().hasClass_()) {
			fieldBuilder.setField(staticFieldPut.getField());
		} else {
			fieldBuilder.setField(getWrapped(fieldType, staticFieldPut.getField().getName()));
		}

		final DataMessage.Builder msgBuilder = newWrapperBuilder(Type.PUT_STATIC_DONE, concentratorUuid, followingUuid)
			.setStaticFieldPutDone(fieldBuilder
				.setClass_(getWrapped(staticFieldPut.getClass_().getName()))
				.setStaticFieldPutUuid(staticFieldPutUuid));

		return msgBuilder.build();
	}


	public DataMessage buildPutStaticDone(UUID concentratorUuid, StaticPart staticPart, Object sender, Object arg) {

		final FieldSignatureImpl fieldSignature = (FieldSignatureImpl) staticPart.getSignature();

		final DataMessage.Builder msgBuilder = newWrapperBuilder(Type.PUT_STATIC_DONE, concentratorUuid)
			.setStaticFieldPutDone(StaticFieldPutDone.newBuilder()
				.setField(getWrapped(fieldSignature.getFieldType(), fieldSignature.getName()))
				.setClass_(getWrapped(fieldSignature.getDeclaringType())));

		return msgBuilder.build();
	}
	//</editor-fold>

	//<editor-fold desc="Instance field put messages">

	/**
	 * This method is to be called when no joinpoint context is available.
	 * Equivalent to the above, for objectRefs
	 */
	public DataMessage buildPutObject(UUID concentratorUuid, String className, String fieldName, String targetObjRef,
																		String valueClassName, Object value) {

		final DataMessage.Builder msgBuilder = newWrapperBuilder(Type.PUT_FIELD, concentratorUuid, null)
			.setInstanceFieldPut(InstanceFieldPut.newBuilder()
				.setClass_(getWrapped(className))
				.setObjectRef(targetObjRef)
				.setField(getWrapped((String) null, fieldName))
				.setValueObject(getWrapped(value, valueClassName, null)));

		return msgBuilder.build();
	}

	/**
	 * This method is to be called when no joinpoint context is available.
	 * Equivalent to the above, for objectRefs
	 */
	public DataMessage buildPutObject(UUID concentratorUuid, String className, String fieldName, String targetObjRef,
																		String valueObjRef) {

		final DataMessage.Builder msgBuilder = newWrapperBuilder(Type.PUT_FIELD, concentratorUuid)
			.setInstanceFieldPut(InstanceFieldPut.newBuilder()
				.setClass_(getWrapped(className))
				.setObjectRef(targetObjRef)
				.setField(getWrapped((String) null, fieldName))
				.setValueObjectRef(valueObjRef));

		return msgBuilder.build();
	}

	public DataMessage buildPutObject(UUID concentratorUuid, StaticPart staticPart, Object sender, Object target,
																		Object arg) {

		final FieldSignatureImpl fieldSignature = (FieldSignatureImpl) staticPart.getSignature();

		final DataMessage.Builder msgBuilder = newWrapperBuilder(Type.PUT_FIELD, concentratorUuid)
			.setInstanceFieldPut(InstanceFieldPut.newBuilder()
				.setClass_(getWrapped(fieldSignature.getDeclaringType()))
				.setObject(getWrapped(target, fieldSignature.getDeclaringType(), null))
				.setField(getWrapped(fieldSignature.getFieldType(), fieldSignature.getName()))
				.setValueObject(getWrapped(arg, fieldSignature.getFieldType().getName(), null))
				.setModifiers(fieldSignature.getModifiers())
				.setContext(getWrapped(staticPart, sender)));

		return msgBuilder.build();
	}

	public DataMessage buildPutObjectDone(UUID concentratorUuid, String instanceFieldPutUuid,
																				Fields.InstanceFieldPut instanceFieldPut, Class fieldType, String followingUuid) {

		final Fields.InstanceFieldPutDone.Builder fieldBuilder = InstanceFieldPutDone.newBuilder();
		if (instanceFieldPut.getField().hasClass_()) {
			fieldBuilder.setField(instanceFieldPut.getField());
		} else {
			fieldBuilder.setField(getWrapped(fieldType, instanceFieldPut.getField().getName()));
		}

		final DataMessage.Builder msgBuilder = newWrapperBuilder(Type.PUT_FIELD_DONE, concentratorUuid, followingUuid)
			.setInstanceFieldPutDone(fieldBuilder
				.setClass_(getWrapped(instanceFieldPut.getClass_().getName()))
				.setInstanceFieldPutUuid(instanceFieldPutUuid));

		return msgBuilder.build();
	}

	public DataMessage buildPutObjectDone(UUID concentratorUuid, StaticPart staticPart, Object sender, Object target,
																				Object arg) {

		final FieldSignatureImpl fieldSignature = (FieldSignatureImpl) staticPart.getSignature();

		final DataMessage.Builder msgBuilder = newWrapperBuilder(Type.PUT_FIELD_DONE, concentratorUuid)
			.setInstanceFieldPutDone(InstanceFieldPutDone.newBuilder()
				.setClass_(getWrapped(fieldSignature.getDeclaringType()))
				.setField(getWrapped(fieldSignature.getFieldType(), fieldSignature.getName())));

		return msgBuilder.build();
	}
	//</editor-fold>

	//<editor-fold desc="Throwable messages">
	public DataMessage buildAccessibleObjectThrowable(UUID concentratorUuid, AccessibleObject accessibleObject,
																										Exception exception, String followingUuid) {

		final Exceptions.RaisedThrowable.Builder thrBuilder = Exceptions.RaisedThrowable.newBuilder();
		if (accessibleObject instanceof Constructor) {
			thrBuilder.setConstructor(((Constructor) accessibleObject).getDeclaringClass().getName());
			thrBuilder.setModifiers(((Constructor) accessibleObject).getModifiers());
		} else if (accessibleObject instanceof Method) {
			thrBuilder.setMethod(((Method) accessibleObject).getName());
			thrBuilder.setModifiers(((Method) accessibleObject).getModifiers());
		} else if (accessibleObject instanceof Field) {
			thrBuilder.setField(((Field) accessibleObject).getName());
			thrBuilder.setModifiers(((Field) accessibleObject).getModifiers());
		} else {
			throw new UnsupportedOperationException(String.format("Unsupported accessibleObject type: %s",
				accessibleObject.getClass().getName()));
		}

		final DataMessage.Builder msgBuilder = newWrapperBuilder(Type.THROWABLE, concentratorUuid, followingUuid)
			.setRaisedThrowable(thrBuilder
				.setClass_(getWrapped(exception.getClass().getName()))
				.setThrowable(buildThrowableMessage(exception)));

		return msgBuilder.build();
	}

	public DataMessage buildInitializerThrowable(UUID concentratorUuid, StaticPart staticPart, Exception exception) {

		final DataMessage.Builder msgBuilder = newWrapperBuilder(Type.THROWABLE, concentratorUuid)
			.setRaisedThrowable(Exceptions.RaisedThrowable.newBuilder()
				.setInInitializer(true)
				.setClass_(getWrapped(staticPart.getSignature().getDeclaringTypeName()))
				.setModifiers(staticPart.getSignature().getModifiers())
				.setThrowable(buildThrowableMessage(exception)));

		return msgBuilder.build();
	}


	private Exceptions.Throwable.Builder buildThrowableMessage(Throwable throwable) {

		final Exceptions.Throwable.Builder msgBuilder = Exceptions.Throwable.newBuilder();
		//type
		msgBuilder.setType(throwable.getClass().getName());
		//message
		if (throwable.getMessage() != null) {
			msgBuilder.setMessage(throwable.getMessage());
		}
		//stack trace
		StackTraceElement[] stackTrace = throwable.getStackTrace();
		if (stackTrace != null) {
			for (StackTraceElement ste : stackTrace) {
				msgBuilder.addStackTraceElement(ste.toString());
			}
		}
		//fill in cause(s) -- recursive
		if (throwable.getCause() != null) {
			msgBuilder.setCause(buildThrowableMessage(throwable.getCause()));
		}

		return msgBuilder;
	}

	//</editor-fold>

	//<editor-fold desc="Return value messages">
	public DataMessage buildReturnValue(UUID concentratorUuid, Object object, Class type, String objectKey,
																			boolean isVoid, String followingUuid) {

		final ReturnValue.Builder contentBuilder = Values.ReturnValue.newBuilder();
		if (!isVoid) {
			contentBuilder.setObject(getWrapped(object, type, objectKey));
		}

		final DataMessage.Builder msgBuilder = newWrapperBuilder(Type.RETURN_VALUE, concentratorUuid, followingUuid)
			.setReturnValue(contentBuilder
				.setIsVoid(isVoid)
				.setClazz(getWrapped(type)));

		return msgBuilder.build();
	}

	@Override
	public void dontStoreObjects() {
		this.storeUncachedObjects = false;
	}
	//</editor-fold>
}
