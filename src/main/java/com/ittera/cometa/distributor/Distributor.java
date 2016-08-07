package com.ittera.cometa.distributor;

import com.ittera.cometa.distributor.messages.*;
import com.ittera.cometa.distributor.returntypes.ExceptionWrapper;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.ittera.cometa.util.SuperStack;

import java.util.Deque;
import java.util.LinkedList;

import java.lang.reflect.Field;


public class Distributor {
  protected static Logger logger = LogManager.getLogger("distributor");
  protected static Deque<Object> returnedValues = new LinkedList<Object>();
  protected static Deque<ExceptionWrapper> raisedExceptions = new LinkedList<>();

  /************************ INTERFACE ***************************/

  // <editor-fold defaultstate="collapsed" desc="METHOD CALLS">

  /** Para llamadas a metodos de instancias con parametros.
   * @param sender
   * @param receiver Objeto al que se invoca.
   * @param methodName Nombre del metodo al que se invoca.
   * @param methodSignature Firma (Signature) que identifica los tipos de parametros y el return type.
   * @param args SuperSuperStack que alberga los parametros al metodo estatico. Estos se meten en el orden
   * de los parametros en el metodo, y se han de sacar (popear) en el orden inverso.
   */
  public static void instanceMethod(Object sender, Object receiver, String methodName,
    String methodSignature, Class[] parameterTypes, Object[] args) throws ExecutableMessageCreationException {
    logger.debug("instanceMethod: calling " + methodName);

    final String senderClassName = sender==null? "" : sender.getClass().getName();

    final String receiverClassName = receiver.getClass().getName();

    final ExecutableMessage message = new InstanceMethodMessage(sender, senderClassName, receiver, receiverClassName, methodName,
          methodSignature, parameterTypes, args);

    MessageExecutor.sendExecutableMessage(message);
  }

  /** Para llamadas a metodos de clases estaticas con parametros.
   * @param receiverClassName Clase a la que se invoca.
   * @param methodName Nombre del metodo estatico al que se invoca.
   * @param methodSignature Firma (Signature) que identifica los tipos de parametros y el return type.
   * @param paramStack SuperStack que alberga los parametros al metodo estatico. Estos se meten en el orden
   * de los parametros en el metodo, y se han de sacar (popear) en el orden inverso.
   */
  public static void staticMethod(Object sender, String receiverClassName, String methodName,
    String methodSignature, SuperStack paramStack) throws Throwable {
    logger.debug("staticMethod: calling " + methodName);

    final String senderClassName = sender==null? "" : sender.getClass().getName();

    ExecutableMessage message = new ClassMethodMessage(sender, senderClassName, receiverClassName, methodName,
          methodSignature, paramStack);

    sendExecutableMessage(message);
  }

  /** Para llamadas a constructores con parametros.
   * @param receiverClassName Clase del nuevo objeto a crear.
   * @param paramStack SuperStack que alberga los parametros del constructor deseado.
   */
  public static void constructor(Object sender, String receiverClassName, String methodSignature,
    SuperStack paramStack) throws Throwable {
    logger.debug("constructor: calling constructor of " + receiverClassName + "\n w/ superstack :" + paramStack);

    final String senderClassName = sender==null? "" : sender.getClass().getName();

    ExecutableMessage message =  new ConstructorMessage(senderClassName, sender, receiverClassName, methodSignature,
          paramStack);

    sendExecutableMessage(message);
  }

  // </editor-fold>

  // <editor-fold defaultstate="collapsed" desc="GETSTATIC OPERATIONS">
  public static Object getStaticObject(String className, String fieldName) {
    Object fieldValue = null;
    try {
      fieldValue = getAccessibleDeclaredClassField(className, fieldName).get(null);
    } catch (IllegalAccessException ex) {
      throw new DistributorError("Illegal access",ex);
    } catch (IllegalArgumentException ex) {
      throw new DistributorError("Illegal argument",ex);
    }

    return fieldValue;
  }

