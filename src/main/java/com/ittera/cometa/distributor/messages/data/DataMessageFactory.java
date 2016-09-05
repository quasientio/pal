package com.ittera.cometa.distributor.messages.data;

import org.aspectj.runtime.reflect.FieldSignatureImpl;
import org.aspectj.lang.reflect.ConstructorSignature;
import org.aspectj.lang.reflect.MethodSignature;
import org.aspectj.lang.reflect.InitializerSignature;
import org.aspectj.lang.JoinPoint.StaticPart;

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

/**
 * Methods of this class receive aspectj objects (i.e. StaticPart) as arguments as convenience.
 * TO DO: Unwrapp the necessary arguments in the caller (Distributor) to make this class agnostic
 */
public class DataMessageFactory {
  protected static final Logger logger = LogManager.getLogger(DataMessageFactory.class);

  protected static final int STRING_MAX_LEN = 50;

  public static Wrappers.DataMessage buildClassInitializerMessage(int distributorId, StaticPart staticPart, Object sender) {

    /** Build protobuf message **/
    final InitializerSignature codeSignature = (InitializerSignature) staticPart.getSignature();

    final Wrappers.DataMessage.Builder msgBuilder = Wrappers.DataMessage.newBuilder();

    final Calls.ClInitCall.Builder callBuilder = Calls.ClInitCall.newBuilder();
    callBuilder.setDistributorId(distributorId); //1
    callBuilder.setThreadId(Thread.currentThread().getId()); //2
    callBuilder.setCurrentTime(System.currentTimeMillis()); //3
    callBuilder.setName(codeSignature.getDeclaringTypeName()); //4
    callBuilder.setModifiers(codeSignature.getModifiers()); //5
    callBuilder.setSenderClassName(staticPart.getSourceLocation().getWithinType().getName()); //6
    callBuilder.setSender(System.identityHashCode(sender)); //7
    callBuilder.setSourceLocationFile(staticPart.getSourceLocation().getFileName()); //8
    callBuilder.setSourceLocationLine(staticPart.getSourceLocation().getLine()); //9
    callBuilder.setSourceLocationType(staticPart.getSourceLocation().getWithinType().getCanonicalName()); //10

    msgBuilder.setThreadId(Thread.currentThread().getId());
    msgBuilder.setMsgType("Static Constructor");
    msgBuilder.setClinitCall(callBuilder);

    return msgBuilder.build();
  }

  public static Wrappers.DataMessage buildConstructorMessage(int distributorId, StaticPart staticPart, Object sender, Object[] args) {

    /** Build protobuf message **/
    final ConstructorSignature codeSignature = (ConstructorSignature) staticPart.getSignature();
    final Wrappers.DataMessage.Builder msgBuilder = Wrappers.DataMessage.newBuilder();

    final Calls.ConstructorCall.Builder callBuilder = Calls.ConstructorCall.newBuilder();
    callBuilder.setDistributorId(distributorId); //1
    callBuilder.setThreadId(Thread.currentThread().getId()); //2
    callBuilder.setCurrentTime(System.currentTimeMillis()); //3
    callBuilder.setName(codeSignature.getDeclaringTypeName()); //4
    callBuilder.setModifiers(codeSignature.getModifiers()); //5
    //6
    for (String name : codeSignature.getParameterNames()) {
      callBuilder.addParameterNames(name);
    }
    //7
    for (Class clazz : codeSignature.getParameterTypes()) {
      callBuilder.addParameterClasses(clazz.getName());
    }
    //8
    for (Class clazz : codeSignature.getExceptionTypes()) {
      callBuilder.addExceptionTypes(clazz.getPackage().getName() + "." + clazz.getName());
    }
    //9
    for (Object param : args) {
      callBuilder.addParameter(getWrappedValue(param, true));
    }
    callBuilder.setSenderClassName(staticPart.getSourceLocation().getWithinType().getName()); //10
    callBuilder.setSender(System.identityHashCode(sender)); //11
    callBuilder.setSourceLocationFile(staticPart.getSourceLocation().getFileName()); //12
    callBuilder.setSourceLocationLine(staticPart.getSourceLocation().getLine()); //13
    callBuilder.setSourceLocationType(staticPart.getSourceLocation().getWithinType().getCanonicalName()); //14

    msgBuilder.setThreadId(Thread.currentThread().getId());
    msgBuilder.setMsgType("Constructor");
    msgBuilder.setConstructorCall(callBuilder);

    return msgBuilder.build();
  }


