package com.ittera.cometa.distributor.messages;


import com.ittera.cometa.common.ByteSerializable;
import com.ittera.cometa.common.exceptions.ErrorConstituyendoMensaje;
import com.ittera.cometa.common.exceptions.ErrorReconstituyendoMensaje;

import com.ittera.cometa.distributor.MessageExecutionException;
import com.ittera.cometa.distributor.returntypes.ErrorWrapper;
import com.ittera.cometa.distributor.returntypes.ExceptionWrapper;
import com.ittera.cometa.distributor.returntypes.RuntimeExceptionWrapper;
import com.ittera.cometa.distributor.returntypes.Void;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import org.aspectj.lang.reflect.CodeSignature;

public class ClassMethodMessage extends ArgedMessage implements ExecutableMessage, ByteSerializable {
  public static byte MAGIC = 103;
  private int distributorID=1;
  private String senderClassName;
  private Object sender;
  private String receiverClassName;
  private String methodName;

  public ClassMethodMessage(CodeSignature codeSignature, Object sender, Object[] args) {
    this.senderClassName = sender==null? "" : sender.getClass().getName();
    this.sender = sender;
    this.receiverClassName =  codeSignature.getDeclaringTypeName();
    this.methodName = codeSignature.getName();

    setParameters(args, codeSignature.getParameterTypes());
  }

  public Object execute()
    throws MessageExecutionException {
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
        valor_devuelto = new Void();
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

    return ml;
  }
}
