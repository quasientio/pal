package com.ittera.cometa.concentrator.messages.protobuf;

import com.ittera.cometa.concentrator.messages.protobuf.data.Exceptions;
import com.ittera.cometa.concentrator.messages.protobuf.data.Fields;
import com.ittera.cometa.concentrator.messages.protobuf.data.Values;
import com.ittera.cometa.concentrator.messages.protobuf.data.Wrappers.DataMessage;
import com.ittera.cometa.concentrator.messages.protobuf.data.Fields.*;
import com.ittera.cometa.concentrator.messages.protobuf.data.Calls.*;
import com.ittera.cometa.concentrator.messages.protobuf.data.Values.*;

import org.aspectj.runtime.reflect.FieldSignatureImpl;
import org.aspectj.lang.reflect.CodeSignature;
import org.aspectj.lang.reflect.ConstructorSignature;
import org.aspectj.lang.reflect.MethodSignature;
import org.aspectj.lang.reflect.InitializerSignature;
import org.aspectj.lang.JoinPoint.StaticPart;

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.google.protobuf.Message;

/**
 * Methods of this class receive aspectj objects (i.e. StaticPart) as arguments as convenience.
 */
public final class DataMessageFactory {
  protected static final Logger logger = LogManager.getLogger(DataMessageFactory.class);

  private DataMessageFactory() {
    //avoid instantiation
  }

  //<editor-fold desc="Private Auxiliary methods">

  private static Message.Builder addParameter(Message.Builder callBuilder, String parameterType, Object arg, String argObjRef) {
    if (callBuilder instanceof ConstructorCall.Builder) {
      ((ConstructorCall.Builder) callBuilder).addParameter(Wrapper.getWrappedObject(arg, parameterType, argObjRef));
    } else if (callBuilder instanceof InstanceMethodCall.Builder) {
      ((InstanceMethodCall.Builder) callBuilder).addParameter(Wrapper.getWrappedObject(arg, parameterType, argObjRef));
    } else if (callBuilder instanceof ClassMethodCall.Builder) {
      ((ClassMethodCall.Builder) callBuilder).addParameter(Wrapper.getWrappedObject(arg, parameterType, argObjRef));
    } else {
      throw new UnsupportedOperationException(String.format("Unsupported Message.Builder class: %s ", callBuilder.getClass().getName()));
    }

    return callBuilder;
  }

