package com.ittera.cometa.distributor;

import com.ittera.cometa.distributor.messages.*;
import com.ittera.cometa.distributor.returntypes.ExceptionWrapper;
import com.ittera.cometa.distributor.returntypes.Null;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.ittera.cometa.util.SuperStack;
import org.apache.logging.log4j.core.config.plugins.convert.TypeConverters;

import java.lang.reflect.Field;

import java.util.Deque;
import java.util.LinkedList;


/**
 * @TODO remove System.exit() calls
 * @author libre
 */
public abstract class AbstractDistributor implements IDistributor {
  protected static Logger logger = LogManager.getLogger("distributor");
  protected int id;
  protected Deque<ExceptionWrapper> raisedExceptions = new LinkedList<ExceptionWrapper>();
  protected java.lang.ClassLoader classLoader;

  /************************ INTERFACE ***************************/

  // <editor-fold defaultstate="collapsed" desc="METHOD CALLS">

  /** Para llamadas a metodos de instancias con parametros.
   * @param sender
   * @param receiver Objeto al que se invoca.
   * @param methodName Nombre del metodo al que se invoca.
   * @param methodSignature Firma (Signature) que identifica los tipos de parametros y el return type.
   * @param paramStack SuperSuperStack que alberga los parametros al metodo estatico. Estos se meten en el orden
   * de los parametros en el metodo, y se han de sacar (popear) en el orden inverso.
   */
  public synchronized void instanceMethodWithArgs(Object sender, Object receiver, String methodName,
    String methodSignature, SuperStack paramStack) throws Throwable {
    logger.debug("instanceMethodWithArgs: llamando a " + methodName);

    String senderClassName;

    if (sender != null) {
      senderClassName = sender.getClass().getName();
    } else {
      senderClassName = "";
    }

    String receiverClassName = receiver.getClass().getName();

    ExecutableMessage mensaje = null;

    if (paramStack == null) {
      mensaje = new NoArgsInstanceMethodMessage(id, sender, senderClassName, receiver, receiverClassName,
          methodName, methodSignature);
    } else {
      mensaje = new InstanceMethodMessage(id, sender, senderClassName, receiver, receiverClassName, methodName,
          methodSignature, paramStack);
    }

    sendExecutableMessage(mensaje);
  }

  /** Para llamadas a metodos de instancias sin parametros.
   * @param receiver Objeto al que se invoca.
   * @param methodName Nombre del metodo al que se invoca.
   * @param methodSignature Firma (Signature) que identifica los tipos de parametros y el return type.
   */
  public synchronized void instanceMethodNoArgs(Object sender, Object receiver, String methodName,
    String methodSignature) throws Throwable {
    logger.debug("instanceMethodNoArgs: llamando a " + methodName);
    instanceMethodWithArgs(sender, receiver, methodName, methodSignature, null);
  }

  /** Para llamadas a metodos de clases estaticas con parametros.
   * @param receiverClassName Clase a la que se invoca.
   * @param methodName Nombre del metodo estatico al que se invoca.
   * @param methodSignature Firma (Signature) que identifica los tipos de parametros y el return type.
   * @param paramStack SuperStack que alberga los parametros al metodo estatico. Estos se meten en el orden
   * de los parametros en el metodo, y se han de sacar (popear) en el orden inverso.
   */
  public synchronized void staticMethodWithArgs(Object sender, String receiverClassName, String methodName,
    String methodSignature, SuperStack paramStack) throws Throwable {
    logger.debug("staticMethodWithArgs:l lamando a " + methodName);
    String senderClassName;

    if (sender != null) {
      senderClassName = sender.getClass().getName();
    } else {
      senderClassName = "";
    }

    ExecutableMessage mensaje = null;
    if (paramStack == null) {
      mensaje = new NoArgsClassMethodMessage(id, sender, senderClassName, receiverClassName, methodName,
          methodSignature);
    } else {
      mensaje = new ClassMethodMessage(id, sender, senderClassName, receiverClassName, methodName,
          methodSignature, paramStack);
    }

    sendExecutableMessage(mensaje);
  }

