package com.ittera.cometa.concentrator;

import com.ittera.cometa.concentrator.messages.protobuf.data.Wrappers.DataMessage;
import com.ittera.cometa.concentrator.messages.DataMessageBuilder;
import com.ittera.cometa.concentrator.messages.protobuf.ProtobufDataMessageBuilder;
import com.ittera.cometa.concentrator.messages.protobuf.Unwrapper;
import com.ittera.cometa.concentrator.messages.protobuf.data.Fields;
import com.ittera.cometa.concentrator.messages.protobuf.data.Primitives;
import com.ittera.cometa.concentrator.messages.protobuf.data.Calls;
import com.ittera.cometa.concentrator.messages.protobuf.data.Wrappers;
import com.ittera.cometa.util.ReflectionHelper;

import com.ittera.cometa.concentrator.messages.DataMessageBuilder;
import com.ittera.cometa.concentrator.messages.DataMessageDispatcher;

import org.aspectj.lang.reflect.ConstructorSignature;
import org.aspectj.lang.reflect.MethodSignature;
import org.aspectj.lang.JoinPoint.StaticPart;
import org.aspectj.runtime.reflect.FieldSignatureImpl;

import java.io.FileInputStream;
import java.io.IOException;

import java.lang.Class;
import java.lang.reflect.Field;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.InvocationTargetException;

import java.util.Properties;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import org.apache.commons.lang3.StringUtils;

import com.google.inject.name.Names;
import com.google.inject.*;

public class Concentrator {

  protected static final Logger logger = LogManager.getLogger(Concentrator.class);

  /**
   * Static data (singletons) shared by all threads - sources of contention
   */
  private static int id;

  @Inject
  private static DataMessageBuilder dataMessageBuilder;

  @Inject
  private static MessageBroker messageBroker;

  @Inject
  private static DataMessageDispatcher dataMessageDispatcher;

  private static final Map<Long, BlockingQueue<DataMessage>> threadBlockingQueueMap = new ConcurrentHashMap<Long, BlockingQueue<DataMessage>>();

  /************************ INTERFACE ***************************/

  // <editor-fold defaultstate="collapsed" desc="CONSTRUCTORS">
  public static boolean classConstructor(StaticPart staticPart, Object sender) throws ClassNotFoundException {
    logger.traceEntry("with staticPart: {}, sender: {}", staticPart.getSignature(), sender);

    /** 1. Wrap message **/
    final Wrappers.DataMessage msg = dataMessageBuilder.buildClassInitializer(id, staticPart, sender);

    /** 2. Send message **/
    //ATTENTION: this send is asynchronous. Must call get later.
    messageBroker.send(msg);

    if (mustWait(msg)) {
      /** 3. Receive message **/
      Wrappers.DataMessage rcvdMsg = messageBroker.receiveMsgForCurrentThread();

      logger.debug("Message received: {}", rcvdMsg.getMsgType());
    }

    /** 4. Load and initialize class  -  WARNING: For some reason the class is not being initialized! **/
    Class clazz = null;
    ClassNotFoundException exceptionWhileLoadingClass = null;
    try {
      clazz = Class.forName(staticPart.getSignature().getDeclaringType().getName());
      //Class.forName(codeSignature.getDeclaringTypeName(),true, Concentrator.class.getClassLoader());
    } catch (ClassNotFoundException cnfe) {
      exceptionWhileLoadingClass = cnfe;
    }

    /** 5. Store and wrap class/exception if any **/
    String objKey = null;
    final Wrappers.DataMessage invokedMsg;
    if (exceptionWhileLoadingClass != null) {
      invokedMsg = dataMessageBuilder.buildInitializerThrowable(id, staticPart, exceptionWhileLoadingClass);
    } else {
      objKey = ObjectStore.storeObject(clazz);
      invokedMsg = dataMessageBuilder.buildLoadedClass(id, clazz);
    }

    /** 6. Send object/exception **/
    messageBroker.send(invokedMsg);

    if (mustWait(invokedMsg)) {
      /** 7. Receive object/exception **/
      Wrappers.DataMessage rcvdMsg = messageBroker.receiveMsgForCurrentThread();

      logger.debug("Message received: {}", rcvdMsg.getMsgType());
    }

    /** 8. Return or re-raise exception **/
    if (exceptionWhileLoadingClass != null) {
      throw exceptionWhileLoadingClass;
    }

    //Since class initialization is not working, we will return false if we want to aspectj to proceed(), indicating class isn't initialized
    boolean returnValue = false;
    logger.traceExit("with return bool: {}", returnValue);
    return returnValue;
  }

