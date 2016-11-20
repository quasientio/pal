package com.ittera.cometa.concentrator.messages.data;

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
public class DataMessageFactory {
  protected static final Logger logger = LogManager.getLogger(DataMessageFactory.class);

  public static Wrappers.DataMessage buildClassInitializerMessage(int concentratorId, StaticPart staticPart, Object sender) {

    /** Build protobuf message **/
    final InitializerSignature codeSignature = (InitializerSignature) staticPart.getSignature();

    final Wrappers.DataMessage.Builder msgBuilder = Wrappers.DataMessage.newBuilder();

    final Calls.ClInitCall.Builder callBuilder = Calls.ClInitCall.newBuilder();
    callBuilder.setConcentratorId(concentratorId);
    callBuilder.setThreadId(Thread.currentThread().getId());
    callBuilder.setCurrentTime(System.currentTimeMillis());
    callBuilder.setClass_(getWrappedClass(codeSignature.getDeclaringTypeName()));
    callBuilder.setModifiers(codeSignature.getModifiers());
    callBuilder.setContext(getWrappedContext(staticPart, sender));

    msgBuilder.setThreadId(Thread.currentThread().getId());
    msgBuilder.setMsgType("Static Constructor");
    msgBuilder.setClinitCall(callBuilder);

    return msgBuilder.build();
  }

