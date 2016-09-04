package com.ittera.cometa.distributor;

import com.ittera.cometa.distributor.messages.data.*;

import org.aspectj.lang.reflect.ConstructorSignature;
import org.aspectj.lang.reflect.MethodSignature;
import org.aspectj.lang.JoinPoint.StaticPart;

import org.aspectj.runtime.reflect.FieldSignatureImpl;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.KafkaProducer;

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
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import org.apache.commons.lang3.StringUtils;

public class Distributor {
  protected static final Logger logger = LogManager.getLogger(Distributor.class);

  protected static KafkaProducer producer;
  protected static String kafkaTopic;
  protected static int id;

  //static data shared by all threads - sources of contention
  static Map<Long, BlockingQueue> threadBlockingQueueMap = new ConcurrentHashMap<Long, BlockingQueue>();

  private static Wrappers.DataMessage receiveMsgForCurrentThread() {
    long currThreadId = Thread.currentThread().getId();
    if (threadBlockingQueueMap.get(currThreadId) == null) {
      threadBlockingQueueMap.put(currThreadId, new ArrayBlockingQueue(1000));
    }
    Wrappers.DataMessage rcvdMsg = null;
    do {
      try {
        rcvdMsg = (Wrappers.DataMessage) threadBlockingQueueMap.get(Thread.currentThread().getId()).take();
      } catch (InterruptedException e) {
        e.printStackTrace();
      }
    } while (rcvdMsg == null);

    return rcvdMsg;
  }

  /************************ INTERFACE ***************************/

  // <editor-fold defaultstate="collapsed" desc="CONSTRUCTORS">
  public static boolean classConstructor(StaticPart staticPart, Object sender) throws ClassNotFoundException {
    logger.debug("in D.classConstructor: " + staticPart.getSignature());

    /** 1. Wrap message **/
    final Wrappers.DataMessage msg = DataMessageFactory.buildClassInitializerMessage(id, staticPart, sender);

    /** 2. Send message **/
    //ATTENTION: this send is asynchronous. Must call get later.
    producer.send(new ProducerRecord(kafkaTopic, msg));
    if (logger.isDebugEnabled()) {
      logger.debug("Sent new message!");
    }

    if (mustWait(msg)) {
      /** 3. Receive message **/
      Wrappers.DataMessage rcvdMsg = receiveMsgForCurrentThread();

      //TODO compare
      logger.info("Message received: " + rcvdMsg.getMsgType());
    }

    /** 4. Load and initialize class  -  WARNING: For some reason the class is not being initialized! **/
    Class clazz = null;
    ClassNotFoundException exceptionWhileLoadingClass = null;
    try {
      clazz = Class.forName(staticPart.getSignature().getDeclaringType().getName());
      //Class.forName(codeSignature.getDeclaringTypeName(),true, Distributor.class.getClassLoader());
    } catch (ClassNotFoundException cnfe) {
      exceptionWhileLoadingClass = cnfe;
    }

    /** 5. Wrap exception if any **/
    final Wrappers.DataMessage invokedMsg;
    if (exceptionWhileLoadingClass != null) {
      invokedMsg = DataMessageFactory.buildInitializerThrowableMessage(id, staticPart, exceptionWhileLoadingClass);
    } else {
      invokedMsg = DataMessageFactory.buildLoadedClassMessage(id, clazz);
    }

    /** 6. Send object/exception **/
    producer.send(new ProducerRecord(kafkaTopic, invokedMsg));
    if (logger.isDebugEnabled()) {
      logger.debug("Sent new message!");
    }

    if (mustWait(invokedMsg)) {
      /** 7. Receive object/exception **/
      Wrappers.DataMessage rcvdMsg = receiveMsgForCurrentThread();

      //TODO compare
      logger.info("Message received: " + rcvdMsg.getMsgType());
    }

    /** 8. Return or re-raise exception **/
    if (exceptionWhileLoadingClass != null) {
      throw exceptionWhileLoadingClass;
    }

    //Since class initialization is not working, we will return false if we want to aspectj to proceed(), indicating class isn't initialized
    return false;
  }

