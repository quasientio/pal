package com.ittera.cometa.concentrator;

import com.ittera.cometa.concentrator.messages.data.*;

import com.ittera.cometa.concentrator.messages.data.Primitives;
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
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import org.apache.commons.lang3.StringUtils;

public class Concentrator {
  protected static final Logger logger = LogManager.getLogger(Concentrator.class);


  /**
   * Static data shared by all threads - sources of contention
   */
  //A map to hold a blocking message queue for each Thread
  static MessageBroker broker;
  static int id;
  static final Map<Long, BlockingQueue> threadBlockingQueueMap = new ConcurrentHashMap();
  static final Map<String, Object> objectMap = new ConcurrentHashMap<>();

  //A map for all objects created by the Concentrator. TODO: store as WeakReferences -> until then, no threads will get garbage cleaned!
  private static Wrappers.DataMessage receiveMsgForCurrentThread() {
    long currThreadId = Thread.currentThread().getId();
    Wrappers.DataMessage rcvdMsg = null;
    do {
      try {
        rcvdMsg = (Wrappers.DataMessage) threadBlockingQueueMap.get(currThreadId).take();
        logger.debug("Taken new message from blocking queue (thread id={})", currThreadId);
      } catch (InterruptedException e) {
        logger.error("Interrupted while taking from blocking queue", e);
      }
    } while (rcvdMsg == null);

    return rcvdMsg;
  }


  /************************ INTERFACE ***************************/

  // <editor-fold defaultstate="collapsed" desc="CONSTRUCTORS">
  public static boolean classConstructor(StaticPart staticPart, Object sender) throws ClassNotFoundException {
    logger.trace("in classConstructor: {}", staticPart.getSignature());

    /** 1. Wrap message **/
    final Wrappers.DataMessage msg = DataMessageFactory.buildClassInitializerMessage(id, staticPart, sender);

    /** 2. Send message **/
    //ATTENTION: this send is asynchronous. Must call get later.
    broker.send(msg);

    if (mustWait(msg)) {
      /** 3. Receive message **/
      Wrappers.DataMessage rcvdMsg = receiveMsgForCurrentThread();

      //TODO compare
      logger.info("Message received: {}", rcvdMsg.getMsgType());
    }

    /** 4. Load and initialize class  -  WARNING: For some reason the class is not being initialized! **/
    Class clazz = null;
    ClassNotFoundException exceptionWhileLoadingClass = null;
    Long randomLong = null;
    try {
      clazz = Class.forName(staticPart.getSignature().getDeclaringType().getName());
      randomLong = ThreadLocalRandom.current().nextLong();
      //Class.forName(codeSignature.getDeclaringTypeName(),true, Concentrator.class.getClassLoader());
    } catch (ClassNotFoundException cnfe) {
      exceptionWhileLoadingClass = cnfe;
    }

    /** 5. Store and wrap class/exception if any **/
    final Wrappers.DataMessage invokedMsg;
    if (exceptionWhileLoadingClass != null) {
      invokedMsg = DataMessageFactory.buildInitializerThrowableMessage(id, staticPart, exceptionWhileLoadingClass);
    } else {
      String objKey = String.format("%d:%d", System.identityHashCode(clazz), randomLong);
      storeObject(objKey, clazz);
      invokedMsg = DataMessageFactory.buildLoadedClassMessage(id, clazz);
    }

    /** 6. Send object/exception **/
    broker.send(invokedMsg);

    if (mustWait(invokedMsg)) {
      /** 7. Receive object/exception **/
      Wrappers.DataMessage rcvdMsg = receiveMsgForCurrentThread();

      //TODO compare
      logger.info("Message received: {}", rcvdMsg.getMsgType());
    }

    /** 8. Return or re-raise exception **/
    if (exceptionWhileLoadingClass != null) {
      throw exceptionWhileLoadingClass;
    }

    //Since class initialization is not working, we will return false if we want to aspectj to proceed(), indicating class isn't initialized
    logger.trace("leaving classConstructor: {}", staticPart.getSignature());
    return false;
  }

