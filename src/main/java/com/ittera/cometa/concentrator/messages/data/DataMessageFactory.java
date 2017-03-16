package com.ittera.cometa.concentrator.messages.data;

import com.ittera.cometa.concentrator.messages.data.Wrappers.DataMessage;
import com.ittera.cometa.concentrator.messages.data.Fields.*;
import com.ittera.cometa.concentrator.messages.data.Calls.*;
import com.ittera.cometa.concentrator.messages.data.Values.*;

import org.aspectj.runtime.reflect.FieldSignatureImpl;
import org.aspectj.lang.reflect.ConstructorSignature;
import org.aspectj.lang.reflect.MethodSignature;
import org.aspectj.lang.reflect.InitializerSignature;
import org.aspectj.lang.JoinPoint.StaticPart;

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Array;

import org.apache.commons.lang3.ClassUtils;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;


/**
 * Methods of this class receive aspectj objects (i.e. StaticPart) as arguments as convenience.
 * TODO: Unwrapp the necessary arguments in the caller (Concentrator) to make this class agnostic
 */
public final class DataMessageFactory {
  protected static final Logger logger = LogManager.getLogger(DataMessageFactory.class);

  private DataMessageFactory() {
    //avoid instantiation
  }

  //<editor-fold desc="Class initialization messages">
  public static DataMessage buildClassInitializerMessage(int concentratorId, StaticPart staticPart, Object sender) {

    final InitializerSignature codeSignature = (InitializerSignature) staticPart.getSignature();

    final DataMessage.Builder msgBuilder = DataMessage.newBuilder()
      .setThreadId(Thread.currentThread().getId())
      .setMsgType("Static Constructor")
      .setClinitCall(ClInitCall.newBuilder()
          .setConcentratorId(concentratorId)
          .setThreadId(Thread.currentThread().getId())
          .setCurrentTime(System.currentTimeMillis())
          .setClass_(DataMessageWrapper.getWrappedClass(codeSignature.getDeclaringTypeName()))
          .setModifiers(codeSignature.getModifiers())
          .setContext(DataMessageWrapper.getWrappedContext(staticPart, sender)));

    return msgBuilder.build();
  }

  public static DataMessage buildLoadedClassMessage(int concentratorId, Class clazz) {

    final DataMessage.Builder msgBuilder = DataMessage.newBuilder()
      .setThreadId(Thread.currentThread().getId())
      .setMsgType("Return class")
      .setReturnValue(ReturnValue.newBuilder()
          .setConcentratorId(concentratorId)
          .setThreadId(Thread.currentThread().getId())
          .setCurrentTime(System.currentTimeMillis())
          .setIsClass(true)
          .setClazz(DataMessageWrapper.getWrappedClass(clazz.getName())));

    return msgBuilder.build();
  }

  //</editor-fold>

  //<editor-fold desc="Constructor messages">
  /**
   * This method is to be called when no joinpoint context is available.
   */
  public static DataMessage buildEmptyConstructorMessage(String concentratorId, String className) {

    final DataMessage.Builder msgBuilder = DataMessage.newBuilder()
      .setThreadId(Thread.currentThread().getId())
      .setMsgType("Constructor")
      .setConstructorCall(ConstructorCall.newBuilder()
          .setConcentratorId(Integer.parseInt(concentratorId))
          .setThreadId(Thread.currentThread().getId())
          .setCurrentTime(System.currentTimeMillis())
          .setClass_(DataMessageWrapper.getWrappedClass(className)));

    return msgBuilder.build();
  }

  /**
   * Args must be set either in args or argObjRefs. If null in both, value is assumed to be null.
   *
   * @param concentratorId
   * @param className
   * @param parameterTypes
   * @param args           Should be of same length as parameterTypes. For Strings, primitives and wrappers.
   * @param argObjRefs     Should be of same length as parameterTypes. For objectrefs.
   * @return
   */
  public static DataMessage buildNonEmptyConstructorMessage(String concentratorId, String className, String[] parameterTypes, Object[] args, String[] argObjRefs) {

    final ConstructorCall.Builder callBuilder = ConstructorCall.newBuilder();
    for (int i = 0; i < parameterTypes.length; i++) {
      if (argObjRefs[i] != null) {
        //parameter is an objectref
        callBuilder.addParameter(DataMessageWrapper.getWrappedObject(null, parameterTypes[i], argObjRefs[i]));
      } else if (args[i] != null) {
        //parameter is string, primitive or wrapper
        callBuilder.addParameter(DataMessageWrapper.getWrappedObject(args[i], parameterTypes[i], null));
      } else {
        //parameter is null
        callBuilder.addParameter(DataMessageWrapper.getWrappedObject(null, parameterTypes[i], null));
      }
    }

    final DataMessage.Builder msgBuilder = DataMessage.newBuilder()
      .setThreadId(Thread.currentThread().getId())
      .setMsgType("Constructor")
      .setConstructorCall(callBuilder
          .setConcentratorId(Integer.parseInt(concentratorId))
          .setThreadId(Thread.currentThread().getId())
          .setCurrentTime(System.currentTimeMillis())
          .setClass_(DataMessageWrapper.getWrappedClass(className)));

    return msgBuilder.build();

  }