  /** Para llamadas a metodos de clases estaticas sin parametros.
   * @param receiverClassName Clase a la que se invoca.
   * @param methodName Nombre del metodo estatico al que se invoca.
   * @param methodSignature Firma (Signature) que identifica los tipos de parametros y el return type.
   */
  public synchronized void staticMethodNoArgs(Object sender, String receiverClassName, String methodName,
    String methodSignature) throws Throwable {
    logger.debug("staticMethodNoArgs: llamando a " + methodName);
    staticMethodWithArgs(sender, receiverClassName, methodName, methodSignature, null);
  }

  /** Para llamadas a constructores con parametros.
   * @param receiverClassName Clase del nuevo objeto a crear.
   * @param paramStack SuperStack que alberga los parametros del constructor deseado.
   */
  public synchronized void initWithArgs(Object sender, String receiverClassName, String methodSignature,
    SuperStack paramStack) throws Throwable {
    logger.debug("initWithArgs: llamando a constructor de " + receiverClassName + "\n Superstack :" + paramStack);

    String senderClassName;

    if (sender != null) {
      senderClassName = sender.getClass().getName();
    } else {
      senderClassName = "";
    }

    ExecutableMessage mensaje = null;

    if (paramStack == null) {
      mensaje = new NoArgsConstructorMessage(id, senderClassName, sender, receiverClassName);
    } else {
      mensaje = new ConstructorMessage(id, senderClassName, sender, receiverClassName, methodSignature,
          paramStack);
    }

    sendExecutableMessage(mensaje);
  }

  /** Para llamadas a constructores sin parametros.
     * @param receiverClassName Clase del nuevo objeto a crear.
     */
  public synchronized void initNoArgs(Object sender, String receiverClassName)
    throws Throwable {
    logger.debug("initNoArgs: llamando a constructor de " + receiverClassName);
    initWithArgs(sender, receiverClassName, null, null);
  }

  // </editor-fold>

  // <editor-fold defaultstate="collapsed" desc="GETTERS FOR VALUES RETURNED FROM METHODS">

  /** Entrega el objeto devuelto por el ultimo metodo ejecutado.
   * @return Objeto devuelto por el ultimo metodo ejecutado.
   */
  public synchronized Object getReturnedObject() throws Throwable {
    Object value = getLastReturnedObject();
    logger.debug("getReturnedObject: value grabbed: " + value);
    if (value instanceof Null) {
      return null;
    } else {
      return value;
    }
  }

  /**
   * Like getObject for String methods
   * @return String
   */
  public synchronized String getReturnedString() throws Throwable {
    Object value = getLastReturnedObject();
    if (value == null) {
      logger.debug("returned String is null");
    }

    return (String) value;
  }

  /** Entrega el objeto devuelto como un Long.
   * @return ultimo valor como primitivo de tipo long.
   */
  public synchronized long getReturnedLong() throws Throwable {
    Object value = getLastReturnedObject();
    return (long) value;
  }

  /** Entrega el objeto devuelto por el ultimo metodo ejecutado.
   * @return ultimo valor como primitivo de tipo double.
   */
  public synchronized double getReturnedDouble() throws Throwable {
    Object value = getLastReturnedObject();
    return (double) value;
  }

  /** Entrega el objeto devuelto por el ultimo metodo ejecutado.
   * @return ultimo valor como primitivo de tipo int.
   */
  public synchronized int getReturnedInt() throws Throwable {
    Object value = getLastReturnedObject();
    return (int) value;
  }

  /** Entrega el objeto devuelto por el ultimo metodo ejecutado.
   * @return ultimo valor como primitivo de tipo boolean.
   */
  public synchronized boolean getReturnedBoolean() throws Throwable {
    Object value = getLastReturnedObject();
    return (boolean) value;
  }

  /** Entrega el objeto devuelto por el ultimo metodo ejecutado.
   * @return ultimo valor como primitivo de tipo float.
   */
  public synchronized float getReturnedFloat() throws Throwable {
    Object value = getLastReturnedObject();
    return (float)value;
  }

  /** Entrega el objeto devuelto por el ultimo metodo ejecutado.
   * @return ultimo valor como primitivo de tipo short.
   */
  public synchronized short getReturnedShort() throws Throwable {
    Object value = getLastReturnedObject();
    return (short)value;
  }