  /**
   * Only supports calls to empty constructor
   * This method is to be called when no joinpoint context is available (calling class hasn't been weaved). Example of caller: CommandLineClient
   */
  public static Wrappers.DataMessage buildEmptyConstructorMessage(String concentratorId, String className) {
    final Wrappers.DataMessage.Builder msgBuilder = Wrappers.DataMessage.newBuilder();

    final Calls.ConstructorCall.Builder callBuilder = Calls.ConstructorCall.newBuilder();
    callBuilder.setConcentratorId(Integer.parseInt(concentratorId));
    callBuilder.setThreadId(Thread.currentThread().getId());
    callBuilder.setCurrentTime(System.currentTimeMillis());
    callBuilder.setClass_(getWrappedClass(className));

    msgBuilder.setThreadId(Thread.currentThread().getId());
    msgBuilder.setMsgType("Constructor");
    msgBuilder.setConstructorCall(callBuilder);

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
  public static Wrappers.DataMessage buildNonEmptyConstructorMessage(String concentratorId, String className, String[] parameterTypes, Object[] args, String[] argObjRefs) {
    final Wrappers.DataMessage.Builder msgBuilder = Wrappers.DataMessage.newBuilder();

    final Calls.ConstructorCall.Builder callBuilder = Calls.ConstructorCall.newBuilder();
    callBuilder.setConcentratorId(Integer.parseInt(concentratorId));
    callBuilder.setThreadId(Thread.currentThread().getId());
    callBuilder.setCurrentTime(System.currentTimeMillis());
    callBuilder.setClass_(getWrappedClass(className));

    for (int i = 0; i < parameterTypes.length; i++) {
      if (argObjRefs[i] != null) {
        //parameter is an objectref
        callBuilder.addParameter(getWrappedObject(null, parameterTypes[i], argObjRefs[i]));
      } else if (args[i] != null) {
        //parameter is string, primitive or wrapper
        callBuilder.addParameter(getWrappedObject(args[i], parameterTypes[i], null));
      } else {
        //parameter is null
        callBuilder.addParameter(getWrappedObject(null, parameterTypes[i], null));
      }
    }

    msgBuilder.setThreadId(Thread.currentThread().getId());
    msgBuilder.setMsgType("Constructor");
    msgBuilder.setConstructorCall(callBuilder);

    return msgBuilder.build();


  }

  public static Wrappers.DataMessage buildConstructorMessage(int concentratorId, StaticPart staticPart, Object sender, Object[] args) {

    /** Build protobuf message **/
    final ConstructorSignature codeSignature = (ConstructorSignature) staticPart.getSignature();
    final Wrappers.DataMessage.Builder msgBuilder = Wrappers.DataMessage.newBuilder();

    final Calls.ConstructorCall.Builder callBuilder = Calls.ConstructorCall.newBuilder();
    callBuilder.setConcentratorId(concentratorId);
    callBuilder.setThreadId(Thread.currentThread().getId());
    callBuilder.setCurrentTime(System.currentTimeMillis());
    callBuilder.setClass_(getWrappedClass(codeSignature.getDeclaringTypeName()));
    callBuilder.setModifiers(codeSignature.getModifiers());

    for (String name : codeSignature.getParameterNames()) {
      callBuilder.addParameterName(name);
    }

    for (Class clazz : codeSignature.getExceptionTypes()) {
      callBuilder.addExceptionType(getWrappedClass(clazz.getName()));
    }

    for (int i = 0; i < args.length; i++) {
      callBuilder.addParameter(getWrappedObject(args[i], codeSignature.getParameterTypes()[i].getName(), null));
    }
    callBuilder.setContext(getWrappedContext(staticPart, sender));

    msgBuilder.setThreadId(Thread.currentThread().getId());
    msgBuilder.setMsgType("Constructor");
    msgBuilder.setConstructorCall(callBuilder);

    return msgBuilder.build();
  }

  /**
   * This method is to be called when no joinpoint context is available (calling class hasn't been weaved). Example of caller: CommandLineClient
   */
  public static Wrappers.DataMessage buildInstanceMethodMessage(String concentratorId, String className, String methodName, String objRef, String[] parameterTypes, Object[] args) {

    final Wrappers.DataMessage.Builder msgBuilder = Wrappers.DataMessage.newBuilder();

    final Calls.InstanceMethodCall.Builder callBuilder = Calls.InstanceMethodCall.newBuilder();
    callBuilder.setConcentratorId(Integer.parseInt(concentratorId));
    callBuilder.setThreadId(Thread.currentThread().getId());
    callBuilder.setCurrentTime(System.currentTimeMillis());
    callBuilder.setClass_(getWrappedClass(className));
    callBuilder.setName(methodName);
    callBuilder.setObjectRef(objRef);
    //TO DO if it's primitive/String/Class ??
//    callBuilder.setTarget(getWrappedObject(target, className, null));

    for (int i = 0; i < args.length; i++) {
      callBuilder.addParameter(getWrappedObject(args[i], parameterTypes[i], null));
    }

    msgBuilder.setThreadId(Thread.currentThread().getId());
    msgBuilder.setMsgType("Instance method");
    msgBuilder.setInstanceMethodCall(callBuilder);

    return msgBuilder.build();

  }


  public static Wrappers.DataMessage buildInstanceMethodMessage(int concentratorId, StaticPart staticPart, Object sender, Object target, Object[] args) {

    /** Build protobuf message **/
    final MethodSignature codeSignature = (MethodSignature) staticPart.getSignature();

    final Wrappers.DataMessage.Builder msgBuilder = Wrappers.DataMessage.newBuilder();

    final Calls.InstanceMethodCall.Builder callBuilder = Calls.InstanceMethodCall.newBuilder();
    callBuilder.setConcentratorId(concentratorId);
    callBuilder.setThreadId(Thread.currentThread().getId());
    callBuilder.setCurrentTime(System.currentTimeMillis());
    callBuilder.setClass_(getWrappedClass(codeSignature.getDeclaringTypeName()));
    callBuilder.setName(codeSignature.getName());
    callBuilder.setObject(getWrappedObject(target, codeSignature.getDeclaringTypeName(), null));
    callBuilder.setModifiers(codeSignature.getModifiers());

    for (String name : codeSignature.getParameterNames()) {
      callBuilder.addParameterName(name);
    }

    for (Class clazz : codeSignature.getExceptionTypes()) {
      callBuilder.addExceptionType(getWrappedClass(clazz.getName()));
    }

    for (int i = 0; i < args.length; i++) {
      callBuilder.addParameter(getWrappedObject(args[i], codeSignature.getParameterTypes()[i].getName(), null));
    }

    callBuilder.setContext(getWrappedContext(staticPart, sender));

    msgBuilder.setThreadId(Thread.currentThread().getId());
    msgBuilder.setMsgType("Instance method");
    msgBuilder.setInstanceMethodCall(callBuilder);

    return msgBuilder.build();
  }


  /**
   * This method is to be called when no joinpoint context is available (calling class hasn't been weaved). Example of caller: CommandLineClient
   */
  public static Wrappers.DataMessage buildClassMethodMessage(String concentratorId, String className, String methodName, int modifiers, Class returnType, String[] parameterTypes, Object[] args) {
    final Wrappers.DataMessage.Builder msgBuilder = Wrappers.DataMessage.newBuilder();

    final Calls.ClassMethodCall.Builder callBuilder = Calls.ClassMethodCall.newBuilder();
    callBuilder.setConcentratorId(Integer.parseInt(concentratorId));
    callBuilder.setThreadId(Thread.currentThread().getId());
    callBuilder.setCurrentTime(System.currentTimeMillis());
    callBuilder.setClass_(getWrappedClass(className));
    callBuilder.setName(methodName);

    boolean isMain = isMain(methodName, returnType, parameterTypes, modifiers);
    if (!isMain) {
      throw new IllegalArgumentException("Currently only calls to psvm can be wrapped by this method");
    }
    for (int i = 0; i < args.length; i++) {
      callBuilder.addParameter(getWrappedObject(args[i], parameterTypes[i], null));
    }

    msgBuilder.setThreadId(Thread.currentThread().getId());
    msgBuilder.setMsgType("Class method");
    msgBuilder.setClassMethodCall(callBuilder);

    return msgBuilder.build();

  }

  public static Wrappers.DataMessage buildClassMethodMessage(int concentratorId, StaticPart staticPart, Object sender, Object[] args) {

    /** Build protobuf message **/
    final MethodSignature codeSignature = (MethodSignature) staticPart.getSignature();

    final Wrappers.DataMessage.Builder msgBuilder = Wrappers.DataMessage.newBuilder();

    final Calls.ClassMethodCall.Builder callBuilder = Calls.ClassMethodCall.newBuilder();
    callBuilder.setConcentratorId(concentratorId);
    callBuilder.setThreadId(Thread.currentThread().getId());
    callBuilder.setCurrentTime(System.currentTimeMillis());
    callBuilder.setClass_(getWrappedClass(codeSignature.getDeclaringTypeName()));
    callBuilder.setName(codeSignature.getName());
    callBuilder.setModifiers(codeSignature.getModifiers());

    for (String name : codeSignature.getParameterNames()) {
      callBuilder.addParameterName(name);
    }

    for (Class clazz : codeSignature.getExceptionTypes()) {
      callBuilder.addExceptionType(getWrappedClass(clazz.getName()));
    }

    for (int i = 0; i < args.length; i++) {
      callBuilder.addParameter(getWrappedObject(args[i], codeSignature.getParameterTypes()[i].getName(), null));
    }
    callBuilder.setContext(getWrappedContext(staticPart, sender));

    msgBuilder.setThreadId(Thread.currentThread().getId());
    msgBuilder.setMsgType("Class method");
    msgBuilder.setClassMethodCall(callBuilder);

    return msgBuilder.build();
  }

  /**
   * This method is to be called when no joinpoint context is available (calling class hasn't been weaved). Example of caller: CommandLineClient
   */
  public static Wrappers.DataMessage buildGetStaticMessage(String concentratorId, String className, String fieldName) {

    /** Build protobuf message **/
    final Wrappers.DataMessage.Builder msgBuilder = Wrappers.DataMessage.newBuilder();

    final Fields.StaticFieldGet.Builder fieldBuilder = Fields.StaticFieldGet.newBuilder();
    fieldBuilder.setConcentratorId(Integer.parseInt(concentratorId));
    fieldBuilder.setThreadId(Thread.currentThread().getId());
    fieldBuilder.setCurrentTime(System.currentTimeMillis());
    fieldBuilder.setClass_(getWrappedClass(className));
    fieldBuilder.setField(getWrappedField(className, fieldName));
    msgBuilder.setThreadId(Thread.currentThread().getId());
    msgBuilder.setMsgType("Get static");
    msgBuilder.setStaticFieldGet(fieldBuilder);

    return msgBuilder.build();
  }

  public static Wrappers.DataMessage buildGetStaticMessage(int concentratorId, StaticPart staticPart, Object sender) {

    /** Build protobuf message **/
    final FieldSignatureImpl fieldSignature = (FieldSignatureImpl) staticPart.getSignature();

    final Wrappers.DataMessage.Builder msgBuilder = Wrappers.DataMessage.newBuilder();

    final Fields.StaticFieldGet.Builder fieldBuilder = Fields.StaticFieldGet.newBuilder();
    fieldBuilder.setConcentratorId(concentratorId);
    fieldBuilder.setThreadId(Thread.currentThread().getId());
    fieldBuilder.setCurrentTime(System.currentTimeMillis());
    fieldBuilder.setClass_(getWrappedClass(fieldSignature.getDeclaringTypeName()));
    fieldBuilder.setField(getWrappedField(fieldSignature.getFieldType(), fieldSignature.getName()));
    fieldBuilder.setModifiers(fieldSignature.getModifiers());
    fieldBuilder.setContext(getWrappedContext(staticPart, sender));

    msgBuilder.setThreadId(Thread.currentThread().getId());
    msgBuilder.setMsgType("Get static");
    msgBuilder.setStaticFieldGet(fieldBuilder);

    return msgBuilder.build();
  }

  /**
   * This method is to be called when no joinpoint context is available (calling class hasn't been weaved). Example of caller: CommandLineClient
   */
  public static Wrappers.DataMessage buildGetObjectMessage(String concentratorId, String className, String fieldName, String targetObjRef) {

    /** Build protobuf message **/
    final Wrappers.DataMessage.Builder msgBuilder = Wrappers.DataMessage.newBuilder();

    final Fields.InstanceFieldGet.Builder fieldBuilder = Fields.InstanceFieldGet.newBuilder();
    fieldBuilder.setConcentratorId(Integer.parseInt(concentratorId));
    fieldBuilder.setThreadId(Thread.currentThread().getId());
    fieldBuilder.setCurrentTime(System.currentTimeMillis());
    fieldBuilder.setClass_(getWrappedClass(className));
    fieldBuilder.setObjectRef(targetObjRef);
    fieldBuilder.setField(getWrappedField((String) null, fieldName));

    msgBuilder.setThreadId(Thread.currentThread().getId());
    msgBuilder.setMsgType("Get field");
    msgBuilder.setInstanceFieldGet(fieldBuilder);

    return msgBuilder.build();
  }

  public static Wrappers.DataMessage buildGetObjectMessage(int concentratorId, StaticPart staticPart, Object sender, Object target) {

    /** Build protobuf message **/
    final FieldSignatureImpl fieldSignature = (FieldSignatureImpl) staticPart.getSignature();

    final Wrappers.DataMessage.Builder msgBuilder = Wrappers.DataMessage.newBuilder();

    final Fields.InstanceFieldGet.Builder fieldBuilder = Fields.InstanceFieldGet.newBuilder();
    fieldBuilder.setConcentratorId(concentratorId);
    fieldBuilder.setThreadId(Thread.currentThread().getId());
    fieldBuilder.setCurrentTime(System.currentTimeMillis());
    fieldBuilder.setClass_(getWrappedClass(fieldSignature.getDeclaringTypeName()));
    fieldBuilder.setObject(getWrappedObject(target, fieldSignature.getDeclaringTypeName(), null));
    fieldBuilder.setField(getWrappedField(fieldSignature.getFieldType(), fieldSignature.getName()));
    fieldBuilder.setModifiers(fieldSignature.getModifiers());
    fieldBuilder.setContext(getWrappedContext(staticPart, sender));

    msgBuilder.setThreadId(Thread.currentThread().getId());
    msgBuilder.setMsgType("Get field");
    msgBuilder.setInstanceFieldGet(fieldBuilder);

    return msgBuilder.build();
  }

  /**
   * This method is to be called when no joinpoint context is available (calling class hasn't been weaved). Example of caller: CommandLineClient
   */
  public static Wrappers.DataMessage buildPutStaticMessage(String concentratorId, String className, String fieldName, String valueClassName, Object value) {

    /** Build protobuf message **/
    final Wrappers.DataMessage.Builder msgBuilder = Wrappers.DataMessage.newBuilder();

    final Fields.StaticFieldPut.Builder fieldBuilder = Fields.StaticFieldPut.newBuilder();
    fieldBuilder.setConcentratorId(Integer.parseInt(concentratorId));
    fieldBuilder.setThreadId(Thread.currentThread().getId());
    fieldBuilder.setCurrentTime(System.currentTimeMillis());
    fieldBuilder.setClass_(getWrappedClass(className));
    fieldBuilder.setField(getWrappedField((String) null, fieldName));
    fieldBuilder.setObject(getWrappedObject(value, valueClassName, null));

    msgBuilder.setThreadId(Thread.currentThread().getId());
    msgBuilder.setMsgType("Put static");
    msgBuilder.setStaticFieldPut(fieldBuilder);

    return msgBuilder.build();
  }

  /**
   * This method is to be called when no joinpoint context is available (calling class hasn't been weaved). Example of caller: CommandLineClient
   * Equivalent to the above, for objectRefs
   */
  public static Wrappers.DataMessage buildPutStaticMessage(String concentratorId, String className, String fieldName, String objectRef) {

    /** Build protobuf message **/
    final Wrappers.DataMessage.Builder msgBuilder = Wrappers.DataMessage.newBuilder();

    final Fields.StaticFieldPut.Builder fieldBuilder = Fields.StaticFieldPut.newBuilder();
    fieldBuilder.setConcentratorId(Integer.parseInt(concentratorId));
    fieldBuilder.setThreadId(Thread.currentThread().getId());
    fieldBuilder.setCurrentTime(System.currentTimeMillis());
    fieldBuilder.setClass_(getWrappedClass(className));
    fieldBuilder.setField(getWrappedField((String) null, fieldName));
    fieldBuilder.setObjectRef(objectRef);

    msgBuilder.setThreadId(Thread.currentThread().getId());
    msgBuilder.setMsgType("Put static");
    msgBuilder.setStaticFieldPut(fieldBuilder);

    return msgBuilder.build();
  }

  public static Wrappers.DataMessage buildPutStaticMessage(int concentratorId, StaticPart staticPart, Object sender, Object arg) {

    /** Build protobuf message **/
    final FieldSignatureImpl fieldSignature = (FieldSignatureImpl) staticPart.getSignature();

    final Wrappers.DataMessage.Builder msgBuilder = Wrappers.DataMessage.newBuilder();

    final Fields.StaticFieldPut.Builder fieldBuilder = Fields.StaticFieldPut.newBuilder();
    fieldBuilder.setConcentratorId(concentratorId);
    fieldBuilder.setThreadId(Thread.currentThread().getId());
    fieldBuilder.setCurrentTime(System.currentTimeMillis());
    fieldBuilder.setClass_(getWrappedClass(fieldSignature.getDeclaringType()));
    fieldBuilder.setField(getWrappedField(fieldSignature.getFieldType(), fieldSignature.getName()));
    fieldBuilder.setObject(getWrappedObject(arg, fieldSignature.getFieldType(), null));
    fieldBuilder.setModifiers(fieldSignature.getModifiers());
    fieldBuilder.setContext(getWrappedContext(staticPart, sender));

    msgBuilder.setThreadId(Thread.currentThread().getId());
    msgBuilder.setMsgType("Put static");
    msgBuilder.setStaticFieldPut(fieldBuilder);

    return msgBuilder.build();
  }

  /**
   * This method is to be called when no joinpoint context is available (calling class hasn't been weaved). Example of caller: CommandLineClient
   * Equivalent to the above, for objectRefs
   */
  public static Wrappers.DataMessage buildPutStaticDoneMessage(int concentratorId, Fields.StaticFieldPut staticFieldPut, Class fieldType, Long followingOffset) {

    /** Build protobuf message **/
    final Wrappers.DataMessage.Builder msgBuilder = Wrappers.DataMessage.newBuilder();

    final Fields.StaticFieldPutDone.Builder fieldBuilder = Fields.StaticFieldPutDone.newBuilder();
    fieldBuilder.setConcentratorId(concentratorId);
    fieldBuilder.setThreadId(Thread.currentThread().getId());
    fieldBuilder.setCurrentTime(System.currentTimeMillis());
    fieldBuilder.setClass_(getWrappedClass(staticFieldPut.getClass_().getName()));
    fieldBuilder.setStaticFieldPut(staticFieldPut);
    if (staticFieldPut.getField().hasClass_()) {
      fieldBuilder.setField(staticFieldPut.getField());
    } else {
      fieldBuilder.setField(getWrappedField(fieldType, staticFieldPut.getField().getName()));
    }
    msgBuilder.setThreadId(Thread.currentThread().getId());
    msgBuilder.setMsgType("Put static done");
    if (followingOffset != null) {
      msgBuilder.setFollowing(followingOffset);
    }
    msgBuilder.setStaticFieldPutDone(fieldBuilder);

    return msgBuilder.build();
  }


  public static Wrappers.DataMessage buildPutStaticDoneMessage(int concentratorId, StaticPart staticPart, Object sender, Object arg) {

    /** Build protobuf message **/
    final FieldSignatureImpl fieldSignature = (FieldSignatureImpl) staticPart.getSignature();

    final Wrappers.DataMessage.Builder msgBuilder = Wrappers.DataMessage.newBuilder();

    final Fields.StaticFieldPutDone.Builder fieldBuilder = Fields.StaticFieldPutDone.newBuilder();
    fieldBuilder.setConcentratorId(concentratorId);
    fieldBuilder.setThreadId(Thread.currentThread().getId());
    fieldBuilder.setCurrentTime(System.currentTimeMillis());
    fieldBuilder.setField(getWrappedField(fieldSignature.getFieldType(), fieldSignature.getName()));
    fieldBuilder.setClass_(getWrappedClass(fieldSignature.getDeclaringType()));

    msgBuilder.setThreadId(Thread.currentThread().getId());
    msgBuilder.setMsgType("Put static done");
    msgBuilder.setStaticFieldPutDone(fieldBuilder);

    logger.debug("message built!");
    return msgBuilder.build();
  }


  /**
   * This method is to be called when no joinpoint context is available (calling class hasn't been weaved). Example of caller: CommandLineClient
   * Equivalent to the above, for objectRefs
   */
  public static Wrappers.DataMessage buildPutObjectMessage(String concentratorId, String className, String fieldName, String targetObjRef, String valueClassName, Object value) {

    /** Build protobuf message **/
    final Wrappers.DataMessage.Builder msgBuilder = Wrappers.DataMessage.newBuilder();

    final Fields.InstanceFieldPut.Builder fieldBuilder = Fields.InstanceFieldPut.newBuilder();
    fieldBuilder.setConcentratorId(Integer.parseInt(concentratorId));
    fieldBuilder.setThreadId(Thread.currentThread().getId());
    fieldBuilder.setCurrentTime(System.currentTimeMillis());
    fieldBuilder.setClass_(getWrappedClass(className));
    fieldBuilder.setObjectRef(targetObjRef);
    fieldBuilder.setField(getWrappedField((String) null, fieldName));
    fieldBuilder.setValueObject(getWrappedObject(value, valueClassName, null));

    msgBuilder.setThreadId(Thread.currentThread().getId());
    msgBuilder.setMsgType("Put field");
    msgBuilder.setInstanceFieldPut(fieldBuilder);

    return msgBuilder.build();
  }

  /**
   * This method is to be called when no joinpoint context is available (calling class hasn't been weaved). Example of caller: CommandLineClient
   * Equivalent to the above, for objectRefs
   */
  public static Wrappers.DataMessage buildPutObjecSetNulltMessage(String concentratorId, String className, String fieldName, String targetObjRef) {

    /** Build protobuf message **/
    final Wrappers.DataMessage.Builder msgBuilder = Wrappers.DataMessage.newBuilder();

    final Fields.InstanceFieldPut.Builder fieldBuilder = Fields.InstanceFieldPut.newBuilder();
    fieldBuilder.setConcentratorId(Integer.parseInt(concentratorId));
    fieldBuilder.setThreadId(Thread.currentThread().getId());
    fieldBuilder.setCurrentTime(System.currentTimeMillis());
    fieldBuilder.setClass_(getWrappedClass(className));
    fieldBuilder.setObjectRef(targetObjRef);
    fieldBuilder.setField(getWrappedField((String) null, fieldName));
    fieldBuilder.setValueObject(getWrappedObject(null, (Class) null, null));

    msgBuilder.setThreadId(Thread.currentThread().getId());
    msgBuilder.setMsgType("Put field");
    msgBuilder.setInstanceFieldPut(fieldBuilder);

    return msgBuilder.build();
  }

  /**
   * This method is to be called when no joinpoint context is available (calling class hasn't been weaved). Example of caller: CommandLineClient
   * Equivalent to the above, for objectRefs
   */
  public static Wrappers.DataMessage buildPutObjectMessage(String concentratorId, String className, String fieldName, String targetObjRef, String valueObjRef) {

    /** Build protobuf message **/
    final Wrappers.DataMessage.Builder msgBuilder = Wrappers.DataMessage.newBuilder();

    final Fields.InstanceFieldPut.Builder fieldBuilder = Fields.InstanceFieldPut.newBuilder();
    fieldBuilder.setConcentratorId(Integer.parseInt(concentratorId));
    fieldBuilder.setThreadId(Thread.currentThread().getId());
    fieldBuilder.setCurrentTime(System.currentTimeMillis());
    fieldBuilder.setClass_(getWrappedClass(className));
    fieldBuilder.setObjectRef(targetObjRef);
    fieldBuilder.setField(getWrappedField((String) null, fieldName));
    fieldBuilder.setValueObjectRef(valueObjRef);

    msgBuilder.setThreadId(Thread.currentThread().getId());
    msgBuilder.setMsgType("Put field");
    msgBuilder.setInstanceFieldPut(fieldBuilder);

    return msgBuilder.build();
  }

  public static Wrappers.DataMessage buildPutObjectMessage(int concentratorId, StaticPart staticPart, Object sender, Object target, Object arg) {

    /** Build protobuf message **/
    final FieldSignatureImpl fieldSignature = (FieldSignatureImpl) staticPart.getSignature();

    final Wrappers.DataMessage.Builder msgBuilder = Wrappers.DataMessage.newBuilder();

    final Fields.InstanceFieldPut.Builder fieldBuilder = Fields.InstanceFieldPut.newBuilder();
    fieldBuilder.setConcentratorId(concentratorId);
    fieldBuilder.setThreadId(Thread.currentThread().getId());
    fieldBuilder.setCurrentTime(System.currentTimeMillis());
    fieldBuilder.setClass_(getWrappedClass(fieldSignature.getDeclaringType()));
    fieldBuilder.setObject(getWrappedObject(target, fieldSignature.getDeclaringType(), null));
    fieldBuilder.setField(getWrappedField(fieldSignature.getFieldType(), fieldSignature.getName()));
    fieldBuilder.setValueObject(getWrappedObject(arg, fieldSignature.getFieldType().getName(), null));
    fieldBuilder.setModifiers(fieldSignature.getModifiers());
    fieldBuilder.setContext(getWrappedContext(staticPart, sender));

    msgBuilder.setThreadId(Thread.currentThread().getId());
    msgBuilder.setMsgType("Put field");
    msgBuilder.setInstanceFieldPut(fieldBuilder);

    return msgBuilder.build();
  }

  public static Wrappers.DataMessage buildPutObjectDoneMessage(int concentratorId, Fields.InstanceFieldPut instanceFieldPut, Class fieldType, Long followingOffset) {

    /** Build protobuf message **/
    final Wrappers.DataMessage.Builder msgBuilder = Wrappers.DataMessage.newBuilder();
    final Object target;

    final Fields.InstanceFieldPutDone.Builder fieldBuilder = Fields.InstanceFieldPutDone.newBuilder();
    fieldBuilder.setConcentratorId(concentratorId);
    fieldBuilder.setThreadId(Thread.currentThread().getId());
    fieldBuilder.setCurrentTime(System.currentTimeMillis());
    fieldBuilder.setClass_(getWrappedClass(instanceFieldPut.getClass_().getName()));
    fieldBuilder.setInstanceFieldPut(instanceFieldPut);
    if (instanceFieldPut.getField().hasClass_()) {
      fieldBuilder.setField(instanceFieldPut.getField());
    } else {
      fieldBuilder.setField(getWrappedField(fieldType, instanceFieldPut.getField().getName()));
    }
    msgBuilder.setThreadId(Thread.currentThread().getId());
    msgBuilder.setMsgType("Put field done");
    if (followingOffset != null) {
      msgBuilder.setFollowing(followingOffset);
    }
    msgBuilder.setInstanceFieldPutDone(fieldBuilder);

    return msgBuilder.build();
  }

  public static Wrappers.DataMessage buildPutObjectDoneMessage(int concentratorId, StaticPart staticPart, Object sender, Object target, Object arg) {

    /** Build protobuf message **/
    final FieldSignatureImpl fieldSignature = (FieldSignatureImpl) staticPart.getSignature();

    final Wrappers.DataMessage.Builder msgBuilder = Wrappers.DataMessage.newBuilder();

    final Fields.InstanceFieldPutDone.Builder fieldBuilder = Fields.InstanceFieldPutDone.newBuilder();
    fieldBuilder.setConcentratorId(concentratorId);
    fieldBuilder.setThreadId(Thread.currentThread().getId());
    fieldBuilder.setCurrentTime(System.currentTimeMillis());
    fieldBuilder.setClass_(getWrappedClass(fieldSignature.getDeclaringType()));
    fieldBuilder.setField(getWrappedField(fieldSignature.getFieldType(), fieldSignature.getName()));
    msgBuilder.setThreadId(Thread.currentThread().getId());
    msgBuilder.setMsgType("Put field done");
    msgBuilder.setInstanceFieldPutDone(fieldBuilder);

    return msgBuilder.build();
  }

  public static Wrappers.DataMessage buildAccessibleObjectThrowableMessage(int concentratorId, AccessibleObject accessibleObject, Exception exception, Long followingOffset) {

    /** Build protobuf message **/
    final Wrappers.DataMessage.Builder msgBuilder = Wrappers.DataMessage.newBuilder();
    final Exceptions.RaisedThrowable.Builder thrBuilder = Exceptions.RaisedThrowable.newBuilder();
    thrBuilder.setConcentratorId(concentratorId);
    thrBuilder.setThreadId(Thread.currentThread().getId());
    thrBuilder.setCurrentTime(System.currentTimeMillis());
    thrBuilder.setClass_(getWrappedClass(exception.getClass().getName()));
    if (accessibleObject instanceof Constructor) {
      thrBuilder.setConstructor(((Constructor) accessibleObject).getDeclaringClass().getName());
      thrBuilder.setModifiers(((Constructor) accessibleObject).getModifiers());
    } else if (accessibleObject instanceof Method) {
      thrBuilder.setMethod(((Method) accessibleObject).getName());
      thrBuilder.setModifiers(((Method) accessibleObject).getModifiers());
    }
    thrBuilder.setThrowable(buildThrowableMessage(exception));

    msgBuilder.setThreadId(Thread.currentThread().getId());
    msgBuilder.setMsgType("Throwable");
    if (followingOffset != null) {
      msgBuilder.setFollowing(followingOffset);
    }
    msgBuilder.setRaisedThrowable(thrBuilder);

    return msgBuilder.build();
  }

  public static Wrappers.DataMessage buildInitializerThrowableMessage(int concentratorId, StaticPart staticPart, Exception exception) {

    /** Build protobuf message **/
    final Wrappers.DataMessage.Builder msgBuilder = Wrappers.DataMessage.newBuilder();
    final Exceptions.RaisedThrowable.Builder thrBuilder = Exceptions.RaisedThrowable.newBuilder();
    thrBuilder.setConcentratorId(concentratorId);
    thrBuilder.setThreadId(Thread.currentThread().getId());
    thrBuilder.setCurrentTime(System.currentTimeMillis());
    thrBuilder.setInInitializer(true);
    thrBuilder.setClass_(getWrappedClass(staticPart.getSignature().getDeclaringTypeName()));
    thrBuilder.setModifiers(staticPart.getSignature().getModifiers());
    thrBuilder.setThrowable(buildThrowableMessage(exception));

    msgBuilder.setThreadId(Thread.currentThread().getId());
    msgBuilder.setMsgType("Throwable");
    msgBuilder.setRaisedThrowable(thrBuilder);

    return msgBuilder.build();
  }

  public static Wrappers.DataMessage buildReturnValueMessage(int concentratorId, Object object, Class type, String objectKey, boolean isVoid, Long followingOffset) {

    /** Build protobuf message **/
    final Wrappers.DataMessage.Builder msgBuilder = Wrappers.DataMessage.newBuilder();

    final Values.ReturnValue.Builder valBuilder = Values.ReturnValue.newBuilder();
    valBuilder.setConcentratorId(concentratorId);
    valBuilder.setThreadId(Thread.currentThread().getId());
    valBuilder.setCurrentTime(System.currentTimeMillis());
    valBuilder.setIsVoid(isVoid);
    valBuilder.setClazz(getWrappedClass(type));
    if (!isVoid) {
      valBuilder.setObject(getWrappedObject(object, type, objectKey));
    }

    msgBuilder.setThreadId(Thread.currentThread().getId());
    msgBuilder.setMsgType("Return value");
    if (followingOffset != null) {
      msgBuilder.setFollowing(followingOffset);
    }
    msgBuilder.setReturnValue(valBuilder);

    return msgBuilder.build();
  }

  public static Wrappers.DataMessage buildLoadedClassMessage(int concentratorId, Class clazz) {

    /** Build protobuf message **/
    final Wrappers.DataMessage.Builder msgBuilder = Wrappers.DataMessage.newBuilder();

    final Values.ReturnValue.Builder valBuilder = Values.ReturnValue.newBuilder();
    valBuilder.setConcentratorId(concentratorId);
    valBuilder.setThreadId(Thread.currentThread().getId());
    valBuilder.setCurrentTime(System.currentTimeMillis());
    valBuilder.setIsClass(true);
    valBuilder.setClazz(getWrappedClass(clazz.getName()));

    msgBuilder.setThreadId(Thread.currentThread().getId());
    msgBuilder.setMsgType("Return class");
    msgBuilder.setReturnValue(valBuilder);

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

  /** WRAPPING METHODS:
   * Two versions of these exist, as we have generally more information when messages are built from local calls (with full reflection details),
   * than when these messaages are built for remote calls, and not all context and type information is available.
   */

  /**
   * Wrapped is the actual value if object is a primitive, a String, or an array of these types
   * Objects created by this Concentrator, are expected to be looked up in the object map by their identity hashCode.
   *
   * @param object
   * @return
   */
  private static Primitives.Object getWrappedObjectAux(Primitives.Object.Builder builder, Object object, Class clazz, String objectKey) {
    logger.traceEntry("with object: {}, class: {}, objectKey: {}", object, clazz, objectKey);

    //value is null if both object and objectKey are null
    builder.setIsNull(objectKey == null && object == null);

    //set required fields (class already set at this point)
    builder.setIdentityHash(System.identityHashCode(object));

    if (clazz != null && clazz.isArray()) {
      builder.setIsArray(true);
    }

    if (objectKey != null) {
      builder.setRef(objectKey);
    }

    if (object != null) {
      builder.setHash(object.hashCode());
      if (object instanceof String) {
        builder.setValue((String) object);
      } else if (object.getClass().isArray()) {
        builder.setIsArray(true);
        //TODO only handles 1-dimensional arrays ?? Check out Arrays.deepToString

        int length = Array.getLength(object);
        //NOTE: we iterate using reflection (Array) because the array type is unknown
        for (int i = 0; i < length; i++) {
          Object arrayElem = Array.get(object, i);
          //wrap and all array elements -- recursive
          builder.addArrayValue(getWrappedObject(arrayElem, arrayElem.getClass(), objectKey));
        }
      } else if (ClassUtils.isPrimitiveOrWrapper(object.getClass())) {
        builder.setValue(String.valueOf(object));
      } else {
        /** the object is not primitive, String or Array
         *  We set the isRef flag. We assume the object will be found in the objects map keyed with its identityHash, set below
         *  TODO: when object not created by this Concentrator, full (deep) serialization/deserialization will be required
         *  TODO: if it's of type Class, treat differently?
         **/
      }
    }

    Primitives.Object builtValue = builder.build();
    logger.traceExit("with wrappedValue: {}", builtValue);
    return builtValue;
  }

  /**
   * Called when we have only a class name
   *
   * @param object
   * @param className
   * @param objectKey
   * @return
   */
  private static Primitives.Object getWrappedObject(Object object, String className, String objectKey) {
    final Primitives.Object.Builder builder = Primitives.Object.newBuilder();
    logger.debug("entering (w/ className) for: {}", object);

    //set required fields
    builder.setClass_(getWrappedClass(className));

    return getWrappedObjectAux(builder, object, null, objectKey);
  }

  /**
   * Called when we have a full class object
   *
   * @param object
   * @param clazz
   * @param objectKey
   * @return
   */
  private static Primitives.Object getWrappedObject(Object object, Class clazz, String objectKey) {
    final Primitives.Object.Builder builder = Primitives.Object.newBuilder();
    logger.debug("entering (w/ class {}) for: {}", clazz.getName(), object);

    //set required fields
    builder.setClass_(getWrappedClass(clazz));

    return getWrappedObjectAux(builder, object, clazz, objectKey);
  }

  private static Primitives.Class getWrappedClass(String className) {
    final Primitives.Class.Builder clazzBuilder = Primitives.Class.newBuilder();
    if (className == null) {
      clazzBuilder.setUnknown(true);
    } else {
      clazzBuilder.setName(className);
    }
    return clazzBuilder.build();
  }

  private static Primitives.Class getWrappedClass(Class clazz) {
    final Primitives.Class.Builder clazzBuilder = Primitives.Class.newBuilder();
    if (clazz == null) {
      clazzBuilder.setUnknown(true);
    } else {
      clazzBuilder.setName(clazz.getName());
    }
    //TODO: fill all other available Class info
    return clazzBuilder.build();
  }

  private static Fields.Field getWrappedField(Class clazz, String fieldName) {
    Fields.Field.Builder fieldBuilder = Fields.Field.newBuilder();

    fieldBuilder.setName(fieldName);
    fieldBuilder.setClass_(getWrappedClass(clazz));
    return fieldBuilder.build();
  }

  private static Fields.Field getWrappedField(String className, String fieldName) {
    Fields.Field.Builder fieldBuilder = Fields.Field.newBuilder();

    fieldBuilder.setName(fieldName);
    fieldBuilder.setClass_(getWrappedClass(className));
    return fieldBuilder.build();
  }

  private static Ctxt.Context getWrappedContext(StaticPart staticPart, Object sender) {
    Ctxt.Context.Builder ctxtBuilder = Ctxt.Context.newBuilder();

    ctxtBuilder.setSenderClass(getWrappedClass(staticPart.getSourceLocation().getWithinType()));
    if (sender != null) {
      ctxtBuilder.setSender(getWrappedObject(sender, staticPart.getSourceLocation().getWithinType(), null));
    }
    ctxtBuilder.setSourceLocationFile(staticPart.getSourceLocation().getFileName());
    ctxtBuilder.setSourceLocationLine(staticPart.getSourceLocation().getLine());
    ctxtBuilder.setSourceLocationType(staticPart.getSourceLocation().getWithinType().getName());

    return ctxtBuilder.build();
  }

  private static boolean isMain(String methodName, Class returnType, Class[] paramTypes, int modifiers) {

    if ("main".equals(methodName) && returnType.equals(Void.class)) {
      return (paramTypes.length == 1 && paramTypes[0].equals(String[].class)
        && Modifier.isPublic(modifiers) && Modifier.isStatic(modifiers));
    }
    return false;
  }

  private static boolean isMain(String methodName, Class returnType, String[] paramTypes, int modifiers) {

    if ("main".equals(methodName) && returnType.equals(Void.class)) {
      return (paramTypes.length == 1 && paramTypes[0].equals("[Ljava.lang.String;")
        && Modifier.isPublic(modifiers) && Modifier.isStatic(modifiers));
    }
    return false;
  }
}