  public static DataMessage buildConstructorMessage(int concentratorId, StaticPart staticPart, Object sender, Object[] args) {

    final ConstructorSignature codeSignature = (ConstructorSignature) staticPart.getSignature();

    final ConstructorCall.Builder callBuilder = ConstructorCall.newBuilder();
    for (String name : codeSignature.getParameterNames()) {
      callBuilder.addParameterName(name);
    }
    for (Class clazz : codeSignature.getExceptionTypes()) {
      callBuilder.addExceptionType(DataMessageWrapper.getWrappedClass(clazz.getName()));
    }
    for (int i = 0; i < args.length; i++) {
      callBuilder.addParameter(DataMessageWrapper.getWrappedObject(args[i], codeSignature.getParameterTypes()[i].getName(), null));
    }

    final DataMessage.Builder msgBuilder = DataMessage.newBuilder()
      .setThreadId(Thread.currentThread().getId())
      .setMsgType("Constructor")
      .setConstructorCall(callBuilder
          .setConcentratorId(concentratorId)
          .setThreadId(Thread.currentThread().getId())
          .setCurrentTime(System.currentTimeMillis())
          .setClass_(DataMessageWrapper.getWrappedClass(codeSignature.getDeclaringTypeName()))
          .setModifiers(codeSignature.getModifiers())
          .setContext(DataMessageWrapper.getWrappedContext(staticPart, sender)));

    return msgBuilder.build();
  }
  //</editor-fold>

  //<editor-fold desc="Instance method messages">
  /**
   * This method is to be called when no joinpoint context is available.
   */
  public static DataMessage buildInstanceMethodMessage(String concentratorId, String className, String methodName, String objRef, String[] parameterTypes, Object[] args, String[] argObjRefs) {

    final InstanceMethodCall.Builder callBuilder = InstanceMethodCall.newBuilder();

    for (int i = 0; i < parameterTypes.length; i++) {
      if (argObjRefs[i] != null) {
        //parameter is an objectref
        callBuilder.addParameter(DataMessageWrapper.getWrappedObject(null, parameterTypes[i], argObjRefs[i]));
      } else if (args[i] != null) {
        //parameter is string, primitive or wrapper
        callBuilder.addParameter(DataMessageWrapper.getWrappedObject(args[i], parameterTypes[i], null));
      } else {
        //parameter is null
        callBuilder.addParameter(DataMessageWrapper.getWrappedObject(null, parameterTypes[i], null));
      }
    }

    final DataMessage.Builder msgBuilder = DataMessage.newBuilder()
      .setThreadId(Thread.currentThread().getId())
      .setMsgType("Instance method")
      .setInstanceMethodCall(callBuilder
          .setConcentratorId(Integer.parseInt(concentratorId))
          .setThreadId(Thread.currentThread().getId())
          .setCurrentTime(System.currentTimeMillis())
          .setClass_(DataMessageWrapper.getWrappedClass(className))
          .setName(methodName)
          .setObjectRef(objRef));

    return msgBuilder.build();
  }