  /** Entrega el objeto devuelto por el ultimo metodo ejecutado.
   * @return ultimo valor como primitivo de tipo char.
   */
  public synchronized char getReturnedChar() throws Throwable {
    Object value = getLastReturnedObject();
    return (char)value;
  }

  /** Entrega el objeto devuelto por el ultimo metodo ejecutado.
   * @return ultimo valor como primitivo de tipo byte.
   */
  public synchronized byte getReturnedByte() throws Throwable {
    Object value = getLastReturnedObject();
    return (byte)value;
  }

  // </editor-fold>

  // <editor-fold defaultstate="collapsed" desc="GETSTATIC OPERATIONS">
  public synchronized Object getStaticObject(String className, String fieldName) {
    Object fieldValue = null;
    try {
      fieldValue = getAccessibleDeclaredClassField(className, fieldName).get(null);
    } catch (IllegalAccessException ex) {
      logger.error("Illegal access", ex);
      System.exit(1);
    } catch (IllegalArgumentException ex) {
      logger.error("Illegal argument", ex);
      System.exit(1);
    }

    return fieldValue;
  }

  public synchronized boolean getStaticBoolean(String className, String fieldName) {
    boolean fieldValue = false;

    try {
      fieldValue = getAccessibleDeclaredClassField(className, fieldName).getBoolean(null);
    } catch (IllegalAccessException ex) {
      logger.error("Illegal access", ex);
      System.exit(1);
    } catch (IllegalArgumentException ex) {
      logger.error("Illegal argument", ex);
      System.exit(1);
    }

    return fieldValue;
  }

  public synchronized byte getStaticByte(String className, String fieldName) {
    byte fieldValue = 0;

    try {
      fieldValue = getAccessibleDeclaredClassField(className, fieldName).getByte(null);
    } catch (IllegalAccessException ex) {
      logger.error("Illegal access", ex);
      System.exit(1);
    } catch (IllegalArgumentException ex) {
      logger.error("Illegal argument", ex);
      System.exit(1);
    }

    return fieldValue;
  }

  public synchronized char getStaticChar(String className, String fieldName) {
    char fieldValue = 0;

    try {
      fieldValue = getAccessibleDeclaredClassField(className, fieldName).getChar(null);
    } catch (IllegalAccessException ex) {
      logger.error("Illegal access", ex);
      System.exit(1);
    } catch (IllegalArgumentException ex) {
      logger.error("Illegal argument", ex);
      System.exit(1);
    }

    return fieldValue;
  }

  public synchronized int getStaticInt(String className, String fieldName) {
    int fieldValue = 0;

    try {
      fieldValue = getAccessibleDeclaredClassField(className, fieldName).getInt(null);
    } catch (IllegalAccessException ex) {
      logger.error("Illegal access", ex);
      System.exit(1);
    } catch (IllegalArgumentException ex) {
      logger.error("Illegal argument", ex);
      System.exit(1);
    }

    return fieldValue;
  }

  public synchronized double getStaticDouble(String className, String fieldName) {
    double fieldValue = 0;

    try {
      fieldValue = getAccessibleDeclaredClassField(className, fieldName).getDouble(null);
    } catch (IllegalAccessException ex) {
      logger.error("Illegal access", ex);
      System.exit(1);
    } catch (IllegalArgumentException ex) {
      logger.error("Illegal argument", ex);
      System.exit(1);
    }

    return fieldValue;
  }

  public synchronized float getStaticFloat(String className, String fieldName) {
    float fieldValue = 0;

    try {
      fieldValue = getAccessibleDeclaredClassField(className, fieldName).getFloat(null);
    } catch (IllegalAccessException ex) {
      logger.error("Illegal access", ex);
      System.exit(1);
    } catch (IllegalArgumentException ex) {
      logger.error("Illegal argument", ex);
      System.exit(1);
    }

    return fieldValue;
  }

  public synchronized long getStaticLong(String className, String fieldName) {
    long fieldValue = 0;

    try {
      fieldValue = getAccessibleDeclaredClassField(className, fieldName).getLong(null);
    } catch (IllegalAccessException ex) {
      logger.error("Illegal access", ex);
      System.exit(1);
    } catch (IllegalArgumentException ex) {
      logger.error("Illegal argument", ex);
      System.exit(1);
    }

    return fieldValue;
  }