  public static boolean getStaticBoolean(String className, String fieldName) {
    final boolean fieldValue;

    try {
      fieldValue = getAccessibleDeclaredClassField(className, fieldName).getBoolean(null);
    } catch (IllegalAccessException ex) {
      throw new DistributorError("Illegal access",ex);
    } catch (IllegalArgumentException ex) {
      throw new DistributorError("Illegal argument",ex);
    }

    return fieldValue;
  }

  public static byte getStaticByte(String className, String fieldName) {
    final byte fieldValue;

    try {
      fieldValue = getAccessibleDeclaredClassField(className, fieldName).getByte(null);
    } catch (IllegalAccessException ex) {
      throw new DistributorError("Illegal access",ex);
    } catch (IllegalArgumentException ex) {
      throw new DistributorError("Illegal argument",ex);
    }

    return fieldValue;
  }

  public static char getStaticChar(String className, String fieldName) {
    final char fieldValue;

    try {
      fieldValue = getAccessibleDeclaredClassField(className, fieldName).getChar(null);
    } catch (IllegalAccessException ex) {
      throw new DistributorError("Illegal access",ex);
    } catch (IllegalArgumentException ex) {
      throw new DistributorError("Illegal argument",ex);
    }

    return fieldValue;
  }

  public static int getStaticInt(String className, String fieldName) {
    final int fieldValue;

    try {
      fieldValue = getAccessibleDeclaredClassField(className, fieldName).getInt(null);
    } catch (IllegalAccessException ex) {
      throw new DistributorError("Illegal access",ex);
    } catch (IllegalArgumentException ex) {
      throw new DistributorError("Illegal argument",ex);
    }

    return fieldValue;
  }

  public static double getStaticDouble(String className, String fieldName) {
    final double fieldValue;

    try {
      fieldValue = getAccessibleDeclaredClassField(className, fieldName).getDouble(null);
    } catch (IllegalAccessException ex) {
      throw new DistributorError("Illegal access",ex);
    } catch (IllegalArgumentException ex) {
      throw new DistributorError("Illegal argument",ex);
    }

    return fieldValue;
  }

  public static float getStaticFloat(String className, String fieldName) {
    final float fieldValue;

    try {
      fieldValue = getAccessibleDeclaredClassField(className, fieldName).getFloat(null);
    } catch (IllegalAccessException ex) {
      throw new DistributorError("Illegal access",ex);
    } catch (IllegalArgumentException ex) {
      throw new DistributorError("Illegal argument",ex);
    }

    return fieldValue;
  }

  public static long getStaticLong(String className, String fieldName) {
    final long fieldValue;

    try {
      fieldValue = getAccessibleDeclaredClassField(className, fieldName).getLong(null);
    } catch (IllegalAccessException ex) {
      throw new DistributorError("Illegal access",ex);
    } catch (IllegalArgumentException ex) {
      throw new DistributorError("Illegal argument",ex);
    }

    return fieldValue;
  }

  public static short getStaticShort(String className, String fieldName) {
    final short fieldValue;

    try {
      fieldValue = getAccessibleDeclaredClassField(className, fieldName).getShort(null);
    } catch (IllegalAccessException ex) {
      throw new DistributorError("Illegal access",ex);
    } catch (IllegalArgumentException ex) {
      throw new DistributorError("Illegal argument",ex);
    }

    return fieldValue;
  }

  public static String getStaticString(String className, String fieldName) {
    return (String) getStaticObject(className, fieldName);
  }

  // </editor-fold>

  // <editor-fold defaultstate="collapsed" desc="GETFIELD OPERATIONS">
  public static Object getObject(Object objectref, String fieldName) {
    final Object fieldValue;

    try {
      fieldValue = getAccessibleDeclaredObjectField(objectref, fieldName).get(objectref);
    } catch (IllegalAccessException ex) {
      throw new DistributorError("Illegal access",ex);
    } catch (IllegalArgumentException ex) {
      throw new DistributorError("Illegal argument",ex);
    }

    return fieldValue;
  }