  public static DataMessage buildInstanceMethodMessage(int concentratorId, StaticPart staticPart, Object sender, Object target, Object[] args) {

    final MethodSignature codeSignature = (MethodSignature) staticPart.getSignature();

    final InstanceMethodCall.Builder callBuilder = InstanceMethodCall.newBuilder();
    for (String name : codeSignature.getParameterNames()) {
      callBuilder.addParameterName(name);
    }
    for (Class clazz : codeSignature.getExceptionTypes()) {
      callBuilder.addExceptionType(DataMessageWrapper.getWrappedClass(clazz.getName()));
    }
    for (int i = 0; i < args.length; i++) {
      callBuilder.addParameter(DataMessageWrapper.getWrappedObject(args[i], codeSignature.getParameterTypes()[i].getName(), null));
    }

    final DataMessage.Builder msgBuilder = DataMessage.newBuilder()
      .setThreadId(Thread.currentThread().getId())
      .setMsgType("Instance method")
      .setInstanceMethodCall(callBuilder
          .setConcentratorId(concentratorId)
          .setThreadId(Thread.currentThread().getId())
          .setCurrentTime(System.currentTimeMillis())
          .setClass_(DataMessageWrapper.getWrappedClass(codeSignature.getDeclaringTypeName()))
          .setName(codeSignature.getName())
          .setObject(DataMessageWrapper.getWrappedObject(target, codeSignature.getDeclaringTypeName(), null))
          .setModifiers(codeSignature.getModifiers())
          .setContext(DataMessageWrapper.getWrappedContext(staticPart, sender)));

    return msgBuilder.build();
  }
  //</editor-fold>

  //<editor-fold desc="Class method messages">
  /**
   * This method is to be called when no joinpoint context is available.
   */
  public static DataMessage buildClassMethodMessage(String concentratorId, String className, String methodName, String[] parameterTypes, Object[] args, String[] argObjRefs) {

    final ClassMethodCall.Builder callBuilder = ClassMethodCall.newBuilder();
    for (int i = 0; i < parameterTypes.length; i++) {
      if (argObjRefs[i] != null) {
        //parameter is an objectref
        callBuilder.addParameter(DataMessageWrapper.getWrappedObject(null, parameterTypes[i], argObjRefs[i]));
      } else if (args[i] != null) {
        //parameter is string, primitive or wrapper
        callBuilder.addParameter(DataMessageWrapper.getWrappedObject(args[i], parameterTypes[i], null));
      } else {
        //parameter is null
        callBuilder.addParameter(DataMessageWrapper.getWrappedObject(null, parameterTypes[i], null));
      }
    }

    final DataMessage.Builder msgBuilder = DataMessage.newBuilder()
      .setThreadId(Thread.currentThread().getId())
      .setMsgType("Class method")
      .setClassMethodCall(callBuilder
          .setConcentratorId(Integer.parseInt(concentratorId))
          .setThreadId(Thread.currentThread().getId())
          .setCurrentTime(System.currentTimeMillis())
          .setClass_(DataMessageWrapper.getWrappedClass(className))
          .setName(methodName));

    return msgBuilder.build();

  }

  public static DataMessage buildClassMethodMessage(int concentratorId, StaticPart staticPart, Object sender, Object[] args) {

    final MethodSignature codeSignature = (MethodSignature) staticPart.getSignature();

    final ClassMethodCall.Builder callBuilder = ClassMethodCall.newBuilder();
    for (String name : codeSignature.getParameterNames()) {
      callBuilder.addParameterName(name);
    }
    for (Class clazz : codeSignature.getExceptionTypes()) {
      callBuilder.addExceptionType(DataMessageWrapper.getWrappedClass(clazz.getName()));
    }
    for (int i = 0; i < args.length; i++) {
      callBuilder.addParameter(DataMessageWrapper.getWrappedObject(args[i], codeSignature.getParameterTypes()[i].getName(), null));
    }

    final DataMessage.Builder msgBuilder = DataMessage.newBuilder()
      .setThreadId(Thread.currentThread().getId())
      .setMsgType("Class method")
      .setClassMethodCall(callBuilder
          .setContext(DataMessageWrapper.getWrappedContext(staticPart, sender))
          .setConcentratorId(concentratorId)
          .setThreadId(Thread.currentThread().getId())
          .setCurrentTime(System.currentTimeMillis())
          .setClass_(DataMessageWrapper.getWrappedClass(codeSignature.getDeclaringTypeName()))
          .setName(codeSignature.getName())
          .setModifiers(codeSignature.getModifiers()));

    return msgBuilder.build();
  }
  //</editor-fold>

