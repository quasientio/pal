package com.ittera.cometa.distributor;

import com.ittera.cometa.distributor.messages.*;
import com.ittera.cometa.distributor.messages.data.Calls;
import com.ittera.cometa.distributor.returntypes.ExceptionWrapper;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import java.lang.reflect.Constructor;
import java.util.Deque;
import java.util.LinkedList;

import org.aspectj.lang.reflect.CodeSignature;
import org.aspectj.lang.reflect.ConstructorSignature;
import org.aspectj.lang.reflect.FieldSignature;
import org.aspectj.lang.JoinPoint.StaticPart;

import java.lang.reflect.Field;


public class Distributor {
  protected static Logger logger = LogManager.getLogger("distributor");
  protected static Deque<Object> returnedValues = new LinkedList<Object>();
  protected static Deque<ExceptionWrapper> raisedExceptions = new LinkedList<>();

  protected static final int id = 10;

  /************************ INTERFACE ***************************/

  // <editor-fold defaultstate="collapsed" desc="METHOD CALLS">

  public static void voidInstanceMethod(StaticPart staticPart, Object sender, Object receiver, Object[] args) {
    logger.debug("in D.voidInstanceMethod: " + staticPart.getSignature());

    final ExecutableMessage message = new InstanceMethodMessage((CodeSignature)staticPart.getSignature(), sender, receiver, args);
    MessageExecutor.sendExecutableMessage(message);
  }

  public static Object nonVoidInstanceMethod(StaticPart staticPart, Object sender, Object receiver, Object[] args) {
    logger.debug("in D.nonVoidInstanceMethod: " + staticPart.getSignature());

    final ExecutableMessage message = new InstanceMethodMessage((CodeSignature)staticPart.getSignature(), sender, receiver, args);
    MessageExecutor.sendExecutableMessage(message);
    //WARNING: NOT THREAD-SAFE!!
    return MessageExecutor.getLastReturnedObject();
  }


  public static void voidClassMethod(StaticPart staticPart, Object sender, Object[] args) {
    logger.debug("in D.voidClassMethod: " + staticPart.getSignature());

    ExecutableMessage message = new ClassMethodMessage((CodeSignature)staticPart.getSignature(), sender, args);
    MessageExecutor.sendExecutableMessage(message);
  }

  public static Object nonVoidClassMethod(StaticPart staticPart, Object sender, Object[] args) {
    logger.debug("in D.nonVoidClassMethod: " + staticPart.getSignature());

    ExecutableMessage message = new ClassMethodMessage((CodeSignature)staticPart.getSignature(), sender, args);
    MessageExecutor.sendExecutableMessage(message);
    //WARNING: NOT THREAD-SAFE!!
    return MessageExecutor.getLastReturnedObject();
  }


  public static Object constructor(StaticPart staticPart, Object sender, Object[] args) {
    logger.debug("in D.constructor: " + staticPart.getSignature());

    /**
    ExecutableMessage message =  new ConstructorMessage(codeSignature, sender, args);
    MessageExecutor.sendExecutableMessage(message);
    //WARNING: NOT THREAD-SAFE!!
    return MessageExecutor.getLastReturnedObject();
     */
    CodeSignature codeSignature = (CodeSignature) staticPart.getSignature();
    Calls.ConstructorCall.Builder callBuilder = Calls.ConstructorCall.newBuilder();
    callBuilder.setDistributorId(id);
    callBuilder.setThreadId(Thread.currentThread().getId());
    callBuilder.setCurrentTime(System.currentTimeMillis());
    callBuilder.setName(codeSignature.getDeclaringTypeName());
    callBuilder.setModifiers(codeSignature.getModifiers());
    for (String name: codeSignature.getParameterNames()) {
      callBuilder.addParameterNames(name);
    }
    for (Class clazz: codeSignature.getParameterTypes()) {
      callBuilder.addParameterClasses(clazz.getPackage().getName()+"."+clazz.getName());
    }
    for (Class clazz: codeSignature.getExceptionTypes()) {
      callBuilder.addExceptionTypes(clazz.getPackage().getName()+"."+clazz.getName());
    }
    for (Object param: args) {
      callBuilder.addParameters(System.identityHashCode(param));
    }
    callBuilder.setSenderClassName(sender==null? "" : sender.getClass().getName());
    callBuilder.setSender(System.identityHashCode(sender));
    callBuilder.setSourceLocationFile(staticPart.getSourceLocation().getFileName());
    callBuilder.setSourceLocationLine(staticPart.getSourceLocation().getLine());
    callBuilder.setSourceLocationType(staticPart.getSourceLocation().getWithinType().getPackage().getName()+"."+
            staticPart.getSourceLocation().getWithinType().getClass().getName());

    Calls.ConstructorCall call = callBuilder.build();


    //TO DO: send call down the wire to execute
    //MessageExecutor.sendExecutableMessage(message);

    //WARNING: NOT THREAD-SAFE!!
    return MessageExecutor.getLastReturnedObject();
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