  public static Wrappers.DataMessage buildInstanceMethodMessage(int distributorId, StaticPart staticPart, Object sender, Object target, Object[] args) {

    /** Build protobuf message **/
    final MethodSignature codeSignature = (MethodSignature) staticPart.getSignature();

    final Wrappers.DataMessage.Builder msgBuilder = Wrappers.DataMessage.newBuilder();

    final Calls.InstanceMethodCall.Builder callBuilder = Calls.InstanceMethodCall.newBuilder();
    callBuilder.setDistributorId(distributorId); //1
    callBuilder.setThreadId(Thread.currentThread().getId()); //2
    callBuilder.setCurrentTime(System.currentTimeMillis()); //3
    callBuilder.setClass_(codeSignature.getDeclaringTypeName()); //4
    callBuilder.setName(codeSignature.getName()); //5
    callBuilder.setTarget(System.identityHashCode(target)); //6
    callBuilder.setModifiers(codeSignature.getModifiers()); //7
    //8
    for (String name : codeSignature.getParameterNames()) {
      callBuilder.addParameterNames(name);
    }
    //9
    for (Class clazz : codeSignature.getParameterTypes()) {
      callBuilder.addParameterClasses(clazz.getName());
    }
    //10
    for (Class clazz : codeSignature.getExceptionTypes()) {
      callBuilder.addExceptionTypes(clazz.getPackage().getName() + "." + clazz.getName());
    }
    //11
    for (Object param : args) {
      callBuilder.addParameter(getWrappedValue(param, true));
    }
    callBuilder.setSenderClassName(staticPart.getSourceLocation().getWithinType().getName()); //12
    callBuilder.setSender(System.identityHashCode(sender)); //13
    callBuilder.setSourceLocationFile(staticPart.getSourceLocation().getFileName()); //14
    callBuilder.setSourceLocationLine(staticPart.getSourceLocation().getLine()); //15
    callBuilder.setSourceLocationType(staticPart.getSourceLocation().getWithinType().getCanonicalName()); //16

    msgBuilder.setThreadId(Thread.currentThread().getId());
    msgBuilder.setMsgType("Instance method");
    msgBuilder.setInstanceMethodCall(callBuilder);

    return msgBuilder.build();
  }


  /**
   * This method is to be called when no joinpoint context is available (calling class hasn't been weaved). Example of caller: AppLauncher
   */
  public static Wrappers.DataMessage buildClassMethodMessage(String distributorId, String className, String methodName, int modifiers, Class returnType, Class[] parameterTypes, Object[] args) {
    final Wrappers.DataMessage.Builder msgBuilder = Wrappers.DataMessage.newBuilder();

    final Calls.ClassMethodCall.Builder callBuilder = Calls.ClassMethodCall.newBuilder();
    callBuilder.setDistributorId(Integer.parseInt(distributorId)); //1
    callBuilder.setThreadId(Thread.currentThread().getId());
    callBuilder.setCurrentTime(System.currentTimeMillis()); //3
    callBuilder.setClass_(className); //4
    callBuilder.setName(methodName); //5
    for (Class paramType : parameterTypes) {
      callBuilder.addParameterClasses(paramType.getName());
    }
    boolean isMain = isMain(methodName, returnType, parameterTypes, modifiers);
    if (!isMain) {
      throw new IllegalArgumentException("Currently only calls to psvm can be wrapped by this method");
    }
    for (Object param : args) {
      callBuilder.addParameter(getWrappedValue(param, !isMain));
    }

    msgBuilder.setThreadId(Thread.currentThread().getId());
    msgBuilder.setMsgType("Class method");
    msgBuilder.setClassMethodCall(callBuilder);

    return msgBuilder.build();

  }