  //<editor-fold desc="Static field get messages">
  /**
   * This method is to be called when no joinpoint context is available.
   */
  public static DataMessage buildGetStaticMessage(String concentratorId, String className, String fieldName) {

    final DataMessage.Builder msgBuilder = DataMessage.newBuilder()
      .setThreadId(Thread.currentThread().getId())
      .setMsgType("Get static")
      .setStaticFieldGet(StaticFieldGet.newBuilder()
          .setConcentratorId(Integer.parseInt(concentratorId))
          .setThreadId(Thread.currentThread().getId())
          .setCurrentTime(System.currentTimeMillis())
          .setClass_(DataMessageWrapper.getWrappedClass(className))
          .setField(DataMessageWrapper.getWrappedField(className, fieldName)));

    return msgBuilder.build();
  }

  public static DataMessage buildGetStaticMessage(int concentratorId, StaticPart staticPart, Object sender) {

    final FieldSignatureImpl fieldSignature = (FieldSignatureImpl) staticPart.getSignature();

    final DataMessage.Builder msgBuilder = DataMessage.newBuilder()
      .setThreadId(Thread.currentThread().getId())
      .setMsgType("Get static")
      .setStaticFieldGet(StaticFieldGet.newBuilder()
          .setConcentratorId(concentratorId)
          .setThreadId(Thread.currentThread().getId())
          .setCurrentTime(System.currentTimeMillis())
          .setClass_(DataMessageWrapper.getWrappedClass(fieldSignature.getDeclaringTypeName()))
          .setField(DataMessageWrapper.getWrappedField(fieldSignature.getFieldType(), fieldSignature.getName()))
          .setModifiers(fieldSignature.getModifiers())
          .setContext(DataMessageWrapper.getWrappedContext(staticPart, sender)));

    return msgBuilder.build();
  }
  //</editor-fold>

  //<editor-fold desc="Instance field get messages">
  /**
   * This method is to be called when no joinpoint context is available.
   */
  public static DataMessage buildGetObjectMessage(String concentratorId, String className, String fieldName, String targetObjRef) {

    final DataMessage.Builder msgBuilder = DataMessage.newBuilder()
      .setThreadId(Thread.currentThread().getId())
      .setMsgType("Get field")
      .setInstanceFieldGet(InstanceFieldGet.newBuilder()
          .setConcentratorId(Integer.parseInt(concentratorId))
          .setThreadId(Thread.currentThread().getId())
          .setCurrentTime(System.currentTimeMillis())
          .setClass_(DataMessageWrapper.getWrappedClass(className))
          .setObjectRef(targetObjRef)
          .setField(DataMessageWrapper.getWrappedField((String) null, fieldName)));

    return msgBuilder.build();
  }

  public static DataMessage buildGetObjectMessage(int concentratorId, StaticPart staticPart, Object sender, Object target) {

    final FieldSignatureImpl fieldSignature = (FieldSignatureImpl) staticPart.getSignature();

    final DataMessage.Builder msgBuilder = DataMessage.newBuilder()
      .setThreadId(Thread.currentThread().getId())
      .setMsgType("Get field")
      .setInstanceFieldGet(InstanceFieldGet.newBuilder()
          .setConcentratorId(concentratorId)
          .setThreadId(Thread.currentThread().getId())
          .setCurrentTime(System.currentTimeMillis())
          .setClass_(DataMessageWrapper.getWrappedClass(fieldSignature.getDeclaringTypeName()))
          .setObject(DataMessageWrapper.getWrappedObject(target, fieldSignature.getDeclaringTypeName(), null))
          .setField(DataMessageWrapper.getWrappedField(fieldSignature.getFieldType(), fieldSignature.getName()))
          .setModifiers(fieldSignature.getModifiers())
          .setContext(DataMessageWrapper.getWrappedContext(staticPart, sender)));

    return msgBuilder.build();
  }
  //</editor-fold>

  //<editor-fold desc="Static field put messages">
  /**
   * This method is to be called when no joinpoint context is available.
   */
  public static DataMessage buildPutStaticMessage(String concentratorId, String className, String fieldName, String valueClassName, Object value) {

    final DataMessage.Builder msgBuilder = DataMessage.newBuilder()
      .setThreadId(Thread.currentThread().getId())
      .setMsgType("Put static")
      .setStaticFieldPut(StaticFieldPut.newBuilder()
          .setConcentratorId(Integer.parseInt(concentratorId))
          .setThreadId(Thread.currentThread().getId())
          .setCurrentTime(System.currentTimeMillis())
          .setClass_(DataMessageWrapper.getWrappedClass(className))
          .setField(DataMessageWrapper.getWrappedField((String) null, fieldName))
          .setObject(DataMessageWrapper.getWrappedObject(value, valueClassName, null)));

    return msgBuilder.build();
  }

