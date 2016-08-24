package com.ittera.cometa.distributor;
import com.ittera.cometa.distributor.messages.*;
import com.ittera.cometa.distributor.messages.data.Calls;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import org.aspectj.lang.reflect.*;
import org.aspectj.lang.JoinPoint.StaticPart;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.Properties;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.KafkaProducer;

public class Distributor {
  protected static final Logger logger = LogManager.getLogger("distributor");
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

  // <editor-fold defaultstate="collapsed" desc="METHOD CALLS">

  public static void voidInstanceMethod(StaticPart staticPart, Object sender, Object receiver, Object[] args) {
    logger.debug("in D.voidInstanceMethod: " + staticPart.getSignature());

    final Calls.InstanceMethodCall call = buildInstanceMethodMessage(staticPart, sender, receiver, args);
    /** TO DO: send call down the wire to execute **/
    //ATTENTION: this send is asynchronous. Must call get later.
    producer.send(new ProducerRecord(kafkaTopic,call.toString()));

    final ExecutableMessage message = new InstanceMethodMessage((CodeSignature)staticPart.getSignature(), sender, receiver, args);
    MessageExecutor.sendExecutableMessage(message);
  }

  public static Object nonVoidInstanceMethod(StaticPart staticPart, Object sender, Object receiver, Object[] args) {
    logger.debug("in D.nonVoidInstanceMethod: " + staticPart.getSignature());

    final Calls.InstanceMethodCall call = buildInstanceMethodMessage(staticPart, sender, receiver, args);
    /** TO DO: send call down the wire to execute **/
    //ATTENTION: this send is asynchronous. Must call get later.
    producer.send(new ProducerRecord(kafkaTopic,call.toString()));

    final ExecutableMessage message = new InstanceMethodMessage((CodeSignature)staticPart.getSignature(), sender, receiver, args);
    MessageExecutor.sendExecutableMessage(message);
    //WARNING: NOT THREAD-SAFE!!
    return MessageExecutor.getLastReturnedObject();
  }

  private static Calls.InstanceMethodCall buildInstanceMethodMessage(StaticPart staticPart, Object sender, Object receiver, Object[] args) {

    /** Build protobuf message **/
    final MethodSignature codeSignature = (MethodSignature) staticPart.getSignature();
    final Calls.InstanceMethodCall.Builder callBuilder = Calls.InstanceMethodCall.newBuilder();
    callBuilder.setMsgType("Instance method");
    callBuilder.setDistributorId(id); //1
    callBuilder.setThreadId(Thread.currentThread().getId()); //2
    callBuilder.setCurrentTime(System.currentTimeMillis()); //3
    callBuilder.setClass_(codeSignature.getDeclaringTypeName()); //4
    callBuilder.setName(codeSignature.getName()); //5
    callBuilder.setTarget(System.identityHashCode(receiver)); //6
    callBuilder.setModifiers(codeSignature.getModifiers()); //7
    //8
    for (String name: codeSignature.getParameterNames()) {
      callBuilder.addParameterNames(name);
    }
    //9
    for (Class clazz: codeSignature.getParameterTypes()) {
      callBuilder.addParameterClasses(clazz.getName());
    }
    //10
    for (Class clazz: codeSignature.getExceptionTypes()) {
      callBuilder.addExceptionTypes(clazz.getPackage().getName()+"."+clazz.getName());
    }
    //11
    for (Object param: args) {
      callBuilder.addParameters(System.identityHashCode(param));
    }
    callBuilder.setSenderClassName(staticPart.getSourceLocation().getWithinType().getName()); //12
    callBuilder.setSender(System.identityHashCode(sender)); //13
    callBuilder.setSourceLocationFile(staticPart.getSourceLocation().getFileName()); //14
    callBuilder.setSourceLocationLine(staticPart.getSourceLocation().getLine()); //15
    callBuilder.setSourceLocationType(staticPart.getSourceLocation().getWithinType().getCanonicalName()); //16

    return callBuilder.build();
  }

  public static void voidClassMethod(StaticPart staticPart, Object sender, Object[] args) {
    logger.debug("in D.voidClassMethod: " + staticPart.getSignature());

    final Calls.ClassMethodCall call = buildClassMethodMessage(staticPart, sender, args);
    /** TO DO: send call down the wire to execute **/
    //ATTENTION: this send is asynchronous. Must call get later.
    producer.send(new ProducerRecord(kafkaTopic,call.toString()));

    ExecutableMessage message = new ClassMethodMessage((CodeSignature)staticPart.getSignature(), sender, args);
    MessageExecutor.sendExecutableMessage(message);
  }