  public synchronized short getStaticShort(String className, String fieldName) {
    short fieldValue = 0;

    try {
      fieldValue = getAccessibleDeclaredClassField(className, fieldName).getShort(null);
    } catch (IllegalAccessException ex) {
      logger.error("Illegal access", ex);
      System.exit(1);
    } catch (IllegalArgumentException ex) {
      logger.error("Illegal argument", ex);
      System.exit(1);
    }

    return fieldValue;
  }

  public synchronized String getStaticString(String className, String fieldName) {
    return (String) getStaticObject(className, fieldName);
  }

  // </editor-fold>

  // <editor-fold defaultstate="collapsed" desc="GETFIELD OPERATIONS">
  public synchronized Object getObject(Object objectref, String fieldName) {
    Object fieldValue = null;

    try {
      fieldValue = getAccessibleDeclaredObjectField(objectref, fieldName).get(objectref);
    } catch (IllegalAccessException ex) {
      logger.error("Illegal access", ex);
      System.exit(1);
    } catch (IllegalArgumentException ex) {
      logger.error("Illegal argument", ex);
      System.exit(1);
    }

    return fieldValue;
  }

  public synchronized boolean getBoolean(Object objectref, String fieldName) {
    boolean fieldValue = false;
    try {
      fieldValue = getAccessibleDeclaredObjectField(objectref, fieldName).getBoolean(objectref);
    } catch (IllegalAccessException ex) {
      logger.error("Illegal access", ex);
      System.exit(1);
    } catch (IllegalArgumentException ex) {
      logger.error("Illegal argument", ex);
      System.exit(1);
    }

    return fieldValue;
  }

  public synchronized byte getByte(Object objectref, String fieldName) {
    byte fieldValue = 0;
    try {
      fieldValue = getAccessibleDeclaredObjectField(objectref, fieldName).getByte(objectref);
    } catch (IllegalAccessException ex) {
      logger.error("Illegal access", ex);
      System.exit(1);
    } catch (IllegalArgumentException ex) {
      logger.error("Illegal argument", ex);
      System.exit(1);
    }

    return fieldValue;
  }

  public synchronized char getChar(Object objectref, String fieldName) {
    char fieldValue = 0;
    try {
      fieldValue = getAccessibleDeclaredObjectField(objectref, fieldName).getChar(objectref);
    } catch (IllegalAccessException ex) {
      logger.error("Illegal access", ex);
      System.exit(1);
    } catch (IllegalArgumentException ex) {
      logger.error("Illegal argument", ex);
      System.exit(1);
    }

    return fieldValue;
  }

  public synchronized int getInt(Object objectref, String fieldName) {
    int fieldValue = 0;
    try {
      fieldValue = getAccessibleDeclaredObjectField(objectref, fieldName).getInt(objectref);
    } catch (IllegalAccessException ex) {
      logger.error("Illegal access", ex);
      System.exit(1);
    } catch (IllegalArgumentException ex) {
      logger.error("Illegal argument", ex);
      System.exit(1);
    }

    return fieldValue;
  }

  public synchronized double getDouble(Object objectref, String fieldName) {
    double fieldValue = 0;
    try {
      fieldValue = getAccessibleDeclaredObjectField(objectref, fieldName).getDouble(objectref);
    } catch (IllegalAccessException ex) {
      logger.error("Illegal access", ex);
      System.exit(1);
    } catch (IllegalArgumentException ex) {
      logger.error("Illegal argument", ex);
      System.exit(1);
    }

    return fieldValue;
  }

  public synchronized float getFloat(Object objectref, String fieldName) {
    float fieldValue = 0;
    try {
      fieldValue = getAccessibleDeclaredObjectField(objectref, fieldName).getFloat(objectref);
    } catch (IllegalAccessException ex) {
      logger.error("Illegal access", ex);
      System.exit(1);
    } catch (IllegalArgumentException ex) {
      logger.error("Illegal argument", ex);
      System.exit(1);
    }

    return fieldValue;
  }