  /**
   * This method currently only support calling constructor whose arg(s) value(s) are fully contained in the msg. i.e. --> primitives, and Strings that haven't been trimmed.
   *
   * @param constructorCall
   * @throws Throwable
   */
  static void incomingConstructor(Calls.ConstructorCall constructorCall) {
    logger.debug("in D.incomingConstructor: " + constructorCall.getName());

    /** 1. Unwrap message and load constructor **/
    final Class clazz;
    final List<Class> paramClasses = new ArrayList<>();
    Constructor constructor = null;
    Exception exceptionWhileLoading = null;
    try {
      clazz = Class.forName(constructorCall.getName());
      for (String paramClassStr : constructorCall.getParameterClassesList()) {
        paramClasses.add(Class.forName(paramClassStr));
      }
      constructor = clazz.getDeclaredConstructor((Class[]) paramClasses.toArray(new Class[paramClasses.size()]));
    } catch (Exception e) {
      exceptionWhileLoading = e;
    }


    /** 2. If class and constructor loaded, unwrap arguments and invoke constructor **/
    Exception exceptionWhileInvoking = null;
    Object newObject = null;

    if (exceptionWhileLoading == null) {
      constructor.setAccessible(true);
      try {
        List<Object> args = new ArrayList<>();
        int objIdx = 0;
        for (Primitives.Object obj : constructorCall.getParameterList()) {
          args.add(ProtobufUtils.unwrapObject(obj, paramClasses.get(objIdx)));
        }
        newObject = constructor.newInstance(args);
      } catch (Exception ite) {
        exceptionWhileInvoking = ite;
      }
    }

    /** 3. Wrap new object or exception **/
    final Wrappers.DataMessage invokedMsg;

    if (exceptionWhileLoading != null) {
      invokedMsg = DataMessageFactory.buildAccessibleObjectThrowableMessage(id, constructor, exceptionWhileLoading);
    } else if (exceptionWhileInvoking != null) {
      invokedMsg = DataMessageFactory.buildAccessibleObjectThrowableMessage(id, constructor, exceptionWhileInvoking);
    } else {
      invokedMsg = DataMessageFactory.buildReturnValueMessage(id, newObject, false);
    }


    /** 4. Send object/exception **/
    producer.send(new ProducerRecord(kafkaTopic, invokedMsg));
    if (logger.isDebugEnabled()) {
      logger.debug("Sent new message!");
    }
  }

  public static Object constructor(StaticPart staticPart, Object sender, Object[] args) throws Throwable {
    logger.debug("in D.constructor: " + staticPart.getSignature());

    final ConstructorSignature constructorSignature = (ConstructorSignature) staticPart.getSignature();

    /** 1. Wrap message **/
    final Wrappers.DataMessage callMsg = DataMessageFactory.buildConstructorMessage(id, staticPart, sender, args);

    /** 2. Send message **/
    //ATTENTION: this send is asynchronous. Must call get later.
    producer.send(new ProducerRecord(kafkaTopic, callMsg));
    if (logger.isDebugEnabled()) {
      logger.debug("Sent new message!");
    }

    if (mustWait(callMsg)) {
      /** 3. Receive message **/
      Wrappers.DataMessage rcvdMsg = receiveMsgForCurrentThread();

      //TODO compare
      logger.info("Message received: " + rcvdMsg.getMsgType());
    }


    /** 4. Invoke constructor **/

    Constructor constructor = constructorSignature.getConstructor();

    Object newObject = null;
    Exception exceptionWhileInvoking = null;
    constructor.setAccessible(true);
    try {
      newObject = constructor.newInstance(args);
    } catch (Exception ite) {
      exceptionWhileInvoking = ite;
    }

    /** 5. Wrap new object or exception **/
    Wrappers.DataMessage invokedMsg = null;

    if (exceptionWhileInvoking != null) {
      invokedMsg = DataMessageFactory.buildAccessibleObjectThrowableMessage(id, constructor, exceptionWhileInvoking);
    } else {
      invokedMsg = DataMessageFactory.buildReturnValueMessage(id, newObject, false);
    }


    /** 6. Send object/exception **/
    producer.send(new ProducerRecord(kafkaTopic, invokedMsg));
    if (logger.isDebugEnabled()) {
      logger.debug("Sent new message!");
    }


    if (mustWait(invokedMsg)) {
      /** 7. Receive object/exception **/
      Wrappers.DataMessage rcvdMsg = receiveMsgForCurrentThread();

      //TODO compare
      logger.info("Message received: " + rcvdMsg.getMsgType());
    }

    /** 8. Return object or re-raise exception **/
    if (exceptionWhileInvoking != null) {
      if (exceptionWhileInvoking instanceof InvocationTargetException) {
        throw exceptionWhileInvoking.getCause();
      } else {
        throw exceptionWhileInvoking;
      }
    }

    return newObject;
  }
  // </editor-fold>