  /**
   * This method currently only support calling constructor whose arg(s) value(s) are fully contained in the msg. i.e. --> primitives, and Strings that haven't been trimmed.
   *
   * @param constructorCall
   * @throws Throwable
   */
  static void incomingConstructor(Calls.ConstructorCall constructorCall, long recordOffset) {
    logger.traceEntry("with constructorCall: {}, recordOffset", constructorCall, recordOffset);

    /** 1. Unwrap message and load constructor **/
    Class clazz = null;
    final List<Class> paramClasses = new ArrayList<>();
    Constructor constructor = null;
    Exception exceptionWhileLoading = null;
    try {
      clazz = Class.forName(constructorCall.getClass_().getName());
      for (Primitives.Object param : constructorCall.getParameterList()) {
        paramClasses.add(Class.forName(param.getClass_().getName()));
      }
      constructor = clazz.getDeclaredConstructor((Class[]) paramClasses.toArray(new Class[paramClasses.size()]));
    } catch (Exception e) {
      exceptionWhileLoading = e;
    }


    /** 2. If class and constructor loaded, unwrap arguments and invoke constructor **/
    Exception exceptionWhileInvoking = null;
    Object newObject = null;
    String objKey = null;

    if (exceptionWhileLoading == null) {
      constructor.setAccessible(true);
      try {
        List<Object> args = new ArrayList<>();
        for (int i = 0; i < constructorCall.getParameterCount(); i++) {
          Primitives.Object obj = constructorCall.getParameter(i);
          if (obj.getIsNull()) {
            args.add(null);
          } else if (obj.hasRef()) {
            args.add(ObjectStore.lookupObject(obj.getRef()));
          } else {
            args.add(Unwrapper.unwrapObject(obj, paramClasses.get(i)));
          }
        }
        newObject = constructor.newInstance(args.toArray(new Object[args.size()]));
        //store in object map
        objKey = ObjectStore.storeObject(newObject);
      } catch (Exception ite) {
        exceptionWhileInvoking = ite;
      }
    }

    /** 3. Wrap new object or exception **/
    final Wrappers.DataMessage invokedMsg;

    if (exceptionWhileLoading != null) {
      invokedMsg = dataMessageBuilder.buildAccessibleObjectThrowable(id, constructor, exceptionWhileLoading, recordOffset);
    } else if (exceptionWhileInvoking != null) {
      invokedMsg = dataMessageBuilder.buildAccessibleObjectThrowable(id, constructor, exceptionWhileInvoking, recordOffset);
    } else {
      invokedMsg = dataMessageBuilder.buildReturnValue(id, newObject, clazz, objKey, false, recordOffset);
    }


    /** 4. Send object/exception **/
    messageBroker.send(invokedMsg);

    logger.traceExit();
    return;
  }

  public static Object constructor(StaticPart staticPart, Object sender, Object[] args) throws Throwable {
    logger.traceEntry("with staticPart: {}, sender: {}, args: {}", staticPart.getSignature(), sender, args);

    final ConstructorSignature constructorSignature = (ConstructorSignature) staticPart.getSignature();

    /** 1. Wrap message **/
    final Wrappers.DataMessage callMsg = dataMessageBuilder.buildConstructor(id, staticPart, sender, args);

    /** 2. Send message **/
    //ATTENTION: this send is asynchronous. Must call get later.
    messageBroker.send(callMsg);

    if (mustWait(callMsg)) {
      /** 3. Receive message **/
      Wrappers.DataMessage rcvdMsg = messageBroker.receiveMsgForCurrentThread();

      logger.debug("Message received: {}", rcvdMsg.getMsgType());
    }


    /** 4. Invoke constructor **/

    Constructor constructor = constructorSignature.getConstructor();

    Object newObject = null;
    Exception exceptionWhileInvoking = null;
    constructor.setAccessible(true);
    String objKey = null;
    try {
      newObject = constructor.newInstance(args);
      //store in object map
      objKey = ObjectStore.storeObject(newObject);
    } catch (Exception ite) {
      exceptionWhileInvoking = ite;
    }

    /** 5. Wrap new object or exception **/
    Wrappers.DataMessage invokedMsg = null;

    if (exceptionWhileInvoking != null) {
      invokedMsg = dataMessageBuilder.buildAccessibleObjectThrowable(id, constructor, exceptionWhileInvoking, null);
    } else {
      invokedMsg = dataMessageBuilder.buildReturnValue(id, newObject, constructor.getClass(), objKey, false, null);
    }


    /** 6. Send object/exception **/
    messageBroker.send(invokedMsg);

    if (mustWait(invokedMsg)) {
      /** 7. Receive object/exception **/
      Wrappers.DataMessage rcvdMsg = messageBroker.receiveMsgForCurrentThread();

      logger.debug("Message received: {}", rcvdMsg.getMsgType());
    }

    /** 8. Return object or re-raise exception **/
    if (exceptionWhileInvoking != null) {
      if (exceptionWhileInvoking instanceof InvocationTargetException) {
        throw exceptionWhileInvoking.getCause();
      } else {
        throw exceptionWhileInvoking;
      }
    }

    logger.traceExit("with new object: {}", newObject);
    return newObject;
  }
  // </editor-fold>

  // <editor-fold defaultstate="collapsed" desc="METHOD CALLS">

  public static void voidInstanceMethod(StaticPart staticPart, Object sender, Object target, Object[] args) throws Throwable {
    logger.traceEntry("with staticPart: {}, sender: {}, target: {}, args: {}", staticPart.getSignature(), sender, target, args);

    final MethodSignature methodSignature = (MethodSignature) staticPart.getSignature();

    /** 1. Wrap message **/
    final Wrappers.DataMessage msg = dataMessageBuilder.buildInstanceMethod(id, staticPart, sender, target, args);


    /** 2. Send message **/
    //ATTENTION: this send is asynchronous. Must call get later.
    messageBroker.send(msg);

    if (mustWait(msg)) {
      /** 3. Receive message **/
      Wrappers.DataMessage rcvdMsg = messageBroker.receiveMsgForCurrentThread();

      logger.debug("Message received: {}", rcvdMsg.getMsgType());
    }


    /** 4. Invoke method **/

    Method method = methodSignature.getMethod();

    Exception exceptionWhileInvoking = null;
    method.setAccessible(true);
    try {
      method.invoke(target, args);
    } catch (Exception e) {
      exceptionWhileInvoking = e;
    }

    /** 5. Wrap new object or exception **/
    final Wrappers.DataMessage invokedMsg;

    if (exceptionWhileInvoking != null) {
      invokedMsg = dataMessageBuilder.buildAccessibleObjectThrowable(id, method, exceptionWhileInvoking, null);
    } else {
      invokedMsg = dataMessageBuilder.buildReturnValue(id, Void.class, method.getReturnType(), null, true, null);
    }


    /** 6. Send object/exception **/
    messageBroker.send(invokedMsg);

    if (mustWait(invokedMsg)) {
      /** 7. Receive object/exception **/
      Wrappers.DataMessage rcvdMsg = messageBroker.receiveMsgForCurrentThread();

      logger.debug("Message received: {}", rcvdMsg.getMsgType());
    }

    /** 8. Return object or re-raise exception **/
    if (exceptionWhileInvoking != null) {
      if (exceptionWhileInvoking instanceof InvocationTargetException) {
        throw exceptionWhileInvoking.getCause();
      } else {
        throw exceptionWhileInvoking;
      }
    }

    logger.traceExit();
    return;
  }