  public synchronized long getLong(Object objectref, String fieldName) {
    long fieldValue = 0;
    try {
      fieldValue = getAccessibleDeclaredObjectField(objectref, fieldName).getLong(objectref);
    } catch (IllegalAccessException ex) {
      logger.error("Illegal access", ex);
      System.exit(1);
    } catch (IllegalArgumentException ex) {
      logger.error("Illegal argument", ex);
      System.exit(1);
    }

    return fieldValue;
  }

  public synchronized short getShort(Object objectref, String fieldName) {
    short fieldValue = 0;
    try {
      fieldValue = getAccessibleDeclaredObjectField(objectref, fieldName).getShort(objectref);
    } catch (IllegalAccessException ex) {
      logger.error("Illegal access", ex);
      System.exit(1);
    } catch (IllegalArgumentException ex) {
      logger.error("Illegal argument", ex);
      System.exit(1);
    }

    return fieldValue;
  }

  public synchronized String getString(Object objectref, String fieldName) {
    return (String) getObject(objectref, fieldName);
  }

  // </editor-fold>

  // <editor-fold defaultstate="collapsed" desc="PUTSTATIC OPERATIONS">
  public synchronized void putStatic(Object value, String className, String fieldName) {
    Field objectField = null;

    objectField = getAccessibleDeclaredClassField(className, fieldName);

    try {
      objectField.set(null, value);
    } catch (IllegalAccessException ex) {
      logger.error("Illegal access", ex);
      System.exit(1);
    } catch (IllegalArgumentException ex) {
      logger.error("Illegal argument", ex);
      System.exit(1);
    }
  }

  public synchronized void putStatic(boolean value, String className, String fieldName) {
    Field objectField = null;

    objectField = getAccessibleDeclaredClassField(className, fieldName);

    try {
      objectField.setBoolean(null, value);
    } catch (IllegalAccessException ex) {
      logger.error("Illegal access", ex);
      System.exit(1);
    } catch (IllegalArgumentException ex) {
      logger.error("Illegal argument", ex);
      System.exit(1);
    }
  }

  public synchronized void putStatic(byte value, String className, String fieldName) {
    Field objectField = null;

    objectField = getAccessibleDeclaredClassField(className, fieldName);

    try {
      objectField.setByte(null, value);
    } catch (IllegalAccessException ex) {
      logger.error("Illegal access", ex);
      System.exit(1);
    } catch (IllegalArgumentException ex) {
      logger.error("Illegal argument", ex);
      System.exit(1);
    }
  }

  public synchronized void putStatic(char value, String className, String fieldName) {
    Field objectField = null;

    objectField = getAccessibleDeclaredClassField(className, fieldName);

    try {
      objectField.setChar(null, value);
    } catch (IllegalAccessException ex) {
      logger.error("Illegal access", ex);
      System.exit(1);
    } catch (IllegalArgumentException ex) {
      logger.error("Illegal argument", ex);
      System.exit(1);
    }
  }

  public synchronized void putStatic(int value, String className, String fieldName) {
    Field objectField = null;

    objectField = getAccessibleDeclaredClassField(className, fieldName);

    try {
      objectField.setInt(null, value);
    } catch (IllegalAccessException ex) {
      logger.error("Illegal access", ex);
      System.exit(1);
    } catch (IllegalArgumentException ex) {
      logger.error("Illegal argument", ex);
      System.exit(1);
    }
  }

  public synchronized void putStatic(double value, String className, String fieldName) {
    Field objectField = null;

    objectField = getAccessibleDeclaredClassField(className, fieldName);

    try {
      objectField.setDouble(null, value);
    } catch (IllegalAccessException ex) {
      logger.error("Illegal access", ex);
      System.exit(1);
    } catch (IllegalArgumentException ex) {
      logger.error("Illegal argument", ex);
      System.exit(1);
    }
  }

  public synchronized void putStatic(float value, String className, String fieldName) {
    Field objectField = null;

    objectField = getAccessibleDeclaredClassField(className, fieldName);

    try {
      objectField.setFloat(null, value);
    } catch (IllegalAccessException ex) {
      logger.error("Illegal access", ex);
      System.exit(1);
    } catch (IllegalArgumentException ex) {
      logger.error("Illegal argument", ex);
      System.exit(1);
    }
  }

