package com.ittera.cometa.messages;

import com.ittera.cometa.common.lang.Context;
import com.ittera.cometa.common.lang.ObjectRef;
import com.ittera.cometa.common.lang.reflect.ExecutableObjectType;

import com.ittera.cometa.messages.protobuf.data.Wrappers;
import com.ittera.cometa.messages.protobuf.data.Wrappers.Type;
import com.ittera.cometa.messages.protobuf.data.Wrappers.ExecMessage;

import java.util.UUID;
import java.util.Optional;

import java.lang.reflect.AccessibleObject;

public interface ExecMessageBuilder {

	/**
	 * header builders
	 */
	Wrappers.InternalHeader buildWriteAheadHeader(UUID concentratorUuid);

	/**
	 * constructor builders
	 */
	ExecMessage buildEmptyConstructor(UUID concentratorUuid, String className);

	ExecMessage buildNonEmptyConstructor(UUID concentratorUuid, String className, String[] parameterTypes, Object[] args,
																			 ObjectRef[] argObjRefs);

	ExecMessage buildConstructor(UUID concentratorUuid, Context context, Object sender, ObjectRef senderObjRef,
															 Object[] args, ObjectRef[] argObjRefs);

	/**
	 * instance method builders
	 */
	ExecMessage buildInstanceMethod(UUID concentratorUuid, String className, String methodName, Object target,
																	ObjectRef targetObjRef, String[] parameterTypes, Object[] args,
																	ObjectRef[] argObjRefs);

	ExecMessage buildInstanceMethod(UUID concentratorUuid, Context context, Object sender, ObjectRef senderObjRef,
																	Object target, ObjectRef targetObjRef, Object[] args, ObjectRef[] argObjRefs);

	/**
	 * class method builders
	 */
	ExecMessage buildClassMethod(UUID concentratorUuid, String className, String methodName, String[] parameterTypes,
															 Object sender, ObjectRef senderObjRef, Object[] args, ObjectRef[] argObjRefs);

	ExecMessage buildClassMethod(UUID concentratorUuid, Context context, Object sender, ObjectRef senderObjRef,
															 Object[] args, ObjectRef[] argObjRefs);

	/**
	 * field op builders
	 */
	ExecMessage buildFieldOp(UUID concentratorUuid, Context context, Type type, Object sender,
													 ObjectRef senderObjRef, Object target, ObjectRef targetObjRef, Object arg,
													 ObjectRef argObjRef);

	ExecMessage buildGetStatic(UUID concentratorUuid, String className, String fieldName);

	ExecMessage buildGetObject(UUID concentratorUuid, String className, String fieldName, ObjectRef targetObjRef);

	ExecMessage buildPutStatic(UUID concentratorUuid, String className, String fieldName, String valueClassName,
														 Object value);

	ExecMessage buildPutStatic(UUID concentratorUuid, String className, String fieldName, ObjectRef valueObjectRef);

	ExecMessage buildPutObject(UUID concentratorUuid, String className, String fieldName, ObjectRef targetObjRef,
														 String valueClassName, Object value);

	ExecMessage buildPutObject(UUID concentratorUuid, String className, String fieldName, ObjectRef targetObjRef,
														 ObjectRef valueObjectRef);

	/**
	 * field op done builders
	 */
	ExecMessage buildFieldOpDone(UUID concentratorUuid, AccessibleObject accessibleObject, Context context, Type type);

	ExecMessage buildPutStaticDone(UUID concentratorUuid, AccessibleObject accessibleObject, String staticFieldPutUuid,
																 String followingUuid);


	ExecMessage buildPutObjectDone(UUID concentratorUuid, AccessibleObject accessibleObject, String instanceFieldPutUuid,
																 String followingUuid);

	/**
	 * return builders
	 */
	ExecMessage buildAccessibleObjectThrowable(UUID concentratorUuid, Optional<AccessibleObject> accessibleObject,
																						 ExecutableObjectType type, Throwable throwable, String followingUuid);

	ExecMessage buildReturnValue(UUID concentratorUuid, Object object, AccessibleObject accessibleObject,
															 ObjectRef objectRef, boolean isVoid, String followingUuid);

	/**
	 * other
	 */
	void resetThreadLocalSequence();
}