  public static Object nonVoidInstanceMethod(StaticPart staticPart, Object sender, Object target, Object[] args) throws Throwable {
    logger.traceEntry("with staticPart: {}, sender: {}, target: {}, args: {}", staticPart.getSignature(), sender, target, args);

    final MethodSignature methodSignature = (MethodSignature) staticPart.getSignature();

    /** 1. Wrap message **/
    final Wrappers.DataMessage msg = dataMessageBuilder.buildInstanceMethod(id, staticPart, sender, target, args);


    /** 2. Send message **/
    //ATTENTION: this send is asynchronous. Must call get later.
    messageBroker.send(msg);

    if (mustWait(msg)) {
      /** 3. Receive message **/
      Wrappers.DataMessage rcvdMsg = messageBroker.receiveMsgForCurrentThread();

      logger.debug("Message received: {}", rcvdMsg.getMsgType());
    }


    /** 4. Invoke method **/

    Method method = methodSignature.getMethod();
    Object returnValue = null;
    Exception exceptionWhileInvoking = null;
    method.setAccessible(true);
    try {
      returnValue = method.invoke(target, args);
    } catch (Exception e) {
      exceptionWhileInvoking = e;
    }

    /** 5. Wrap new object or exception **/
    final Wrappers.DataMessage invokedMsg;

    if (exceptionWhileInvoking != null) {
      invokedMsg = dataMessageBuilder.buildAccessibleObjectThrowable(id, method, exceptionWhileInvoking, null);
    } else {
      invokedMsg = dataMessageBuilder.buildReturnValue(id, returnValue, method.getReturnType(), null, false, null);
    }


    /** 6. Send object/exception **/
    messageBroker.send(invokedMsg);

    if (mustWait(invokedMsg)) {
      /** 7. Receive object/exception **/
      Wrappers.DataMessage rcvdMsg = messageBroker.receiveMsgForCurrentThread();

      logger.debug("Message received: {}", rcvdMsg.getMsgType());
    }

    /** 8. Return object or re-raise exception **/
    if (exceptionWhileInvoking != null) {
      if (exceptionWhileInvoking instanceof InvocationTargetException) {
        throw exceptionWhileInvoking.getCause();
      } else {
        throw exceptionWhileInvoking;
      }
    }

    logger.traceExit("with return value: {}", returnValue);
    return returnValue;
  }

  /**
   * This method currently only support calling method whose value is fully contained in the msg. i.e. --> primitives, and Strings that haven't been trimmed.
   *
   * @param instanceMethodCall
   */
  static void incomingInstanceMethod(Calls.InstanceMethodCall instanceMethodCall, long recordOffset) {
    logger.traceEntry("with instanceMethodCall: {}, recordOffset: {}", instanceMethodCall, recordOffset);

    /** 1. Unwrap message and load class **/
    Class clazz = null;
    Method method = null;
    Exception exceptionWhileLoading = null;
    List<Class> paramClasses = new ArrayList<>();
    try {
      clazz = Class.forName(instanceMethodCall.getClass_().getName());
      for (Primitives.Object obj : instanceMethodCall.getParameterList()) {
        Class paramClass = Unwrapper.getClassForPrimitive(obj.getClass_().getName());
        if (paramClass == null) {
          paramClass = Class.forName(obj.getClass_().getName());
        }
        paramClasses.add(paramClass);
      }
    } catch (Exception e) {
      exceptionWhileLoading = e;
    }

    /** 2. If class loaded, unwrap/retrieve arguments and invoke method **/
    Exception exceptionWhileInvoking = null;
    Object returnValue = null;
    if (exceptionWhileLoading == null) {
      List<Object> args = new ArrayList<>();
      for (int i = 0; i < instanceMethodCall.getParameterCount(); i++) {
        Primitives.Object obj = instanceMethodCall.getParameter(i);
        if (obj.getIsNull()) {
          args.add(null);
        } else if (obj.hasRef()) {
          args.add(ObjectStore.lookupObject(obj.getRef()));
        } else {
          args.add(Unwrapper.unwrapObject(obj, paramClasses.get(i)));
        }
      }
      try {
        Object target = ObjectStore.lookupObject(instanceMethodCall.getObjectRef());
        method = ReflectionHelper.getMethodToInvoke(clazz, args.toArray(), instanceMethodCall.getName());
        if (method == null) {
          //TODO perhaps this should be thrown by ReflectionHelper instead of returning null
          throw new NoSuchMethodException(String.format("Can't find method:%s in class:%s with given parameter types", instanceMethodCall.getName(), clazz.getName()));
        }
        method.setAccessible(true);
        returnValue = method.invoke(target, args.toArray());
      } catch (Exception e) {
        exceptionWhileInvoking = e;
      }
    }


    /** 3. Wrap return value or exception **/
    final Wrappers.DataMessage invokedMsg;

    if (exceptionWhileLoading != null) {
      invokedMsg = dataMessageBuilder.buildAccessibleObjectThrowable(id, method, exceptionWhileLoading, recordOffset);
    } else if (exceptionWhileInvoking != null) {
      invokedMsg = dataMessageBuilder.buildAccessibleObjectThrowable(id, method, exceptionWhileInvoking, recordOffset);
    } else {
      boolean isVoid = method.getReturnType() == void.class;
      invokedMsg = dataMessageBuilder.buildReturnValue(id, isVoid ? Void.class : returnValue, method.getReturnType(),
        returnValue == null ? null : ObjectStore.lookupObjectRef(returnValue), isVoid, recordOffset);
    }

    /** 4. Send object/exception **/
    messageBroker.send(invokedMsg);

    logger.traceExit();
    return;
  }

