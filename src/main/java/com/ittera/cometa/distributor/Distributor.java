package com.ittera.cometa.distributor;

import com.ittera.cometa.distributor.messages.data.Primitives;
import com.ittera.cometa.distributor.messages.data.Calls;
import com.ittera.cometa.distributor.messages.data.Fields;
import com.ittera.cometa.distributor.messages.data.Wrappers;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import org.aspectj.lang.reflect.CodeSignature;
import org.aspectj.lang.reflect.InitializerSignature;
import org.aspectj.lang.reflect.ConstructorSignature;
import org.aspectj.lang.reflect.MethodSignature;
import org.aspectj.lang.JoinPoint.StaticPart;
import org.aspectj.runtime.reflect.FieldSignatureImpl;

import java.lang.reflect.Field;
import java.util.Properties;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.KafkaProducer;

public class Distributor {
  protected static final Logger logger = LogManager.getLogger("distributor");
  protected static final KafkaProducer producer;
  protected static final String kafkaTopic = "test";
  protected static final int id = 10;

  protected static final int STRING_MAX_LEN=50;

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

    final Wrappers.DataMessage msg = buildInstanceMethodMessage(staticPart, sender, receiver, args);

    //ATTENTION: this send is asynchronous. Must call get later.
    producer.send(new ProducerRecord(kafkaTopic,msg.toString()));