  // <editor-fold defaultstate="collapsed" desc="METHOD CALLS">

  public static void voidInstanceMethod(StaticPart staticPart, Object sender, Object target, Object[] args) throws Throwable {
    logger.debug("in D.voidInstanceMethod: " + staticPart.getSignature());

    final MethodSignature methodSignature = (MethodSignature) staticPart.getSignature();

    /** 1. Wrap message **/
    final Wrappers.DataMessage msg = DataMessageFactory.buildInstanceMethodMessage(id, staticPart, sender, target, args);


    /** 2. Send message **/
    //ATTENTION: this send is asynchronous. Must call get later.
    producer.send(new ProducerRecord(kafkaTopic, msg));
    if (logger.isDebugEnabled()) {
      logger.debug("Sent new message!");
    }

    if (mustWait(msg)) {
      /** 3. Receive message **/
      Wrappers.DataMessage rcvdMsg = receiveMsgForCurrentThread();

      //TODO compare
      logger.info("Message received: " + rcvdMsg.getMsgType());
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
      invokedMsg = DataMessageFactory.buildAccessibleObjectThrowableMessage(id, method, exceptionWhileInvoking);
    } else {
      invokedMsg = DataMessageFactory.buildReturnValueMessage(id, Void.class, true);
    }


    /** 6. Send object/exception **/
    producer.send(new ProducerRecord(kafkaTopic, invokedMsg));
    if (logger.isDebugEnabled()) {
      logger.debug("Sent new message!");
    }


    if (mustWait(invokedMsg)) {
      /** 7. Receive object/exception **/
      Wrappers.DataMessage rcvdMsg = receiveMsgForCurrentThread();

      //TODO compare
      logger.info("Message received: " + rcvdMsg.getMsgType());
    }

    /** 8. Return object or re-raise exception **/
    if (exceptionWhileInvoking != null) {
      if (exceptionWhileInvoking instanceof InvocationTargetException) {
        throw exceptionWhileInvoking.getCause();
      } else {
        throw exceptionWhileInvoking;
      }
    }

    return;
  }

  public static Object nonVoidInstanceMethod(StaticPart staticPart, Object sender, Object target, Object[] args) throws Throwable {
    logger.debug("in D.nonVoidInstanceMethod: " + staticPart.getSignature());

    final MethodSignature methodSignature = (MethodSignature) staticPart.getSignature();

    /** 1. Wrap message **/
    final Wrappers.DataMessage msg = DataMessageFactory.buildInstanceMethodMessage(id, staticPart, sender, target, args);


    /** 2. Send message **/
    //ATTENTION: this send is asynchronous. Must call get later.
    producer.send(new ProducerRecord(kafkaTopic, msg));
    if (logger.isDebugEnabled()) {
      logger.debug("Sent new message!");
    }

    if (mustWait(msg)) {
      /** 3. Receive message **/
      Wrappers.DataMessage rcvdMsg = receiveMsgForCurrentThread();

      //TODO compare
      logger.info("Message received: " + rcvdMsg.getMsgType());
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
      invokedMsg = DataMessageFactory.buildAccessibleObjectThrowableMessage(id, method, exceptionWhileInvoking);
    } else {
      invokedMsg = DataMessageFactory.buildReturnValueMessage(id, returnValue, false);
    }


    /** 6. Send object/exception **/
    producer.send(new ProducerRecord(kafkaTopic, invokedMsg));
    if (logger.isDebugEnabled()) {
      logger.debug("Sent new message!");
    }


    if (mustWait(invokedMsg)) {
      /** 7. Receive object/exception **/
      Wrappers.DataMessage rcvdMsg = receiveMsgForCurrentThread();

      //TODO compare
      logger.info("Message received: " + rcvdMsg.getMsgType());
    }

    /** 8. Return object or re-raise exception **/
    if (exceptionWhileInvoking != null) {
      if (exceptionWhileInvoking instanceof InvocationTargetException) {
        throw exceptionWhileInvoking.getCause();
      } else {
        throw exceptionWhileInvoking;
      }
    }

    return returnValue;
  }

