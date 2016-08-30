package com.ittera.cometa.distributor;

import com.ittera.cometa.distributor.messages.data.DataMessageFactory;
import com.ittera.cometa.distributor.messages.data.Wrappers;

import org.aspectj.lang.reflect.ConstructorSignature;
import org.aspectj.lang.reflect.MethodSignature;
import org.aspectj.lang.JoinPoint.StaticPart;

import org.aspectj.runtime.reflect.FieldSignatureImpl;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.KafkaProducer;

import java.lang.Class;
import java.lang.reflect.Field;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.InvocationTargetException;

import java.util.Properties;

public class Distributor {
  protected static final KafkaProducer producer;
  protected static final String kafkaTopic = "test";
  protected static final int id = 10;


  static {
    //Initialize Kafka Producer
    final Properties props = new Properties();
    props.put("bootstrap.servers", "localhost:9092");
    props.put("client.id", String.valueOf(id));
    props.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
    props.put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer");
    //props.put("value.serializer", "com.ittera.cometa.distributor.messages.ProtobufSerializer");
    producer = new KafkaProducer<>(props);
  }

  /************************ INTERFACE ***************************/

  // <editor-fold defaultstate="collapsed" desc="CONSTRUCTORS">
  public static boolean classConstructor(StaticPart staticPart, Object sender) throws ClassNotFoundException {

    /** 1. Wrap message **/
    final Wrappers.DataMessage msg = DataMessageFactory.buildClassInitializerMessage(id, staticPart, sender);

    /** 2. Send message **/
    //ATTENTION: this send is asynchronous. Must call get later.
    producer.send(new ProducerRecord(kafkaTopic,msg.toString()));

    /** 3. Receive message **/

    //TO DO receive

    //TO DO compare

    /** 4. Load and initialize class  -  WARNING: For some reason the class is not being initialized! **/
    Class clazz=null;
    ClassNotFoundException exceptionWhileLoadingClass = null;
    try {
      clazz = Class.forName(staticPart.getSignature().getDeclaringType().getName());
      //Class.forName(codeSignature.getDeclaringTypeName(),true, Distributor.class.getClassLoader());
    } catch (ClassNotFoundException cnfe) {
      exceptionWhileLoadingClass = cnfe;
    }

    /** 5. Wrap exception if any **/
    Wrappers.DataMessage exceptionMsg = null;
    if (exceptionWhileLoadingClass != null) {
      exceptionMsg = DataMessageFactory.buildInitializerThrowableMessage(id, staticPart, exceptionWhileLoadingClass);
    }

    /** 6. Send object/exception **/
    if (exceptionWhileLoadingClass != null) {
      producer.send(new ProducerRecord(kafkaTopic, exceptionMsg.toString()));
    }
    //TO DO send class initialization return message (what value?)


    /** 7. Receive object/exception **/

    //TO DO receive

    //TO DO compare

    /** 8. Return or re-raise exception **/
    if (exceptionWhileLoadingClass != null) {
      throw exceptionWhileLoadingClass;
    }

    //Since class initialization is not working, we will return false if we want to aspectj to proceed(), indicating class isn't initialized
    return false;
  }

  public static Object constructor(StaticPart staticPart, Object sender, Object[] args) throws Throwable {

    final ConstructorSignature codeSignature = (ConstructorSignature) staticPart.getSignature();

    /** 1. Wrap message **/
    final Wrappers.DataMessage callMsg = DataMessageFactory.buildConstructorMessage(id, staticPart, sender, args);


    /** 2. Send message **/
    //ATTENTION: this send is asynchronous. Must call get later.
    producer.send(new ProducerRecord(kafkaTopic,callMsg.toString()));

    /** 3. Receive message **/

    //TO DO receive

    //TO DO compare


    /** 4. Invoke constructor **/

    Constructor constructor = codeSignature.getConstructor();

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
      invokedMsg = DataMessageFactory.buildAccessibleObjectThrowableMessage(id,constructor,exceptionWhileInvoking);
    } else {
      invokedMsg = DataMessageFactory.buildReturnValueMessage(id, newObject, false);
    }


    /** 6. Send object/exception **/
    producer.send(new ProducerRecord(kafkaTopic,invokedMsg.toString()));


    /** 7. Receive object/exception **/

    //TO DO receive

    //TO DO compare

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

    final MethodSignature codeSignature = (MethodSignature) staticPart.getSignature();