  public static void voidClassMethod(StaticPart staticPart, Object sender, Object[] args) throws Throwable {
    logger.traceEntry("with staticPart: {}, sender: {}, args: {}", staticPart.getSignature(), sender, args);

    final MethodSignature methodSignature = (MethodSignature) staticPart.getSignature();

    /** 1. Wrap message **/
    final Wrappers.DataMessage msg = dataMessageBuilder.buildClassMethod(id, staticPart, sender, args);


    /** 2. Send message **/
    //ATTENTION: this send is asynchronous. Must call get later.
    messageBroker.send(msg);

    if (mustWait(msg)) {
      /** 3. Receive message **/
      Wrappers.DataMessage rcvdMsg = messageBroker.receiveMsgForCurrentThread();

      logger.debug("Message received: {}", rcvdMsg.getMsgType());
    }


    /** 4. Invoke method **/

    Method method = methodSignature.getMethod();
    Exception exceptionWhileInvoking = null;
    method.setAccessible(true);
    try {
      method.invoke(null, args);
    } catch (Exception e) {
      exceptionWhileInvoking = e;
    }

    /** 5. Wrap new object or exception **/
    final Wrappers.DataMessage invokedMsg;

    if (exceptionWhileInvoking != null) {
      invokedMsg = dataMessageBuilder.buildAccessibleObjectThrowable(id, method, exceptionWhileInvoking, null);
    } else {
      invokedMsg = dataMessageBuilder.buildReturnValue(id, Void.class, method.getReturnType(), null, true, null);
    }


    /** 6. Send object/exception **/
    messageBroker.send(invokedMsg);

    if (mustWait(invokedMsg)) {
      /** 7. Receive object/exception **/
      Wrappers.DataMessage rcvdMsg = messageBroker.receiveMsgForCurrentThread();

      logger.debug("Message received: {}", rcvdMsg.getMsgType());
    }

    /** 8. Return object or re-raise exception **/
    if (exceptionWhileInvoking != null) {
      if (exceptionWhileInvoking instanceof InvocationTargetException) {
        throw exceptionWhileInvoking.getCause();
      } else {
        throw exceptionWhileInvoking;
      }
    }

    logger.traceExit();
    return;
  }

  public static Object nonVoidClassMethod(StaticPart staticPart, Object sender, Object[] args) throws Throwable {
    logger.traceEntry("with staticPart: {}, sender: {}, args: {}", staticPart.getSignature(), sender, args);

    final MethodSignature methodSignature = (MethodSignature) staticPart.getSignature();

    /** 1. Wrap message **/
    final Wrappers.DataMessage msg = dataMessageBuilder.buildClassMethod(id, staticPart, sender, args);


    /** 2. Send message **/
    //ATTENTION: this send is asynchronous. Must call get later.
    messageBroker.send(msg);

    if (mustWait(msg)) {
      /** 3. Receive message **/
      Wrappers.DataMessage rcvdMsg = messageBroker.receiveMsgForCurrentThread();

      logger.debug("Message received: {}", rcvdMsg.getMsgType());
    }


    /** 4. Invoke method **/

    Method method = methodSignature.getMethod();
    Object returnValue = null;
    Exception exceptionWhileInvoking = null;
    method.setAccessible(true);
    try {
      returnValue = method.invoke(null, args);
    } catch (Exception e) {
      exceptionWhileInvoking = e;
    }

    /** 5. Wrap new object or exception **/
    final Wrappers.DataMessage invokedMsg;

    if (exceptionWhileInvoking != null) {
      invokedMsg = dataMessageBuilder.buildAccessibleObjectThrowable(id, method, exceptionWhileInvoking, null);
    } else {
      invokedMsg = dataMessageBuilder.buildReturnValue(id, returnValue, method.getReturnType(), null, false, null);
    }


    /** 6. Send object/exception **/
    messageBroker.send(invokedMsg);

    if (mustWait(invokedMsg)) {
      /** 7. Receive object/exception **/
      Wrappers.DataMessage rcvdMsg = messageBroker.receiveMsgForCurrentThread();

      logger.debug("Message received: {}", rcvdMsg.getMsgType());
    }

    /** 8. Return object or re-raise exception **/
    if (exceptionWhileInvoking != null) {
      if (exceptionWhileInvoking instanceof InvocationTargetException) {
        throw exceptionWhileInvoking.getCause();
      } else {
        throw exceptionWhileInvoking;
      }
    }

    logger.traceExit("with return value: {}", returnValue);
    return returnValue;
  }