  private static Message.Builder addParameters(Message.Builder callBuilder,
                                               String[] parameterTypes, Object[] args, String[] argObjRefs) {

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

  private static Message.Builder addNamedParameter(Message.Builder callBuilder, String paramName, String paramType, Object param) {
    if (callBuilder instanceof ConstructorCall.Builder) {
      ((ConstructorCall.Builder) callBuilder).addParameterName(paramName);
      ((ConstructorCall.Builder) callBuilder).addParameter(Wrapper.getWrappedObject(param, paramType, null));
    } else if (callBuilder instanceof InstanceMethodCall.Builder) {
      ((InstanceMethodCall.Builder) callBuilder).addParameterName(paramName);
      ((InstanceMethodCall.Builder) callBuilder).addParameter(Wrapper.getWrappedObject(param, paramType, null));
    } else if (callBuilder instanceof ClassMethodCall.Builder) {
      ((ClassMethodCall.Builder) callBuilder).addParameterName(paramName);
      ((ClassMethodCall.Builder) callBuilder).addParameter(Wrapper.getWrappedObject(param, paramType, null));
    } else {
      throw new UnsupportedOperationException(String.format("Unsupported Message.Builder class: %s ", callBuilder.getClass().getName()));
    }

    return callBuilder;
  }

  private static Message.Builder addParameters(Message.Builder callBuilder, StaticPart staticPart, Object[] args) {

    final CodeSignature codeSignature = (CodeSignature) staticPart.getSignature();
    for (int i = 0; codeSignature.getParameterTypes() != null && i < args.length; i++) {
      //TODO what about objectRefs?
      addNamedParameter(callBuilder, codeSignature.getParameterNames()[i], codeSignature.getParameterTypes()[i].getName(), args[i]);
    }

    return callBuilder;
  }


  private static DataMessage.Builder newWrapperBuilder(String msgType, Integer concentratorId, Long followingOffset) {

    DataMessage.Builder msgBuilder = DataMessage.newBuilder()
      .setConcentratorId(concentratorId)
      .setMsgType(msgType)
      .setThreadId(Thread.currentThread().getId())
      .setCurrentTime(System.currentTimeMillis());

    if (followingOffset != null) {
      msgBuilder.setFollowing(followingOffset);
    }

    return msgBuilder;
  }

  private static DataMessage.Builder newWrapperBuilder(String msgType, Integer concentratorId) {
    return newWrapperBuilder(msgType, concentratorId, null);
  }

  //</editor-fold>

  //<editor-fold desc="Class initialization messages">
  public static DataMessage buildClassInitializerMessage(int concentratorId, StaticPart staticPart, Object sender) {

    final InitializerSignature codeSignature = (InitializerSignature) staticPart.getSignature();

    final DataMessage.Builder msgBuilder = newWrapperBuilder("Static Constructor", concentratorId)
      .setClinitCall(ClInitCall.newBuilder()
        .setClass_(Wrapper.getWrappedClass(codeSignature.getDeclaringTypeName()))
        .setModifiers(codeSignature.getModifiers())
        .setContext(Wrapper.getWrappedContext(staticPart, sender)));

    return msgBuilder.build();
  }

  public static DataMessage buildLoadedClassMessage(int concentratorId, Class clazz) {

    final DataMessage.Builder msgBuilder = newWrapperBuilder("Return class", concentratorId)
      .setReturnValue(ReturnValue.newBuilder()
        .setIsClass(true)
        .setClazz(Wrapper.getWrappedClass(clazz.getName())));

    return msgBuilder.build();
  }

  //</editor-fold>

  //<editor-fold desc="Constructor messages">


  private final static DataMessage buildConstructorMessage(Integer concentratorId, String className, StaticPart staticPart, Object sender,
                                                           String[] parameterTypes, Object[] args, String[] argObjRefs) {

    final ConstructorCall.Builder constructorCallBuilder = ConstructorCall.newBuilder();
    if (staticPart != null) {
      final ConstructorSignature codeSignature = (ConstructorSignature) staticPart.getSignature();
      addParameters(constructorCallBuilder, staticPart, args);
      constructorCallBuilder.setModifiers(codeSignature.getModifiers());
      constructorCallBuilder.setContext(Wrapper.getWrappedContext(staticPart, sender));
      constructorCallBuilder.setClass_(Wrapper.getWrappedClass(codeSignature.getDeclaringTypeName()));
    } else {
      addParameters(constructorCallBuilder, parameterTypes, args, argObjRefs);
      constructorCallBuilder.setClass_(Wrapper.getWrappedClass(className));
    }

    final DataMessage.Builder msgBuilder = newWrapperBuilder("Constructor", Integer.valueOf(concentratorId))
      .setConstructorCall(constructorCallBuilder);

    return msgBuilder.build();
  }

  /**
   * This method is to be called when no joinpoint context is available.
   */
  public static DataMessage buildEmptyConstructorMessage(String concentratorId, String className) {

    return buildConstructorMessage(Integer.valueOf(concentratorId), className, null, null, null, null, null);
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

    return buildConstructorMessage(Integer.valueOf(concentratorId), className, null, null, parameterTypes, args, argObjRefs);
  }

  public static DataMessage buildConstructorMessage(int concentratorId, StaticPart staticPart, Object sender, Object[] args) {

    return buildConstructorMessage(concentratorId, null, staticPart, sender, null, args, null);
  }
  //</editor-fold>

  //<editor-fold desc="Instance method messages">

  /**
   * This method is to be called when no joinpoint context is available.
   */
  public static DataMessage buildInstanceMethodMessage(String concentratorId, String className, String methodName, String objRef, String[] parameterTypes, Object[] args, String[] argObjRefs) {

    final InstanceMethodCall.Builder callBuilder = InstanceMethodCall.newBuilder();
    addParameters(callBuilder, parameterTypes, args, argObjRefs);

    final DataMessage.Builder msgBuilder = newWrapperBuilder("Instance method", Integer.valueOf(concentratorId))
      .setInstanceMethodCall(callBuilder
        .setClass_(Wrapper.getWrappedClass(className))
        .setName(methodName)
        .setObjectRef(objRef));

    return msgBuilder.build();
  }


  public static DataMessage buildInstanceMethodMessage(int concentratorId, StaticPart staticPart, Object sender, Object target, Object[] args) {

    final MethodSignature codeSignature = (MethodSignature) staticPart.getSignature();

    final InstanceMethodCall.Builder callBuilder = InstanceMethodCall.newBuilder();
    addParameters(callBuilder, staticPart, args);

    final DataMessage.Builder msgBuilder = newWrapperBuilder("Instance method", concentratorId)
      .setInstanceMethodCall(callBuilder
        .setClass_(Wrapper.getWrappedClass(codeSignature.getDeclaringTypeName()))
        .setName(codeSignature.getName())
        .setObject(Wrapper.getWrappedObject(target, codeSignature.getDeclaringTypeName(), null))
        .setModifiers(codeSignature.getModifiers())
        .setContext(Wrapper.getWrappedContext(staticPart, sender)));

    return msgBuilder.build();
  }
  //</editor-fold>

  //<editor-fold desc="Class method messages">

  /**
   * This method is to be called when no joinpoint context is available.
   */
  public static DataMessage buildClassMethodMessage(String concentratorId, String className, String methodName, String[] parameterTypes, Object[] args, String[] argObjRefs) {

    final ClassMethodCall.Builder callBuilder = ClassMethodCall.newBuilder();
    addParameters(callBuilder, parameterTypes, args, argObjRefs);

    final DataMessage.Builder msgBuilder = newWrapperBuilder("Class method", Integer.valueOf(concentratorId))
      .setClassMethodCall(callBuilder
        .setClass_(Wrapper.getWrappedClass(className))
        .setName(methodName));

    return msgBuilder.build();

  }

  public static DataMessage buildClassMethodMessage(int concentratorId, StaticPart staticPart, Object sender, Object[] args) {

    final MethodSignature codeSignature = (MethodSignature) staticPart.getSignature();

    final ClassMethodCall.Builder callBuilder = ClassMethodCall.newBuilder();
    addParameters(callBuilder, staticPart, args);

    final DataMessage.Builder msgBuilder = newWrapperBuilder("Class method", concentratorId)
      .setClassMethodCall(callBuilder
        .setContext(Wrapper.getWrappedContext(staticPart, sender))
        .setClass_(Wrapper.getWrappedClass(codeSignature.getDeclaringTypeName()))
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

    final DataMessage.Builder msgBuilder = newWrapperBuilder("Get static", Integer.valueOf(concentratorId))
      .setStaticFieldGet(StaticFieldGet.newBuilder()
        .setClass_(Wrapper.getWrappedClass(className))
        .setField(Wrapper.getWrappedField(className, fieldName)));

    return msgBuilder.build();
  }

  public static DataMessage buildGetStaticMessage(int concentratorId, StaticPart staticPart, Object sender) {

    final FieldSignatureImpl fieldSignature = (FieldSignatureImpl) staticPart.getSignature();

    final DataMessage.Builder msgBuilder = newWrapperBuilder("Get static", concentratorId)
      .setStaticFieldGet(StaticFieldGet.newBuilder()
        .setClass_(Wrapper.getWrappedClass(fieldSignature.getDeclaringTypeName()))
        .setField(Wrapper.getWrappedField(fieldSignature.getFieldType(), fieldSignature.getName()))
        .setModifiers(fieldSignature.getModifiers())
        .setContext(Wrapper.getWrappedContext(staticPart, sender)));

    return msgBuilder.build();
  }
  //</editor-fold>

  //<editor-fold desc="Instance field get messages">

  /**
   * This method is to be called when no joinpoint context is available.
   */
  public static DataMessage buildGetObjectMessage(String concentratorId, String className, String fieldName, String targetObjRef) {

    final DataMessage.Builder msgBuilder = newWrapperBuilder("Get field", Integer.valueOf(concentratorId))
      .setInstanceFieldGet(InstanceFieldGet.newBuilder()
        .setClass_(Wrapper.getWrappedClass(className))
        .setObjectRef(targetObjRef)
        .setField(Wrapper.getWrappedField((String) null, fieldName)));

    return msgBuilder.build();
  }

  public static DataMessage buildGetObjectMessage(int concentratorId, StaticPart staticPart, Object sender, Object target) {

    final FieldSignatureImpl fieldSignature = (FieldSignatureImpl) staticPart.getSignature();

    final DataMessage.Builder msgBuilder = newWrapperBuilder("Get field", concentratorId)
      .setInstanceFieldGet(InstanceFieldGet.newBuilder()
        .setClass_(Wrapper.getWrappedClass(fieldSignature.getDeclaringTypeName()))
        .setObject(Wrapper.getWrappedObject(target, fieldSignature.getDeclaringTypeName(), null))
        .setField(Wrapper.getWrappedField(fieldSignature.getFieldType(), fieldSignature.getName()))
        .setModifiers(fieldSignature.getModifiers())
        .setContext(Wrapper.getWrappedContext(staticPart, sender)));

    return msgBuilder.build();
  }
  //</editor-fold>

  //<editor-fold desc="Static field put messages">

  /**
   * This method is to be called when no joinpoint context is available.
   */
  public static DataMessage buildPutStaticMessage(String concentratorId, String className, String fieldName, String valueClassName, Object value) {

    final DataMessage.Builder msgBuilder = newWrapperBuilder("Put static", Integer.valueOf(concentratorId))
      .setStaticFieldPut(StaticFieldPut.newBuilder()
        .setClass_(Wrapper.getWrappedClass(className))
        .setField(Wrapper.getWrappedField((String) null, fieldName))
        .setObject(Wrapper.getWrappedObject(value, valueClassName, null)));

    return msgBuilder.build();
  }

  /**
   * This method is to be called when no joinpoint context is available.
   * Equivalent to the above, for objectRefs
   */
  public static DataMessage buildPutStaticMessage(String concentratorId, String className, String fieldName, String objectRef) {

    final DataMessage.Builder msgBuilder = newWrapperBuilder("Put static", Integer.valueOf(concentratorId))
      .setStaticFieldPut(StaticFieldPut.newBuilder()
        .setClass_(Wrapper.getWrappedClass(className))
        .setField(Wrapper.getWrappedField((String) null, fieldName))
        .setObjectRef(objectRef));

    return msgBuilder.build();
  }

  public static DataMessage buildPutStaticMessage(int concentratorId, StaticPart staticPart, Object sender, Object arg) {

    final FieldSignatureImpl fieldSignature = (FieldSignatureImpl) staticPart.getSignature();

    final DataMessage.Builder msgBuilder = newWrapperBuilder("Put static", concentratorId)
      .setStaticFieldPut(StaticFieldPut.newBuilder()
        .setClass_(Wrapper.getWrappedClass(fieldSignature.getDeclaringType()))
        .setField(Wrapper.getWrappedField(fieldSignature.getFieldType(), fieldSignature.getName()))
        .setObject(Wrapper.getWrappedObject(arg, fieldSignature.getFieldType(), null))
        .setModifiers(fieldSignature.getModifiers())
        .setContext(Wrapper.getWrappedContext(staticPart, sender)));

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
      fieldBuilder.setField(Wrapper.getWrappedField(fieldType, staticFieldPut.getField().getName()));
    }

    final DataMessage.Builder msgBuilder = newWrapperBuilder("Put static done", concentratorId, followingOffset)
      .setStaticFieldPutDone(fieldBuilder
        .setClass_(Wrapper.getWrappedClass(staticFieldPut.getClass_().getName()))
        .setStaticFieldPut(staticFieldPut));

    return msgBuilder.build();
  }


  public static DataMessage buildPutStaticDoneMessage(int concentratorId, StaticPart staticPart, Object sender, Object arg) {

    final FieldSignatureImpl fieldSignature = (FieldSignatureImpl) staticPart.getSignature();

    final DataMessage.Builder msgBuilder = newWrapperBuilder("Put static done", concentratorId)
      .setStaticFieldPutDone(StaticFieldPutDone.newBuilder()
        .setField(Wrapper.getWrappedField(fieldSignature.getFieldType(), fieldSignature.getName()))
        .setClass_(Wrapper.getWrappedClass(fieldSignature.getDeclaringType())));

    return msgBuilder.build();
  }
  //</editor-fold>

  //<editor-fold desc="Instance field put messages">

  /**
   * This method is to be called when no joinpoint context is available.
   * Equivalent to the above, for objectRefs
   */
  public static DataMessage buildPutObjectMessage(String concentratorId, String className, String fieldName, String targetObjRef, String valueClassName, Object value) {

    final DataMessage.Builder msgBuilder = newWrapperBuilder("Put field", Integer.valueOf(concentratorId), null)
      .setInstanceFieldPut(InstanceFieldPut.newBuilder()
        .setClass_(Wrapper.getWrappedClass(className))
        .setObjectRef(targetObjRef)
        .setField(Wrapper.getWrappedField((String) null, fieldName))
        .setValueObject(Wrapper.getWrappedObject(value, valueClassName, null)));

    return msgBuilder.build();
  }

  /**
   * This method is to be called when no joinpoint context is available.
   * Equivalent to the above, for objectRefs
   */
  public static DataMessage buildPutObjectMessage(String concentratorId, String className, String fieldName, String targetObjRef, String valueObjRef) {

    final DataMessage.Builder msgBuilder = newWrapperBuilder("Put field", Integer.valueOf(concentratorId))
      .setInstanceFieldPut(InstanceFieldPut.newBuilder()
        .setClass_(Wrapper.getWrappedClass(className))
        .setObjectRef(targetObjRef)
        .setField(Wrapper.getWrappedField((String) null, fieldName))
        .setValueObjectRef(valueObjRef));

    return msgBuilder.build();
  }

  public static DataMessage buildPutObjectMessage(int concentratorId, StaticPart staticPart, Object sender, Object target, Object arg) {

    final FieldSignatureImpl fieldSignature = (FieldSignatureImpl) staticPart.getSignature();

    final DataMessage.Builder msgBuilder = newWrapperBuilder("Put field", concentratorId)
      .setInstanceFieldPut(InstanceFieldPut.newBuilder()
        .setClass_(Wrapper.getWrappedClass(fieldSignature.getDeclaringType()))
        .setObject(Wrapper.getWrappedObject(target, fieldSignature.getDeclaringType(), null))
        .setField(Wrapper.getWrappedField(fieldSignature.getFieldType(), fieldSignature.getName()))
        .setValueObject(Wrapper.getWrappedObject(arg, fieldSignature.getFieldType().getName(), null))
        .setModifiers(fieldSignature.getModifiers())
        .setContext(Wrapper.getWrappedContext(staticPart, sender)));

    return msgBuilder.build();
  }

  public static DataMessage buildPutObjectDoneMessage(int concentratorId, Fields.InstanceFieldPut instanceFieldPut, Class fieldType, Long followingOffset) {

    final Fields.InstanceFieldPutDone.Builder fieldBuilder = InstanceFieldPutDone.newBuilder();
    if (instanceFieldPut.getField().hasClass_()) {
      fieldBuilder.setField(instanceFieldPut.getField());
    } else {
      fieldBuilder.setField(Wrapper.getWrappedField(fieldType, instanceFieldPut.getField().getName()));
    }

    final DataMessage.Builder msgBuilder = newWrapperBuilder("Put field done", concentratorId, followingOffset)
      .setInstanceFieldPutDone(fieldBuilder
        .setClass_(Wrapper.getWrappedClass(instanceFieldPut.getClass_().getName()))
        .setInstanceFieldPut(instanceFieldPut));

    return msgBuilder.build();
  }

  public static DataMessage buildPutObjectDoneMessage(int concentratorId, StaticPart staticPart, Object sender, Object target, Object arg) {

    final FieldSignatureImpl fieldSignature = (FieldSignatureImpl) staticPart.getSignature();

    final DataMessage.Builder msgBuilder = newWrapperBuilder("Put field done", concentratorId)
      .setInstanceFieldPutDone(InstanceFieldPutDone.newBuilder()
        .setClass_(Wrapper.getWrappedClass(fieldSignature.getDeclaringType()))
        .setField(Wrapper.getWrappedField(fieldSignature.getFieldType(), fieldSignature.getName())));

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
    } else {
      throw new UnsupportedOperationException(String.format("Unsupported accessibleObject type: %s", accessibleObject.getClass().getName()));
    }

    final DataMessage.Builder msgBuilder = newWrapperBuilder("Throwable", concentratorId, followingOffset)
      .setRaisedThrowable(thrBuilder
        .setClass_(Wrapper.getWrappedClass(exception.getClass().getName()))
        .setThrowable(buildThrowableMessage(exception)));

    return msgBuilder.build();
  }

  public static DataMessage buildInitializerThrowableMessage(int concentratorId, StaticPart staticPart, Exception exception) {

    final DataMessage.Builder msgBuilder = newWrapperBuilder("Throwable", concentratorId)
      .setRaisedThrowable(Exceptions.RaisedThrowable.newBuilder()
        .setInInitializer(true)
        .setClass_(Wrapper.getWrappedClass(staticPart.getSignature().getDeclaringTypeName()))
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

    final ReturnValue.Builder contentBuilder = Values.ReturnValue.newBuilder();
    if (!isVoid) {
      ((Values.ReturnValue.Builder) contentBuilder).setObject(Wrapper.getWrappedObject(object, type, objectKey));
    }

    final DataMessage.Builder msgBuilder = newWrapperBuilder("Return value", concentratorId, followingOffset)
      .setReturnValue(contentBuilder
        .setIsVoid(isVoid)
        .setClazz(Wrapper.getWrappedClass(type)));

    return msgBuilder.build();
  }
  //</editor-fold>
}
