package com.ittera.cometa.messages.protobuf;

import com.ittera.cometa.common.lang.reflect.*;
import com.ittera.cometa.messages.protobuf.data.*;
import com.ittera.cometa.messages.protobuf.data.Wrappers.DataMessage;
import com.ittera.cometa.messages.protobuf.data.Wrappers.InternalHeader;
import com.ittera.cometa.messages.protobuf.data.Wrappers.Type;
import com.ittera.cometa.messages.protobuf.data.Wrappers.InternalHeaderType;
import com.ittera.cometa.messages.protobuf.data.Fields.*;
import com.ittera.cometa.messages.protobuf.data.Calls.*;
import com.ittera.cometa.messages.protobuf.data.Values.*;
import com.ittera.cometa.messages.DataMessageBuilder;

import com.ittera.cometa.common.lang.Context;
import com.ittera.cometa.common.lang.ObjectRef;

import java.lang.reflect.*;

import java.util.UUID;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.protobuf.Message.Builder;

public final class ProtobufDataMessageBuilder implements DataMessageBuilder {

	protected static final Logger logger = LoggerFactory.getLogger(ProtobufDataMessageBuilder.class);

	//<editor-fold desc="Thread-local sequence stamping methods">

	private final ThreadLocal<AtomicLong> threadDispatchSequence = ThreadLocal.withInitial(() -> new AtomicLong(1));

	private final ThreadLocal<AtomicLong> threadBuilderSequence = ThreadLocal.withInitial(() -> new AtomicLong(1));

	public ProtobufDataMessageBuilder() {
	}

	@Override
	public void resetThreadLocalSequence() {
		threadBuilderSequence.set(new AtomicLong(1));
		threadDispatchSequence.get().getAndIncrement();
	}

	//</editor-fold>

	//<editor-fold desc="Private Auxiliary methods">

	private void addParameter(Builder callBuilder, String parameterType, Object arg, ObjectRef argObjRef) {

		// TODO : deal with objectRef
		Primitives.Parameter.Builder paramBuilder = Primitives.Parameter.newBuilder();
		paramBuilder.setType(getWrappedClass(parameterType));
		paramBuilder.setValue(getWrappedObject(arg, parameterType, argObjRef));

		if (callBuilder instanceof ConstructorCall.Builder) {
			((ConstructorCall.Builder) callBuilder).addParameter(paramBuilder);
		} else if (callBuilder instanceof InstanceMethodCall.Builder) {
			((InstanceMethodCall.Builder) callBuilder).addParameter(paramBuilder);
		} else if (callBuilder instanceof ClassMethodCall.Builder) {
			((ClassMethodCall.Builder) callBuilder).addParameter(paramBuilder);
		} else {
			throw new UnsupportedOperationException(String.format("Unsupported Builder class: %s ",
				callBuilder.getClass().getName()));
		}

	}

	private void addParameters(Builder callBuilder, String[] parameterTypes, Object[] args, ObjectRef[] argObjRefs) {

		for (int i = 0; parameterTypes != null && i < parameterTypes.length; i++) {
			if (argObjRefs[i] != null) { //parameter is an objectref
				addParameter(callBuilder, parameterTypes[i], null, argObjRefs[i]);
			} else if (args[i] != null) { //parameter is string, primitive or wrapper
				addParameter(callBuilder, parameterTypes[i], args[i], null);
			} else { //parameter is null
				addParameter(callBuilder, parameterTypes[i], null, null);
			}
		}

	}

	private void addNamedParameter(Builder callBuilder, Parameter parameter, String paramName, String paramType,
																 Object param, ObjectRef paramObjRef) {

		// TODO : deal with objectRef
		Primitives.Parameter.Builder paramBuilder = Primitives.Parameter.newBuilder();
		paramBuilder.setName(paramName == null ? parameter.getName() : paramName);
		paramBuilder.setType(getWrappedClass(paramType == null ? parameter.getType().getName() : paramType));
		paramBuilder.setIsVarArgs(parameter.isVarArgs());
		paramBuilder.setValue(getWrappedObject(param, paramType, paramObjRef));

		if (callBuilder instanceof ConstructorCall.Builder) {
			((ConstructorCall.Builder) callBuilder).addParameter(paramBuilder);
		} else if (callBuilder instanceof InstanceMethodCall.Builder) {
			((InstanceMethodCall.Builder) callBuilder).addParameter(paramBuilder);
		} else if (callBuilder instanceof ClassMethodCall.Builder) {
			((ClassMethodCall.Builder) callBuilder).addParameter(paramBuilder);
		} else {
			throw new UnsupportedOperationException(String.format("Unsupported Builder class: %s ",
				callBuilder.getClass().getName()));
		}

	}