  public static Wrappers.DataMessage buildClassMethodMessage(int distributorId, StaticPart staticPart, Object sender, Object[] args) {

    /** Build protobuf message **/
    final MethodSignature codeSignature = (MethodSignature) staticPart.getSignature();

    final Wrappers.DataMessage.Builder msgBuilder = Wrappers.DataMessage.newBuilder();

    final Calls.ClassMethodCall.Builder callBuilder = Calls.ClassMethodCall.newBuilder();
    callBuilder.setDistributorId(distributorId); //1
    callBuilder.setThreadId(Thread.currentThread().getId()); //2
    callBuilder.setCurrentTime(System.currentTimeMillis()); //3
    callBuilder.setClass_(codeSignature.getDeclaringTypeName()); //4
    callBuilder.setName(codeSignature.getName()); //5
    callBuilder.setModifiers(codeSignature.getModifiers()); //6
    //7
    for (String name : codeSignature.getParameterNames()) {
      callBuilder.addParameterNames(name);
    }
    //8
    for (Class clazz : codeSignature.getParameterTypes()) {
      callBuilder.addParameterClasses(clazz.getName());
    }
    //9
    for (Class clazz : codeSignature.getExceptionTypes()) {
      callBuilder.addExceptionTypes(clazz.getPackage().getName() + "." + clazz.getName());
    }
    //10
    boolean methodIsMain = isMain(staticPart);
    for (Object param : args) {
      callBuilder.addParameter(getWrappedValue(param, !methodIsMain));
    }
    callBuilder.setSenderClassName(staticPart.getSourceLocation().getWithinType().getName()); //11
    callBuilder.setSender(System.identityHashCode(sender)); //12
    callBuilder.setSourceLocationFile(staticPart.getSourceLocation().getFileName()); //13
    callBuilder.setSourceLocationLine(staticPart.getSourceLocation().getLine()); //14
    callBuilder.setSourceLocationType(staticPart.getSourceLocation().getWithinType().getCanonicalName()); //15

    msgBuilder.setThreadId(Thread.currentThread().getId());
    msgBuilder.setMsgType("Class method");
    msgBuilder.setClassMethodCall(callBuilder);

    return msgBuilder.build();
  }


  public static Wrappers.DataMessage buildGetStaticMessage(int distributorId, StaticPart staticPart, Object sender) {

    /** Build protobuf message **/
    final FieldSignatureImpl fieldSignature = (FieldSignatureImpl) staticPart.getSignature();

    final Wrappers.DataMessage.Builder msgBuilder = Wrappers.DataMessage.newBuilder();

    final Fields.StaticFieldGet.Builder fieldBuilder = Fields.StaticFieldGet.newBuilder();
    fieldBuilder.setDistributorId(distributorId); //1
    fieldBuilder.setThreadId(Thread.currentThread().getId()); //2
    fieldBuilder.setCurrentTime(System.currentTimeMillis()); //3
    fieldBuilder.setClass_(fieldSignature.getDeclaringTypeName()); //4
    fieldBuilder.setField(fieldSignature.getName()); //5
    fieldBuilder.setFieldType(fieldSignature.getFieldType().getCanonicalName()); //6
    fieldBuilder.setModifiers(fieldSignature.getModifiers()); //7
    fieldBuilder.setSenderClassName(staticPart.getSourceLocation().getWithinType().getName()); //8
    fieldBuilder.setSender(getWrappedValue(sender, true)); //9
    fieldBuilder.setSourceLocationFile(staticPart.getSourceLocation().getFileName()); //10
    fieldBuilder.setSourceLocationLine(staticPart.getSourceLocation().getLine()); //11
    fieldBuilder.setSourceLocationType(staticPart.getSourceLocation().getWithinType().getCanonicalName()); //12

    msgBuilder.setThreadId(Thread.currentThread().getId());
    msgBuilder.setMsgType("Get static");
    msgBuilder.setStaticFieldGet(fieldBuilder);

    return msgBuilder.build();
  }