    //LocalJavaExecutor.executeInstanceMethodMessage(msg);
    return;
  }

  public static Object nonVoidInstanceMethod(StaticPart staticPart, Object sender, Object receiver, Object[] args) {
    logger.debug("in D.nonVoidInstanceMethod: " + staticPart.getSignature());

    final Wrappers.DataMessage msg = buildInstanceMethodMessage(staticPart, sender, receiver, args);

    //ATTENTION: this send is asynchronous. Must call get later.
    producer.send(new ProducerRecord(kafkaTopic,msg.toString()));

    //return LocalJavaExecutor.executeInstanceMethodMessage(msg);
    return null;
  }

  private static Wrappers.DataMessage buildInstanceMethodMessage(StaticPart staticPart, Object sender, Object receiver, Object[] args) {

    /** Build protobuf message **/
    final MethodSignature codeSignature = (MethodSignature) staticPart.getSignature();

    final Wrappers.DataMessage.Builder msgBuilder = Wrappers.DataMessage.newBuilder();

    final Calls.InstanceMethodCall.Builder callBuilder = Calls.InstanceMethodCall.newBuilder();
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
    msgBuilder.setMsgType("Instance method");
    msgBuilder.setInstanceMethodCall(callBuilder);

    return msgBuilder.build();
  }

  public static void voidClassMethod(StaticPart staticPart, Object sender, Object[] args) {
    logger.debug("in D.voidClassMethod: " + staticPart.getSignature());

    final Wrappers.DataMessage msg = buildClassMethodMessage(staticPart, sender, args);

    //ATTENTION: this send is asynchronous. Must call get later.
    producer.send(new ProducerRecord(kafkaTopic,msg.toString()));

    //LocalJavaExecutor.executeClassMethodMessage(msg);
    return;
  }

  public static Object nonVoidClassMethod(StaticPart staticPart, Object sender, Object[] args) {
    logger.debug("in D.nonVoidClassMethod: " + staticPart.getSignature());

    final Wrappers.DataMessage msg = buildClassMethodMessage(staticPart, sender, args);

    //ATTENTION: this send is asynchronous. Must call get later.
    producer.send(new ProducerRecord(kafkaTopic,msg.toString()));

    //LocalJavaExecutor.executeClassMethodMessage(msg);
    return null;
  }

  private static Wrappers.DataMessage buildClassMethodMessage(StaticPart staticPart, Object sender, Object[] args) {

    /** Build protobuf message **/
    final MethodSignature codeSignature = (MethodSignature) staticPart.getSignature();

    final Wrappers.DataMessage.Builder msgBuilder = Wrappers.DataMessage.newBuilder();

    final Calls.ClassMethodCall.Builder callBuilder = Calls.ClassMethodCall.newBuilder();
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

    msgBuilder.setMsgType("Class method");
    msgBuilder.setClassMethodCall(callBuilder);

    return msgBuilder.build();
  }

  public static Object constructor(StaticPart staticPart, Object sender, Object[] args) {
    logger.debug("in D.constructor: " + staticPart.getSignature());

    /** Build protobuf message **/
    final ConstructorSignature codeSignature = (ConstructorSignature) staticPart.getSignature();

    final Wrappers.DataMessage.Builder msgBuilder = Wrappers.DataMessage.newBuilder();

    final Calls.ConstructorCall.Builder callBuilder = Calls.ConstructorCall.newBuilder();
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

    msgBuilder.setMsgType("Constructor");
    msgBuilder.setConstructorCall(callBuilder);

    //ATTENTION: this send is asynchronous. Must call get later.
    producer.send(new ProducerRecord(kafkaTopic,msgBuilder.build().toString()));

    //WARNING: NOT THREAD-SAFE!!
//    LocalJavaExecutor.executeConstructorMessage(msg);
    return null;
  }

  public static Class classConstructor(StaticPart staticPart, Object sender) {
    logger.debug("in D.classConstructor: " + staticPart.getSignature());

    /** Build protobuf message **/
    final InitializerSignature codeSignature = (InitializerSignature) staticPart.getSignature();

    final Wrappers.DataMessage.Builder msgBuilder = Wrappers.DataMessage.newBuilder();

    final Calls.ClInitCall.Builder callBuilder = Calls.ClInitCall.newBuilder();
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

    msgBuilder.setMsgType("Static Constructor");
    msgBuilder.setClinitCall(callBuilder);

    //ATTENTION: this send is asynchronous. Must call get later.
    producer.send(new ProducerRecord(kafkaTopic,msgBuilder.build().toString()));

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
//    return null; //if we don't want to proceed() BETTER to just return boolean then
    return clazz;
  }

  // </editor-fold>

  // <editor-fold defaultstate="collapsed" desc="FIELD OPERATIONS">
  //@TODO field operations should also be sent as messages
  public static Object getObjectStatic(StaticPart staticPart, Object sender) {
    logger.debug("in D.getstatic: " + staticPart.getSignature());

    /** Build protobuf message **/
    final FieldSignatureImpl fieldSignature = (FieldSignatureImpl) staticPart.getSignature();

    final Wrappers.DataMessage.Builder msgBuilder = Wrappers.DataMessage.newBuilder();

    final Fields.StaticFieldGet.Builder fieldBuilder = Fields.StaticFieldGet.newBuilder();
    fieldBuilder.setDistributorId(id); //1
    fieldBuilder.setThreadId(Thread.currentThread().getId()); //2
    fieldBuilder.setCurrentTime(System.currentTimeMillis()); //3
    fieldBuilder.setClass_(fieldSignature.getDeclaringTypeName()); //4
    fieldBuilder.setField(fieldSignature.getName()); //5
    fieldBuilder.setFieldType(fieldSignature.getFieldType().getCanonicalName()); //6
    fieldBuilder.setModifiers(fieldSignature.getModifiers()); //7
    fieldBuilder.setSenderClassName(staticPart.getSourceLocation().getWithinType().getName()); //8
    fieldBuilder.setSender(getWrappedValue(sender)); //9
    fieldBuilder.setSourceLocationFile(staticPart.getSourceLocation().getFileName()); //10
    fieldBuilder.setSourceLocationLine(staticPart.getSourceLocation().getLine()); //11
    fieldBuilder.setSourceLocationType(staticPart.getSourceLocation().getWithinType().getCanonicalName()); //12

    msgBuilder.setMsgType("Get static");
    msgBuilder.setStaticFieldGet(fieldBuilder);

    //ATTENTION: this send is asynchronous. Must call get later.
    producer.send(new ProducerRecord(kafkaTopic,msgBuilder.build().toString()));

    Field field = fieldSignature.getField();
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

    /** Build protobuf message **/
    final FieldSignatureImpl fieldSignature = (FieldSignatureImpl) staticPart.getSignature();

    final Wrappers.DataMessage.Builder msgBuilder = Wrappers.DataMessage.newBuilder();

    final Fields.InstanceFieldGet.Builder fieldBuilder = Fields.InstanceFieldGet.newBuilder();
    fieldBuilder.setDistributorId(id); //1
    fieldBuilder.setThreadId(Thread.currentThread().getId()); //2
    fieldBuilder.setCurrentTime(System.currentTimeMillis()); //3
    fieldBuilder.setClass_(fieldSignature.getDeclaringTypeName()); //4
    fieldBuilder.setTarget(getWrappedValue(receiver)); //5
    fieldBuilder.setField(fieldSignature.getName()); //6
    fieldBuilder.setFieldType(fieldSignature.getFieldType().getCanonicalName()); //7
    fieldBuilder.setModifiers(fieldSignature.getModifiers()); //8
    fieldBuilder.setSenderClassName(staticPart.getSourceLocation().getWithinType().getName()); //9
    fieldBuilder.setSender(getWrappedValue(sender)); //10
    fieldBuilder.setSourceLocationFile(staticPart.getSourceLocation().getFileName()); //11
    fieldBuilder.setSourceLocationLine(staticPart.getSourceLocation().getLine()); //12
    fieldBuilder.setSourceLocationType(staticPart.getSourceLocation().getWithinType().getCanonicalName()); //13

    msgBuilder.setMsgType("Get field");
    msgBuilder.setInstanceFieldGet(fieldBuilder);

    //ATTENTION: this send is asynchronous. Must call get later.
    producer.send(new ProducerRecord(kafkaTopic,msgBuilder.build().toString()));


    Field field = fieldSignature.getField();
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

    /** Build protobuf message **/
    final FieldSignatureImpl fieldSignature = (FieldSignatureImpl) staticPart.getSignature();

    final Wrappers.DataMessage.Builder msgBuilder = Wrappers.DataMessage.newBuilder();

    final Fields.StaticFieldPut.Builder fieldBuilder = Fields.StaticFieldPut.newBuilder();
    fieldBuilder.setDistributorId(id); //1
    fieldBuilder.setThreadId(Thread.currentThread().getId()); //2
    fieldBuilder.setCurrentTime(System.currentTimeMillis()); //3
    fieldBuilder.setClass_(fieldSignature.getDeclaringTypeName()); //4
    fieldBuilder.setField(fieldSignature.getName()); //5
    fieldBuilder.setFieldType(fieldSignature.getFieldType().getCanonicalName()); //6
    fieldBuilder.setValue(getWrappedValue(args[0])); //7
    fieldBuilder.setModifiers(fieldSignature.getModifiers()); //8
    fieldBuilder.setSenderClassName(staticPart.getSourceLocation().getWithinType().getName()); //9
    fieldBuilder.setSender(getWrappedValue(sender)); //10
    fieldBuilder.setSourceLocationFile(staticPart.getSourceLocation().getFileName()); //11
    fieldBuilder.setSourceLocationLine(staticPart.getSourceLocation().getLine()); //12
    fieldBuilder.setSourceLocationType(staticPart.getSourceLocation().getWithinType().getCanonicalName()); //13

    msgBuilder.setMsgType("Put static");
    msgBuilder.setStaticFieldPut(fieldBuilder);

    /** TO DO: send call down the wire to execute **/
    //ATTENTION: this send is asynchronous. Must call get later.
    producer.send(new ProducerRecord(kafkaTopic,msgBuilder.build().toString()));


    Field field = fieldSignature.getField();
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

    /** Build protobuf message **/
    final FieldSignatureImpl fieldSignature = (FieldSignatureImpl) staticPart.getSignature();

    final Wrappers.DataMessage.Builder msgBuilder = Wrappers.DataMessage.newBuilder();

    final Fields.InstanceFieldPut.Builder fieldBuilder = Fields.InstanceFieldPut.newBuilder();
    fieldBuilder.setDistributorId(id); //1
    fieldBuilder.setThreadId(Thread.currentThread().getId()); //2
    fieldBuilder.setCurrentTime(System.currentTimeMillis()); //3
    fieldBuilder.setClass_(fieldSignature.getDeclaringTypeName()); //4
    fieldBuilder.setTarget(getWrappedValue(receiver)); //5
    fieldBuilder.setField(fieldSignature.getName()); //6
    fieldBuilder.setFieldType(fieldSignature.getFieldType().getCanonicalName()); //7
    fieldBuilder.setValue(getWrappedValue(args[0])); //8
    fieldBuilder.setModifiers(fieldSignature.getModifiers()); //9
    fieldBuilder.setSenderClassName(staticPart.getSourceLocation().getWithinType().getName()); //10
    fieldBuilder.setSender(getWrappedValue(sender)); //11
    fieldBuilder.setSourceLocationFile(staticPart.getSourceLocation().getFileName()); //12
    fieldBuilder.setSourceLocationLine(staticPart.getSourceLocation().getLine()); //13
    fieldBuilder.setSourceLocationType(staticPart.getSourceLocation().getWithinType().getCanonicalName()); //14

    msgBuilder.setMsgType("Put field");
    msgBuilder.setInstanceFieldPut(fieldBuilder);

    /** TO DO: send call down the wire to execute **/
    //ATTENTION: this send is asynchronous. Must call get later.
    producer.send(new ProducerRecord(kafkaTopic,msgBuilder.build().toString()));

    Field field = fieldSignature.getField();
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


  /**
   * Wrapped is the actual value if object is a primitive or if String (Strings of length > STRING_MAX_LEN are trimmed)
   * If the object isn't null, the hashCode and class are also returned
   * It always returns the identityHashCode
   * @param object
   * @return
   */
  protected static Primitives.Value getWrappedValue(Object object) {
    final Primitives.Value.Builder value = Primitives.Value.newBuilder();

    //1
    if (object != null) {
      if (object instanceof String) {
        if (((String) object).length() > STRING_MAX_LEN) {
          value.setValue(((String) object).substring(0, STRING_MAX_LEN));
          //5
          value.setTrimmed(true);
       } else {
         value.setValue(String.valueOf(object));
       }
     }
    else if (object.getClass().isPrimitive() || com.google.common.primitives.Primitives.isWrapperType(object.getClass())) {
       value.setValue(String.valueOf(object));
     }
    }

    //2
    if (object!=null) {
      value.setHash(object.hashCode());
    }

    //3
    value.setIdentityHash(System.identityHashCode(object));

    //4
    if (object!=null) {
      value.setClass_(object.getClass().getCanonicalName());
    }

    return value.build();
  }
}