  /**
   * This method currently only support calling constructor whose arg(s) value(s) are fully contained in the msg. i.e. --> primitives, and Strings that haven't been trimmed.
   *
   * @param constructorCall
   * @throws Throwable
   */
  static void incomingConstructor(Calls.ConstructorCall constructorCall, long recordOffset) {
    logger.trace("in incomingConstructor: {}", constructorCall.getClass_().getName());

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
    Long randomLong = null;

    if (exceptionWhileLoading == null) {
      constructor.setAccessible(true);
      try {
        List<Object> args = new ArrayList<>();
        int objIdx = 0;
        for (Primitives.Object obj : constructorCall.getParameterList()) {
          args.add(ProtobufUtils.unwrapObject(obj, paramClasses.get(objIdx)));
        }
        //store in object map
        newObject = constructor.newInstance(args.toArray(new Object[args.size()]));
        randomLong = ThreadLocalRandom.current().nextLong();
        //store in object map
        objKey = String.format("%d:%d", System.identityHashCode(newObject), randomLong);
        storeObject(objKey, newObject);
      } catch (Exception ite) {
        exceptionWhileInvoking = ite;
      }
    }

    /** 3. Wrap new object or exception **/
    final Wrappers.DataMessage invokedMsg;

    if (exceptionWhileLoading != null) {
      invokedMsg = DataMessageFactory.buildAccessibleObjectThrowableMessage(id, constructor, exceptionWhileLoading, recordOffset);
    } else if (exceptionWhileInvoking != null) {
      invokedMsg = DataMessageFactory.buildAccessibleObjectThrowableMessage(id, constructor, exceptionWhileInvoking, recordOffset);
    } else {
      invokedMsg = DataMessageFactory.buildReturnValueMessage(id, newObject, clazz, objKey, false, recordOffset);
    }


    /** 4. Send object/exception **/
    broker.send(invokedMsg);

    logger.trace("leaving incomingConstructor: {}", constructorCall.getClass_().getName());
    return;
  }

  public static Object constructor(StaticPart staticPart, Object sender, Object[] args) throws Throwable {
    logger.trace("in constructor: {}", staticPart.getSignature());

    final ConstructorSignature constructorSignature = (ConstructorSignature) staticPart.getSignature();

    /** 1. Wrap message **/
    final Wrappers.DataMessage callMsg = DataMessageFactory.buildConstructorMessage(id, staticPart, sender, args);

    /** 2. Send message **/
    //ATTENTION: this send is asynchronous. Must call get later.
    broker.send(callMsg);

    if (mustWait(callMsg)) {
      /** 3. Receive message **/
      Wrappers.DataMessage rcvdMsg = receiveMsgForCurrentThread();

      //TODO compare
      logger.info("Message received: {}", rcvdMsg.getMsgType());
    }


    /** 4. Invoke constructor **/

    Constructor constructor = constructorSignature.getConstructor();

    Object newObject = null;
    Exception exceptionWhileInvoking = null;
    constructor.setAccessible(true);
    String objKey = null;
    Long randomLong = null;
    try {
      newObject = constructor.newInstance(args);
      randomLong = ThreadLocalRandom.current().nextLong();
      //store in object map
      objKey = String.format("%d:%d", System.identityHashCode(newObject), randomLong);
      storeObject(objKey, newObject);
    } catch (Exception ite) {
      exceptionWhileInvoking = ite;
    }

    /** 5. Wrap new object or exception **/
    Wrappers.DataMessage invokedMsg = null;

    if (exceptionWhileInvoking != null) {
      invokedMsg = DataMessageFactory.buildAccessibleObjectThrowableMessage(id, constructor, exceptionWhileInvoking, null);
    } else {
      invokedMsg = DataMessageFactory.buildReturnValueMessage(id, newObject, constructor.getClass(), objKey, false, null);
    }


    /** 6. Send object/exception **/
    broker.send(invokedMsg);

    if (mustWait(invokedMsg)) {
      /** 7. Receive object/exception **/
      Wrappers.DataMessage rcvdMsg = receiveMsgForCurrentThread();

      //TODO compare
      logger.info("Message received: {}", rcvdMsg.getMsgType());
    }

    /** 8. Return object or re-raise exception **/
    if (exceptionWhileInvoking != null) {
      if (exceptionWhileInvoking instanceof InvocationTargetException) {
        throw exceptionWhileInvoking.getCause();
      } else {
        throw exceptionWhileInvoking;
      }
    }

    logger.trace("leaving constructor: {}", staticPart.getSignature());
    return newObject;
  }
  // </editor-fold>

  // <editor-fold defaultstate="collapsed" desc="METHOD CALLS">