  /**
   * This method currently only support calling method whose value is fully contained in the msg. i.e. --> primitives, and Strings that haven't been trimmed.
   *
   * @param classMethodCall
   */
  static void incomingClassMethod(Calls.ClassMethodCall classMethodCall, long recordOffset) {
    logger.traceEntry("with classMethodCall: {}, recordOffset: {}", classMethodCall, recordOffset);

    /** 1. Unwrap message and load class **/
    Class clazz = null;
    Method method = null;
    Exception exceptionWhileLoading = null;
    List<Class> paramClasses = new ArrayList<>();
    try {
      logger.debug("Attempting to load (initialize) class");
      clazz = Class.forName(classMethodCall.getClass_().getName());
      for (Primitives.Object obj : classMethodCall.getParameterList()) {
        Class paramClass = Unwrapper.getClassForPrimitive(obj.getClass_().getName());
        if (paramClass == null) {
          paramClass = Class.forName(obj.getClass_().getName());
        }
        paramClasses.add(paramClass);
      }
    } catch (Exception e) {
      exceptionWhileLoading = e;
    }

    /** 2. If class loaded, unwrap/retrieve arguments and invoke method **/
    Exception exceptionWhileInvoking = null;
    Object returnValue = null;
    if (exceptionWhileLoading == null) {
      logger.debug("Unwrapping parameters");
      List<Object> args = new ArrayList<>();
      for (int i = 0; i < classMethodCall.getParameterCount(); i++) {
        Primitives.Object obj = classMethodCall.getParameter(i);
        if (obj.getIsNull()) {
          args.add(null);
        } else if (obj.hasRef()) {
          args.add(ObjectStore.lookupObject(obj.getRef()));
        } else {
          args.add(Unwrapper.unwrapObject(obj, paramClasses.get(i)));
        }
      }
      try {
        method = ReflectionHelper.getMethodToInvoke(clazz, args.toArray(), classMethodCall.getName());
        if (method == null) {
          throw new NoSuchMethodException(String.format("Can't find method:%s in class:%s with given parameter types", classMethodCall.getName(), clazz.getName()));
        }
        method.setAccessible(true);
        returnValue = method.invoke(null, args.toArray());
      } catch (Exception e) {
        exceptionWhileInvoking = e;
      }
    }

    /** 3. Wrap return value or exception **/
    final Wrappers.DataMessage invokedMsg;

    if (exceptionWhileLoading != null) {
      invokedMsg = dataMessageBuilder.buildAccessibleObjectThrowable(id, method, exceptionWhileLoading, recordOffset);
    } else if (exceptionWhileInvoking != null) {
      invokedMsg = dataMessageBuilder.buildAccessibleObjectThrowable(id, method, exceptionWhileInvoking, recordOffset);
    } else {
      boolean isVoid = method.getReturnType() == void.class;
      invokedMsg = dataMessageBuilder.buildReturnValue(id, isVoid ? Void.class : returnValue, method.getReturnType(),
        returnValue == null ? null : ObjectStore.lookupObjectRef(returnValue), isVoid, recordOffset);
    }

    /** 4. Send object/exception **/
    messageBroker.send(invokedMsg);

    logger.traceExit();
    return;
  }


  // </editor-fold>

  // <editor-fold defaultstate="collapsed" desc="FIELD OPERATIONS">

  public static void incomingGetStatic(Fields.StaticFieldGet staticFieldGet, long recordOffset) {
    logger.traceEntry("with staticFieldGet: {}, recordOffset: {}", staticFieldGet, recordOffset);

    /** 1. Get Object **/
    Class clazz = null;
    Field field = null;
    Exception exceptionWhileLoading = null;
    try {
      clazz = Class.forName(staticFieldGet.getClass_().getName());
      field = clazz.getDeclaredField(staticFieldGet.getField().getName());
      logger.debug("field {} is of type {}", field.getName(), field.getType());
    } catch (Exception e) {
      exceptionWhileLoading = e;
    }

    /** 2. If class and field loaded, invoke field get **/
    Exception exceptionWhileInvoking = null;

    Object fieldValue = null;
    if (exceptionWhileLoading == null) {
      field.setAccessible(true);
      try {
        fieldValue = field.get(null);
      } catch (Exception e) {
        exceptionWhileInvoking = e;
      }
    }


    /** 3. Wrap return value or exception **/
    Wrappers.DataMessage invokedMsg = null;

    if (exceptionWhileLoading != null) {
      invokedMsg = dataMessageBuilder.buildAccessibleObjectThrowable(id, field, exceptionWhileLoading, recordOffset);
    } else if (exceptionWhileInvoking != null) {
      invokedMsg = dataMessageBuilder.buildAccessibleObjectThrowable(id, field, exceptionWhileInvoking, recordOffset);
    } else {
      invokedMsg = dataMessageBuilder.buildReturnValue(id, fieldValue, field.getType(), null, false, recordOffset);
    }


    /** 4. Send object/exception **/
    messageBroker.send(invokedMsg);

    logger.traceExit();
    return;

  }

