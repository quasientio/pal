package com.ittera.cometa.concentrator.messages;

import com.ittera.cometa.concentrator.messages.protobuf.data.Fields;
import com.ittera.cometa.concentrator.messages.protobuf.data.Wrappers.DataMessage;

import org.aspectj.lang.JoinPoint.StaticPart;

import java.lang.reflect.AccessibleObject;

/**
 * TODO: This interface should NOT depend on protobuf nor aspectj classes!
 * We should encapsulate these classes in our own types and pass those around
 **/
public interface DataMessageBuilder {
  DataMessage buildClassInitializer(int concentratorId, StaticPart staticPart, Object sender);

  DataMessage buildLoadedClass(int concentratorId, Class clazz);

  DataMessage buildEmptyConstructor(String concentratorId, String className);

  DataMessage buildNonEmptyConstructor(String concentratorId, String className, String[] parameterTypes, Object[] args, String[] argObjRefs);

  DataMessage buildConstructor(int concentratorId, StaticPart staticPart, Object sender, Object[] args);

  DataMessage buildInstanceMethod(String concentratorId, String className, String methodName, String objRef, String[] parameterTypes, Object[] args, String[] argObjRefs);

  DataMessage buildInstanceMethod(int concentratorId, StaticPart staticPart, Object sender, Object target, Object[] args);

  DataMessage buildClassMethod(String concentratorId, String className, String methodName, String[] parameterTypes, Object[] args, String[] argObjRefs);

  DataMessage buildClassMethod(int concentratorId, StaticPart staticPart, Object sender, Object[] args);

  DataMessage buildGetStatic(String concentratorId, String className, String fieldName);

  DataMessage buildGetStatic(int concentratorId, StaticPart staticPart, Object sender);

  DataMessage buildGetObject(String concentratorId, String className, String fieldName, String targetObjRef);

  DataMessage buildGetObject(int concentratorId, StaticPart staticPart, Object sender, Object target);

  DataMessage buildPutStatic(String concentratorId, String className, String fieldName, String valueClassName, Object value);

  DataMessage buildPutStatic(String concentratorId, String className, String fieldName, String objectRef);

  DataMessage buildPutStatic(int concentratorId, StaticPart staticPart, Object sender, Object arg);

  DataMessage buildPutStaticDone(int concentratorId, Fields.StaticFieldPut staticFieldPut, Class fieldType, Long followingOffset);

  DataMessage buildPutStaticDone(int concentratorId, StaticPart staticPart, Object sender, Object arg);

  DataMessage buildPutObject(String concentratorId, String className, String fieldName, String targetObjRef, String valueClassName, Object value);

  DataMessage buildPutObject(String concentratorId, String className, String fieldName, String targetObjRef, String valueObjRef);

  DataMessage buildPutObject(int concentratorId, StaticPart staticPart, Object sender, Object target, Object arg);

  DataMessage buildPutObjectDone(int concentratorId, Fields.InstanceFieldPut instanceFieldPut, Class fieldType, Long followingOffset);

  DataMessage buildPutObjectDone(int concentratorId, StaticPart staticPart, Object sender, Object target, Object arg);

  DataMessage buildAccessibleObjectThrowable(int concentratorId, AccessibleObject accessibleObject, Exception exception, Long followingOffset);

  DataMessage buildInitializerThrowable(int concentratorId, StaticPart staticPart, Exception exception);

  DataMessage buildReturnValue(int concentratorId, Object object, Class type, String objectKey, boolean isVoid, Long followingOffset);
}
