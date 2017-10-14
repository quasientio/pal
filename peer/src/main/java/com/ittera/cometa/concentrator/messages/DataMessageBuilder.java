package com.ittera.cometa.concentrator.messages;

import com.ittera.cometa.concentrator.messages.protobuf.data.Fields;
import com.ittera.cometa.concentrator.messages.protobuf.data.Wrappers.DataMessage;

import org.aspectj.lang.JoinPoint.StaticPart;

import java.util.UUID;
import java.lang.reflect.AccessibleObject;

/**
 * TODO: This interface should NOT depend on protobuf nor aspectj classes!
 * We should encapsulate these classes in our own types and pass those around
 **/
public interface DataMessageBuilder {
    DataMessage buildClassInitializer(UUID concentratorUuid, StaticPart staticPart, Object sender);

    DataMessage buildLoadedClass(UUID concentratorUuid, Class clazz);

    DataMessage buildEmptyConstructor(UUID concentratorUuid, String className);

    DataMessage buildNonEmptyConstructor(UUID concentratorUuid, String className, String[] parameterTypes, Object[] args, String[] argObjRefs);

    DataMessage buildConstructor(UUID concentratorUuid, StaticPart staticPart, Object sender, Object[] args);

    DataMessage buildInstanceMethod(UUID concentratorUuid, String className, String methodName, String objRef, String[] parameterTypes, Object[] args, String[] argObjRefs);

    DataMessage buildInstanceMethod(UUID concentratorUuid, StaticPart staticPart, Object sender, Object target, Object[] args);

    DataMessage buildClassMethod(UUID concentratorUuid, String className, String methodName, String[] parameterTypes, Object[] args, String[] argObjRefs);

    DataMessage buildClassMethod(UUID concentratorUuid, StaticPart staticPart, Object sender, Object[] args);

    DataMessage buildGetStatic(UUID concentratorUuid, String className, String fieldName);

    DataMessage buildGetStatic(UUID concentratorUuid, StaticPart staticPart, Object sender);

    DataMessage buildGetObject(UUID concentratorUuid, String className, String fieldName, String targetObjRef);

    DataMessage buildGetObject(UUID concentratorUuid, StaticPart staticPart, Object sender, Object target);

    DataMessage buildPutStatic(UUID concentratorUuid, String className, String fieldName, String valueClassName, Object value);

    DataMessage buildPutStatic(UUID concentratorUuid, String className, String fieldName, String objectRef);

    DataMessage buildPutStatic(UUID concentratorUuid, StaticPart staticPart, Object sender, Object arg);

    DataMessage buildPutStaticDone(UUID concentratorUuid, String staticFieldPutUuid, Fields.StaticFieldPut staticFieldPut, Class fieldType, String followingUuid);

    DataMessage buildPutStaticDone(UUID concentratorUuid, StaticPart staticPart, Object sender, Object arg);

    DataMessage buildPutObject(UUID concentratorUuid, String className, String fieldName, String targetObjRef, String valueClassName, Object value);

    DataMessage buildPutObject(UUID concentratorUuid, String className, String fieldName, String targetObjRef, String valueObjRef);

    DataMessage buildPutObject(UUID concentratorUuid, StaticPart staticPart, Object sender, Object target, Object arg);

    DataMessage buildPutObjectDone(UUID concentratorUuid, String instanceFieldPutUuid, Fields.InstanceFieldPut instanceFieldPut, Class fieldType, String followingUuid);

    DataMessage buildPutObjectDone(UUID concentratorUuid, StaticPart staticPart, Object sender, Object target, Object arg);

    DataMessage buildAccessibleObjectThrowable(UUID concentratorUuid, AccessibleObject accessibleObject, Exception exception, String followingUuid);

    DataMessage buildInitializerThrowable(UUID concentratorUuid, StaticPart staticPart, Exception exception);

    DataMessage buildReturnValue(UUID concentratorUuid, Object object, Class type, String objectKey, boolean isVoid, String followingUuid);
}