  public static Object getStatic(StaticPart staticPart, Object sender) throws IllegalAccessException {
    logger.traceEntry("with staticPart: {}, sender: {}", staticPart.getSignature(), sender);

    /** 1. Wrap message **/
    final Wrappers.DataMessage msg = dataMessageBuilder.buildGetStatic(id, staticPart, sender);

    /** 2. Send message **/
    //ATTENTION: this send is asynchronous. Must call get later.
    messageBroker.send(msg);

    if (mustWait(msg)) {
      /** 3. Receive message **/
      Wrappers.DataMessage rcvdMsg = messageBroker.receiveMsgForCurrentThread();

      logger.debug("Message received: {}", rcvdMsg.getMsgType());
    }

    /** 4. Get Object **/

    Field field = ((FieldSignatureImpl) staticPart.getSignature()).getField();
    field.setAccessible(true);

    IllegalAccessException exceptionGettingObject = null;
    Object fieldValue = null;
    try {
      fieldValue = field.get(null);
    } catch (IllegalAccessException iae) {
      exceptionGettingObject = iae;
    }

    /** 5. Wrap exception if any **/
    Wrappers.DataMessage invokedMsg = null;
    if (exceptionGettingObject != null) {
      invokedMsg = dataMessageBuilder.buildInitializerThrowable(id, staticPart, exceptionGettingObject);
    } else {
      invokedMsg = dataMessageBuilder.buildReturnValue(id, fieldValue, field.getType(), null, false, null);
    }

    /** 6. Send object/exception **/
    messageBroker.send(invokedMsg);

    if (mustWait(invokedMsg)) {
      /** 7. Receive object/exception **/
      Wrappers.DataMessage rcvdMsg = messageBroker.receiveMsgForCurrentThread();

      logger.debug("Message received: {}", rcvdMsg.getMsgType());
    }

    /** 8. Return or re-raise exception **/
    if (exceptionGettingObject != null) {
      throw exceptionGettingObject;
    }

    logger.traceExit("with fieldValue: {}", fieldValue);
    return fieldValue;
  }

  public static void incomingGetObject(Fields.InstanceFieldGet instanceFieldGet, long recordOffset) {
    logger.traceEntry("with instanceFieldGet: {}, recordOffset: {}", instanceFieldGet, recordOffset);

    /** 1. Get Object **/
    Class clazz = null;
    Field field = null;
    Exception exceptionWhileLoading = null;
    try {
      clazz = Class.forName(instanceFieldGet.getClass_().getName());
      field = clazz.getDeclaredField(instanceFieldGet.getField().getName());
    } catch (Exception e) {
      exceptionWhileLoading = e;
    }

    /** 2. If class and field loaded, invoke field get **/
    Exception exceptionWhileInvoking = null;

    Object fieldValue = null;
    if (exceptionWhileLoading == null) {
      field.setAccessible(true);
      try {
        Object target = ObjectStore.lookupObject(instanceFieldGet.getObjectRef());
        fieldValue = field.get(target);
      } catch (Exception e) {
        exceptionWhileInvoking = e;
      }
    }


    /** 3. Wrap return value or exception **/
    Wrappers.DataMessage invokedMsg = null;

    if (exceptionWhileLoading != null) {
      invokedMsg = dataMessageBuilder.buildAccessibleObjectThrowable(id, field, exceptionWhileLoading, recordOffset);
    } else if (exceptionWhileInvoking != null) {
      invokedMsg = dataMessageBuilder.buildAccessibleObjectThrowable(id, field, exceptionWhileInvoking, recordOffset);
    } else {
      invokedMsg = dataMessageBuilder.buildReturnValue(id, fieldValue, field.getType(), null, false, recordOffset);
    }


    /** 4. Send object/exception **/
    messageBroker.send(invokedMsg);

    logger.traceExit();
    return;

  }

  public static Object getObject(StaticPart staticPart, Object sender, Object target) throws IllegalAccessException {
    logger.traceEntry("with staticPart: {}, sender: {}, target: {}", staticPart.getSignature(), sender, target);

    /** 1. Wrap message **/
    final Wrappers.DataMessage msg = dataMessageBuilder.buildGetObject(id, staticPart, sender, target);

    /** 2. Send message **/
    //ATTENTION: this send is asynchronous. Must call get later.
    messageBroker.send(msg);

    if (mustWait(msg)) {
      /** 3. Receive message **/
      Wrappers.DataMessage rcvdMsg = messageBroker.receiveMsgForCurrentThread();

      logger.debug("Message received: {}", rcvdMsg.getMsgType());
    }


    /** 4. Get Object **/

    Field field = ((FieldSignatureImpl) staticPart.getSignature()).getField();
    field.setAccessible(true);

    IllegalAccessException exceptionGettingObject = null;
    Object fieldValue = null;
    try {
      fieldValue = field.get(target);
    } catch (IllegalAccessException iae) {
      exceptionGettingObject = iae;
    }

    /** 5. Wrap exception if any **/
    Wrappers.DataMessage invokedMsg = null;
    if (exceptionGettingObject != null) {
      invokedMsg = dataMessageBuilder.buildInitializerThrowable(id, staticPart, exceptionGettingObject);
    } else {
      invokedMsg = dataMessageBuilder.buildReturnValue(id, fieldValue, field.getType(), null, false, null);
    }

    /** 6. Send object/exception **/
    messageBroker.send(invokedMsg);

    if (mustWait(invokedMsg)) {
      /** 7. Receive object/exception **/
      Wrappers.DataMessage rcvdMsg = messageBroker.receiveMsgForCurrentThread();

      logger.debug("Message received: {}", rcvdMsg.getMsgType());
    }

    /** 8. Return or re-raise exception **/
    if (exceptionGettingObject != null) {
      throw exceptionGettingObject;
    }

    logger.traceExit("with fieldValue: {}", fieldValue);
    return fieldValue;
  }