  /**
   * This method currently only support calling method whose value is fully contained in the msg. i.e. --> primitives, and Strings that haven't been trimmed.
   *
   * @param instanceMethodCall
   */
  static void incomingInstanceMethod(Calls.InstanceMethodCall instanceMethodCall) {
    logger.debug("in D.incomingInstanceMethod: " + instanceMethodCall.getName());

    /** 1. Unwrap message and load method **/
    Class clazz = null;
    Method method = null;
    Exception exceptionWhileLoading = null;
    List<Class> paramClasses = new ArrayList<>();
    try {
      clazz = Class.forName(instanceMethodCall.getClass_());
      for (String paramClassStr : instanceMethodCall.getParameterClassesList()) {
        paramClasses.add(Class.forName(paramClassStr));
      }
      method = clazz.getDeclaredMethod(instanceMethodCall.getName(), (Class[]) paramClasses.toArray(new Class[paramClasses.size()]));
    } catch (Exception e) {
      exceptionWhileLoading = e;
    }

    /** 2. If class and method loaded, unwrap arguments and invoke method **/
    Exception exceptionWhileInvoking = null;
    Object returnValue = null;
    if (exceptionWhileLoading != null) {
      List<Object> args = new ArrayList<>();
      int objIdx = 0;
      for (Primitives.Object obj : instanceMethodCall.getParameterList()) {
        args.add(ProtobufUtils.unwrapObject(obj, paramClasses.get(objIdx)));
      }
      method.setAccessible(true);
      try {
        Object target = lookupTargetObject(instanceMethodCall.getTarget());
        returnValue = method.invoke(target, args.toArray());
      } catch (Exception e) {
        exceptionWhileInvoking = e;
      }
    }

    /** 3. Wrap return value or exception **/
    final Wrappers.DataMessage invokedMsg;

    if (exceptionWhileLoading != null) {
      invokedMsg = DataMessageFactory.buildAccessibleObjectThrowableMessage(id, method, exceptionWhileLoading);
    } else if (exceptionWhileInvoking != null) {
      invokedMsg = DataMessageFactory.buildAccessibleObjectThrowableMessage(id, method, exceptionWhileInvoking);
    } else {
      if (method.getReturnType() == Void.class) {
        invokedMsg = DataMessageFactory.buildReturnValueMessage(id, Void.class, true);
      } else {
        invokedMsg = DataMessageFactory.buildReturnValueMessage(id, returnValue, false);
      }
    }

    /** 4. Send object/exception **/
    producer.send(new ProducerRecord(kafkaTopic, invokedMsg));
    if (logger.isDebugEnabled()) {
      logger.debug("Sent new message!");
    }

    return;
  }

  public static void voidClassMethod(StaticPart staticPart, Object sender, Object[] args) throws Throwable {
    logger.debug("in D.voidClassMethod: " + staticPart.getSignature());

    final MethodSignature methodSignature = (MethodSignature) staticPart.getSignature();

    /** 1. Wrap message **/
    final Wrappers.DataMessage msg = DataMessageFactory.buildClassMethodMessage(id, staticPart, sender, args);


    /** 2. Send message **/
    //ATTENTION: this send is asynchronous. Must call get later.
    producer.send(new ProducerRecord(kafkaTopic, msg));
    if (logger.isDebugEnabled()) {
      logger.debug("Sent new message!");
    }

    if (mustWait(msg)) {
      /** 3. Receive message **/
      Wrappers.DataMessage rcvdMsg = receiveMsgForCurrentThread();

      //TODO compare
      logger.info("Message received: " + rcvdMsg.getMsgType());
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
      invokedMsg = DataMessageFactory.buildAccessibleObjectThrowableMessage(id, method, exceptionWhileInvoking);
    } else {
      invokedMsg = DataMessageFactory.buildReturnValueMessage(id, Void.class, true);
    }


    /** 6. Send object/exception **/
    producer.send(new ProducerRecord(kafkaTopic, invokedMsg));
    if (logger.isDebugEnabled()) {
      logger.debug("Sent new message!");
    }


    if (mustWait(invokedMsg)) {
      /** 7. Receive object/exception **/
      Wrappers.DataMessage rcvdMsg = receiveMsgForCurrentThread();

      //TODO compare
      logger.info("Message received: " + rcvdMsg.getMsgType());
    }

    /** 8. Return object or re-raise exception **/
    if (exceptionWhileInvoking != null) {
      if (exceptionWhileInvoking instanceof InvocationTargetException) {
        throw exceptionWhileInvoking.getCause();
      } else {
        throw exceptionWhileInvoking;
      }
    }

    return;
  }