  public static boolean getBoolean(Object objectref, String fieldName) {
    final boolean fieldValue;
    try {
      fieldValue = getAccessibleDeclaredObjectField(objectref, fieldName).getBoolean(objectref);
    } catch (IllegalAccessException ex) {
      throw new DistributorError("Illegal access",ex);
    } catch (IllegalArgumentException ex) {
      throw new DistributorError("Illegal argument",ex);
    }

    return fieldValue;
  }

  public static byte getByte(Object objectref, String fieldName) {
    final byte fieldValue;
    try {
      fieldValue = getAccessibleDeclaredObjectField(objectref, fieldName).getByte(objectref);
    } catch (IllegalAccessException ex) {
      throw new DistributorError("Illegal access",ex);
    } catch (IllegalArgumentException ex) {
      throw new DistributorError("Illegal argument",ex);
    }

    return fieldValue;
  }

  public static char getChar(Object objectref, String fieldName) {
    final char fieldValue;
    try {
      fieldValue = getAccessibleDeclaredObjectField(objectref, fieldName).getChar(objectref);
    } catch (IllegalAccessException ex) {
      throw new DistributorError("Illegal access",ex);
    } catch (IllegalArgumentException ex) {
      throw new DistributorError("Illegal argument",ex);
    }

    return fieldValue;
  }

  public static int getInt(Object objectref, String fieldName) {
    final int fieldValue;
    try {
      fieldValue = getAccessibleDeclaredObjectField(objectref, fieldName).getInt(objectref);
    } catch (IllegalAccessException ex) {
      throw new DistributorError("Illegal access",ex);
    } catch (IllegalArgumentException ex) {
      throw new DistributorError("Illegal argument",ex);
    }

    return fieldValue;
  }

  public static double getDouble(Object objectref, String fieldName) {
    final double fieldValue;
    try {
      fieldValue = getAccessibleDeclaredObjectField(objectref, fieldName).getDouble(objectref);
    } catch (IllegalAccessException ex) {
      throw new DistributorError("Illegal access",ex);
    } catch (IllegalArgumentException ex) {
      throw new DistributorError("Illegal argument",ex);
    }

    return fieldValue;
  }

  public static float getFloat(Object objectref, String fieldName) {
    final float fieldValue;
    try {
      fieldValue = getAccessibleDeclaredObjectField(objectref, fieldName).getFloat(objectref);
    } catch (IllegalAccessException ex) {
      throw new DistributorError("Illegal access",ex);
    } catch (IllegalArgumentException ex) {
      throw new DistributorError("Illegal argument",ex);
    }

    return fieldValue;
  }

  public static long getLong(Object objectref, String fieldName) {
    final long fieldValue;
    try {
      fieldValue = getAccessibleDeclaredObjectField(objectref, fieldName).getLong(objectref);
    } catch (IllegalAccessException ex) {
      throw new DistributorError("Illegal access",ex);
    } catch (IllegalArgumentException ex) {
      throw new DistributorError("Illegal argument",ex);
    }

    return fieldValue;
  }

  public static short getShort(Object objectref, String fieldName) {
    final short fieldValue;
    try {
      fieldValue = getAccessibleDeclaredObjectField(objectref, fieldName).getShort(objectref);
    } catch (IllegalAccessException ex) {
      throw new DistributorError("Illegal access",ex);
    } catch (IllegalArgumentException ex) {
      throw new DistributorError("Illegal argument",ex);
    }

    return fieldValue;
  }

  public static String getString(Object objectref, String fieldName) {
    return (String) getObject(objectref, fieldName);
  }

  // </editor-fold>

  // <editor-fold defaultstate="collapsed" desc="PUTSTATIC OPERATIONS">
  public static void putStatic(Object value, String className, String fieldName) {
    final Field objectField = getAccessibleDeclaredClassField(className, fieldName);

    try {
      objectField.set(null, value);
    } catch (IllegalAccessException ex) {
      throw new DistributorError("Illegal access",ex);
    } catch (IllegalArgumentException ex) {
      throw new DistributorError("Illegal argument",ex);
    }
  }