  public static void voidInstanceMethod(StaticPart staticPart, Object sender, Object target, Object[] args) throws Throwable {
    logger.trace("in voidInstanceMethod: {}", staticPart.getSignature());

    final MethodSignature methodSignature = (MethodSignature) staticPart.getSignature();

    /** 1. Wrap message **/
    final Wrappers.DataMessage msg = DataMessageFactory.buildInstanceMethodMessage(id, staticPart, sender, target, args);


    /** 2. Send message **/
    //ATTENTION: this send is asynchronous. Must call get later.
    broker.send(msg);

    if (mustWait(msg)) {
      /** 3. Receive message **/
      Wrappers.DataMessage rcvdMsg = receiveMsgForCurrentThread();

      //TODO compare
      logger.info("Message received: {}", rcvdMsg.getMsgType());
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
      invokedMsg = DataMessageFactory.buildAccessibleObjectThrowableMessage(id, method, exceptionWhileInvoking, null);
    } else {
      invokedMsg = DataMessageFactory.buildReturnValueMessage(id, Void.class, method.getReturnType(), null, true, null);
    }


    /** 6. Send object/exception **/
    broker.send(invokedMsg);

    if (mustWait(invokedMsg)) {
      /** 7. Receive object/exception **/
      Wrappers.DataMessage rcvdMsg = receiveMsgForCurrentThread();

      //TODO compare
      logger.info("Message received: {}", rcvdMsg.getMsgType());
    }

    /** 8. Return object or re-raise exception **/
    if (exceptionWhileInvoking != null) {
      if (exceptionWhileInvoking instanceof InvocationTargetException) {
        throw exceptionWhileInvoking.getCause();
      } else {
        throw exceptionWhileInvoking;
      }
    }

    logger.trace("leaving voidInstanceMethod: {}", staticPart.getSignature());
    return;
  }

  public static Object nonVoidInstanceMethod(StaticPart staticPart, Object sender, Object target, Object[] args) throws Throwable {
    logger.trace("in nonVoidInstanceMethod: {}", staticPart.getSignature());

    final MethodSignature methodSignature = (MethodSignature) staticPart.getSignature();

    /** 1. Wrap message **/
    final Wrappers.DataMessage msg = DataMessageFactory.buildInstanceMethodMessage(id, staticPart, sender, target, args);


    /** 2. Send message **/
    //ATTENTION: this send is asynchronous. Must call get later.
    broker.send(msg);

    if (mustWait(msg)) {
      /** 3. Receive message **/
      Wrappers.DataMessage rcvdMsg = receiveMsgForCurrentThread();

      //TODO compare
      logger.info("Message received: {}", rcvdMsg.getMsgType());
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
      invokedMsg = DataMessageFactory.buildAccessibleObjectThrowableMessage(id, method, exceptionWhileInvoking, null);
    } else {
      invokedMsg = DataMessageFactory.buildReturnValueMessage(id, returnValue, method.getReturnType(), "TO DO", false, null);
    }


    /** 6. Send object/exception **/
    broker.send(invokedMsg);

    if (mustWait(invokedMsg)) {
      /** 7. Receive object/exception **/
      Wrappers.DataMessage rcvdMsg = receiveMsgForCurrentThread();

      //TODO compare
      logger.info("Message received: {}", rcvdMsg.getMsgType());
    }

    /** 8. Return object or re-raise exception **/
    if (exceptionWhileInvoking != null) {
      if (exceptionWhileInvoking instanceof InvocationTargetException) {
        throw exceptionWhileInvoking.getCause();
      } else {
        throw exceptionWhileInvoking;
      }
    }

    logger.trace("leaving nonVoidInstanceMethod: {}", staticPart.getSignature());
    return returnValue;
  }