  /**
   * This method is to be called when no joinpoint context is available.
   * Equivalent to the above, for objectRefs
   */
  public static DataMessage buildPutStaticMessage(String concentratorId, String className, String fieldName, String objectRef) {

    final DataMessage.Builder msgBuilder = DataMessage.newBuilder()
      .setThreadId(Thread.currentThread().getId())
      .setMsgType("Put static")
      .setStaticFieldPut(StaticFieldPut.newBuilder()
          .setConcentratorId(Integer.parseInt(concentratorId))
          .setThreadId(Thread.currentThread().getId())
          .setCurrentTime(System.currentTimeMillis())
          .setClass_(DataMessageWrapper.getWrappedClass(className))
          .setField(DataMessageWrapper.getWrappedField((String) null, fieldName))
          .setObjectRef(objectRef));

    return msgBuilder.build();
  }

  public static DataMessage buildPutStaticMessage(int concentratorId, StaticPart staticPart, Object sender, Object arg) {

    final FieldSignatureImpl fieldSignature = (FieldSignatureImpl) staticPart.getSignature();

    final DataMessage.Builder msgBuilder = DataMessage.newBuilder()
      .setThreadId(Thread.currentThread().getId())
      .setMsgType("Put static")
      .setStaticFieldPut(StaticFieldPut.newBuilder()
          .setConcentratorId(concentratorId)
          .setThreadId(Thread.currentThread().getId())
          .setCurrentTime(System.currentTimeMillis())
          .setClass_(DataMessageWrapper.getWrappedClass(fieldSignature.getDeclaringType()))
          .setField(DataMessageWrapper.getWrappedField(fieldSignature.getFieldType(), fieldSignature.getName()))
          .setObject(DataMessageWrapper.getWrappedObject(arg, fieldSignature.getFieldType(), null))
          .setModifiers(fieldSignature.getModifiers())
          .setContext(DataMessageWrapper.getWrappedContext(staticPart, sender)));

    return msgBuilder.build();
  }

  /**
   * This method is to be called when no joinpoint context is available.
   * Equivalent to the above, for objectRefs
   */
  public static DataMessage buildPutStaticDoneMessage(int concentratorId, Fields.StaticFieldPut staticFieldPut, Class fieldType, Long followingOffset) {

   final StaticFieldPutDone.Builder fieldBuilder = StaticFieldPutDone.newBuilder();
   if (staticFieldPut.getField().hasClass_()) {
      fieldBuilder.setField(staticFieldPut.getField());
    } else {
      fieldBuilder.setField(DataMessageWrapper.getWrappedField(fieldType, staticFieldPut.getField().getName()));
    }

    final DataMessage.Builder msgBuilder = DataMessage.newBuilder()
      .setThreadId(Thread.currentThread().getId())
      .setMsgType("Put static done")
      .setStaticFieldPutDone(fieldBuilder
          .setConcentratorId(concentratorId)
          .setThreadId(Thread.currentThread().getId())
          .setCurrentTime(System.currentTimeMillis())
          .setClass_(DataMessageWrapper.getWrappedClass(staticFieldPut.getClass_().getName()))
          .setStaticFieldPut(staticFieldPut));

    if (followingOffset != null) {
      msgBuilder.setFollowing(followingOffset);
    }
    return msgBuilder.build();
  }


  public static DataMessage buildPutStaticDoneMessage(int concentratorId, StaticPart staticPart, Object sender, Object arg) {

    final FieldSignatureImpl fieldSignature = (FieldSignatureImpl) staticPart.getSignature();

    final DataMessage.Builder msgBuilder = DataMessage.newBuilder()
      .setThreadId(Thread.currentThread().getId())
      .setMsgType("Put static done")
      .setStaticFieldPutDone(StaticFieldPutDone.newBuilder()
          .setConcentratorId(concentratorId)
          .setThreadId(Thread.currentThread().getId())
          .setCurrentTime(System.currentTimeMillis())
          .setField(DataMessageWrapper.getWrappedField(fieldSignature.getFieldType(), fieldSignature.getName()))
          .setClass_(DataMessageWrapper.getWrappedClass(fieldSignature.getDeclaringType())));

    return msgBuilder.build();
  }
  //</editor-fold>

