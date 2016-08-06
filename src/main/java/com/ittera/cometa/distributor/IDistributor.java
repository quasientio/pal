package com.ittera.cometa.distributor;

import com.ittera.cometa.util.SuperStack;


/**
 *
 * @author libre
 */
public interface IDistributor {
  // <editor-fold defaultstate="collapsed" desc="METHOD CALLS">
  void instanceMethodWithArgs(Object sender, Object receiver, String methodName, String methodSignature,
    SuperStack paramStack) throws Throwable;

  void instanceMethodNoArgs(Object sender, Object receiver, String methodName, String methodSignature)
    throws Throwable;

  void staticMethodWithArgs(Object sender, String receiverClassName, String methodName, String methodSignature,
    SuperStack paramStack) throws Throwable;

  void staticMethodNoArgs(Object sender, String receiverClassName, String methodName, String methodSignature)
    throws Throwable;

  void initWithArgs(Object sender, String receiverClassName, String methodSignature, SuperStack paramStack)
    throws Throwable;

  void initNoArgs(Object sender, String receiverClassName)
    throws Throwable;

  // </editor-fold>

  // <editor-fold defaultstate="collapsed" desc="GETTERS FOR VALUES RETURNED FROM METHODS">
  Object getReturnedObject() throws Throwable;

  String getReturnedString() throws Throwable;

  long getReturnedLong() throws Throwable;

  double getReturnedDouble() throws Throwable;

  int getReturnedInt() throws Throwable;

  boolean getReturnedBoolean() throws Throwable;

  float getReturnedFloat() throws Throwable;

  short getReturnedShort() throws Throwable;

  char getReturnedChar() throws Throwable;

  byte getReturnedByte() throws Throwable;

  // </editor-fold>

  // <editor-fold defaultstate="collapsed" desc="GETSTATIC OPERATIONS">
  Object getStaticObject(String className, String fieldName);

  boolean getStaticBoolean(String className, String fieldName);

  byte getStaticByte(String className, String fieldName);

  char getStaticChar(String className, String fieldName);

  int getStaticInt(String className, String fieldName);

  double getStaticDouble(String className, String fieldName);

  float getStaticFloat(String className, String fieldName);

  long getStaticLong(String className, String fieldName);

  short getStaticShort(String className, String fieldName);

  String getStaticString(String className, String fieldName);

  // </editor-fold>

  // <editor-fold defaultstate="collapsed" desc="GETFIELD OPERATIONS">
  Object getObject(Object objectref, String fieldName);

  boolean getBoolean(Object objectref, String fieldName);

  byte getByte(Object objectref, String fieldName);

  char getChar(Object objectref, String fieldName);

  int getInt(Object objectref, String fieldName);

  double getDouble(Object objectref, String fieldName);

  float getFloat(Object objectref, String fieldName);

  long getLong(Object objectref, String fieldName);

  short getShort(Object objectref, String fieldName);

  String getString(Object objectref, String fieldName);

  // </editor-fold>

  // <editor-fold defaultstate="collapsed" desc="PUTSTATIC OPERATIONS">
  void putStatic(Object value, String className, String fieldName);

  void putStatic(boolean value, String className, String fieldName);

  void putStatic(byte value, String className, String fieldName);

  void putStatic(char value, String className, String fieldName);

  void putStatic(int value, String className, String fieldName);

  void putStatic(double value, String className, String fieldName);

  void putStatic(float value, String className, String fieldName);

  void putStatic(long value, String className, String fieldName);

  void putStatic(short value, String className, String fieldName);

  // </editor-fold>

  // <editor-fold defaultstate="collapsed" desc="PUTFIELD OPERATIONS">
  void putField(Object objectref, Object value, String fieldName);

  void putField(Object objectref, boolean value, String fieldName);

  void putField(Object objectref, byte value, String fieldName);

  void putField(Object objectref, char value, String fieldName);

  void putField(Object objectref, int value, String fieldName);

  void putField(Object objectref, double value, String fieldName);

  void putField(Object objectref, float value, String fieldName);

  void putField(Object objectref, long value, String fieldName);

  void putField(Object objectref, short value, String fieldName);

  // </editor-fold>
}