  public static void incomingPutStatic(Fields.StaticFieldPut staticFieldPut, long recordOffset) {
    logger.traceEntry("with staticFieldPut: {}, recordOffset", staticFieldPut, recordOffset);

    /** 1. Load class and field **/
    final Class clazz;
    Field field = null;
    Exception exceptionWhileLoading = null;
    try {
      clazz = Class.forName(staticFieldPut.getClass_().getName());
      field = clazz.getDeclaredField(staticFieldPut.getField().getName());
    } catch (Exception e) {
      exceptionWhileLoading = e;
    }

    /** 2. If class and field loaded, unwrap value and invoke field set **/
    //TODO unwrap or load object before and have a separate exception for this step

    Exception exceptionWhileInvoking = null;

    if (exceptionWhileLoading == null) {
      field.setAccessible(true);
      try {
        final Object value;
        if (staticFieldPut.hasObject()) {
          value = Unwrapper.unwrapObject(staticFieldPut.getObject(), field.getType());
          logger.debug("Unwrapped value: {}", value);
        } else {
          value = ObjectStore.lookupObject(staticFieldPut.getObjectRef());
          logger.debug("Loaded value: {}", value);
        }
        //invoke set
        field.set(null, value);
      } catch (Exception e) {
        exceptionWhileInvoking = e;
      }
    }


    /** 3. Wrap return value or exception **/
    Wrappers.DataMessage invokedMsg = null;

    if (exceptionWhileLoading != null) {
      invokedMsg = dataMessageBuilder.buildAccessibleObjectThrowable(id, field, exceptionWhileLoading, recordOffset);
    } else if (exceptionWhileInvoking != null) {
      invokedMsg = dataMessageBuilder.buildAccessibleObjectThrowable(id, field, exceptionWhileInvoking, recordOffset);
    } else {
      invokedMsg = dataMessageBuilder.buildPutStaticDone(id, staticFieldPut, field.getType(), recordOffset);
    }


    /** 4. Send object/exception **/
    messageBroker.send(invokedMsg);

    logger.traceExit();
    return;

  }

  public static void putStatic(StaticPart staticPart, Object sender, Object[] args) throws IllegalAccessException {
    logger.traceEntry("with staticPart: {}, sender: {}, args: {}", staticPart.getSignature(), sender, args);

    /** 1. Wrap message **/
    final Wrappers.DataMessage msg = dataMessageBuilder.buildPutStatic(id, staticPart, sender, args[0]);

    /** 2. Send message **/
    //ATTENTION: this send is asynchronous. Must call get later.
    messageBroker.send(msg);

    if (mustWait(msg)) {
      /** 3. Receive message **/
      Wrappers.DataMessage rcvdMsg = messageBroker.receiveMsgForCurrentThread();

      logger.debug("Message received: {}", rcvdMsg.getMsgType());
    }

    /** 4. Put Object **/

    Field field = ((FieldSignatureImpl) staticPart.getSignature()).getField();
    field.setAccessible(true);

    IllegalAccessException exceptionSettingObject = null;
    try {
      //invoke set
      field.set(null, args[0]);
    } catch (IllegalAccessException iae) {
      exceptionSettingObject = iae;
    }

    /** 5. Wrap exception if any **/
    Wrappers.DataMessage invokedMsg = null;
    if (exceptionSettingObject != null) {
      invokedMsg = dataMessageBuilder.buildInitializerThrowable(id, staticPart, exceptionSettingObject);
    } else {
      invokedMsg = dataMessageBuilder.buildPutStaticDone(id, staticPart, sender, args[0]);
    }

    /** 6. Send object/exception **/
    messageBroker.send(invokedMsg);

    if (mustWait(msg)) {
      /** 7. Receive object/exception **/
      Wrappers.DataMessage rcvdMsg = messageBroker.receiveMsgForCurrentThread();

      logger.debug("Message received: {}", rcvdMsg.getMsgType());
    }

    /** 8. Return or re-raise exception **/
    if (exceptionSettingObject != null) {
      throw exceptionSettingObject;
    }

    logger.traceExit();
    return;
  }

  public static void putField(StaticPart staticPart, Object sender, Object target, Object[] args) throws IllegalAccessException {
    logger.traceEntry("with staticPart: {}, sender: {}, target: {}, args: {}", staticPart.getSignature(), sender, target, args);

    /** 1. Wrap message **/
    final Wrappers.DataMessage msg = dataMessageBuilder.buildPutObject(id, staticPart, sender, target, args[0]);

    /** 2. Send message **/
    //ATTENTION: this send is asynchronous. Must call get later.
    messageBroker.send(msg);

    if (mustWait(msg)) {
      /** 3. Receive message **/
      Wrappers.DataMessage rcvdMsg = messageBroker.receiveMsgForCurrentThread();

      logger.debug("Message received: {}", rcvdMsg.getMsgType());
    }

    /** 4. Put Object **/

    Field field = ((FieldSignatureImpl) staticPart.getSignature()).getField();
    field.setAccessible(true);

    IllegalAccessException exceptionSettingObject = null;
    try {
      //invoke set
      field.set(target, args[0]);
    } catch (IllegalAccessException iae) {
      exceptionSettingObject = iae;
    }

    /** 5. Wrap exception if any **/
    Wrappers.DataMessage invokedMsg = null;
    if (exceptionSettingObject != null) {
      invokedMsg = dataMessageBuilder.buildInitializerThrowable(id, staticPart, exceptionSettingObject);
    } else {
      invokedMsg = dataMessageBuilder.buildPutObjectDone(id, staticPart, sender, target, args[0]);
    }

    /** 6. Send object/exception **/
    messageBroker.send(invokedMsg);

    if (mustWait(invokedMsg)) {
      /** 7. Receive object/exception **/
      Wrappers.DataMessage rcvdMsg = messageBroker.receiveMsgForCurrentThread();

      logger.debug("Message received: {}", rcvdMsg.getMsgType());
    }

    /** 8. Return or re-raise exception **/
    if (exceptionSettingObject != null) {
      throw exceptionSettingObject;
    }

    logger.traceExit();
    return;
  }

