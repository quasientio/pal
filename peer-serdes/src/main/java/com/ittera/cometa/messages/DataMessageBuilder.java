package com.ittera.cometa.messages;

import com.ittera.cometa.messages.protobuf.data.Fields;
import com.ittera.cometa.messages.protobuf.data.Wrappers.Type;
import com.ittera.cometa.messages.protobuf.data.Wrappers.DataMessage;

import com.ittera.cometa.common.lang.Context;

import java.util.UUID;

import java.lang.reflect.AccessibleObject;

public interface DataMessageBuilder {

	DataMessage buildEmptyConstructor(UUID concentratorUuid, String className);

	DataMessage buildNonEmptyConstructor(UUID concentratorUuid, String className, String[] parameterTypes, Object[] args,
																			 String[] argObjRefs);

	DataMessage buildConstructor(UUID concentratorUuid, Context context, Object sender, Object[] args);

	DataMessage buildInstanceMethod(UUID concentratorUuid, String className, String methodName, String objRef,
																	String[] parameterTypes, Object[] args, String[] argObjRefs);

	DataMessage buildInstanceMethod(UUID concentratorUuid, Context context, Object sender, Object target, Object[] args);

	DataMessage buildClassMethod(UUID concentratorUuid, String className, String methodName, String[] parameterTypes,
															 Object[] args, String[] argObjRefs);

	DataMessage buildClassMethod(UUID concentratorUuid, Context context, Object sender, Object[] args);

	DataMessage buildFieldOp(UUID concentratorUuid, Context context, Type type, Object sender, Object target, Object arg);

	DataMessage buildGetStatic(UUID concentratorUuid, String className, String fieldName);

	DataMessage buildGetObject(UUID concentratorUuid, String className, String fieldName, String targetObjRef);

	DataMessage buildPutStatic(UUID concentratorUuid, String className, String fieldName, String valueClassName,
														 Object value);

	DataMessage buildFieldOpDone(UUID concentratorUuid, Context context, Type type);

	DataMessage buildPutStaticDone(UUID concentratorUuid, String staticFieldPutUuid, Fields.StaticFieldPut staticFieldPut,
																 Class fieldType, String followingUuid);

	DataMessage buildPutObject(UUID concentratorUuid, String className, String fieldName, String targetObjRef,
														 String valueClassName, Object value);

	DataMessage buildPutObjectDone(UUID concentratorUuid, String instanceFieldPutUuid,
																 Fields.InstanceFieldPut instanceFieldPut, Class fieldType, String followingUuid);

	DataMessage buildAccessibleObjectThrowable(UUID concentratorUuid, AccessibleObject accessibleObject,
																						 Exception exception, String followingUuid);

	DataMessage buildReturnValue(UUID concentratorUuid, Object object, Class type, String objectKey, boolean isVoid,
															 String followingUuid);

	void dontStoreObjects();

	void resetThreadLocalSequence();
}