  /**
   * This method currently only support calling method whose value is fully contained in the msg. i.e. --> primitives, and Strings that haven't been trimmed.
   *
   * @param instanceMethodCall
   */
  static void incomingInstanceMethod(Calls.InstanceMethodCall instanceMethodCall, long recordOffset) {
    logger.trace("in incomingInstanceMethod: {}", instanceMethodCall.getName());

    /** 1. Unwrap message and load method **/
    Class clazz = null;
    Method method = null;
    Exception exceptionWhileLoading = null;
    List<Class> paramClasses = new ArrayList<>();
    try {
      clazz = Class.forName(instanceMethodCall.getClass_().getName());
      for (Primitives.Object obj : instanceMethodCall.getParameterList()) {
        paramClasses.add(Class.forName(obj.getClass_().getName()));
      }
      method = clazz.getDeclaredMethod(instanceMethodCall.getName(), (Class[]) paramClasses.toArray(new Class[paramClasses.size()]));
    } catch (Exception e) {
      exceptionWhileLoading = e;
    }

    /** 2. If class and method loaded, unwrap arguments and invoke method **/
    Exception exceptionWhileInvoking = null;
    Object returnValue = null;
    if (exceptionWhileLoading == null) {
      List<Object> args = new ArrayList<>();
      int objIdx = 0;
      for (Primitives.Object obj : instanceMethodCall.getParameterList()) {
        //if object created by this Concentrator, get it from object map
//        if (objects.containsKey(obj.getIdentityHash())) {
//          args.add(lookupObject(obj));
//        } else { //else unwrap using ProtobufUtils (only primitives and Strings supported)
        args.add(ProtobufUtils.unwrapObject(obj, paramClasses.get(objIdx)));
//        }
      }
      method.setAccessible(true);
      try {
        Object target = lookupObject(instanceMethodCall.getObjectRef());
        returnValue = method.invoke(target, args.toArray());
      } catch (Exception e) {
        exceptionWhileInvoking = e;
      }
    }

    /** 3. Wrap return value or exception **/
    final Wrappers.DataMessage invokedMsg;

    if (exceptionWhileLoading != null) {
      invokedMsg = DataMessageFactory.buildAccessibleObjectThrowableMessage(id, method, exceptionWhileLoading, recordOffset);
    } else if (exceptionWhileInvoking != null) {
      invokedMsg = DataMessageFactory.buildAccessibleObjectThrowableMessage(id, method, exceptionWhileInvoking, recordOffset);
    } else {
      if (method.getReturnType() == void.class) {
        invokedMsg = DataMessageFactory.buildReturnValueMessage(id, Void.class, method.getReturnType(), null, true, recordOffset);
      } else {
        invokedMsg = DataMessageFactory.buildReturnValueMessage(id, returnValue, method.getReturnType(), "TO DO", false, recordOffset);
      }
    }

    /** 4. Send object/exception **/
    broker.send(invokedMsg);

    logger.trace("leaving incomingInstanceMethod: {}", instanceMethodCall.getName());
    return;
  }

  public static void voidClassMethod(StaticPart staticPart, Object sender, Object[] args) throws Throwable {
    logger.trace("in voidClassMethod: {}", staticPart.getSignature());

    final MethodSignature methodSignature = (MethodSignature) staticPart.getSignature();

    /** 1. Wrap message **/
    final Wrappers.DataMessage msg = DataMessageFactory.buildClassMethodMessage(id, staticPart, sender, args);


    /** 2. Send message **/
    //ATTENTION: this send is asynchronous. Must call get later.
    broker.send(msg);

    if (mustWait(msg)) {
      /** 3. Receive message **/
      Wrappers.DataMessage rcvdMsg = receiveMsgForCurrentThread();

      //TODO compare
      logger.info("Message received: {}", rcvdMsg.getMsgType());
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
      invokedMsg = DataMessageFactory.buildAccessibleObjectThrowableMessage(id, method, exceptionWhileInvoking, null);
    } else {
      invokedMsg = DataMessageFactory.buildReturnValueMessage(id, Void.class, method.getReturnType(), null, true, null);
    }


    /** 6. Send object/exception **/
    broker.send(invokedMsg);

    if (mustWait(invokedMsg)) {
      /** 7. Receive object/exception **/
      Wrappers.DataMessage rcvdMsg = receiveMsgForCurrentThread();

      //TODO compare
      logger.info("Message received: {}", rcvdMsg.getMsgType());
    }

    /** 8. Return object or re-raise exception **/
    if (exceptionWhileInvoking != null) {
      if (exceptionWhileInvoking instanceof InvocationTargetException) {
        throw exceptionWhileInvoking.getCause();
      } else {
        throw exceptionWhileInvoking;
      }
    }

    logger.trace("leaving voidClassMethod: {}", staticPart.getSignature());
    return;
  }

  public static Object nonVoidClassMethod(StaticPart staticPart, Object sender, Object[] args) throws Throwable {
    logger.trace("in nonVoidClassMethod: {}", staticPart.getSignature());

    final MethodSignature methodSignature = (MethodSignature) staticPart.getSignature();

    /** 1. Wrap message **/
    final Wrappers.DataMessage msg = DataMessageFactory.buildClassMethodMessage(id, staticPart, sender, args);


    /** 2. Send message **/
    //ATTENTION: this send is asynchronous. Must call get later.
    broker.send(msg);

    if (mustWait(msg)) {
      /** 3. Receive message **/
      Wrappers.DataMessage rcvdMsg = receiveMsgForCurrentThread();

      //TODO compare
      logger.info("Message received: {}", rcvdMsg.getMsgType());
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
      invokedMsg = DataMessageFactory.buildAccessibleObjectThrowableMessage(id, method, exceptionWhileInvoking, null);
    } else {
      invokedMsg = DataMessageFactory.buildReturnValueMessage(id, returnValue, method.getReturnType(), "TO DO", false, null);
    }


    /** 6. Send object/exception **/
    broker.send(invokedMsg);

    if (mustWait(invokedMsg)) {
      /** 7. Receive object/exception **/
      Wrappers.DataMessage rcvdMsg = receiveMsgForCurrentThread();

      //TODO compare
      logger.info("Message received: {}", rcvdMsg.getMsgType());
    }

    /** 8. Return object or re-raise exception **/
    if (exceptionWhileInvoking != null) {
      if (exceptionWhileInvoking instanceof InvocationTargetException) {
        throw exceptionWhileInvoking.getCause();
      } else {
        throw exceptionWhileInvoking;
      }
    }

    logger.trace("leaving nonVoidClassMethod: {}", staticPart.getSignature());
    return returnValue;
  }