  public static Object nonVoidClassMethod(StaticPart staticPart, Object sender, Object[] args) {
    logger.debug("in D.nonVoidClassMethod: " + staticPart.getSignature());

    final Calls.ClassMethodCall call = buildClassMethodMessage(staticPart, sender, args);
    /** TO DO: send call down the wire to execute **/
    //ATTENTION: this send is asynchronous. Must call get later.
    producer.send(new ProducerRecord(kafkaTopic,call.toString()));

    ExecutableMessage message = new ClassMethodMessage((CodeSignature)staticPart.getSignature(), sender, args);
    MessageExecutor.sendExecutableMessage(message);
    //WARNING: NOT THREAD-SAFE!!
    return MessageExecutor.getLastReturnedObject();
  }

  private static Calls.ClassMethodCall buildClassMethodMessage(StaticPart staticPart, Object sender, Object[] args) {

    /** Build protobuf message **/
    final MethodSignature codeSignature = (MethodSignature) staticPart.getSignature();
    final Calls.ClassMethodCall.Builder callBuilder = Calls.ClassMethodCall.newBuilder();
    callBuilder.setMsgType("Class method");
    callBuilder.setDistributorId(id); //1
    callBuilder.setThreadId(Thread.currentThread().getId()); //2
    callBuilder.setCurrentTime(System.currentTimeMillis()); //3
    callBuilder.setClass_(codeSignature.getDeclaringTypeName()); //4
    callBuilder.setName(codeSignature.getName()); //5
    callBuilder.setModifiers(codeSignature.getModifiers()); //6
    //7
    for (String name: codeSignature.getParameterNames()) {
      callBuilder.addParameterNames(name);
    }
    //8
    for (Class clazz: codeSignature.getParameterTypes()) {
      callBuilder.addParameterClasses(clazz.getName());
    }
    //9
    for (Class clazz: codeSignature.getExceptionTypes()) {
      callBuilder.addExceptionTypes(clazz.getPackage().getName()+"."+clazz.getName());
    }
    //10
    for (Object param: args) {
      callBuilder.addParameters(System.identityHashCode(param));
    }
    callBuilder.setSenderClassName(staticPart.getSourceLocation().getWithinType().getName()); //11
    callBuilder.setSender(System.identityHashCode(sender)); //12
    callBuilder.setSourceLocationFile(staticPart.getSourceLocation().getFileName()); //13
    callBuilder.setSourceLocationLine(staticPart.getSourceLocation().getLine()); //14
    callBuilder.setSourceLocationType(staticPart.getSourceLocation().getWithinType().getCanonicalName()); //15

    return callBuilder.build();
  }

  public static Object constructor(StaticPart staticPart, Object sender, Object[] args) {
    logger.debug("in D.constructor: " + staticPart.getSignature());

    /** Build protobuf message **/
    final ConstructorSignature codeSignature = (ConstructorSignature) staticPart.getSignature();
    final Calls.ConstructorCall.Builder callBuilder = Calls.ConstructorCall.newBuilder();
    callBuilder.setMsgType("Constructor");
    callBuilder.setDistributorId(id); //1
    callBuilder.setThreadId(Thread.currentThread().getId()); //2
    callBuilder.setCurrentTime(System.currentTimeMillis()); //3
    callBuilder.setName(codeSignature.getDeclaringTypeName()); //4
    callBuilder.setModifiers(codeSignature.getModifiers()); //5
    //6
    for (String name: codeSignature.getParameterNames()) {
      callBuilder.addParameterNames(name);
    }
    //7
    for (Class clazz: codeSignature.getParameterTypes()) {
      callBuilder.addParameterClasses(clazz.getName());
    }
    //8
    for (Class clazz: codeSignature.getExceptionTypes()) {
      callBuilder.addExceptionTypes(clazz.getPackage().getName()+"."+clazz.getName());
    }
    //9
    for (Object param: args) {
      callBuilder.addParameters(System.identityHashCode(param));
    }
    callBuilder.setSenderClassName(staticPart.getSourceLocation().getWithinType().getName()); //10
    callBuilder.setSender(System.identityHashCode(sender)); //11
    callBuilder.setSourceLocationFile(staticPart.getSourceLocation().getFileName()); //12
    callBuilder.setSourceLocationLine(staticPart.getSourceLocation().getLine()); //13
    callBuilder.setSourceLocationType(staticPart.getSourceLocation().getWithinType().getCanonicalName()); //14

    final Calls.ConstructorCall call = callBuilder.build();

    /** TO DO: send call down the wire to execute **/
    //ATTENTION: this send is asynchronous. Must call get later.
    producer.send(new ProducerRecord(kafkaTopic,call.toString()));

    final ExecutableMessage message = new ConstructorMessage(codeSignature, sender, args);
    MessageExecutor.sendExecutableMessage(message);

    //WARNING: NOT THREAD-SAFE!!
    return MessageExecutor.getLastReturnedObject();
  }