  //<editor-fold desc="Instance field put messages">
  /**
   * This method is to be called when no joinpoint context is available.
   * Equivalent to the above, for objectRefs
   */
  public static DataMessage buildPutObjectMessage(String concentratorId, String className, String fieldName, String targetObjRef, String valueClassName, Object value) {

    final DataMessage.Builder msgBuilder = DataMessage.newBuilder()
      .setThreadId(Thread.currentThread().getId())
      .setMsgType("Put field")
      .setInstanceFieldPut(InstanceFieldPut.newBuilder()
          .setConcentratorId(Integer.parseInt(concentratorId))
          .setThreadId(Thread.currentThread().getId())
          .setCurrentTime(System.currentTimeMillis())
          .setClass_(DataMessageWrapper.getWrappedClass(className))
          .setObjectRef(targetObjRef)
          .setField(DataMessageWrapper.getWrappedField((String) null, fieldName))
          .setValueObject(DataMessageWrapper.getWrappedObject(value, valueClassName, null)));

    return msgBuilder.build();
  }

  /**
   * This method is to be called when no joinpoint context is available.
   * Equivalent to the above, for objectRefs
   */
  public static DataMessage buildPutObjectMessage(String concentratorId, String className, String fieldName, String targetObjRef, String valueObjRef) {

    final DataMessage.Builder msgBuilder = DataMessage.newBuilder()
      .setThreadId(Thread.currentThread().getId())
      .setMsgType("Put field")
      .setInstanceFieldPut(InstanceFieldPut.newBuilder()
          .setConcentratorId(Integer.parseInt(concentratorId))
          .setThreadId(Thread.currentThread().getId())
          .setCurrentTime(System.currentTimeMillis())
          .setClass_(DataMessageWrapper.getWrappedClass(className))
          .setObjectRef(targetObjRef)
          .setField(DataMessageWrapper.getWrappedField((String) null, fieldName))
          .setValueObjectRef(valueObjRef));

    return msgBuilder.build();
  }

  public static DataMessage buildPutObjectMessage(int concentratorId, StaticPart staticPart, Object sender, Object target, Object arg) {

    final FieldSignatureImpl fieldSignature = (FieldSignatureImpl) staticPart.getSignature();

    final DataMessage.Builder msgBuilder = DataMessage.newBuilder()
      .setThreadId(Thread.currentThread().getId())
      .setMsgType("Put field")
      .setInstanceFieldPut(InstanceFieldPut.newBuilder()
          .setConcentratorId(concentratorId)
          .setThreadId(Thread.currentThread().getId())
          .setCurrentTime(System.currentTimeMillis())
          .setClass_(DataMessageWrapper.getWrappedClass(fieldSignature.getDeclaringType()))
          .setObject(DataMessageWrapper.getWrappedObject(target, fieldSignature.getDeclaringType(), null))
          .setField(DataMessageWrapper.getWrappedField(fieldSignature.getFieldType(), fieldSignature.getName()))
          .setValueObject(DataMessageWrapper.getWrappedObject(arg, fieldSignature.getFieldType().getName(), null))
          .setModifiers(fieldSignature.getModifiers())
          .setContext(DataMessageWrapper.getWrappedContext(staticPart, sender)));

    return msgBuilder.build();
  }

  public static DataMessage buildPutObjectDoneMessage(int concentratorId, Fields.InstanceFieldPut instanceFieldPut, Class fieldType, Long followingOffset) {

    final Fields.InstanceFieldPutDone.Builder fieldBuilder = InstanceFieldPutDone.newBuilder();
    if (instanceFieldPut.getField().hasClass_()) {
      fieldBuilder.setField(instanceFieldPut.getField());
    } else {
      fieldBuilder.setField(DataMessageWrapper.getWrappedField(fieldType, instanceFieldPut.getField().getName()));
    }

    final DataMessage.Builder msgBuilder = DataMessage.newBuilder()
      .setThreadId(Thread.currentThread().getId())
      .setMsgType("Put field done")
      .setInstanceFieldPutDone(fieldBuilder
          .setConcentratorId(concentratorId)
          .setThreadId(Thread.currentThread().getId())
          .setCurrentTime(System.currentTimeMillis())
          .setClass_(DataMessageWrapper.getWrappedClass(instanceFieldPut.getClass_().getName()))
          .setInstanceFieldPut(instanceFieldPut));

    if (followingOffset!=null) {
      msgBuilder.setFollowing(followingOffset);
    }

    return msgBuilder.build();
  }