  public static Wrappers.DataMessage buildGetObjectMessage(int distributorId, StaticPart staticPart, Object sender, Object target) {

    /** Build protobuf message **/
    final FieldSignatureImpl fieldSignature = (FieldSignatureImpl) staticPart.getSignature();

    final Wrappers.DataMessage.Builder msgBuilder = Wrappers.DataMessage.newBuilder();

    final Fields.InstanceFieldGet.Builder fieldBuilder = Fields.InstanceFieldGet.newBuilder();
    fieldBuilder.setDistributorId(distributorId); //1
    fieldBuilder.setThreadId(Thread.currentThread().getId()); //2
    fieldBuilder.setCurrentTime(System.currentTimeMillis()); //3
    fieldBuilder.setClass_(fieldSignature.getDeclaringTypeName()); //4
    fieldBuilder.setTarget(getWrappedValue(target, true)); //5
    fieldBuilder.setField(fieldSignature.getName()); //6
    fieldBuilder.setFieldType(fieldSignature.getFieldType().getCanonicalName()); //7
    fieldBuilder.setModifiers(fieldSignature.getModifiers()); //8
    fieldBuilder.setSenderClassName(staticPart.getSourceLocation().getWithinType().getName()); //9
    fieldBuilder.setSender(getWrappedValue(sender, true)); //10
    fieldBuilder.setSourceLocationFile(staticPart.getSourceLocation().getFileName()); //11
    fieldBuilder.setSourceLocationLine(staticPart.getSourceLocation().getLine()); //12
    fieldBuilder.setSourceLocationType(staticPart.getSourceLocation().getWithinType().getCanonicalName()); //13

    msgBuilder.setThreadId(Thread.currentThread().getId());
    msgBuilder.setMsgType("Get field");
    msgBuilder.setInstanceFieldGet(fieldBuilder);

    return msgBuilder.build();
  }

  public static Wrappers.DataMessage buildPutStaticMessage(int distributorId, StaticPart staticPart, Object sender, Object arg) {

    /** Build protobuf message **/
    final FieldSignatureImpl fieldSignature = (FieldSignatureImpl) staticPart.getSignature();

    final Wrappers.DataMessage.Builder msgBuilder = Wrappers.DataMessage.newBuilder();

    final Fields.StaticFieldPut.Builder fieldBuilder = Fields.StaticFieldPut.newBuilder();
    fieldBuilder.setDistributorId(distributorId); //1
    fieldBuilder.setThreadId(Thread.currentThread().getId()); //2
    fieldBuilder.setCurrentTime(System.currentTimeMillis()); //3
    fieldBuilder.setClass_(fieldSignature.getDeclaringTypeName()); //4
    fieldBuilder.setField(fieldSignature.getName()); //5
    fieldBuilder.setFieldType(fieldSignature.getFieldType().getCanonicalName()); //6
    fieldBuilder.setValue(getWrappedValue(arg, true)); //7
    fieldBuilder.setModifiers(fieldSignature.getModifiers()); //8
    fieldBuilder.setSenderClassName(staticPart.getSourceLocation().getWithinType().getName()); //9
    fieldBuilder.setSender(getWrappedValue(sender, true)); //10
    fieldBuilder.setSourceLocationFile(staticPart.getSourceLocation().getFileName()); //11
    fieldBuilder.setSourceLocationLine(staticPart.getSourceLocation().getLine()); //12
    fieldBuilder.setSourceLocationType(staticPart.getSourceLocation().getWithinType().getCanonicalName()); //13

    msgBuilder.setThreadId(Thread.currentThread().getId());
    msgBuilder.setMsgType("Put static");
    msgBuilder.setStaticFieldPut(fieldBuilder);

    return msgBuilder.build();
  }

