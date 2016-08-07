package com.ittera.cometa.distributor.messages;

import com.ittera.cometa.common.ByteSerializable;
import com.ittera.cometa.common.exceptions.ErrorConstituyendoMensaje;
import com.ittera.cometa.common.exceptions.ErrorReconstituyendoMensaje;

import com.ittera.cometa.distributor.ExecutableMessageCreationException;
import com.ittera.cometa.distributor.MessageExecutionException;
import com.ittera.cometa.distributor.returntypes.ErrorWrapper;
import com.ittera.cometa.distributor.returntypes.ExceptionWrapper;
import com.ittera.cometa.distributor.returntypes.RuntimeExceptionWrapper;
import com.ittera.cometa.distributor.returntypes.Void;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class InstanceMethodMessage extends ArgedMessage implements ExecutableMessage, ByteSerializable {
  public static byte MAGIC = 105;
  private int distributorID=-1;
  private String senderClassName;
  private Object sender;
  private String receiverClassName;
  private Object receiver;
  private String methodName;

  public InstanceMethodMessage(Object sender, String senderClassName, Object receiver,
                               String receiverClassName, String methodName, String methodSignatureStr, Class[] parameterTypes, Object[] args)
    throws ExecutableMessageCreationException {
    this.senderClassName = senderClassName;
    this.sender = sender;
    this.receiverClassName = receiverClassName;
    this.receiver = receiver;

    if (methodName == null || methodName.isEmpty()) {
      throw new ExecutableMessageCreationException("Method name is null or <empty string>.");
    } else {
      this.methodName = methodName;
    }

    this.methodSignatureStr = methodSignatureStr;

    if (args == null) {
      throw new ExecutableMessageCreationException("Parametros = null.");
    } else {
      setParameters(args, parameterTypes);
    }
  }

  public Object execute()
    throws MessageExecutionException {
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
        returnedValue = new Void();
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

  public void fromBytes(byte[] bytes) throws ErrorReconstituyendoMensaje {
  }

  public byte[] toBytes() throws ErrorConstituyendoMensaje {
    return null;
  }

  public ThinMessage toMensajeLigero() {
    ThinMessage ml = new ThinMessage();

    ml.DistributorID = this.distributorID;
    ml.MensajeEjecutableRef = 0;
    ml.NombreClaseSender = this.senderClassName;
    ml.Sender = 0;
    ml.NombreClaseReceiver = this.receiverClassName;
    ml.Receiver = 0;
    ml.NombreMetodo = this.methodName;
    ml.Parametros = 0;
    ml.FirmaMetodo = methodSignatureStr;

    return ml;
  }

  @Override
  public String toString() {
    // falta imprimir los parametros como una lista
    return ("Message Llamada\n" + "---------------\n" + "Distributor:        " + distributorID + "\n" +
    "Sender (Ref):       " + sender + "\n" + "ClaseSender:       " + senderClassName + "\n" +
    "Receiver (Ref):     " + receiver + "\n" + "ClaseReceiver:     " + receiverClassName + "\n" +
    "Metodo:              " + methodName + "\n" + "Firma M�todo      " + methodSignatureStr + "\n" +
    "Parametros:        " + parameters);
  }
}