  public static void putStatic(boolean value, String className, String fieldName) {
    final Field objectField = getAccessibleDeclaredClassField(className, fieldName);

    try {
      objectField.setBoolean(null, value);
    } catch (IllegalAccessException ex) {
      throw new DistributorError("Illegal access",ex);
    } catch (IllegalArgumentException ex) {
      throw new DistributorError("Illegal argument",ex);
    }
  }

  public static void putStatic(byte value, String className, String fieldName) {
    final Field objectField = getAccessibleDeclaredClassField(className, fieldName);

    try {
      objectField.setByte(null, value);
    } catch (IllegalAccessException ex) {
      throw new DistributorError("Illegal access",ex);
    } catch (IllegalArgumentException ex) {
      throw new DistributorError("Illegal argument",ex);
    }
  }

  public static void putStatic(char value, String className, String fieldName) {
    final Field objectField = getAccessibleDeclaredClassField(className, fieldName);

    try {
      objectField.setChar(null, value);
    } catch (IllegalAccessException ex) {
      throw new DistributorError("Illegal access",ex);
    } catch (IllegalArgumentException ex) {
      throw new DistributorError("Illegal argument",ex);
    }
  }

  public static void putStatic(int value, String className, String fieldName) {
    final Field objectField = getAccessibleDeclaredClassField(className, fieldName);

    try {
      objectField.setInt(null, value);
    } catch (IllegalAccessException ex) {
      throw new DistributorError("Illegal access",ex);
    } catch (IllegalArgumentException ex) {
      throw new DistributorError("Illegal argument",ex);
    }
  }

  public static void putStatic(double value, String className, String fieldName) {
    final Field objectField = getAccessibleDeclaredClassField(className, fieldName);

    try {
      objectField.setDouble(null, value);
    } catch (IllegalAccessException ex) {
      throw new DistributorError("Illegal access",ex);
    } catch (IllegalArgumentException ex) {
      throw new DistributorError("Illegal argument",ex);
    }
  }

  public static void putStatic(float value, String className, String fieldName) {
    final Field objectField = getAccessibleDeclaredClassField(className, fieldName);

    try {
      objectField.setFloat(null, value);
    } catch (IllegalAccessException ex) {
      throw new DistributorError("Illegal access",ex);
    } catch (IllegalArgumentException ex) {
      throw new DistributorError("Illegal argument",ex);
    }
  }

  public static void putStatic(long value, String className, String fieldName) {
    final Field objectField = getAccessibleDeclaredClassField(className, fieldName);

    try {
      objectField.setLong(null, value);
    } catch (IllegalAccessException ex) {
      throw new DistributorError("Illegal access",ex);
    } catch (IllegalArgumentException ex) {
      throw new DistributorError("Illegal argument",ex);
    }
  }

  public static void putStatic(short value, String className, String fieldName) {
    final Field objectField = getAccessibleDeclaredClassField(className, fieldName);

    try {
      objectField.setShort(null, value);
    } catch (IllegalAccessException ex) {
      throw new DistributorError("Illegal access",ex);
    } catch (IllegalArgumentException ex) {
      throw new DistributorError("Illegal argument",ex);
    }
  }

  // </editor-fold>

  // <editor-fold defaultstate="collapsed" desc="PUTFIELD OPERATIONS">
  public static void putField(Object objectref, Object value, String fieldName) {
    final Field objectField = getAccessibleDeclaredObjectField(objectref, fieldName);

    try {
      objectField.set(objectref, value);
    } catch (IllegalAccessException ex) {
      throw new DistributorError("Illegal access",ex);
    } catch (IllegalArgumentException ex) {
      throw new DistributorError("Illegal argument",ex);
    }
  }

  public static void putField(Object objectref, boolean value, String fieldName) {
    final Field objectField = getAccessibleDeclaredObjectField(objectref, fieldName);

    try {
      objectField.setBoolean(objectref, value);
    } catch (IllegalAccessException ex) {
      throw new DistributorError("Illegal access",ex);
    } catch (IllegalArgumentException ex) {
      throw new DistributorError("Illegal argument",ex);
    }
  }