  public static DataMessage buildPutObjectDoneMessage(int concentratorId, StaticPart staticPart, Object sender, Object target, Object arg) {

    final FieldSignatureImpl fieldSignature = (FieldSignatureImpl) staticPart.getSignature();

    final DataMessage.Builder msgBuilder = DataMessage.newBuilder()
      .setThreadId(Thread.currentThread().getId())
      .setMsgType("Put field done")
      .setInstanceFieldPutDone(InstanceFieldPutDone.newBuilder()
          .setConcentratorId(concentratorId)
          .setThreadId(Thread.currentThread().getId())
          .setCurrentTime(System.currentTimeMillis())
          .setClass_(DataMessageWrapper.getWrappedClass(fieldSignature.getDeclaringType()))
          .setField(DataMessageWrapper.getWrappedField(fieldSignature.getFieldType(), fieldSignature.getName())));

    return msgBuilder.build();
  }
  //</editor-fold>

  //<editor-fold desc="Throwable messages">
  public static DataMessage buildAccessibleObjectThrowableMessage(int concentratorId, AccessibleObject accessibleObject, Exception exception, Long followingOffset) {

    final Exceptions.RaisedThrowable.Builder thrBuilder = Exceptions.RaisedThrowable.newBuilder();
    if (accessibleObject instanceof Constructor) {
      thrBuilder.setConstructor(((Constructor) accessibleObject).getDeclaringClass().getName());
      thrBuilder.setModifiers(((Constructor) accessibleObject).getModifiers());
    } else if (accessibleObject instanceof Method) {
      thrBuilder.setMethod(((Method) accessibleObject).getName());
      thrBuilder.setModifiers(((Method) accessibleObject).getModifiers());
    }

    final DataMessage.Builder msgBuilder = DataMessage.newBuilder()
      .setThreadId(Thread.currentThread().getId())
      .setMsgType("Throwable")
      .setRaisedThrowable(thrBuilder
          .setConcentratorId(concentratorId)
          .setThreadId(Thread.currentThread().getId())
          .setCurrentTime(System.currentTimeMillis())
          .setClass_(DataMessageWrapper.getWrappedClass(exception.getClass().getName()))
          .setThrowable(buildThrowableMessage(exception)));

    if (followingOffset!=null) {
      msgBuilder.setFollowing(followingOffset);
    }

    return msgBuilder.build();
  }

  public static DataMessage buildInitializerThrowableMessage(int concentratorId, StaticPart staticPart, Exception exception) {

    final DataMessage.Builder msgBuilder = DataMessage.newBuilder()
      .setThreadId(Thread.currentThread().getId())
      .setMsgType("Throwable")
      .setRaisedThrowable(Exceptions.RaisedThrowable.newBuilder()
          .setConcentratorId(concentratorId)
          .setThreadId(Thread.currentThread().getId())
          .setCurrentTime(System.currentTimeMillis())
          .setInInitializer(true)
          .setClass_(DataMessageWrapper.getWrappedClass(staticPart.getSignature().getDeclaringTypeName()))
          .setModifiers(staticPart.getSignature().getModifiers())
          .setThrowable(buildThrowableMessage(exception)));

    return msgBuilder.build();
  }


  private static Exceptions.Throwable.Builder buildThrowableMessage(Throwable throwable) {

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
  public static DataMessage buildReturnValueMessage(int concentratorId, Object object, Class type, String objectKey, boolean isVoid, Long followingOffset) {

    final Values.ReturnValue.Builder valBuilder = Values.ReturnValue.newBuilder();
    if (!isVoid) {
      valBuilder.setObject(DataMessageWrapper.getWrappedObject(object, type, objectKey));
    }

    final DataMessage.Builder msgBuilder = DataMessage.newBuilder()
      .setThreadId(Thread.currentThread().getId())
      .setMsgType("Return value")
      .setReturnValue(valBuilder
          .setConcentratorId(concentratorId)
          .setThreadId(Thread.currentThread().getId())
          .setCurrentTime(System.currentTimeMillis())
          .setIsVoid(isVoid)
          .setClazz(DataMessageWrapper.getWrappedClass(type)));

    if (followingOffset != null) {
      msgBuilder.setFollowing(followingOffset);
    }

    return msgBuilder.build();
  }
  //</editor-fold>
}
