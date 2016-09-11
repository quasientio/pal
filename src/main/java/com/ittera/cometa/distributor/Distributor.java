package com.ittera.cometa.distributor;

import com.ittera.cometa.distributor.messages.data.*;

import com.ittera.cometa.distributor.messages.data.Primitives;
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
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingDeque;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import org.apache.commons.lang3.StringUtils;

public class Distributor {
  protected static final Logger logger = LogManager.getLogger(Distributor.class);

  static KafkaProducer producer;
  static String kafkaTopic;
  static int id;


  /**
   * static data shared by all threads - sources of contention
   */

  //A map to hold a blocking message queue for each Thread
  static final Map<Long, BlockingQueue> threadBlockingQueueMap = new ConcurrentHashMap();
  static final Map<String, Object> objectMap = new ConcurrentHashMap<>();

  //A map for all objects created by the Distributor. TODO: store as WeakReferences -> until then, no objects will get garbage cleaned!

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

  private static void checkCreateThreadQueue() {
    long currThreadId = Thread.currentThread().getId();
    if (!threadBlockingQueueMap.containsKey(currThreadId)) {
      threadBlockingQueueMap.put(currThreadId, new LinkedBlockingDeque());
      logger.debug("Added new blocking queue to map, with thread id={}", currThreadId);
    }
  }

  /************************ INTERFACE ***************************/

  // <editor-fold defaultstate="collapsed" desc="CONSTRUCTORS">
  public static boolean classConstructor(StaticPart staticPart, Object sender) throws ClassNotFoundException {
    logger.trace("in D.classConstructor: {}", staticPart.getSignature());

    /** 0. Ensure thread has a receiving message queue */
    checkCreateThreadQueue();

    /** 1. Wrap message **/
    final Wrappers.DataMessage msg = DataMessageFactory.buildClassInitializerMessage(id, staticPart, sender);

    /** 2. Send message **/
    //ATTENTION: this send is asynchronous. Must call get later.
    producer.send(new ProducerRecord(kafkaTopic, msg));
    logger.debug("Sent new message!");

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
      //Class.forName(codeSignature.getDeclaringTypeName(),true, Distributor.class.getClassLoader());
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
    producer.send(new ProducerRecord(kafkaTopic, invokedMsg));
    logger.debug("Sent new message!");

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
    logger.trace("leavingin D.classConstructor: {}", staticPart.getSignature());
    return false;
  }

  /**
   * This method currently only support calling constructor whose arg(s) value(s) are fully contained in the msg. i.e. --> primitives, and Strings that haven't been trimmed.
   *
   * @param constructorCall
   * @throws Throwable
   */
  static void incomingConstructor(Calls.ConstructorCall constructorCall) {
    logger.trace("in D.incomingConstructor: {}", constructorCall.getClass_().getName());

    /** 1. Unwrap message and load constructor **/
    final Class clazz;
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
      invokedMsg = DataMessageFactory.buildAccessibleObjectThrowableMessage(id, constructor, exceptionWhileLoading);
    } else if (exceptionWhileInvoking != null) {
      invokedMsg = DataMessageFactory.buildAccessibleObjectThrowableMessage(id, constructor, exceptionWhileInvoking);
    } else {
      invokedMsg = DataMessageFactory.buildReturnValueMessage(id, newObject, objKey, false);
    }


    /** 4. Send object/exception **/
    producer.send(new ProducerRecord(kafkaTopic, invokedMsg));
    logger.debug("Sent new message!");