  /**
   * This method currently only support calling method whose value is fully contained in the msg. i.e. --> primitives, and Strings that haven't been trimmed.
   *
   * @param classMethodCall
   */
  static void incomingClassMethod(Calls.ClassMethodCall classMethodCall, long recordOffset) {
    logger.trace("in incomingClassMethod: {}", classMethodCall.getName());

    /** 1. Unwrap message and load method **/
    Class clazz = null;
    Method method = null;
    Exception exceptionWhileLoading = null;
    List<Class> paramClasses = new ArrayList<>();
    try {
      logger.debug("Attempting to load (initialize) class");
      clazz = Class.forName(classMethodCall.getClass_().getName());
      for (Primitives.Object obj : classMethodCall.getParameterList()) {
        paramClasses.add(Class.forName(obj.getClass_().getName()));
      }
      method = clazz.getDeclaredMethod(classMethodCall.getName(), (Class[]) paramClasses.toArray(new Class[paramClasses.size()]));
    } catch (Exception e) {
      exceptionWhileLoading = e;
    }

    /** 2. If class and method loaded, unwrap arguments and invoke method **/
    Exception exceptionWhileInvoking = null;
    Object returnValue = null;
    if (exceptionWhileLoading == null) {
      List<Object> args = new ArrayList<>();
      int objIdx = 0;
      for (Primitives.Object obj : classMethodCall.getParameterList()) {
        args.add(ProtobufUtils.unwrapObject(obj, paramClasses.get(objIdx)));
      }
      method.setAccessible(true);
      try {
        logger.debug("Invoking class method NOW!");
        returnValue = method.invoke(null, args.toArray());
      } catch (Exception e) {
        exceptionWhileInvoking = e;
      }
    }

    /** 3. Wrap return value or exception **/
    final Wrappers.DataMessage invokedMsg;

    if (exceptionWhileLoading != null) {
      invokedMsg = DataMessageFactory.buildAccessibleObjectThrowableMessage(id, method, exceptionWhileLoading, recordOffset);
    } else if (exceptionWhileInvoking != null) {
      invokedMsg = DataMessageFactory.buildAccessibleObjectThrowableMessage(id, method, exceptionWhileInvoking, recordOffset);
    } else {
      if (method.getReturnType() == void.class) {
        invokedMsg = DataMessageFactory.buildReturnValueMessage(id, Void.class, method.getReturnType(), null, true, recordOffset);
      } else {
        invokedMsg = DataMessageFactory.buildReturnValueMessage(id, returnValue, method.getReturnType(), "TO DO", false, recordOffset);
      }
    }

    /** 4. Send object/exception **/
    broker.send(invokedMsg);

    logger.trace("leaving incomingClassMethod: {}", classMethodCall.getName());
    return;
  }


  // </editor-fold>

  // <editor-fold defaultstate="collapsed" desc="FIELD OPERATIONS">

  public static void incomingGetStatic(Fields.StaticFieldGet staticFieldGet, long recordOffset) {
    logger.trace("in incomingGetStatic: {}.{}", staticFieldGet.getClass_(), staticFieldGet.getField());

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
      invokedMsg = DataMessageFactory.buildAccessibleObjectThrowableMessage(id, field, exceptionWhileLoading, recordOffset);
    } else if (exceptionWhileInvoking != null) {
      invokedMsg = DataMessageFactory.buildAccessibleObjectThrowableMessage(id, field, exceptionWhileInvoking, recordOffset);
    } else {
      invokedMsg = DataMessageFactory.buildReturnValueMessage(id, fieldValue, field.getType(), "TO DO", false, recordOffset);
    }


    /** 4. Send object/exception **/
    broker.send(invokedMsg);

