package com.ittera.cometa.messages;

import com.ittera.cometa.common.lang.Context;
import com.ittera.cometa.common.lang.ObjectRef;
import com.ittera.cometa.common.lang.reflect.AccessibleObjectType;

import com.ittera.cometa.messages.protobuf.data.Fields;
import com.ittera.cometa.messages.protobuf.data.Wrappers.Type;
import com.ittera.cometa.messages.protobuf.data.Wrappers.DataMessage;

import java.util.UUID;
import java.util.Optional;

import java.lang.reflect.AccessibleObject;

public interface DataMessageBuilder {

	/**
	 * constructor builders
	 */
	DataMessage buildEmptyConstructor(UUID concentratorUuid, String className);

	DataMessage buildNonEmptyConstructor(UUID concentratorUuid, String className, String[] parameterTypes, Object[] args,
																			 ObjectRef[] argObjRefs);

	DataMessage buildConstructor(UUID concentratorUuid, Context context, Object sender, ObjectRef senderObjRef,
															 Object[] args, ObjectRef[] argObjRefs);

	/**
	 * instance method builders
	 */
	DataMessage buildInstanceMethod(UUID concentratorUuid, String className, String methodName, Object target,
																	ObjectRef targetObjRef, String[] parameterTypes, Object[] args,
																	ObjectRef[] argObjRefs);

	DataMessage buildInstanceMethod(UUID concentratorUuid, Context context, Object sender, ObjectRef senderObjRef,
																	Object target, ObjectRef targetObjRef, Object[] args, ObjectRef[] argObjRefs);

	/**
	 * class method builders
	 */
	DataMessage buildClassMethod(UUID concentratorUuid, String className, String methodName, String[] parameterTypes,
															 Object sender, ObjectRef senderObjRef, Object[] args, ObjectRef[] argObjRefs);

	DataMessage buildClassMethod(UUID concentratorUuid, Context context, Object sender, ObjectRef senderObjRef,
															 Object[] args, ObjectRef[] argObjRefs);

	/**
	 * field op builders
	 */
	DataMessage buildFieldOp(UUID concentratorUuid, Context context, Type type, Object sender,
													 ObjectRef senderObjRef, Object target, ObjectRef targetObjRef, Object arg,
													 ObjectRef argObjRef);

	DataMessage buildGetStatic(UUID concentratorUuid, String className, String fieldName);

	DataMessage buildGetObject(UUID concentratorUuid, String className, String fieldName, ObjectRef targetObjRef);

	DataMessage buildPutStatic(UUID concentratorUuid, String className, String fieldName, String valueClassName,
														 Object value);

	DataMessage buildPutStatic(UUID concentratorUuid, String className, String fieldName, ObjectRef valueObjectRef);

	DataMessage buildPutObject(UUID concentratorUuid, String className, String fieldName, ObjectRef targetObjRef,
														 String valueClassName, Object value);

	DataMessage buildPutObject(UUID concentratorUuid, String className, String fieldName, ObjectRef targetObjRef,
														 ObjectRef valueObjectRef);

	/**
	 * field op done builders
	 */
	DataMessage buildFieldOpDone(UUID concentratorUuid, Context context, Type type);

	DataMessage buildPutStaticDone(UUID concentratorUuid, String staticFieldPutUuid, Fields.StaticFieldPut staticFieldPut,
																 Class fieldType, String followingUuid);


	DataMessage buildPutObjectDone(UUID concentratorUuid, String instanceFieldPutUuid,
																 Fields.InstanceFieldPut instanceFieldPut, Class fieldType, String followingUuid);

	/**
	 * return builders
	 */
	DataMessage buildAccessibleObjectThrowable(UUID concentratorUuid, Optional<AccessibleObject> accessibleObject,
																						 AccessibleObjectType type, Throwable throwable, String followingUuid);

	DataMessage buildReturnValue(UUID concentratorUuid, Object object, Class type, ObjectRef objectRef, boolean isVoid,
															 String followingUuid);

	/**
	 * other
	 */
	void resetThreadLocalSequence();
}