    /** 1. Wrap message **/
    final Wrappers.DataMessage msg = DataMessageFactory.buildInstanceMethodMessage(id, staticPart, sender, target, args);


    /** 2. Send message **/
    //ATTENTION: this send is asynchronous. Must call get later.
    producer.send(new ProducerRecord(kafkaTopic,msg.toString()));

    /** 3. Receive message **/

    //TO DO receive

    //TO DO compare


    /** 4. Invoke method **/

    Method method = codeSignature.getMethod();

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
      invokedMsg = DataMessageFactory.buildAccessibleObjectThrowableMessage(id,method,exceptionWhileInvoking);
    } else {
      invokedMsg = DataMessageFactory.buildReturnValueMessage(id, Void.class, true);
    }


    /** 6. Send object/exception **/
    producer.send(new ProducerRecord(kafkaTopic,invokedMsg.toString()));


    /** 7. Receive object/exception **/

    //TO DO receive

    //TO DO compare

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

    final MethodSignature codeSignature = (MethodSignature) staticPart.getSignature();

    /** 1. Wrap message **/
    final Wrappers.DataMessage msg = DataMessageFactory.buildInstanceMethodMessage(id, staticPart, sender, target, args);


    /** 2. Send message **/
    //ATTENTION: this send is asynchronous. Must call get later.
    producer.send(new ProducerRecord(kafkaTopic,msg.toString()));

    /** 3. Receive message **/

    //TO DO receive

    //TO DO compare


    /** 4. Invoke method **/

    Method method = codeSignature.getMethod();
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
      invokedMsg = DataMessageFactory.buildAccessibleObjectThrowableMessage(id,method,exceptionWhileInvoking);
    } else {
      invokedMsg = DataMessageFactory.buildReturnValueMessage(id, returnValue, false);
    }


    /** 6. Send object/exception **/
    producer.send(new ProducerRecord(kafkaTopic,invokedMsg.toString()));


    /** 7. Receive object/exception **/

    //TO DO receive

    //TO DO compare

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

  public static void voidClassMethod(StaticPart staticPart, Object sender, Object[] args) throws Throwable {

    final MethodSignature codeSignature = (MethodSignature) staticPart.getSignature();

    /** 1. Wrap message **/
    final Wrappers.DataMessage msg = DataMessageFactory.buildClassMethodMessage(id, staticPart, sender, args);


    /** 2. Send message **/
    //ATTENTION: this send is asynchronous. Must call get later.
    producer.send(new ProducerRecord(kafkaTopic,msg.toString()));

    /** 3. Receive message **/

    //TO DO receive

    //TO DO compare


    /** 4. Invoke method **/

    Method method = codeSignature.getMethod();
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
      invokedMsg = DataMessageFactory.buildAccessibleObjectThrowableMessage(id,method,exceptionWhileInvoking);
    } else {
      invokedMsg = DataMessageFactory.buildReturnValueMessage(id, Void.class, true);
    }


    /** 6. Send object/exception **/
    producer.send(new ProducerRecord(kafkaTopic,invokedMsg.toString()));


    /** 7. Receive object/exception **/

    //TO DO receive

    //TO DO compare

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

  public static Object nonVoidClassMethod(StaticPart staticPart, Object sender, Object[] args) throws Throwable {

    final MethodSignature codeSignature = (MethodSignature) staticPart.getSignature();

    /** 1. Wrap message **/
    final Wrappers.DataMessage msg = DataMessageFactory.buildClassMethodMessage(id, staticPart, sender, args);


    /** 2. Send message **/
    //ATTENTION: this send is asynchronous. Must call get later.
    producer.send(new ProducerRecord(kafkaTopic,msg.toString()));

    /** 3. Receive message **/

    //TO DO receive

    //TO DO compare


    /** 4. Invoke method **/

    Method method = codeSignature.getMethod();
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
      invokedMsg = DataMessageFactory.buildAccessibleObjectThrowableMessage(id,method,exceptionWhileInvoking);
    } else {
      invokedMsg = DataMessageFactory.buildReturnValueMessage(id, returnValue, false);
    }


    /** 6. Send object/exception **/
    producer.send(new ProducerRecord(kafkaTopic,invokedMsg.toString()));


    /** 7. Receive object/exception **/

    //TO DO receive

    //TO DO compare

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

    /** 1. Wrap message **/
    final Wrappers.DataMessage msg = DataMessageFactory.buildGetStaticMessage(id, staticPart, sender);

    /** 2. Send message **/
    //ATTENTION: this send is asynchronous. Must call get later.
    producer.send(new ProducerRecord(kafkaTopic,msg.toString()));

    /** 3. Receive message **/

    //TO DO receive

    //TO DO compare

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
    Wrappers.DataMessage exceptionMsg = null;
    if (exceptionGettingObject != null) {
      exceptionMsg = DataMessageFactory.buildInitializerThrowableMessage(id, staticPart, exceptionGettingObject);
    }

    /** 6. Send object/exception **/
    if (exceptionGettingObject != null) {
      producer.send(new ProducerRecord(kafkaTopic, exceptionMsg.toString()));
    }

    /** 7. Receive object/exception **/

    //TO DO receive

    //TO DO compare

    /** 8. Return or re-raise exception **/
    if (exceptionGettingObject != null) {
      throw exceptionGettingObject;
    }

    return fieldValue;
  }

  public static Object getObject(StaticPart staticPart, Object sender, Object target) throws IllegalAccessException {

    /** 1. Wrap message **/
    final Wrappers.DataMessage msg = DataMessageFactory.buildGetObjectMessage(id, staticPart, sender, target);

    /** 2. Send message **/
    //ATTENTION: this send is asynchronous. Must call get later.
    producer.send(new ProducerRecord(kafkaTopic,msg.toString()));

    /** 3. Receive message **/

    //TO DO receive

    //TO DO compare

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
    Wrappers.DataMessage exceptionMsg = null;
    if (exceptionGettingObject != null) {
      exceptionMsg = DataMessageFactory.buildInitializerThrowableMessage(id, staticPart, exceptionGettingObject);
    }

    /** 6. Send object/exception **/
    if (exceptionGettingObject != null) {
      producer.send(new ProducerRecord(kafkaTopic, exceptionMsg.toString()));
    }

    /** 7. Receive object/exception **/

    //TO DO receive

    //TO DO compare

    /** 8. Return or re-raise exception **/
    if (exceptionGettingObject != null) {
      throw exceptionGettingObject;
    }

    return fieldValue;
  }

  public static void putStatic(StaticPart staticPart, Object sender, Object[] args) throws IllegalAccessException {

    /** 1. Wrap message **/
    final Wrappers.DataMessage msg = DataMessageFactory.buildPutStaticMessage(id, staticPart, sender, args[0]);

    /** 2. Send message **/
    //ATTENTION: this send is asynchronous. Must call get later.
    producer.send(new ProducerRecord(kafkaTopic,msg.toString()));

    /** 3. Receive message **/

    //TO DO receive

    //TO DO compare

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
    Wrappers.DataMessage exceptionMsg = null;
    if (exceptionSettingObject != null) {
      exceptionMsg = DataMessageFactory.buildInitializerThrowableMessage(id, staticPart, exceptionSettingObject);
    }

    /** 6. Send object/exception **/
    if (exceptionSettingObject != null) {
      producer.send(new ProducerRecord(kafkaTopic, exceptionMsg.toString()));
    }

    /** 7. Receive object/exception **/

    //TO DO receive

    //TO DO compare

    /** 8. Return or re-raise exception **/
    if (exceptionSettingObject != null) {
      throw exceptionSettingObject;
    }

    return;
  }

  public static void putField(StaticPart staticPart, Object sender, Object target, Object[] args) throws IllegalAccessException {

    /** 1. Wrap message **/
    final Wrappers.DataMessage msg = DataMessageFactory.buildPutObjectMessage(id, staticPart, sender, target, args[0]);

    /** 2. Send message **/
    //ATTENTION: this send is asynchronous. Must call get later.
    producer.send(new ProducerRecord(kafkaTopic, msg.toString()));

    /** 3. Receive message **/

    //TO DO receive

    //TO DO compare

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
    Wrappers.DataMessage exceptionMsg = null;
    if (exceptionSettingObject != null) {
      exceptionMsg = DataMessageFactory.buildInitializerThrowableMessage(id, staticPart, exceptionSettingObject);
    }

    /** 6. Send object/exception **/
    if (exceptionSettingObject != null) {
      producer.send(new ProducerRecord(kafkaTopic, exceptionMsg.toString()));
    }

    /** 7. Receive object/exception **/

    //TO DO receive

    //TO DO compare

    /** 8. Return or re-raise exception **/
    if (exceptionSettingObject != null) {
      throw exceptionSettingObject;
    }
  }

  // </editor-fold>

}