    logger.trace("leaving incomingGetStatic: {}.{}", staticFieldGet.getClass_(), staticFieldGet.getField());
    return;

  }

  public static Object getStatic(StaticPart staticPart, Object sender) throws IllegalAccessException {
    logger.trace("in getStatic: {}", staticPart.getSignature());

    /** 1. Wrap message **/
    final Wrappers.DataMessage msg = DataMessageFactory.buildGetStaticMessage(id, staticPart, sender);

    /** 2. Send message **/
    //ATTENTION: this send is asynchronous. Must call get later.
    broker.send(msg);

    if (mustWait(msg)) {
      /** 3. Receive message **/
      Wrappers.DataMessage rcvdMsg = receiveMsgForCurrentThread();

      //TODO compare
      logger.info("Message received: {}", rcvdMsg.getMsgType());
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
      invokedMsg = DataMessageFactory.buildInitializerThrowableMessage(id, staticPart, exceptionGettingObject);
    } else {
      invokedMsg = DataMessageFactory.buildReturnValueMessage(id, fieldValue, field.getType(), "TO DO", false, null);
    }

    /** 6. Send object/exception **/
    broker.send(invokedMsg);

    if (mustWait(invokedMsg)) {
      /** 7. Receive object/exception **/
      Wrappers.DataMessage rcvdMsg = receiveMsgForCurrentThread();

      //TODO compare
      logger.info("Message received: {}", rcvdMsg.getMsgType());
    }

    /** 8. Return or re-raise exception **/
    if (exceptionGettingObject != null) {
      throw exceptionGettingObject;
    }

    logger.trace("leaving getStatic: {}", staticPart.getSignature());
    return fieldValue;
  }

  public static void incomingGetObject(Fields.InstanceFieldGet instanceFieldGet, long recordOffset) {
    logger.trace("in incomingGetObject: {}.{}", instanceFieldGet.getClass_(), instanceFieldGet.getField());

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
        Object target = lookupObject(instanceFieldGet.getObjectRef());
        fieldValue = field.get(target);
      } catch (Exception e) {
        exceptionWhileInvoking = e;
      }
    }


    /** 3. Wrap return value or exception **/
    Wrappers.DataMessage invokedMsg = null;

    if (exceptionWhileLoading != null) {
      invokedMsg = DataMessageFactory.buildAccessibleObjectThrowableMessage(id, field, exceptionWhileLoading, recordOffset);
    } else if (exceptionWhileInvoking != null) {
      invokedMsg = DataMessageFactory.buildAccessibleObjectThrowableMessage(id, field, exceptionWhileInvoking, recordOffset);
    } else {
      invokedMsg = DataMessageFactory.buildReturnValueMessage(id, fieldValue, field.getType(), "TO DO", false, recordOffset);
    }


    /** 4. Send object/exception **/
    broker.send(invokedMsg);

    logger.trace("leaving incomingGetObject: {}.{}", instanceFieldGet.getClass_(), instanceFieldGet.getField());
    return;

  }

  public static Object getObject(StaticPart staticPart, Object sender, Object target) throws IllegalAccessException {
    logger.trace("in getObject: {}", staticPart.getSignature());

    /** 1. Wrap message **/
    final Wrappers.DataMessage msg = DataMessageFactory.buildGetObjectMessage(id, staticPart, sender, target);

    /** 2. Send message **/
    //ATTENTION: this send is asynchronous. Must call get later.
    broker.send(msg);

    if (mustWait(msg)) {
      /** 3. Receive message **/
      Wrappers.DataMessage rcvdMsg = receiveMsgForCurrentThread();

      //TODO compare
      logger.info("Message received: {}", rcvdMsg.getMsgType());
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
      invokedMsg = DataMessageFactory.buildInitializerThrowableMessage(id, staticPart, exceptionGettingObject);
    } else {
      invokedMsg = DataMessageFactory.buildReturnValueMessage(id, fieldValue, field.getType(), "TO DO", false, null);
    }

    /** 6. Send object/exception **/
    broker.send(invokedMsg);

    if (mustWait(invokedMsg)) {
      /** 7. Receive object/exception **/
      Wrappers.DataMessage rcvdMsg = receiveMsgForCurrentThread();

      //TODO compare
      logger.info("Message received: {}", rcvdMsg.getMsgType());
    }

    /** 8. Return or re-raise exception **/
    if (exceptionGettingObject != null) {
      throw exceptionGettingObject;
    }

    logger.trace("in getObject: {}", staticPart.getSignature());
    return fieldValue;
  }

  public static void incomingPutStatic(Fields.StaticFieldPut staticFieldPut, long recordOffset) {
    logger.trace("in incomingPutStatic: {}.{}", staticFieldPut.getClass_(), staticFieldPut.getField());

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
          value = ProtobufUtils.unwrapObject(staticFieldPut.getObject(), field.getType());
          logger.debug("Unwrapped value: {}", value);
        } else {
          value = lookupObject(staticFieldPut.getObjectRef());
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
      invokedMsg = DataMessageFactory.buildAccessibleObjectThrowableMessage(id, field, exceptionWhileLoading, recordOffset);
    } else if (exceptionWhileInvoking != null) {
      invokedMsg = DataMessageFactory.buildAccessibleObjectThrowableMessage(id, field, exceptionWhileInvoking, recordOffset);
    } else {
      invokedMsg = DataMessageFactory.buildPutStaticDoneMessage(id, staticFieldPut, field.getType(), recordOffset);
    }


    /** 4. Send object/exception **/
    broker.send(invokedMsg);

    logger.trace("leaving incomingPutStatic: {}.{}", staticFieldPut.getClass_(), staticFieldPut.getField());
    return;

  }

  public static void putStatic(StaticPart staticPart, Object sender, Object[] args) throws IllegalAccessException {
    logger.trace("in putStatic: {}", staticPart.getSignature());

    /** 1. Wrap message **/
    final Wrappers.DataMessage msg = DataMessageFactory.buildPutStaticMessage(id, staticPart, sender, args[0]);

    /** 2. Send message **/
    //ATTENTION: this send is asynchronous. Must call get later.
    broker.send(msg);

    if (mustWait(msg)) {
      /** 3. Receive message **/
      Wrappers.DataMessage rcvdMsg = receiveMsgForCurrentThread();

      //TODO compare
      logger.info("Message received: {}", rcvdMsg.getMsgType());
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
      invokedMsg = DataMessageFactory.buildInitializerThrowableMessage(id, staticPart, exceptionSettingObject);
    } else {
      invokedMsg = DataMessageFactory.buildPutStaticDoneMessage(id, staticPart, sender, args[0]);
    }

    /** 6. Send object/exception **/
    broker.send(invokedMsg);

    if (mustWait(msg)) {
      /** 7. Receive object/exception **/
      Wrappers.DataMessage rcvdMsg = receiveMsgForCurrentThread();

      //TODO compare
      logger.info("Message received: {}", rcvdMsg.getMsgType());
    }

    /** 8. Return or re-raise exception **/
    if (exceptionSettingObject != null) {
      throw exceptionSettingObject;
    }

    logger.trace("leaving putStatic: {}", staticPart.getSignature());
    return;
  }

  public static void putField(StaticPart staticPart, Object sender, Object target, Object[] args) throws IllegalAccessException {
    logger.trace("in putField: {}", staticPart.getSignature());

    /** 1. Wrap message **/
    final Wrappers.DataMessage msg = DataMessageFactory.buildPutObjectMessage(id, staticPart, sender, target, args[0]);

    /** 2. Send message **/
    //ATTENTION: this send is asynchronous. Must call get later.
    broker.send(msg);

    if (mustWait(msg)) {
      /** 3. Receive message **/
      Wrappers.DataMessage rcvdMsg = receiveMsgForCurrentThread();

      //TODO compare
      logger.info("Message received: {}", rcvdMsg.getMsgType());
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
      invokedMsg = DataMessageFactory.buildInitializerThrowableMessage(id, staticPart, exceptionSettingObject);
    } else {
      invokedMsg = DataMessageFactory.buildPutObjectDoneMessage(id, staticPart, sender, target, args[0]);
    }

    /** 6. Send object/exception **/
    broker.send(invokedMsg);

    if (mustWait(invokedMsg)) {
      /** 7. Receive object/exception **/
      Wrappers.DataMessage rcvdMsg = receiveMsgForCurrentThread();

      //TODO compare
      logger.info("Message received: {}", rcvdMsg.getMsgType());
    }

    /** 8. Return or re-raise exception **/
    if (exceptionSettingObject != null) {
      throw exceptionSettingObject;
    }

    logger.trace("leaving putField: {}", staticPart.getSignature());
    return;
  }

  public static void incomingPutField(Fields.InstanceFieldPut instanceFieldPut, long recordOffset) {
    logger.trace("in incomingPutField: {}.{}", instanceFieldPut.getClass_(), instanceFieldPut.getField());

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
          target = ProtobufUtils.unwrapObject(instanceFieldPut.getObject(), field.getType());
          logger.debug("Unwrapped target: {}", target);
        } else {
          target = lookupObject(instanceFieldPut.getObjectRef());
          logger.debug("Loaded target: {}", target);
        }
        //unwrap or load value
        final Object value;
        if (instanceFieldPut.hasValueObject()) {
          value = ProtobufUtils.unwrapObject(instanceFieldPut.getValueObject(), field.getType());
          logger.debug("Unwrapped value: {}", value);
        } else {
          value = lookupObject(instanceFieldPut.getValueObjectRef());
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
      invokedMsg = DataMessageFactory.buildAccessibleObjectThrowableMessage(id, field, exceptionWhileLoading, recordOffset);
    } else if (exceptionWhileInvoking != null) {
      invokedMsg = DataMessageFactory.buildAccessibleObjectThrowableMessage(id, field, exceptionWhileInvoking, recordOffset);
    } else {
      invokedMsg = DataMessageFactory.buildPutObjectDoneMessage(id, instanceFieldPut, field.getType(), recordOffset);
    }


    /** 4. Send object/exception **/
    broker.send(invokedMsg);

    logger.trace("leaving incomingPutField: {}.{}", instanceFieldPut.getClass_(), instanceFieldPut.getField());
    return;

  }

  // </editor-fold>


  /**
   * TODO: IMPLEMENT
   *
   * @param dataMessage
   * @return
   */

  private static boolean mustWait(Wrappers.DataMessage dataMessage) {
    return false;
  }

  /**
   * TODO: IMPLEMENT
   *
   * @param objectRef
   * @return
   */
  private static Object lookupObject(String objectRef) {
    return objectMap.get(objectRef);
  }

  private static void storeObject(String objectRef, Object object) {
    objectMap.put(objectRef, object);
  }

  /**
   * The Concentrator takes 1 only argument, which is the location of the configuration (.properties) file
   *
   * @param args
   */
  public static void main(String[] args) {
    if (args.length != 1) {
      System.err.println("Please provide the path to a configuration file");
      System.exit(1);
    }

    Properties properties = new Properties();
    try {
      properties.load(new FileInputStream(args[0]));
    } catch (IOException e) {
      System.err.println("Please provide a valid path to the configuration file");
      e.printStackTrace();
      System.exit(2);
    }


    /** Configure Concentrator **/
    Concentrator.id = Integer.parseInt(properties.getProperty("id"));

    /** Add shutdown hook **/
    Runtime.getRuntime().addShutdownHook(new Thread() {
      @Override
      public void run() {
        //try to gracefully close broker msg dispatcher connections
        if (DataMessageDispatcher.getInstance() != null) {
          DataMessageDispatcher.getInstance().requestShutdown();
        }

        //try to gracefully close broker connections
        broker.shutdown();

        try {
          Thread.sleep(3000);
        } catch (InterruptedException ie) {
          logger.error("Interrupted in shutdown hook sleep", ie);
        }
      }
    });

    /** Configure and Initialize Kafka Producer **/
    /** TODO refactor the horribly looking loading of properties
     * We could pass them all to the Broker and Dispatcher, and let each parse their own
     **/
    final Properties kafkaProducerProps = new Properties();
    //common kafka properties
    for (String propKey : properties.stringPropertyNames()) {
      if (propKey.startsWith("kafka.") && !(propKey.startsWith("kafka.consumer") || propKey.startsWith("kafka.producer"))) {
        kafkaProducerProps.put(StringUtils.substringAfter(propKey, "kafka."), properties.getProperty(propKey));
      }
    }
    //producer properties
    for (String propKey : properties.stringPropertyNames()) {
      if (propKey.startsWith("kafka.producer.")) {
        kafkaProducerProps.put(StringUtils.substringAfter(propKey, "kafka.producer."), properties.getProperty(propKey));
      }
    }
    //other producer specific props
    kafkaProducerProps.put("client.id", String.valueOf(Concentrator.id));
    String kafkaTopic = properties.getProperty("kafkaTopic");
    broker = new MessageBroker(kafkaProducerProps, kafkaTopic);


    /** Configure and Initialize Kafka Message Consumer/Dispatcher thread **/
    Properties msgDispatcherProps = new Properties();
    for (String propKey : properties.stringPropertyNames()) {
      if (propKey.startsWith("kafka.") && !(propKey.startsWith("kafka.consumer") || propKey.startsWith("kafka.producer"))) {
        msgDispatcherProps.put(StringUtils.substringAfter(propKey, "kafka."), properties.getProperty(propKey));
      }
    }
    //consumer properties
    for (String propKey : properties.stringPropertyNames()) {
      if (propKey.startsWith("kafka.consumer.")) {
        msgDispatcherProps.put(StringUtils.substringAfter(propKey, "kafka.consumer."), properties.getProperty(propKey));
      }
    }
    DataMessageDispatcher messageDispatcher = DataMessageDispatcher.getInstance(msgDispatcherProps);
    messageDispatcher.start();

  }
}