  public static Wrappers.DataMessage buildPutStaticDoneMessage(int distributorId, StaticPart staticPart, Object sender, Object arg) {

    /** Build protobuf message **/
    final FieldSignatureImpl fieldSignature = (FieldSignatureImpl) staticPart.getSignature();

    final Wrappers.DataMessage.Builder msgBuilder = Wrappers.DataMessage.newBuilder();

    final Fields.StaticFieldPutDone.Builder fieldBuilder = Fields.StaticFieldPutDone.newBuilder();
    fieldBuilder.setDistributorId(distributorId); //1
    fieldBuilder.setThreadId(Thread.currentThread().getId()); //2
    fieldBuilder.setCurrentTime(System.currentTimeMillis()); //3
    fieldBuilder.setClass_(fieldSignature.getDeclaringTypeName()); //4
    fieldBuilder.setField(fieldSignature.getName()); //5
    fieldBuilder.setFieldType(fieldSignature.getFieldType().getCanonicalName()); //6
    fieldBuilder.setValue(getWrappedValue(arg, true)); //7
    fieldBuilder.setModifiers(fieldSignature.getModifiers()); //8
    fieldBuilder.setSenderClassName(staticPart.getSourceLocation().getWithinType().getName()); //9
    fieldBuilder.setSender(getWrappedValue(sender, true)); //10

    msgBuilder.setThreadId(Thread.currentThread().getId());
    msgBuilder.setMsgType("Put static done");
    msgBuilder.setStaticFieldPutDone(fieldBuilder);

    return msgBuilder.build();
  }

  public static Wrappers.DataMessage buildPutObjectMessage(int distributorId, StaticPart staticPart, Object sender, Object target, Object arg) {

    /** Build protobuf message **/
    final FieldSignatureImpl fieldSignature = (FieldSignatureImpl) staticPart.getSignature();

    final Wrappers.DataMessage.Builder msgBuilder = Wrappers.DataMessage.newBuilder();

    final Fields.InstanceFieldPut.Builder fieldBuilder = Fields.InstanceFieldPut.newBuilder();
    fieldBuilder.setDistributorId(distributorId); //1
    fieldBuilder.setThreadId(Thread.currentThread().getId()); //2
    fieldBuilder.setCurrentTime(System.currentTimeMillis()); //3
    fieldBuilder.setClass_(fieldSignature.getDeclaringTypeName()); //4
    fieldBuilder.setTarget(getWrappedValue(target, true)); //5
    fieldBuilder.setField(fieldSignature.getName()); //6
    fieldBuilder.setFieldType(fieldSignature.getFieldType().getCanonicalName()); //7
    fieldBuilder.setValue(getWrappedValue(arg, true)); //8
    fieldBuilder.setModifiers(fieldSignature.getModifiers()); //9
    fieldBuilder.setSenderClassName(staticPart.getSourceLocation().getWithinType().getName()); //10
    fieldBuilder.setSender(getWrappedValue(sender, true)); //11
    fieldBuilder.setSourceLocationFile(staticPart.getSourceLocation().getFileName()); //12
    fieldBuilder.setSourceLocationLine(staticPart.getSourceLocation().getLine()); //13
    fieldBuilder.setSourceLocationType(staticPart.getSourceLocation().getWithinType().getCanonicalName()); //14

    msgBuilder.setThreadId(Thread.currentThread().getId());
    msgBuilder.setMsgType("Put field");
    msgBuilder.setInstanceFieldPut(fieldBuilder);

    return msgBuilder.build();
  }