  public synchronized void putStatic(long value, String className, String fieldName) {
    Field objectField = null;

    objectField = getAccessibleDeclaredClassField(className, fieldName);

    try {
      objectField.setLong(null, value);
    } catch (IllegalAccessException ex) {
      logger.error("Illegal access", ex);
      System.exit(1);
    } catch (IllegalArgumentException ex) {
      logger.error("Illegal argument", ex);
      System.exit(1);
    }
  }

  public synchronized void putStatic(short value, String className, String fieldName) {
    Field objectField = null;

    objectField = getAccessibleDeclaredClassField(className, fieldName);

    try {
      objectField.setShort(null, value);
    } catch (IllegalAccessException ex) {
      logger.error("Illegal access", ex);
      System.exit(1);
    } catch (IllegalArgumentException ex) {
      logger.error("Illegal argument", ex);
      System.exit(1);
    }
  }

  // </editor-fold>

  // <editor-fold defaultstate="collapsed" desc="PUTFIELD OPERATIONS">
  public synchronized void putField(Object objectref, Object value, String fieldName) {
    Field objectField = null;

    objectField = getAccessibleDeclaredObjectField(objectref, fieldName);

    try {
      objectField.set(objectref, value);
    } catch (IllegalAccessException ex) {
      logger.error("Illegal access", ex);
      System.exit(1);
    } catch (IllegalArgumentException ex) {
      logger.error("Illegal argument", ex);
      System.exit(1);
    }
  }

  public synchronized void putField(Object objectref, boolean value, String fieldName) {
    Field objectField = null;

    objectField = getAccessibleDeclaredObjectField(objectref, fieldName);

    try {
      objectField.setBoolean(objectref, value);
    } catch (IllegalAccessException ex) {
      logger.error("Illegal access", ex);
      System.exit(1);
    } catch (IllegalArgumentException ex) {
      logger.error("Illegal argument", ex);
      System.exit(1);
    }
  }

  public synchronized void putField(Object objectref, byte value, String fieldName) {
    Field objectField = null;

    objectField = getAccessibleDeclaredObjectField(objectref, fieldName);

    try {
      objectField.setByte(objectref, value);
    } catch (IllegalAccessException ex) {
      logger.error("Illegal access", ex);
      System.exit(1);
    } catch (IllegalArgumentException ex) {
      logger.error("Illegal argument", ex);
      System.exit(1);
    }
  }

  public synchronized void putField(Object objectref, char value, String fieldName) {
    Field objectField = null;

    objectField = getAccessibleDeclaredObjectField(objectref, fieldName);

    try {
      objectField.setChar(objectref, value);
    } catch (IllegalAccessException ex) {
      logger.error("Illegal access", ex);
      System.exit(1);
    } catch (IllegalArgumentException ex) {
      logger.error("Illegal argument", ex);
      System.exit(1);
    }
  }

  public synchronized void putField(Object objectref, int value, String fieldName) {
    Field objectField = null;

    objectField = getAccessibleDeclaredObjectField(objectref, fieldName);

    try {
      objectField.setInt(objectref, value);
    } catch (IllegalAccessException ex) {
      logger.error("Illegal access", ex);
      System.exit(1);
    } catch (IllegalArgumentException ex) {
      logger.error("Illegal argument", ex);
      System.exit(1);
    }
  }

  public synchronized void putField(Object objectref, double value, String fieldName) {
    Field objectField = null;

    objectField = getAccessibleDeclaredObjectField(objectref, fieldName);

    try {
      objectField.setDouble(objectref, value);
    } catch (IllegalAccessException ex) {
      logger.error("Illegal access", ex);
      System.exit(1);
    } catch (IllegalArgumentException ex) {
      logger.error("Illegal argument", ex);
      System.exit(1);
    }
  }

  public synchronized void putField(Object objectref, float value, String fieldName) {
    Field objectField = null;

    objectField = getAccessibleDeclaredObjectField(objectref, fieldName);

    try {
      objectField.setFloat(objectref, value);
    } catch (IllegalAccessException ex) {
      logger.error("Illegal access", ex);
      System.exit(1);
    } catch (IllegalArgumentException ex) {
      logger.error("Illegal argument", ex);
      System.exit(1);
    }
  }