  public static Class classConstructor(StaticPart staticPart, Object sender) {
    logger.debug("in D.classConstructor: " + staticPart.getSignature());

    /** Build protobuf message **/
    final InitializerSignature codeSignature = (InitializerSignature) staticPart.getSignature();
    final Calls.ClInitCall.Builder callBuilder = Calls.ClInitCall.newBuilder();
    callBuilder.setMsgType("Static Constructor");
    callBuilder.setDistributorId(id); //1
    callBuilder.setThreadId(Thread.currentThread().getId()); //2
    callBuilder.setCurrentTime(System.currentTimeMillis()); //3
    callBuilder.setName(codeSignature.getDeclaringTypeName()); //4
    callBuilder.setModifiers(codeSignature.getModifiers()); //5
    callBuilder.setSenderClassName(staticPart.getSourceLocation().getWithinType().getName()); //6
    callBuilder.setSender(System.identityHashCode(sender)); //7
    callBuilder.setSourceLocationFile(staticPart.getSourceLocation().getFileName()); //8
    callBuilder.setSourceLocationLine(staticPart.getSourceLocation().getLine()); //9
    callBuilder.setSourceLocationType(staticPart.getSourceLocation().getWithinType().getCanonicalName()); //10

    final Calls.ClInitCall call = callBuilder.build();

    /** TO DO: send call down the wire to execute **/
    //ATTENTION: this send is asynchronous. Must call get later.
    producer.send(new ProducerRecord(kafkaTopic,call.toString()));

    //final ExecutableMessage message = new ConstructorMessage(codeSignature, sender);
    //MessageExecutor.sendExecutableMessage(message);

    //For some reason the class is not being initialized!
      Class clazz=null;
    try {
      clazz = Class.forName(codeSignature.getDeclaringTypeName());
      //Class.forName(codeSignature.getDeclaringTypeName(),true, Distributor.class.getClassLoader());
    } catch (ClassNotFoundException e) {
      e.printStackTrace();
      logger.error(e);
    }

    //For now, we will return it if we want to aspectj to proceed(), or null if we dont
//    return null; //if we don't want to proceed()
    return clazz;
  }

  // </editor-fold>

  // <editor-fold defaultstate="collapsed" desc="FIELD OPERATIONS">
  //@TODO field operations should also be sent as messages
  public static Object getObjectStatic(StaticPart staticPart, Object sender) {
    logger.debug("in D.getstatic: " + staticPart.getSignature());
    Field field = ((FieldSignature)staticPart.getSignature()).getField();
    field.setAccessible(true);

    final Object fieldValue;
    try {
      fieldValue = field.get(null);
    } catch (IllegalAccessException ex) {
      throw new DistributorError("Illegal access",ex);
    } catch (IllegalArgumentException ex) {
      throw new DistributorError("Illegal argument",ex);
    }

    return fieldValue;
  }

  public static Object getObject(StaticPart staticPart, Object sender, Object receiver) {
    logger.debug("in D.getfield: " + staticPart.getSignature());
    Field field = ((FieldSignature)staticPart.getSignature()).getField();
    field.setAccessible(true);

    final Object fieldValue;
    try {
      fieldValue = field.get(receiver);
    } catch (IllegalAccessException ex) {
      throw new DistributorError("Illegal access",ex);
    } catch (IllegalArgumentException ex) {
      throw new DistributorError("Illegal argument",ex);
    }

    return fieldValue;
  }

  public static void putStatic(StaticPart staticPart, Object sender, Object[] args) {
    logger.debug("in D.putstatic: " + staticPart.getSignature());
    Field field = ((FieldSignature)staticPart.getSignature()).getField();
    field.setAccessible(true);

    try {
      field.set(null, args[0]);
    } catch (IllegalAccessException ex) {
      throw new DistributorError("Illegal access",ex);
    } catch (IllegalArgumentException ex) {
      throw new DistributorError("Illegal argument",ex);
    }
  }
  public static void putField(StaticPart staticPart, Object sender, Object receiver, Object[] args) {
    logger.debug("in D.putfield: " + staticPart.getSignature());
    Field field = ((FieldSignature)staticPart.getSignature()).getField();
    field.setAccessible(true);

    try {
      field.set(receiver, args[0]);
    } catch (IllegalAccessException ex) {
      throw new DistributorError("Illegal access",ex);
    } catch (IllegalArgumentException ex) {
      throw new DistributorError("Illegal argument",ex);
    }
  }
  // </editor-fold>



  protected static Object getLastReturnedObject() {
    return MessageExecutor.getLastReturnedObject();
  }
  /**
   * As of Java 7, static and abstract are incompatible. Otherwise this method should be abstract.
   * @param message
   */
   protected static void sendExecutableMessage(ExecutableMessage message) {
    throw new RuntimeException("sendExecutableMessage is not implemented, must be overriden in Distributor's subclasses!");
  }

}