  public static Wrappers.DataMessage buildPutObjectDoneMessage(int distributorId, StaticPart staticPart, Object sender, Object target, Object arg) {

    /** Build protobuf message **/
    final FieldSignatureImpl fieldSignature = (FieldSignatureImpl) staticPart.getSignature();

    final Wrappers.DataMessage.Builder msgBuilder = Wrappers.DataMessage.newBuilder();

    final Fields.InstanceFieldPutDone.Builder fieldBuilder = Fields.InstanceFieldPutDone.newBuilder();
    fieldBuilder.setDistributorId(distributorId); //1
    fieldBuilder.setThreadId(Thread.currentThread().getId()); //2
    fieldBuilder.setCurrentTime(System.currentTimeMillis()); //3
    fieldBuilder.setClass_(fieldSignature.getDeclaringTypeName()); //4
    fieldBuilder.setTarget(getWrappedValue(target, true)); //5
    fieldBuilder.setField(fieldSignature.getName()); //6
    fieldBuilder.setFieldType(fieldSignature.getFieldType().getCanonicalName()); //7
    fieldBuilder.setValue(getWrappedValue(arg, true)); //8
    fieldBuilder.setModifiers(fieldSignature.getModifiers()); //9
    fieldBuilder.setSenderClassName(staticPart.getSourceLocation().getWithinType().getName()); //10
    fieldBuilder.setSender(getWrappedValue(sender, true)); //11

    msgBuilder.setThreadId(Thread.currentThread().getId());
    msgBuilder.setMsgType("Put field done");
    msgBuilder.setInstanceFieldPutDone(fieldBuilder);

    return msgBuilder.build();
  }

  public static Wrappers.DataMessage buildAccessibleObjectThrowableMessage(int distributorId, AccessibleObject accessibleObject, Exception exception) {

    /** Build protobuf message **/
    final Wrappers.DataMessage.Builder msgBuilder = Wrappers.DataMessage.newBuilder();
    final Exceptions.RaisedThrowable.Builder thrBuilder = Exceptions.RaisedThrowable.newBuilder();
    thrBuilder.setDistributorId(distributorId);
    thrBuilder.setThreadId(Thread.currentThread().getId());
    thrBuilder.setCurrentTime(System.currentTimeMillis());
    thrBuilder.setClassName(exception.getClass().getName());
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
    msgBuilder.setRaisedThrowable(thrBuilder);

    return msgBuilder.build();
  }

  public static Wrappers.DataMessage buildInitializerThrowableMessage(int distributorId, StaticPart staticPart, Exception exception) {

    /** Build protobuf message **/
    final Wrappers.DataMessage.Builder msgBuilder = Wrappers.DataMessage.newBuilder();
    final Exceptions.RaisedThrowable.Builder thrBuilder = Exceptions.RaisedThrowable.newBuilder();
    thrBuilder.setDistributorId(distributorId);
    thrBuilder.setThreadId(Thread.currentThread().getId());
    thrBuilder.setCurrentTime(System.currentTimeMillis());
    thrBuilder.setInInitializer(true);
    thrBuilder.setClassName(staticPart.getSignature().getDeclaringTypeName());
    thrBuilder.setModifiers(staticPart.getSignature().getModifiers());
    thrBuilder.setThrowable(buildThrowableMessage(exception));

    msgBuilder.setThreadId(Thread.currentThread().getId());
    msgBuilder.setMsgType("Throwable");
    msgBuilder.setRaisedThrowable(thrBuilder);

    return msgBuilder.build();
  }

  public static Wrappers.DataMessage buildReturnValueMessage(int distributorId, Object object, boolean isVoid) {

    /** Build protobuf message **/
    final Wrappers.DataMessage.Builder msgBuilder = Wrappers.DataMessage.newBuilder();

    final Values.ReturnValue.Builder valBuilder = Values.ReturnValue.newBuilder();
    valBuilder.setDistributorId(distributorId);
    valBuilder.setThreadId(Thread.currentThread().getId());
    valBuilder.setCurrentTime(System.currentTimeMillis());
    valBuilder.setIsVoid(isVoid);
    if (!isVoid) {
      valBuilder.setValue(getWrappedValue(object, true));
    }

    msgBuilder.setThreadId(Thread.currentThread().getId());
    msgBuilder.setMsgType("Return value");
    msgBuilder.setReturnValue(valBuilder);

    return msgBuilder.build();
  }