    logger.trace("leaving D.incomingConstructor: {}", constructorCall.getClass_().getName());
    return;
  }

  public static Object constructor(StaticPart staticPart, Object sender, Object[] args) throws Throwable {
    logger.trace("in D.constructor: {}", staticPart.getSignature());

    /** 0. Ensure thread has a receiving message queue */
    checkCreateThreadQueue();

    final ConstructorSignature constructorSignature = (ConstructorSignature) staticPart.getSignature();

    /** 1. Wrap message **/
    final Wrappers.DataMessage callMsg = DataMessageFactory.buildConstructorMessage(id, staticPart, sender, args);

    /** 2. Send message **/
    //ATTENTION: this send is asynchronous. Must call get later.
    producer.send(new ProducerRecord(kafkaTopic, callMsg));
    logger.debug("Sent new message!");

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
      invokedMsg = DataMessageFactory.buildAccessibleObjectThrowableMessage(id, constructor, exceptionWhileInvoking);
    } else {
      invokedMsg = DataMessageFactory.buildReturnValueMessage(id, newObject, objKey, false);
    }


    /** 6. Send object/exception **/
    producer.send(new ProducerRecord(kafkaTopic, invokedMsg));
    logger.debug("Sent new message!");


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

    logger.trace("leaving D.constructor: {}", staticPart.getSignature());
    return newObject;
  }
  // </editor-fold>

  // <editor-fold defaultstate="collapsed" desc="METHOD CALLS">

  public static void voidInstanceMethod(StaticPart staticPart, Object sender, Object target, Object[] args) throws Throwable {
    logger.trace("in D.voidInstanceMethod: {}", staticPart.getSignature());

    /** 0. Ensure thread has a receiving message queue */
    checkCreateThreadQueue();

    final MethodSignature methodSignature = (MethodSignature) staticPart.getSignature();

    /** 1. Wrap message **/
    final Wrappers.DataMessage msg = DataMessageFactory.buildInstanceMethodMessage(id, staticPart, sender, target, args);


    /** 2. Send message **/
    //ATTENTION: this send is asynchronous. Must call get later.
    producer.send(new ProducerRecord(kafkaTopic, msg));
    logger.debug("Sent new message!");

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
      invokedMsg = DataMessageFactory.buildAccessibleObjectThrowableMessage(id, method, exceptionWhileInvoking);
    } else {
      invokedMsg = DataMessageFactory.buildReturnValueMessage(id, Void.class, null, true);
    }


    /** 6. Send object/exception **/
    producer.send(new ProducerRecord(kafkaTopic, invokedMsg));
    logger.debug("Sent new message!");


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

    logger.trace("leaving D.voidInstanceMethod: {}", staticPart.getSignature());
    return;
  }

  public static Object nonVoidInstanceMethod(StaticPart staticPart, Object sender, Object target, Object[] args) throws Throwable {
    logger.trace("in D.nonVoidInstanceMethod: {}", staticPart.getSignature());

    /** 0. Ensure thread has a receiving message queue */
    checkCreateThreadQueue();

    final MethodSignature methodSignature = (MethodSignature) staticPart.getSignature();

    /** 1. Wrap message **/
    final Wrappers.DataMessage msg = DataMessageFactory.buildInstanceMethodMessage(id, staticPart, sender, target, args);


    /** 2. Send message **/
    //ATTENTION: this send is asynchronous. Must call get later.
    producer.send(new ProducerRecord(kafkaTopic, msg));
    logger.debug("Sent new message!");

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
      invokedMsg = DataMessageFactory.buildAccessibleObjectThrowableMessage(id, method, exceptionWhileInvoking);
    } else {
      invokedMsg = DataMessageFactory.buildReturnValueMessage(id, returnValue, "TO DO", false);
    }


    /** 6. Send object/exception **/
    producer.send(new ProducerRecord(kafkaTopic, invokedMsg));
    logger.debug("Sent new message!");


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

    logger.trace("leaving D.nonVoidInstanceMethod: {}", staticPart.getSignature());
    return returnValue;
  }

  /**
   * This method currently only support calling method whose value is fully contained in the msg. i.e. --> primitives, and Strings that haven't been trimmed.
   *
   * @param instanceMethodCall
   */
  static void incomingInstanceMethod(Calls.InstanceMethodCall instanceMethodCall) {
    logger.trace("in D.incomingInstanceMethod: {}", instanceMethodCall.getName());

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
        //if object created by this Distributor, get it from object map
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
      invokedMsg = DataMessageFactory.buildAccessibleObjectThrowableMessage(id, method, exceptionWhileLoading);
    } else if (exceptionWhileInvoking != null) {
      invokedMsg = DataMessageFactory.buildAccessibleObjectThrowableMessage(id, method, exceptionWhileInvoking);
    } else {
      if (method.getReturnType() == Void.class) {
        invokedMsg = DataMessageFactory.buildReturnValueMessage(id, Void.class, null, true);
      } else {
        invokedMsg = DataMessageFactory.buildReturnValueMessage(id, returnValue, "TO DO", false);
      }
    }

    /** 4. Send object/exception **/
    producer.send(new ProducerRecord(kafkaTopic, invokedMsg));
    logger.debug("Sent new message!");

    logger.trace("leaving D.incomingInstanceMethod: {}", instanceMethodCall.getName());
    return;
  }

  public static void voidClassMethod(StaticPart staticPart, Object sender, Object[] args) throws Throwable {
    logger.trace("in D.voidClassMethod: {}", staticPart.getSignature());

    /** 0. Ensure thread has a receiving message queue */
    checkCreateThreadQueue();

    final MethodSignature methodSignature = (MethodSignature) staticPart.getSignature();

    /** 1. Wrap message **/
    final Wrappers.DataMessage msg = DataMessageFactory.buildClassMethodMessage(id, staticPart, sender, args);


    /** 2. Send message **/
    //ATTENTION: this send is asynchronous. Must call get later.
    producer.send(new ProducerRecord(kafkaTopic, msg));
    logger.debug("Sent new message!");

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
      invokedMsg = DataMessageFactory.buildAccessibleObjectThrowableMessage(id, method, exceptionWhileInvoking);
    } else {
      invokedMsg = DataMessageFactory.buildReturnValueMessage(id, Void.class, null, true);
    }


    /** 6. Send object/exception **/
    producer.send(new ProducerRecord(kafkaTopic, invokedMsg));
    logger.debug("Sent new message!");


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

    logger.trace("leaving D.voidClassMethod: {}", staticPart.getSignature());
    return;
  }

  public static Object nonVoidClassMethod(StaticPart staticPart, Object sender, Object[] args) throws Throwable {
    logger.trace("in D.nonVoidClassMethod: {}", staticPart.getSignature());

    /** 0. Ensure thread has a receiving message queue */
    checkCreateThreadQueue();

    final MethodSignature methodSignature = (MethodSignature) staticPart.getSignature();

    /** 1. Wrap message **/
    final Wrappers.DataMessage msg = DataMessageFactory.buildClassMethodMessage(id, staticPart, sender, args);


    /** 2. Send message **/
    //ATTENTION: this send is asynchronous. Must call get later.
    producer.send(new ProducerRecord(kafkaTopic, msg));
    logger.debug("Sent new message!");

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
      invokedMsg = DataMessageFactory.buildAccessibleObjectThrowableMessage(id, method, exceptionWhileInvoking);
    } else {
      invokedMsg = DataMessageFactory.buildReturnValueMessage(id, returnValue, "TO DO", false);
    }


    /** 6. Send object/exception **/
    producer.send(new ProducerRecord(kafkaTopic, invokedMsg));
    logger.debug("Sent new message!");


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

    logger.trace("leaving D.nonVoidClassMethod: {}", staticPart.getSignature());
    return returnValue;
  }


  /**
   * This method currently only support calling method whose value is fully contained in the msg. i.e. --> primitives, and Strings that haven't been trimmed.
   *
   * @param classMethodCall
   */
  static void incomingClassMethod(Calls.ClassMethodCall classMethodCall) {
    logger.trace("in D.incomingClassMethod: {}", classMethodCall.getName());

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
      invokedMsg = DataMessageFactory.buildAccessibleObjectThrowableMessage(id, method, exceptionWhileLoading);
    } else if (exceptionWhileInvoking != null) {
      invokedMsg = DataMessageFactory.buildAccessibleObjectThrowableMessage(id, method, exceptionWhileInvoking);
    } else {
      if (method.getReturnType() == Void.class) {
        invokedMsg = DataMessageFactory.buildReturnValueMessage(id, Void.class, null, true);
      } else {
        invokedMsg = DataMessageFactory.buildReturnValueMessage(id, returnValue, "TO DO", false);
      }
    }

    /** 4. Send object/exception **/
    producer.send(new ProducerRecord(kafkaTopic, invokedMsg));
    logger.debug("Sent new message from D.incomingClassMethod!");

    logger.trace("leaving D.incomingClassMethod: {}", classMethodCall.getName());
    return;
  }


  // </editor-fold>

  // <editor-fold defaultstate="collapsed" desc="FIELD OPERATIONS">

  public static void incomingGetStatic(Fields.StaticFieldGet staticFieldGet) {
    logger.trace("in D.incomingGetStatic: {}.{}", staticFieldGet.getClass_(), staticFieldGet.getField());

    /** 0. Ensure thread has a receiving message queue */
    checkCreateThreadQueue();

    /** 1. Get Object **/
    Class clazz = null;
    Field field = null;
    Exception exceptionWhileLoading = null;
    try {
      clazz = Class.forName(staticFieldGet.getClass_().getName());
      field = clazz.getDeclaredField(staticFieldGet.getField());
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
      invokedMsg = DataMessageFactory.buildAccessibleObjectThrowableMessage(id, field, exceptionWhileLoading);
    } else if (exceptionWhileInvoking != null) {
      invokedMsg = DataMessageFactory.buildAccessibleObjectThrowableMessage(id, field, exceptionWhileInvoking);
    } else {
      invokedMsg = DataMessageFactory.buildReturnValueMessage(id, fieldValue, "TO DO", false);
    }


    /** 4. Send object/exception **/
    producer.send(new ProducerRecord(kafkaTopic, invokedMsg));
    logger.debug("Sent new message!");

    logger.trace("leaving D.incomingGetStatic: {}.{}", staticFieldGet.getClass_(), staticFieldGet.getField());
    return;

  }

  public static Object getStatic(StaticPart staticPart, Object sender) throws IllegalAccessException {
    logger.trace("in D.getStatic: {}", staticPart.getSignature());

    /** 0. Ensure thread has a receiving message queue */
    checkCreateThreadQueue();

    /** 1. Wrap message **/
    final Wrappers.DataMessage msg = DataMessageFactory.buildGetStaticMessage(id, staticPart, sender);

    /** 2. Send message **/
    //ATTENTION: this send is asynchronous. Must call get later.
    producer.send(new ProducerRecord(kafkaTopic, msg));
    logger.debug("Sent new message!");

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
      invokedMsg = DataMessageFactory.buildReturnValueMessage(id, fieldValue, "TO DO", false);
    }

    /** 6. Send object/exception **/
    producer.send(new ProducerRecord(kafkaTopic, invokedMsg));
    logger.debug("Sent new message!");

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

    logger.trace("leaving D.getStatic: {}", staticPart.getSignature());
    return fieldValue;
  }

  public static void incomingGetObject(Fields.InstanceFieldGet instanceFieldGet) {
    logger.trace("in D.incomingGetObject: {}.{}", instanceFieldGet.getClass_(), instanceFieldGet.getField());

    /** 0. Ensure thread has a receiving message queue */
    checkCreateThreadQueue();

    /** 1. Get Object **/
    Class clazz = null;
    Field field = null;
    Exception exceptionWhileLoading = null;
    try {
      clazz = Class.forName(instanceFieldGet.getClass_().getName());
      field = clazz.getDeclaredField(instanceFieldGet.getField());
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
      invokedMsg = DataMessageFactory.buildAccessibleObjectThrowableMessage(id, field, exceptionWhileLoading);
    } else if (exceptionWhileInvoking != null) {
      invokedMsg = DataMessageFactory.buildAccessibleObjectThrowableMessage(id, field, exceptionWhileInvoking);
    } else {
      invokedMsg = DataMessageFactory.buildReturnValueMessage(id, fieldValue, "TO DO", false);
    }


    /** 4. Send object/exception **/
    producer.send(new ProducerRecord(kafkaTopic, invokedMsg));
    logger.debug("Sent new message!");

    logger.trace("leaving D.incomingGetObject: {}.{}", instanceFieldGet.getClass_(), instanceFieldGet.getField());
    return;

  }

  public static Object getObject(StaticPart staticPart, Object sender, Object target) throws IllegalAccessException {
    logger.trace("in D.getObject: {}", staticPart.getSignature());

    /** 0. Ensure thread has a receiving message queue */
    checkCreateThreadQueue();

    /** 1. Wrap message **/
    final Wrappers.DataMessage msg = DataMessageFactory.buildGetObjectMessage(id, staticPart, sender, target);

    /** 2. Send message **/
    //ATTENTION: this send is asynchronous. Must call get later.
    producer.send(new ProducerRecord(kafkaTopic, msg));
    logger.debug("Sent new message!");

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
      invokedMsg = DataMessageFactory.buildReturnValueMessage(id, fieldValue, "TO DO", false);
    }

    /** 6. Send object/exception **/
    producer.send(new ProducerRecord(kafkaTopic, invokedMsg));
    logger.debug("Sent new message!");

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

    logger.trace("in D.getObject: {}", staticPart.getSignature());
    return fieldValue;
  }

  public static void incomingPutStatic(Fields.StaticFieldPut staticFieldPut) {
    logger.trace("in D.incomingPutStatic: {}.{}", staticFieldPut.getClass_(), staticFieldPut.getField());

    /** 0. Ensure thread has a receiving message queue */
    checkCreateThreadQueue();

    /** 1. Load class and field **/
    final Class clazz;
    Field field = null;
    Exception exceptionWhileLoading = null;
    try {
      clazz = Class.forName(staticFieldPut.getClass_().getName());
      field = clazz.getDeclaredField(staticFieldPut.getField());
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
      invokedMsg = DataMessageFactory.buildAccessibleObjectThrowableMessage(id, field, exceptionWhileLoading);
    } else if (exceptionWhileInvoking != null) {
      invokedMsg = DataMessageFactory.buildAccessibleObjectThrowableMessage(id, field, exceptionWhileInvoking);
    } else {
      invokedMsg = DataMessageFactory.buildReturnValueMessage(id, Void.class, null, false);
    }


    /** 4. Send object/exception **/
    producer.send(new ProducerRecord(kafkaTopic, invokedMsg));
    logger.debug("Sent new message!");

    logger.trace("leaving D.incomingPutStatic: {}.{}", staticFieldPut.getClass_(), staticFieldPut.getField());
    return;

  }

  public static void putStatic(StaticPart staticPart, Object sender, Object[] args) throws IllegalAccessException {
    logger.trace("in D.putStatic: {}", staticPart.getSignature());

    /** 0. Ensure thread has a receiving message queue */
    checkCreateThreadQueue();

    /** 1. Wrap message **/
    final Wrappers.DataMessage msg = DataMessageFactory.buildPutStaticMessage(id, staticPart, sender, args[0]);

    /** 2. Send message **/
    //ATTENTION: this send is asynchronous. Must call get later.
    producer.send(new ProducerRecord(kafkaTopic, msg));
    logger.debug("Sent new message!");

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
    producer.send(new ProducerRecord(kafkaTopic, invokedMsg));
    logger.debug("Sent new message!");

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

    logger.trace("in D.putStatic: {}", staticPart.getSignature());
    return;
  }

  public static void putField(StaticPart staticPart, Object sender, Object target, Object[] args) throws IllegalAccessException {
    logger.trace("in D.putField: {}", staticPart.getSignature());

    /** 0. Ensure thread has a receiving message queue */
    checkCreateThreadQueue();

    /** 1. Wrap message **/
    final Wrappers.DataMessage msg = DataMessageFactory.buildPutObjectMessage(id, staticPart, sender, target, args[0]);

    /** 2. Send message **/
    //ATTENTION: this send is asynchronous. Must call get later.
    producer.send(new ProducerRecord(kafkaTopic, msg));
    logger.debug("Sent new message!");

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
    producer.send(new ProducerRecord(kafkaTopic, invokedMsg));
    logger.debug("Sent new message!");

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

    logger.trace("leaving D.putField: {}", staticPart.getSignature());
    return;
  }

  public static void incomingPutField(Fields.InstanceFieldPut instanceFieldPut) {
    logger.trace("in D.incomingPutField: {}.{}", instanceFieldPut.getClass_(), instanceFieldPut.getField());

    /** 0. Ensure thread has a receiving message queue */
    checkCreateThreadQueue();

    /** 1. Load class and field **/
    final Class clazz;
    Field field = null;
    Exception exceptionWhileLoading = null;
    try {
      clazz = Class.forName(instanceFieldPut.getClass_().getName());
      field = clazz.getDeclaredField(instanceFieldPut.getField());
    } catch (Exception e) {
      exceptionWhileLoading = e;
    }

    /** 2. If class and field loaded, unwrap/load target object and value and invoke field set **/
    //TODO unwrap or load object before and have a separate exception for this step

    Exception exceptionWhileInvoking = null;

    if (exceptionWhileLoading == null) {
      field.setAccessible(true);
      try {
        //unwrap or load target object
        final Object target;
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
      invokedMsg = DataMessageFactory.buildAccessibleObjectThrowableMessage(id, field, exceptionWhileLoading);
    } else if (exceptionWhileInvoking != null) {
      invokedMsg = DataMessageFactory.buildAccessibleObjectThrowableMessage(id, field, exceptionWhileInvoking);
    } else {
      invokedMsg = DataMessageFactory.buildReturnValueMessage(id, Void.class, null, false);
    }


    /** 4. Send object/exception **/
    producer.send(new ProducerRecord(kafkaTopic, invokedMsg));
    logger.debug("Sent new message!");

    logger.trace("leaving D.incomingPutField: {}.{}", instanceFieldPut.getClass_(), instanceFieldPut.getField());
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
    Distributor.id = Integer.parseInt(properties.getProperty("id"));
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
