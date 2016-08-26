package com.ittera.cometa.distributor;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.ittera.cometa.distributor.returntypes.ErrorWrapper;
import com.ittera.cometa.distributor.returntypes.ExceptionWrapper;
import com.ittera.cometa.distributor.returntypes.Null;
import com.ittera.cometa.distributor.returntypes.RuntimeExceptionWrapper;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Constructor;

import java.util.Deque;
import java.util.LinkedList;

public class LocalJavaExecutor {
  private static String senderClassName;
  private static Object sender;
  private static String receiverClassName;
  private static Object receiver;
  private static String methodName;
  protected static Logger logger = LogManager.getLogger("distributor");
  protected static Class[] parameterClasses;
  protected static Object[] parameters;
  protected static Deque<Object> returnedValues = new LinkedList<Object>();
  protected static Deque<ExceptionWrapper> raisedExceptions = new LinkedList<>();

  public static Object executeInstanceMethodMessage(Object imm) throws MessageExecutionException {

    Object returnedValue = null;

    try {
      Method method = null;

      try {
        method = Class.forName(receiverClassName).getMethod(methodName, parameterClasses);
      } catch (NoSuchMethodException E) {
        method = Class.forName(receiverClassName)
                      .getDeclaredMethod(methodName, parameterClasses);
      }

      method.setAccessible(true);
      if (logger.isDebugEnabled()) {
        logger.debug("Calling method name:" + method.getName() + " on object:" + receiver);
      }
      if (method.getReturnType() == void.class) {
        returnedValue = new com.ittera.cometa.distributor.returntypes.Void();
        method.invoke(receiver, parameters);
      } else {
        returnedValue = method.invoke(receiver, parameters);
      }
    } catch (Exception ex) {
      if (ex instanceof InvocationTargetException) {
        Throwable realEx = ex.getCause();
        if (realEx instanceof Error) {
          returnedValue = new ErrorWrapper((Error) realEx);
        } else if (realEx instanceof RuntimeException) {
          returnedValue = new RuntimeExceptionWrapper((RuntimeException) realEx);
        } else if (realEx instanceof Exception) {
          returnedValue = new ExceptionWrapper((Exception) realEx);
        } else {
          return new MessageExecutionException("Throwable type is not wrappable");
        }
      } else {
        ex.printStackTrace();
        logger.error("Error caught:", ex);
        throw new MessageExecutionException(ex.getMessage());
      }
    }

    return returnedValue;
  }

  public static Object executeClassMethodMessage(Object cmm) throws MessageExecutionException {
    Object valor_devuelto = null;

    try {
      Method Metodo = null;

      try {
        Metodo = Class.forName(receiverClassName).getMethod(methodName, parameterClasses);
      } catch (NoSuchMethodException E) {
        Metodo = Class.forName(receiverClassName)
                      .getDeclaredMethod(methodName, parameterClasses);
      }

      Metodo.setAccessible(true);
      if (Metodo.getReturnType() == void.class) {
        valor_devuelto = new com.ittera.cometa.distributor.returntypes.Void();
        Metodo.invoke(null, parameters);
      } else {
        valor_devuelto = Metodo.invoke(null, parameters);
      }
    } catch (Exception ex) {
      if (ex instanceof InvocationTargetException) {
        Throwable realEx = ex.getCause();
        if (realEx instanceof Error) {
          valor_devuelto = new ErrorWrapper((Error) realEx);
        } else if (realEx instanceof RuntimeException) {
          valor_devuelto = new RuntimeExceptionWrapper((RuntimeException) realEx);
        } else if (realEx instanceof Exception) {
          valor_devuelto = new ExceptionWrapper((Exception) realEx);
        } else {
          return new MessageExecutionException("Throwable type is not wrappable");
        }
      } else {
        logger.error("Error caught:", ex);
        throw new MessageExecutionException(ex.getMessage());
      }
    }

    return valor_devuelto;
  }

  public static Object executeConstructorMessage(Object cm) throws MessageExecutionException {
    Object valor_devuelto = null;

    try {
      Constructor _Constructor = null;

      try {
        _Constructor = Class.forName(receiverClassName).getConstructor(parameterClasses);
      } catch (NoSuchMethodException E) {
        _Constructor = Class.forName(receiverClassName)
                            .getDeclaredConstructor(parameterClasses);
      }

      _Constructor.setAccessible(true);
      valor_devuelto = _Constructor.newInstance(parameters);
    } catch (Exception ex) {
      if (ex instanceof InvocationTargetException) {
        Throwable realEx = ex.getCause();
        if (realEx instanceof Error) {
          valor_devuelto = new ErrorWrapper((Error) realEx);
        } else if (realEx instanceof RuntimeException) {
          valor_devuelto = new RuntimeExceptionWrapper((RuntimeException) realEx);
        } else if (realEx instanceof Exception) {
          valor_devuelto = new ExceptionWrapper((Exception) realEx);
        } else {
          return new MessageExecutionException("Throwable type is not wrappable");
        }
      } else {
        logger.error("Error caught:", ex);
        throw new MessageExecutionException(ex.getMessage());
      }
    }

    return valor_devuelto;
  }


  /**
  public static void sendExecutableMessage(ExecutableMessage message) {
    Object returnedVal = null;
    try {
      returnedVal = message.execute();
    } catch (MessageExecutionException ex) {
      throw new DistributorError("Error executing mensaje", ex);
    }

    if (returnedVal instanceof Void) {
      //we're done
      return;
    }

    if (returnedVal == null) {
      returnedValues.add(new Null());
    } else if (returnedVal instanceof ExceptionWrapper) {
      logger.debug("exception wrapper returned");
      raisedExceptions.add((ExceptionWrapper) returnedVal);
    } else if (returnedVal instanceof ErrorWrapper) {
      throw new RuntimeException("Can't handle RuntimeException: ", ((ErrorWrapper) returnedVal).getError());
    } else if (returnedVal instanceof RuntimeExceptionWrapper) {
      throw new RuntimeException("Can't handle error: ",
        ((RuntimeExceptionWrapper) returnedVal).getRuntimeException());
    } else {
      returnedValues.add(returnedVal);
    }
  }*/

  public static Object getLastReturnedObject() {
    Object value=returnedValues.removeLast();
    if (value instanceof Null) {
      return null;
    }
    return value;
  }
}