  public static Wrappers.DataMessage buildLoadedClassMessage(int distributorId, Class clazz) {

    /** Build protobuf message **/
    final Wrappers.DataMessage.Builder msgBuilder = Wrappers.DataMessage.newBuilder();

    final Values.ReturnValue.Builder valBuilder = Values.ReturnValue.newBuilder();
    valBuilder.setDistributorId(distributorId);
    valBuilder.setThreadId(Thread.currentThread().getId());
    valBuilder.setCurrentTime(System.currentTimeMillis());
    valBuilder.setIsClass(true);
    valBuilder.setClazz(getWrappedClass(clazz));

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

  /**
   * Wrapped is the actual value if object is a primitive or if String (if trimStrings=true, strings of length > STRING_MAX_LEN are trimmed)
   * If the object isn't null, the hashCode and class are also returned
   * It always returns the identityHashCode
   *
   * @param object
   * @return
   */
  private static Primitives.Object getWrappedValue(Object object, boolean trimStrings) {
    final Primitives.Object.Builder value = Primitives.Object.newBuilder();
    if (logger.isDebugEnabled()) {
      logger.debug("in getWrappedValue for:" + object);
    }

    //1
    if (object != null) {
      if (object instanceof String) {
        if (trimStrings && ((String) object).length() > STRING_MAX_LEN) {
          value.setValue(((String) object).substring(0, STRING_MAX_LEN));
          //7
          value.setTrimmed(true);
        } else {
          value.setValue(String.valueOf(object));
        }
      } else if (object.getClass().isPrimitive() || com.google.common.primitives.Primitives.isWrapperType(object.getClass())) {
        value.setValue(String.valueOf(object));
      } else if (object.getClass().isArray()) {
        //5
        value.setIsArray(object.getClass().isArray());
        for (Object arrayElem : (Object[]) object) {
          //wrap and all array elements -- recursive
          value.addArrayValue(getWrappedValue(arrayElem, trimStrings));
        }
      }
    }

    //2
    if (object != null) {
      value.setHash(object.hashCode());
    }

    //3
    value.setIdentityHash(System.identityHashCode(object));

    //4
    if (object != null) {
      value.setClass_(object.getClass().getName());
    }

    Primitives.Object builtValue = value.build();
    if (logger.isDebugEnabled()) {
      logger.debug("Returning wrappedValue:\n" + builtValue);
    }
    return builtValue;
  }

  private static Primitives.Class getWrappedClass(Class clazz) {
    final Primitives.Class.Builder clazzBuilder = Primitives.Class.newBuilder();

    if (clazz != null) {
      //1
      clazzBuilder.setClassname(clazz.getName());
      //2
      clazzBuilder.setHash(clazz.hashCode());
    }

    //3
    clazzBuilder.setIdentityHash(System.identityHashCode(clazz));

    return clazzBuilder.build();
  }

  private static boolean isMain(StaticPart staticPart) {
    MethodSignature methodSignature = (MethodSignature) staticPart.getSignature();
    int modifiers = methodSignature.getModifiers();

    if ("main".equals(methodSignature.getMethod().getName()) && methodSignature.getReturnType().equals(Void.class)) {
      Class[] paramTypes = methodSignature.getParameterTypes();
      return (paramTypes.length == 1 && paramTypes[0].equals(String[].class)
        && Modifier.isPublic(modifiers) && Modifier.isStatic(modifiers));
    }
    return false;
  }

  private static boolean isMain(String methodName, Class returnType, Class[] paramTypes, int modifiers) {

    if ("main".equals(methodName) && returnType.equals(Void.class)) {
      return (paramTypes.length == 1 && paramTypes[0].equals(String[].class)
        && Modifier.isPublic(modifiers) && Modifier.isStatic(modifiers));
    }
    return false;
  }
}