  public static void putField(Object objectref, byte value, String fieldName) {
    final Field objectField = getAccessibleDeclaredObjectField(objectref, fieldName);

    try {
      objectField.setByte(objectref, value);
    } catch (IllegalAccessException ex) {
      throw new DistributorError("Illegal access",ex);
    } catch (IllegalArgumentException ex) {
      throw new DistributorError("Illegal argument",ex);
    }
  }

  public static void putField(Object objectref, char value, String fieldName) {
    final Field objectField = getAccessibleDeclaredObjectField(objectref, fieldName);

    try {
      objectField.setChar(objectref, value);
    } catch (IllegalAccessException ex) {
      throw new DistributorError("Illegal access",ex);
    } catch (IllegalArgumentException ex) {
      throw new DistributorError("Illegal argument",ex);
    }
  }

  public static void putField(Object objectref, int value, String fieldName) {
    final Field objectField = getAccessibleDeclaredObjectField(objectref, fieldName);

    try {
      objectField.setInt(objectref, value);
    } catch (IllegalAccessException ex) {
      throw new DistributorError("Illegal access",ex);
    } catch (IllegalArgumentException ex) {
      throw new DistributorError("Illegal argument",ex);
    }
  }

  public static void putField(Object objectref, double value, String fieldName) {
    final Field objectField = getAccessibleDeclaredObjectField(objectref, fieldName);

    try {
      objectField.setDouble(objectref, value);
    } catch (IllegalAccessException ex) {
      throw new DistributorError("Illegal access",ex);
    } catch (IllegalArgumentException ex) {
      throw new DistributorError("Illegal argument",ex);
    }
  }

  public static void putField(Object objectref, float value, String fieldName) {
    final Field objectField = getAccessibleDeclaredObjectField(objectref, fieldName);

    try {
      objectField.setFloat(objectref, value);
    } catch (IllegalAccessException ex) {
      throw new DistributorError("Illegal access",ex);
    } catch (IllegalArgumentException ex) {
      throw new DistributorError("Illegal argument",ex);
    }
  }

  public static void putField(Object objectref, long value, String fieldName) {
    final Field objectField = getAccessibleDeclaredObjectField(objectref, fieldName);

    try {
      objectField.setLong(objectref, value);
    } catch (IllegalAccessException ex) {
      throw new DistributorError("Illegal access",ex);
    } catch (IllegalArgumentException ex) {
      throw new DistributorError("Illegal argument",ex);
    }
  }

  public static void putField(Object objectref, short value, String fieldName) {
    final Field objectField = getAccessibleDeclaredObjectField(objectref, fieldName);

    try {
      objectField.setShort(objectref, value);
    } catch (IllegalAccessException ex) {
      throw new DistributorError("Illegal access",ex);
    } catch (IllegalArgumentException ex) {
      throw new DistributorError("Illegal argument",ex);
    }
  }

  // </editor-fold>

  //@TODO field operations should also be sent as messages
  // <editor-fold defaultstate="collapsed" desc="helper methods for field operations">
  private static Field getAccessibleDeclaredObjectField(Object objectref, String fieldName) {
    Field objectField = null;

    try {
      objectField = objectref.getClass().getDeclaredField(fieldName);
    } catch (SecurityException ex) {
      throw new DistributorError("Security exception caught when trying to access field " + fieldName + " for class " +
        objectref.getClass().getName(), ex);
    } catch (NoSuchFieldException ex) {
      throw new DistributorError("No such field", ex);
    }

    objectField.setAccessible(true);
    return objectField;
  }

  private static Field getAccessibleDeclaredClassField(String className, String fieldName) {
    Field classField = null;

    try {
      classField = Class.forName(className).getDeclaredField(fieldName);
    } catch (ClassNotFoundException ex1) {
      throw new DistributorError("Class not found", ex1);
    } catch (SecurityException ex) {
      throw new DistributorError("Security exception caught when trying to access field " + fieldName + " for class " +
        className, ex);
    } catch (NoSuchFieldException ex) {
      throw new DistributorError("No such field", ex);
    }

    classField.setAccessible(true);
    return classField;
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