  public static void incomingPutField(Fields.InstanceFieldPut instanceFieldPut, long recordOffset) {
    logger.traceEntry("with instanceFieldPut:\n {}, recordOffset: {}", instanceFieldPut, recordOffset);

    /** 1. Load class and field **/
    final Class clazz;
    Field field = null;
    Exception exceptionWhileLoading = null;
    try {
      clazz = Class.forName(instanceFieldPut.getClass_().getName());
      field = clazz.getDeclaredField(instanceFieldPut.getField().getName());
    } catch (Exception e) {
      exceptionWhileLoading = e;
    }

    /** 2. If class and field loaded, unwrap/load target object and value and invoke field set **/
    //TODO unwrap or load object before and have a separate exception for this step

    Exception exceptionWhileInvoking = null;

    final Object target;
    if (exceptionWhileLoading == null) {
      field.setAccessible(true);
      try {
        //unwrap or load target object
        if (instanceFieldPut.hasObject()) {
          target = Unwrapper.unwrapObject(instanceFieldPut.getObject(), field.getType());
          logger.debug("Unwrapped target: {}", target);
        } else {
          target = ObjectStore.lookupObject(instanceFieldPut.getObjectRef());
          logger.debug("Loaded target: {}", target);
        }
        //unwrap or load value
        final Object value;
        if (instanceFieldPut.hasValueObject()) {
          value = Unwrapper.unwrapObject(instanceFieldPut.getValueObject(), field.getType());
          logger.debug("Unwrapped value: {}", value);
        } else {
          value = ObjectStore.lookupObject(instanceFieldPut.getValueObjectRef());
          logger.debug("Loaded value: {}", value);
        }
        //invoke set
        field.set(target, value);
      } catch (Exception e) {
        exceptionWhileInvoking = e;
      }
    }

    /** 3. Wrap return value or exception **/
    Wrappers.DataMessage invokedMsg = null;

    if (exceptionWhileLoading != null) {
      invokedMsg = dataMessageBuilder.buildAccessibleObjectThrowable(id, field, exceptionWhileLoading, recordOffset);
    } else if (exceptionWhileInvoking != null) {
      invokedMsg = dataMessageBuilder.buildAccessibleObjectThrowable(id, field, exceptionWhileInvoking, recordOffset);
    } else {
      invokedMsg = dataMessageBuilder.buildPutObjectDone(id, instanceFieldPut, field.getType(), recordOffset);
    }


    /** 4. Send object/exception **/
    messageBroker.send(invokedMsg);

    logger.traceExit();
    return;

  }

  // </editor-fold>

  private static boolean mustWait(Wrappers.DataMessage dataMessage) {
    return true;
  }

  /**
   * The Concentrator takes 1 only argument, which is the location of the configuration (.properties) file
   *
   * @param args
   */
  public static void main(final String[] args) {
    if (args.length != 1) {
      System.err.println("Please provide the path to a configuration file");
      System.exit(1);
    }

    final Properties properties = new Properties();
    AbstractModule module = new AbstractModule() {
      @Override
      protected void configure() {
        try {
          properties.load(new FileInputStream(args[0]));
        } catch (IOException e) {
          logger.error("Could not load properties", e);
          System.err.println("Please provide a valid path to the configuration file");
          e.printStackTrace();
          System.exit(2);
        }

        Concentrator.id = Integer.parseInt(properties.getProperty("id"));

        Names.bindProperties(binder(), properties);
        //bind inmplementations
        bind(MessageBroker.class).to(KafkaMessageBroker.class);
        bind(DataMessageBuilder.class).to(ProtobufDataMessageBuilder.class);
        bind(DataMessageDispatcher.class).to(KafkaDataMessageDispatcher.class);
        bind(ExecutorService.class).to(Executor.class);
        //fields to be injected in Concentrator are static
        requestStaticInjection(Concentrator.class);
      }

      @Provides
      Map<Long, BlockingQueue<DataMessage>> returnThreadBlockingQueueMap() {
        return Concentrator.threadBlockingQueueMap;
      }

    };

    Injector injector = Guice.createInjector(module);

    /** Add shutdown hook **/
    Runtime.getRuntime().addShutdownHook(new Thread() {
      @Override
      public void run() {
        //try to gracefully close messageBroker msg dispatcher connections
        if (dataMessageDispatcher != null) {
          dataMessageDispatcher.requestShutdown();
        }

        //try to gracefully close messageBroker connections
        if (messageBroker != null) {
          messageBroker.shutdown();
        }

        try {
          Thread.sleep(3000);
        } catch (InterruptedException ie) {
          logger.error("Interrupted in shutdown hook sleep", ie);
        }
      }
    });

    //Start dispatching incoming messages
    dataMessageDispatcher.run();
  }
}
