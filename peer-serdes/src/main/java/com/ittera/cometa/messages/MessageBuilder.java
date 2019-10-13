package com.ittera.cometa.messages;

import com.ittera.cometa.common.lang.Context;
import com.ittera.cometa.common.lang.ObjectRef;
import com.ittera.cometa.common.lang.reflect.ExecutableObjectType;

import com.ittera.cometa.messages.protobuf.data.Wrappers;
import com.ittera.cometa.messages.protobuf.data.Wrappers.Type;
import com.ittera.cometa.messages.protobuf.data.Wrappers.ExecMessage;
import com.ittera.cometa.messages.protobuf.data.Wrappers.InterceptRequest;

import java.util.UUID;
import java.util.Optional;

import java.lang.reflect.AccessibleObject;

public interface MessageBuilder {

	/**
	 * header builders
	 */
	Wrappers.InternalHeader buildWriteAheadHeader(UUID peerUuid);

	/**
	 * constructor builders
	 */
	ExecMessage buildEmptyConstructor(UUID peerUuid, String className);

	ExecMessage buildNonEmptyConstructor(UUID peerUuid, String className, String[] parameterTypes, Object[] args,
																			 ObjectRef[] argObjRefs);

	ExecMessage buildConstructor(UUID peerUuid, Context context, Object sender, ObjectRef senderObjRef,
															 Object[] args, ObjectRef[] argObjRefs);

	/**
	 * instance method builders
	 */
	ExecMessage buildInstanceMethod(UUID peerUuid, String className, String methodName, Object target,
																	ObjectRef targetObjRef, String[] parameterTypes, Object[] args,
																	ObjectRef[] argObjRefs);

	ExecMessage buildInstanceMethod(UUID peerUuid, Context context, Object sender, ObjectRef senderObjRef,
																	Object target, ObjectRef targetObjRef, Object[] args, ObjectRef[] argObjRefs);

	/**
	 * class method builders
	 */
	ExecMessage buildClassMethod(UUID peerUuid, String className, String methodName, String[] parameterTypes,
															 Object sender, ObjectRef senderObjRef, Object[] args, ObjectRef[] argObjRefs);

	ExecMessage buildClassMethod(UUID peerUuid, Context context, Object sender, ObjectRef senderObjRef,
															 Object[] args, ObjectRef[] argObjRefs);

	/**
	 * field op builders
	 */
	ExecMessage buildFieldOp(UUID peerUuid, Context context, Type type, Object sender,
													 ObjectRef senderObjRef, Object target, ObjectRef targetObjRef, Object arg,
													 ObjectRef argObjRef);

	ExecMessage buildGetStatic(UUID peerUuid, String className, String fieldName);

	ExecMessage buildGetObject(UUID peerUuid, String className, String fieldName, ObjectRef targetObjRef);

	ExecMessage buildPutStatic(UUID peerUuid, String className, String fieldName, String valueClassName,
														 Object value);

	ExecMessage buildPutStatic(UUID peerUuid, String className, String fieldName, ObjectRef valueObjectRef);

	ExecMessage buildPutObject(UUID peerUuid, String className, String fieldName, ObjectRef targetObjRef,
														 String valueClassName, Object value);

	ExecMessage buildPutObject(UUID peerUuid, String className, String fieldName, ObjectRef targetObjRef,
														 ObjectRef valueObjectRef);

	/**
	 * field op done builders
	 */
	ExecMessage buildFieldOpDone(UUID peerUuid, AccessibleObject accessibleObject, Context context, Type type);

	ExecMessage buildPutStaticDone(UUID peerUuid, AccessibleObject accessibleObject, String staticFieldPutUuid,
																 String followingUuid);


	ExecMessage buildPutObjectDone(UUID peerUuid, AccessibleObject accessibleObject, String instanceFieldPutUuid,
																 String followingUuid);

	/**
	 * return builders
	 */
	ExecMessage buildAccessibleObjectThrowable(UUID peerUuid, Optional<AccessibleObject> accessibleObject,
																						 ExecutableObjectType type, Throwable throwable, String followingUuid);

	ExecMessage buildReturnValue(UUID peerUuid, Object object, AccessibleObject accessibleObject,
															 ObjectRef objectRef, boolean isVoid, String followingUuid);

	InterceptRequest buildInterceptRequest(UUID peerUuid, String className, String methodName, String fieldName,
																				 String callbackClassName, String callbackMethodName);

	/**
	 * other
	 */
	void resetThreadLocalSequence();
}