  /**
   * This method currently only support calling method whose value is fully contained in the msg. i.e. --> primitives, and Strings that haven't been trimmed.
   *
   * @param classMethodCall
   */
  static void incomingClassMethod(Calls.ClassMethodCall classMethodCall) {
    logger.debug("in D.incomingClassMethod: " + classMethodCall.getName());

    /** 1. Unwrap message and load method **/
    Class clazz = null;
    Method method = null;
    Exception exceptionWhileLoading = null;
    List<Class> paramClasses = new ArrayList<>();
    try {
      clazz = Class.forName(classMethodCall.getClass_());
      for (String paramClassStr : classMethodCall.getParameterClassesList()) {
        paramClasses.add(Class.forName(paramClassStr));
      }
      method = clazz.getDeclaredMethod(classMethodCall.getName(), (Class[]) paramClasses.toArray(new Class[paramClasses.size()]));
    } catch (Exception e) {
      exceptionWhileLoading = e;
    }

    /** 2. If class and method loaded, unwrap arguments and invoke method **/
    Exception exceptionWhileInvoking = null;
    Object returnValue = null;
    if (exceptionWhileLoading != null) {
      List<Object> args = new ArrayList<>();
      int objIdx = 0;
      for (Primitives.Object obj : classMethodCall.getParameterList()) {
        args.add(ProtobufUtils.unwrapObject(obj, paramClasses.get(objIdx)));
      }
      method.setAccessible(true);
      try {
        returnValue = method.invoke(null, args.toArray());
      } catch (Exception e) {
        exceptionWhileInvoking = e;
      }
    }

    /** 3. Wrap return value or exception **/
    final Wrappers.DataMessage invokedMsg;

    if (exceptionWhileLoading != null) {
      invokedMsg = DataMessageFactory.buildAccessibleObjectThrowableMessage(id, method, exceptionWhileLoading);
    } else if (exceptionWhileInvoking != null) {
      invokedMsg = DataMessageFactory.buildAccessibleObjectThrowableMessage(id, method, exceptionWhileInvoking);
    } else {
      if (method.getReturnType() == Void.class) {
        invokedMsg = DataMessageFactory.buildReturnValueMessage(id, Void.class, true);
      } else {
        invokedMsg = DataMessageFactory.buildReturnValueMessage(id, returnValue, false);
      }
    }

    /** 4. Send object/exception **/
    producer.send(new ProducerRecord(kafkaTopic, invokedMsg));
    if (logger.isDebugEnabled()) {
      logger.debug("Sent new message!");
    }

    return;
  }