  public synchronized void putField(Object objectref, long value, String fieldName) {
    Field objectField = null;

    objectField = getAccessibleDeclaredObjectField(objectref, fieldName);

    try {
      objectField.setLong(objectref, value);
    } catch (IllegalAccessException ex) {
      logger.error("Illegal access", ex);
      System.exit(1);
    } catch (IllegalArgumentException ex) {
      logger.error("Illegal argument", ex);
      System.exit(1);
    }
  }

  public synchronized void putField(Object objectref, short value, String fieldName) {
    Field objectField = null;

    objectField = getAccessibleDeclaredObjectField(objectref, fieldName);

    try {
      objectField.setShort(objectref, value);
    } catch (IllegalAccessException ex) {
      logger.error("Illegal access", ex);
      System.exit(1);
    } catch (IllegalArgumentException ex) {
      logger.error("Illegal argument", ex);
      System.exit(1);
    }
  }

  // </editor-fold>

  //@TODO field operations should also be sent as messages
  // <editor-fold defaultstate="collapsed" desc="helper methods for field operations">
  private Field getAccessibleDeclaredObjectField(Object objectref, String fieldName) {
    Field objectField = null;

    try {
      objectField = objectref.getClass().getDeclaredField(fieldName);
    } catch (SecurityException ex) {
      logger.error("Security exception caught when trying to access field " + fieldName + " for class " +
        objectref.getClass().getName(), ex);
      System.exit(1);
    } catch (NoSuchFieldException ex) {
      logger.error("No such field", ex);
      System.exit(1);
    }

    objectField.setAccessible(true);
    return objectField;
  }

  private Field getAccessibleDeclaredClassField(String className, String fieldName) {
    Field classField = null;

    try {
      classField = Class.forName(className).getDeclaredField(fieldName);
    } catch (ClassNotFoundException ex1) {
      logger.error("Class not found", ex1);
    } catch (SecurityException ex) {
      logger.error("Security exception caught when trying to access field " + fieldName + " for class " +
        className, ex);
      System.exit(1);
    } catch (NoSuchFieldException ex) {
      logger.error("No such field", ex);
      System.exit(1);
    }

    classField.setAccessible(true);
    return classField;
  }

  // </editor-fold>

  // <editor-fold defaultstate="collapsed" desc="EXCEPTION AND ERROR METHODS">
  public boolean raisedException() {
    logger.debug("raisedException");
    return (!raisedExceptions.isEmpty());
  }

  public synchronized Exception getException() {
    // logger.error("getException");
    if (raisedExceptions.isEmpty()) {
      logger.error("exception vector empty");
      System.exit(1);
    }

    ExceptionWrapper exceptionWra = (ExceptionWrapper) raisedExceptions.removeLast();

    if (!(exceptionWra.getException() instanceof Exception)) {
      logger.error("Can't handle Error o Throwable:", exceptionWra.getException());
      System.exit(0);
    }

    logger.debug("passing back exception : " + exceptionWra.getException());
    return (Exception) exceptionWra.getException();
  }

  // </editor-fold>

  // <editor-fold defaultstate="collapsed" desc="INSTANCEOF && CHECKCAST METHODS">
  public synchronized int instanceOf(Object object, String className) {
    if (object == null) {
      return 0;
    }

    //if the object is in another distributor, forward the call

    //otherwise perform the instanceof here
    Class clazz = null;
    try {
      clazz = Class.forName(className);
    } catch (ClassNotFoundException cnfe) {
      logger.error("Can't find the class of an object that is supposed to be loaded: " + cnfe, cnfe);
      throw new DistributorError("Can't find the class of an object that is supposed to be loaded");
    }

    if (clazz.isInstance(object)) {
      return 1;
    } else {
      return 0;
    }
  }

  public synchronized void checkcast(Object object, String className) {
    if ((object != null) && (instanceOf(object, className) == 0)) {
      throw new ClassCastException(object + " is not an instance of " + className);
    }
  }

  // </editor-fold>
  protected abstract void sendExecutableMessage(ExecutableMessage mensaje);

  protected abstract Object getLastReturnedObject();

  public java.lang.ClassLoader getClassLoader() {
    return classLoader;
  }

  public void setClassLoader(java.lang.ClassLoader classLoader) {
    this.classLoader = classLoader;
  }

  public void setId(int id) {
    this.id = id;
  }

  public int getId() {
    return id;
  }
}
