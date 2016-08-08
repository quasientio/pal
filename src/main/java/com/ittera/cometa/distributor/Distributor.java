package com.ittera.cometa.distributor;

import com.ittera.cometa.distributor.messages.*;
import com.ittera.cometa.distributor.returntypes.ExceptionWrapper;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import java.util.Deque;
import java.util.LinkedList;

import org.aspectj.lang.reflect.CodeSignature;
import org.aspectj.lang.reflect.FieldSignature;

import java.lang.reflect.Field;


public class Distributor {
  protected static Logger logger = LogManager.getLogger("distributor");
  protected static Deque<Object> returnedValues = new LinkedList<Object>();
  protected static Deque<ExceptionWrapper> raisedExceptions = new LinkedList<>();

  /************************ INTERFACE ***************************/

  // <editor-fold defaultstate="collapsed" desc="METHOD CALLS">

  public static void voidInstanceMethod(CodeSignature codeSignature, Object sender, Object receiver, Object[] args) {
    logger.debug("in D.voidInstanceMethod: " + codeSignature);

    final ExecutableMessage message = new InstanceMethodMessage(codeSignature, sender, receiver, args);
    MessageExecutor.sendExecutableMessage(message);
  }

  public static Object nonVoidInstanceMethod(CodeSignature codeSignature, Object sender, Object receiver, Object[] args) {
    logger.debug("in D.nonVoidInstanceMethod: " + codeSignature);

    final ExecutableMessage message = new InstanceMethodMessage(codeSignature, sender, receiver, args);
    MessageExecutor.sendExecutableMessage(message);
    //WARNING: NOT THREAD-SAFE!!
    return MessageExecutor.getLastReturnedObject();
  }


  public static void voidClassMethod(CodeSignature codeSignature, Object sender, Object[] args) {
    logger.debug("in D.voidClassMethod: " + codeSignature);

    ExecutableMessage message = new ClassMethodMessage(codeSignature, sender, args);
    MessageExecutor.sendExecutableMessage(message);
  }

  public static Object nonVoidClassMethod(CodeSignature codeSignature, Object sender, Object[] args) {
    logger.debug("in D.nonVoidClassMethod: " + codeSignature);

    ExecutableMessage message = new ClassMethodMessage(codeSignature, sender, args);
    MessageExecutor.sendExecutableMessage(message);
    //WARNING: NOT THREAD-SAFE!!
    return MessageExecutor.getLastReturnedObject();
  }

  public static Object constructor(CodeSignature codeSignature, Object sender, Object[] args) {
    logger.debug("in D.constructor: " + codeSignature);

    ExecutableMessage message =  new ConstructorMessage(codeSignature, sender, args);
    MessageExecutor.sendExecutableMessage(message);
    //WARNING: NOT THREAD-SAFE!!
    return MessageExecutor.getLastReturnedObject();
  }

  // </editor-fold>

  // <editor-fold defaultstate="collapsed" desc="FIELD OPERATIONS">
  //@TODO field operations should also be sent as messages
  public static Object getObjectStatic(FieldSignature fieldSignature, Object sender) {
    logger.debug("in D.getstatic: " + fieldSignature);
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

  public static Object getObject(FieldSignature fieldSignature, Object sender, Object receiver) {
    logger.debug("in D.getfield: " + fieldSignature);
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

  public static void putStatic(FieldSignature fieldSignature, Object sender, Object[] args) {
    logger.debug("in D.putstatic: " + fieldSignature);
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
  public static void putField(FieldSignature fieldSignature, Object sender, Object receiver, Object[] args) {
    logger.debug("in D.putfield: " + fieldSignature);
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