  public static Object nonVoidClassMethod(StaticPart staticPart, Object sender, Object[] args) throws Throwable {
    logger.debug("in D.nonVoidClassMethod: " + staticPart.getSignature());

    final MethodSignature methodSignature = (MethodSignature) staticPart.getSignature();

    /** 1. Wrap message **/
    final Wrappers.DataMessage msg = DataMessageFactory.buildClassMethodMessage(id, staticPart, sender, args);


    /** 2. Send message **/
    //ATTENTION: this send is asynchronous. Must call get later.
    producer.send(new ProducerRecord(kafkaTopic, msg));
    if (logger.isDebugEnabled()) {
      logger.debug("Sent new message!");
    }

    if (mustWait(msg)) {
      /** 3. Receive message **/
      Wrappers.DataMessage rcvdMsg = receiveMsgForCurrentThread();

      //TODO compare
      logger.info("Message received: " + rcvdMsg.getMsgType());
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
      invokedMsg = DataMessageFactory.buildAccessibleObjectThrowableMessage(id, method, exceptionWhileInvoking);
    } else {
      invokedMsg = DataMessageFactory.buildReturnValueMessage(id, returnValue, false);
    }


    /** 6. Send object/exception **/
    producer.send(new ProducerRecord(kafkaTopic, invokedMsg));
    if (logger.isDebugEnabled()) {
      logger.debug("Sent new message!");
    }


    if (mustWait(invokedMsg)) {
      /** 7. Receive object/exception **/
      Wrappers.DataMessage rcvdMsg = receiveMsgForCurrentThread();

      //TODO compare
      logger.info("Message received: " + rcvdMsg.getMsgType());
    }

    /** 8. Return object or re-raise exception **/
    if (exceptionWhileInvoking != null) {
      if (exceptionWhileInvoking instanceof InvocationTargetException) {
        throw exceptionWhileInvoking.getCause();
      } else {
        throw exceptionWhileInvoking;
      }
    }

    return returnValue;
  }


  // </editor-fold>

  // <editor-fold defaultstate="collapsed" desc="FIELD OPERATIONS">
  //@TODO field operations should also be sent as messages
  public static Object getStatic(StaticPart staticPart, Object sender) throws IllegalAccessException {
    logger.debug("in D.getStatic: " + staticPart.getSignature());

    /** 1. Wrap message **/
    final Wrappers.DataMessage msg = DataMessageFactory.buildGetStaticMessage(id, staticPart, sender);

    /** 2. Send message **/
    //ATTENTION: this send is asynchronous. Must call get later.
    producer.send(new ProducerRecord(kafkaTopic, msg));
    if (logger.isDebugEnabled()) {
      logger.debug("Sent new message!");
    }

    if (mustWait(msg)) {
      /** 3. Receive message **/
      Wrappers.DataMessage rcvdMsg = receiveMsgForCurrentThread();

      //TODO compare
      logger.info("Message received: " + rcvdMsg.getMsgType());
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
      invokedMsg = DataMessageFactory.buildReturnValueMessage(id, fieldValue, false);
    }

    /** 6. Send object/exception **/
    producer.send(new ProducerRecord(kafkaTopic, invokedMsg));
    if (logger.isDebugEnabled()) {
      logger.debug("Sent new message!");
    }

    if (mustWait(invokedMsg)) {
      /** 7. Receive object/exception **/
      Wrappers.DataMessage rcvdMsg = receiveMsgForCurrentThread();

      //TODO compare
      logger.info("Message received: " + rcvdMsg.getMsgType());
    }

    /** 8. Return or re-raise exception **/
    if (exceptionGettingObject != null) {
      throw exceptionGettingObject;
    }

    return fieldValue;
  }

  public static Object getObject(StaticPart staticPart, Object sender, Object target) throws IllegalAccessException {
    logger.debug("in D.getObject: " + staticPart.getSignature());

    /** 1. Wrap message **/
    final Wrappers.DataMessage msg = DataMessageFactory.buildGetObjectMessage(id, staticPart, sender, target);

    /** 2. Send message **/
    //ATTENTION: this send is asynchronous. Must call get later.
    producer.send(new ProducerRecord(kafkaTopic, msg));
    if (logger.isDebugEnabled()) {
      logger.debug("Sent new message!");
    }

    if (mustWait(msg)) {
      /** 3. Receive message **/
      Wrappers.DataMessage rcvdMsg = receiveMsgForCurrentThread();

      //TODO compare
      logger.info("Message received: " + rcvdMsg.getMsgType());
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
      invokedMsg = DataMessageFactory.buildReturnValueMessage(id, fieldValue, false);
    }

    /** 6. Send object/exception **/
    producer.send(new ProducerRecord(kafkaTopic, invokedMsg));
    if (logger.isDebugEnabled()) {
      logger.debug("Sent new message!");
    }

    if (mustWait(invokedMsg)) {
      /** 7. Receive object/exception **/
      Wrappers.DataMessage rcvdMsg = receiveMsgForCurrentThread();

      //TODO compare
      logger.info("Message received: " + rcvdMsg.getMsgType());
    }

    /** 8. Return or re-raise exception **/
    if (exceptionGettingObject != null) {
      throw exceptionGettingObject;
    }

    return fieldValue;
  }