	private void addParameters(Builder callBuilder, Context context, Object[] args, ObjectRef[] argObjRefs) {
		final CodeSignature codeSignature = (CodeSignature) context.getSignature();
		Parameter[] parameters = codeSignature.getParameters();
		String paramName, paramTypeName;
		for (int i = 0; codeSignature.getParameterTypes() != null && i < args.length; i++) {
			paramName = codeSignature.getParameterNames()[i];
			paramTypeName = codeSignature.getParameterTypes()[i].getName();
			addNamedParameter(callBuilder, parameters[i], paramName, paramTypeName, args[i], argObjRefs[i]);
		}

	}


	private DataMessage.Builder newWrapperBuilder(Type msgType, UUID concentratorUuid, String followingUuid) {

		DataMessage.Builder msgBuilder = DataMessage.newBuilder()
			.setConcentratorUuid(concentratorUuid.toString())
			.setMessageUuid(UUID.randomUUID().toString())
			.setMsgType(msgType)
			.setThreadId(Thread.currentThread().getId())
			.setThreadName(Thread.currentThread().getName())
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
	private <T> Primitives.Object getWrappedObject(Object object, T t, ObjectRef objectRef) {
		return Wrapper.getWrappedObject(object, t, objectRef);
	}

	private Ctxt.Context getWrappedContext(Context context, Object sender, ObjectRef senderObjRef) {
		return Wrapper.getWrappedContext(context, sender, senderObjRef);
	}

	private Primitives.Field getWrappedField(Field field) {
		return Wrapper.getWrappedField(field);
	}

	private Primitives.Field getWrappedField(Class clazz, String fieldName) {
		return Wrapper.getWrappedField(clazz, fieldName);
	}

	private Primitives.Field getWrappedField(String className, String fieldName) {
		return Wrapper.getWrappedField(className, fieldName);
	}

	private Primitives.Class getWrappedClass(Class clazz) {
		return Wrapper.getWrappedClass(clazz);
	}

	private Primitives.Class getWrappedClass(String className) {
		return Wrapper.getWrappedClass(className);
	}

	//</editor-fold>

	//<editor-fold desc="Header messages">
	private InternalHeader.Builder buildInternalHeaderMessage() {
		return InternalHeader.newBuilder().setHeaderType(InternalHeaderType.WRITE_AHEAD);
	}

	public InternalHeader buildWriteAheadHeader(UUID concentratorUuid) {
		return buildInternalHeaderMessage().setValue(concentratorUuid.toString()).build();
	}

	//</editor-fold>

	//<editor-fold desc="Constructor messages">


	private DataMessage buildConstructorMessage(UUID concentratorUuid, String className, Context context, Object sender,
																							ObjectRef senderObjRef, String[] parameterTypes, Object[] args,
																							ObjectRef[] argObjRefs) {

		final ConstructorCall.Builder constructorCallBuilder = ConstructorCall.newBuilder();
		if (context != null) {
			final ConstructorSignature codeSignature = (ConstructorSignature) context.getSignature();
			addParameters(constructorCallBuilder, context, args, argObjRefs);
			constructorCallBuilder.setModifiers(codeSignature.getModifiers());
			constructorCallBuilder.setContext(getWrappedContext(context, sender, senderObjRef));
			constructorCallBuilder.setClass_(getWrappedClass(codeSignature.getDeclaringTypeName()));
		} else {
			addParameters(constructorCallBuilder, parameterTypes, args, argObjRefs);
			constructorCallBuilder.setClass_(getWrappedClass(className));
		}

		final DataMessage.Builder msgBuilder = newWrapperBuilder(Type.CONSTRUCTOR, concentratorUuid)
			.setConstructorCall(constructorCallBuilder);

		return msgBuilder.build();
	}

	@Override
	public DataMessage buildEmptyConstructor(UUID concentratorUuid, String className) {

		return buildConstructorMessage(concentratorUuid, className,
			null, null, null, null, null, null);
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
	@Override
	public DataMessage buildNonEmptyConstructor(UUID concentratorUuid, String className, String[] parameterTypes,
																							Object[] args, ObjectRef[] argObjRefs) {

		return buildConstructorMessage(concentratorUuid, className, null, null, null,
			parameterTypes, args, argObjRefs);
	}

	@Override
	public DataMessage buildConstructor(UUID concentratorUuid, Context context, Object sender, ObjectRef senderObjRef,
																			Object[] args, ObjectRef[] argObjRefs) {

		return buildConstructorMessage(concentratorUuid, null, context, sender, senderObjRef,
			null, args, argObjRefs);
	}
	//</editor-fold>

	//<editor-fold desc="Instance method messages">

	@Override
	public DataMessage buildInstanceMethod(UUID concentratorUuid, String className, String methodName, Object target,
																				 ObjectRef targetObjRef, String[] parameterTypes, Object[] args,
																				 ObjectRef[] argObjRefs) {

		final InstanceMethodCall.Builder callBuilder = InstanceMethodCall.newBuilder();
		addParameters(callBuilder, parameterTypes, args, argObjRefs);

		final DataMessage.Builder msgBuilder = newWrapperBuilder(Type.INSTANCE_METHOD, concentratorUuid)
			.setInstanceMethodCall(callBuilder
				.setClass_(getWrappedClass(className))
				.setName(methodName)
				.setObjectRef(targetObjRef.getRef()));

		return msgBuilder.build();
	}

	@Override
	public DataMessage buildInstanceMethod(UUID concentratorUuid, Context context, Object sender, ObjectRef senderObjRef,
																				 Object target, ObjectRef targetObjRef, Object[] args, ObjectRef[] argObjRefs) {

		final MethodSignature codeSignature = (MethodSignature) context.getSignature();

		final InstanceMethodCall.Builder callBuilder = InstanceMethodCall.newBuilder();
		addParameters(callBuilder, context, args, argObjRefs);

		final DataMessage.Builder msgBuilder = newWrapperBuilder(Type.INSTANCE_METHOD, concentratorUuid)
			.setInstanceMethodCall(callBuilder
				.setClass_(getWrappedClass(codeSignature.getDeclaringTypeName()))
				.setName(codeSignature.getName())
				.setObject(getWrappedObject(target, codeSignature.getDeclaringTypeName(), targetObjRef))
				.setModifiers(codeSignature.getModifiers())
				.setContext(getWrappedContext(context, sender, senderObjRef)));

		return msgBuilder.build();
	}
	//</editor-fold>

	//<editor-fold desc="Class method messages">

	@Override
	public DataMessage buildClassMethod(UUID concentratorUuid, String className, String methodName,
																			String[] parameterTypes, Object sender, ObjectRef senderObjRef, Object[] args,
																			ObjectRef[] argObjRefs) {

		final ClassMethodCall.Builder callBuilder = ClassMethodCall.newBuilder();
		addParameters(callBuilder, parameterTypes, args, argObjRefs);

		final DataMessage.Builder msgBuilder = newWrapperBuilder(Type.CLASS_METHOD, concentratorUuid)
			.setClassMethodCall(callBuilder
				.setClass_(getWrappedClass(className))
				.setName(methodName));

		return msgBuilder.build();

	}

	@Override
	public DataMessage buildClassMethod(UUID concentratorUuid, Context context, Object sender, ObjectRef senderObjRef,
																			Object[] args, ObjectRef[] argObjRefs) {

		final MethodSignature codeSignature = (MethodSignature) context.getSignature();

		final ClassMethodCall.Builder callBuilder = ClassMethodCall.newBuilder();
		addParameters(callBuilder, context, args, argObjRefs);

		final DataMessage.Builder msgBuilder = newWrapperBuilder(Type.CLASS_METHOD, concentratorUuid)
			.setClassMethodCall(callBuilder
				.setContext(getWrappedContext(context, sender, senderObjRef))
				.setClass_(getWrappedClass(codeSignature.getDeclaringTypeName()))
				.setName(codeSignature.getName())
				.setModifiers(codeSignature.getModifiers()));

		return msgBuilder.build();
	}
	//</editor-fold>

	//<editor-fold desc="Field Ops generic">
	@Override
	public DataMessage buildFieldOp(UUID concentratorUuid, Context context, Type type, Object sender,
																	ObjectRef senderObjRef, Object target, ObjectRef targetObjRef,
																	Object arg, ObjectRef argObjRef) {

		final FieldSignature fieldSignature = (FieldSignature) context.getSignature();

		Primitives.Class clazz = getWrappedClass(fieldSignature.getDeclaringType());
		Primitives.Object targetObj = getWrappedObject(target, fieldSignature.getDeclaringType(), targetObjRef);
		Primitives.Field field = getWrappedField(fieldSignature.getFieldType(), fieldSignature.getName());
		int modifiers = fieldSignature.getModifiers();
		Ctxt.Context ctxt = getWrappedContext(context, sender, senderObjRef);


		final DataMessage.Builder msgBuilder = newWrapperBuilder(type, concentratorUuid);
		switch (type) {
			case GET_FIELD:
				msgBuilder.setInstanceFieldGet(InstanceFieldGet.newBuilder()
					.setClass_(clazz)
					.setObject(targetObj)
					.setField(field)
					.setModifiers(modifiers)
					.setContext(ctxt));
				break;
			case PUT_FIELD:
				msgBuilder.setInstanceFieldPut(InstanceFieldPut.newBuilder()
					.setClass_(clazz)
					.setObject(targetObj)
					.setField(field)
					.setValueObject(getWrappedObject(arg, fieldSignature.getFieldType().getName(), argObjRef))
					.setModifiers(modifiers)
					.setContext(ctxt));
				break;
			case GET_STATIC:
				msgBuilder.setStaticFieldGet(StaticFieldGet.newBuilder()
					.setClass_(clazz)
					.setField(field)
					.setModifiers(modifiers)
					.setContext(ctxt));
				break;
			case PUT_STATIC:
				msgBuilder.setStaticFieldPut(StaticFieldPut.newBuilder()
					.setClass_(clazz)
					.setValueObject(getWrappedObject(arg, fieldSignature.getFieldType(), argObjRef))
					.setField(field)
					.setModifiers(modifiers)
					.setContext(ctxt));
				break;
			default:
				throw new IllegalArgumentException("Unexpected field op type: " + type);
		}

		return msgBuilder.build();
	}

	@Override
	public DataMessage buildFieldOpDone(UUID concentratorUuid, AccessibleObject accessibleObject,
																			Context context, Type type) {

		final FieldSignature fieldSignature = (FieldSignature) context.getSignature();

		final DataMessage.Builder msgBuilder = newWrapperBuilder(type, concentratorUuid);
		switch (type) {
			case PUT_FIELD_DONE:
				msgBuilder.setInstanceFieldPutDone(InstanceFieldPutDone.newBuilder()
					.setClass_(getWrappedClass(fieldSignature.getDeclaringType()))
					.setField(getWrappedField((Field) accessibleObject)));
				break;
			case PUT_STATIC_DONE:
				msgBuilder.setStaticFieldPutDone(StaticFieldPutDone.newBuilder()
					.setClass_(getWrappedClass(fieldSignature.getDeclaringType()))
					.setField(getWrappedField((Field) accessibleObject)));
				break;
			default:
				throw new IllegalArgumentException("Unexpected field op done type: " + type);
		}

		return msgBuilder.build();
	}
	//</editor-fold>

	//<editor-fold desc="Static field get messages">

	@Override
	public DataMessage buildGetStatic(UUID concentratorUuid, String className, String fieldName) {

		final DataMessage.Builder msgBuilder = newWrapperBuilder(Type.GET_STATIC, concentratorUuid)
			.setStaticFieldGet(StaticFieldGet.newBuilder()
				.setClass_(getWrappedClass(className))
				.setField(getWrappedField(className, fieldName)));

		return msgBuilder.build();
	}

	//</editor-fold>

	//<editor-fold desc="Instance field get messages">

	@Override
	public DataMessage buildGetObject(UUID concentratorUuid, String className, String fieldName, ObjectRef targetObjRef) {

		final DataMessage.Builder msgBuilder = newWrapperBuilder(Type.GET_FIELD, concentratorUuid)
			.setInstanceFieldGet(InstanceFieldGet.newBuilder()
				.setClass_(getWrappedClass(className))
				.setObjectRef(targetObjRef.getRef())
				.setField(getWrappedField((String) null, fieldName)));

		return msgBuilder.build();
	}

	//</editor-fold>

	//<editor-fold desc="Static field put messages">

	@Override
	public DataMessage buildPutStatic(UUID concentratorUuid, String className, String fieldName, String valueClassName,
																		Object value) {

		final DataMessage.Builder msgBuilder = newWrapperBuilder(Type.PUT_STATIC, concentratorUuid)
			.setStaticFieldPut(StaticFieldPut.newBuilder()
				.setClass_(getWrappedClass(className))
				.setField(getWrappedField((String) null, fieldName))
				.setValueObject(getWrappedObject(value, valueClassName, null)));

		return msgBuilder.build();
	}

	@Override
	public DataMessage buildPutStatic(UUID concentratorUuid, String className, String fieldName, ObjectRef valueObjectRef) {

		final DataMessage.Builder msgBuilder = newWrapperBuilder(Type.PUT_STATIC, concentratorUuid)
			.setStaticFieldPut(StaticFieldPut.newBuilder()
				.setClass_(getWrappedClass(className))
				.setField(getWrappedField((String) null, fieldName))
				.setValueObjectRef(valueObjectRef.getRef()));

		return msgBuilder.build();
	}

	@Override
	public DataMessage buildPutStaticDone(UUID concentratorUuid, AccessibleObject accessibleObject,
																				String staticFieldPutUuid, String followingUuid) {

		final StaticFieldPutDone.Builder fieldBuilder = StaticFieldPutDone.newBuilder();
		fieldBuilder.setField(getWrappedField((Field) accessibleObject))
			.setClass_(getWrappedClass(((Field) accessibleObject).getDeclaringClass()))
			.setStaticFieldPutUuid(staticFieldPutUuid);

		final DataMessage.Builder msgBuilder = newWrapperBuilder(Type.PUT_STATIC_DONE, concentratorUuid, followingUuid)
			.setStaticFieldPutDone(fieldBuilder);
		return msgBuilder.build();
	}


	//</editor-fold>

	//<editor-fold desc="Instance field put messages">

	@Override
	public DataMessage buildPutObject(UUID concentratorUuid, String className, String fieldName, ObjectRef targetObjRef,
																		String valueClassName, Object value) {

		final DataMessage.Builder msgBuilder = newWrapperBuilder(Type.PUT_FIELD, concentratorUuid, null)
			.setInstanceFieldPut(InstanceFieldPut.newBuilder()
				.setClass_(getWrappedClass(className))
				.setObjectRef(targetObjRef.getRef())
				.setField(getWrappedField((String) null, fieldName))
				.setValueObject(getWrappedObject(value, valueClassName, null)));

		return msgBuilder.build();
	}

	@Override
	public DataMessage buildPutObject(UUID concentratorUuid, String className, String fieldName, ObjectRef targetObjRef,
																		ObjectRef valueObjectRef) {

		final DataMessage.Builder msgBuilder = newWrapperBuilder(Type.PUT_FIELD, concentratorUuid, null)
			.setInstanceFieldPut(InstanceFieldPut.newBuilder()
				.setClass_(getWrappedClass(className))
				.setObjectRef(targetObjRef.getRef())
				.setField(getWrappedField((String) null, fieldName))
				.setValueObjectRef(valueObjectRef.getRef()));

		return msgBuilder.build();
	}

	@Override
	public DataMessage buildPutObjectDone(UUID concentratorUuid, AccessibleObject accessibleObject,
																				String instanceFieldPutUuid, String followingUuid) {

		final Fields.InstanceFieldPutDone.Builder fieldBuilder = InstanceFieldPutDone.newBuilder();
		fieldBuilder.setField(getWrappedField((Field) accessibleObject))
			.setClass_(getWrappedClass(((Field) accessibleObject).getDeclaringClass()))
			.setInstanceFieldPutUuid(instanceFieldPutUuid);

		final DataMessage.Builder msgBuilder = newWrapperBuilder(Type.PUT_FIELD_DONE, concentratorUuid, followingUuid)
			.setInstanceFieldPutDone(fieldBuilder);

		return msgBuilder.build();
	}

	//</editor-fold>

	//<editor-fold desc="Throwable messages">
	@Override
	public DataMessage buildAccessibleObjectThrowable(UUID concentratorUuid, Optional<AccessibleObject> accessibleObject,
																										ExecutableObjectType executableObjectType, Throwable exception,
																										String followingUuid) {

		final Exceptions.RaisedThrowable.Builder thrBuilder = Exceptions.RaisedThrowable.newBuilder();
		if (accessibleObject.isPresent()) {
			if (accessibleObject.get() instanceof Constructor) {
				thrBuilder.setConstructor(((Constructor) accessibleObject.get()).getDeclaringClass().getName());
				thrBuilder.setModifiers(((Constructor) accessibleObject.get()).getModifiers());
			} else if (accessibleObject.get() instanceof Method) {
				thrBuilder.setMethod(((Method) accessibleObject.get()).getName());
				thrBuilder.setModifiers(((Method) accessibleObject.get()).getModifiers());
			} else if (accessibleObject.get() instanceof Field) {
				thrBuilder.setField(((Field) accessibleObject.get()).getName());
				thrBuilder.setModifiers(((Field) accessibleObject.get()).getModifiers());
			} else {
				throw new UnsupportedOperationException(String.format("Unsupported accessibleObject type: %s",
					accessibleObject.getClass().getName()));
			}
		} else {
			switch (executableObjectType) {
				case CONSTRUCTOR:
					thrBuilder.setConstructor("<info not available>");
					break;
				case METHOD:
					thrBuilder.setMethod("<info not available>");
					break;
				case FIELD:
					thrBuilder.setField("<info not available>");
					break;
			}
		}

		final DataMessage.Builder msgBuilder = newWrapperBuilder(Type.THROWABLE, concentratorUuid, followingUuid)
			.setRaisedThrowable(thrBuilder
				.setClass_(getWrappedClass(exception.getClass().getName()))
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
	@Override
	public DataMessage buildReturnValue(UUID concentratorUuid, Object object, AccessibleObject accessibleObject,
																			ObjectRef objectRef, boolean isVoid, String followingUuid) {

		final ReturnValue.Builder valueBuilder = Values.ReturnValue.newBuilder();

		Class declaringClass = ((Member) accessibleObject).getDeclaringClass();

		// set 'object'
		if (!isVoid) {
			Class objectClass;
			if (accessibleObject instanceof Constructor) {
				objectClass = declaringClass;
			} else if (accessibleObject instanceof Method) {
				objectClass = ((Method) accessibleObject).getReturnType();
			} else if (accessibleObject instanceof Field) {
				objectClass = ((Field) accessibleObject).getType();
			} else {
				throw new RuntimeException(String.format("Unable to handle accessible object of type: %s", accessibleObject));
			}
			valueBuilder.setObject(getWrappedObject(object, objectClass, objectRef));
		}

		// set 'from'
		if (accessibleObject instanceof Constructor) {
			valueBuilder.setFrom(Primitives.Reflectable.newBuilder().setConstructor(Primitives.Constructor.newBuilder()
				.setRepr(((Executable) accessibleObject).toGenericString()).build()));
		} else if (accessibleObject instanceof Method) {
			valueBuilder.setFrom(Primitives.Reflectable.newBuilder().setMethod(Primitives.Method.newBuilder()
				.setRepr(((Executable) accessibleObject).toGenericString()).build()));
		} else if (accessibleObject instanceof Field) {
			valueBuilder.setFrom(Primitives.Reflectable.newBuilder().setField(Primitives.Field.newBuilder()
				.setName(((Field) accessibleObject).getName())
				.setRepr(((Field) accessibleObject).toGenericString()).build()));
		} else {
			throw new RuntimeException(String.format("Unable to handle accessible object of type: %s", accessibleObject));
		}

		// set 'class'
		final DataMessage.Builder msgBuilder = newWrapperBuilder(Type.RETURN_VALUE, concentratorUuid, followingUuid)
			.setReturnValue(valueBuilder
				.setIsVoid(isVoid)
				.setClazz(getWrappedClass(declaringClass)));

		return msgBuilder.build();
	}

	//</editor-fold>
}