  public static void putStatic(StaticPart staticPart, Object sender, Object[] args) throws IllegalAccessException {
    logger.debug("in D.putStatic: " + staticPart.getSignature());

    /** 1. Wrap message **/
    final Wrappers.DataMessage msg = DataMessageFactory.buildPutStaticMessage(id, staticPart, sender, args[0]);

    /** 2. Send message **/
    //ATTENTION: this send is asynchronous. Must call get later.
    producer.send(new ProducerRecord(kafkaTopic, msg));
    if (logger.isDebugEnabled()) {
      logger.debug("Sent new message!");
    }

    if (mustWait(msg)) {
      /** 3. Receive message **/
      Wrappers.DataMessage rcvdMsg = receiveMsgForCurrentThread();

      //TODO compare
      logger.info("Message received: " + rcvdMsg.getMsgType());
    }

    /** 4. Put Object **/

    Field field = ((FieldSignatureImpl) staticPart.getSignature()).getField();
    field.setAccessible(true);

    IllegalAccessException exceptionSettingObject = null;
    try {
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
    producer.send(new ProducerRecord(kafkaTopic, invokedMsg));
    if (logger.isDebugEnabled()) {
      logger.debug("Sent new message!");
    }

    if (mustWait(msg)) {
      /** 7. Receive object/exception **/
      Wrappers.DataMessage rcvdMsg = receiveMsgForCurrentThread();

      //TODO compare
      logger.info("Message received: " + rcvdMsg.getMsgType());
    }

    /** 8. Return or re-raise exception **/
    if (exceptionSettingObject != null) {
      throw exceptionSettingObject;
    }

    return;
  }

  public static void putField(StaticPart staticPart, Object sender, Object target, Object[] args) throws IllegalAccessException {
    logger.debug("in D.putField: " + staticPart.getSignature());

    /** 1. Wrap message **/
    final Wrappers.DataMessage msg = DataMessageFactory.buildPutObjectMessage(id, staticPart, sender, target, args[0]);

    /** 2. Send message **/
    //ATTENTION: this send is asynchronous. Must call get later.
    producer.send(new ProducerRecord(kafkaTopic, msg));
    if (logger.isDebugEnabled()) {
      logger.debug("Sent new message!");
    }

    if (mustWait(msg)) {
      /** 3. Receive message **/
      Wrappers.DataMessage rcvdMsg = receiveMsgForCurrentThread();

      //TODO compare
      logger.info("Message received: " + rcvdMsg.getMsgType());
    }

    /** 4. Put Object **/

    Field field = ((FieldSignatureImpl) staticPart.getSignature()).getField();
    field.setAccessible(true);

    IllegalAccessException exceptionSettingObject = null;
    try {
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
    producer.send(new ProducerRecord(kafkaTopic, invokedMsg));
    if (logger.isDebugEnabled()) {
      logger.debug("Sent new message!");
    }

    if (mustWait(invokedMsg)) {
      /** 7. Receive object/exception **/
      Wrappers.DataMessage rcvdMsg = receiveMsgForCurrentThread();

      //TODO compare
      logger.info("Message received: " + rcvdMsg.getMsgType());
    }

    /** 8. Return or re-raise exception **/
    if (exceptionSettingObject != null) {
      throw exceptionSettingObject;
    }
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
   * @param targetHash
   * @return
   */
  private static Object lookupTargetObject(int targetHash) {
    return null;
  }

  /**
   * The Distributor takes 1 only argument, which is the location of the configuration (.properties) file
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


    /** Configure Distributor **/
    Distributor.id = Integer.valueOf(properties.getProperty("id"));
    kafkaTopic = properties.getProperty("kafkaTopic");


    /** Configure and Initialize Kafka Producer **/
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
    kafkaProducerProps.put("client.id", String.valueOf(Distributor.id));
    producer = new KafkaProducer<>(kafkaProducerProps);


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
