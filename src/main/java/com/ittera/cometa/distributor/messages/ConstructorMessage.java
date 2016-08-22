package com.ittera.cometa.distributor.messages;

import com.ittera.cometa.common.ByteSerializable;
import com.ittera.cometa.common.exceptions.ErrorConstituyendoMensaje;
import com.ittera.cometa.common.exceptions.ErrorReconstituyendoMensaje;

import com.ittera.cometa.distributor.MessageExecutionException;
import com.ittera.cometa.distributor.returntypes.ErrorWrapper;
import com.ittera.cometa.distributor.returntypes.ExceptionWrapper;
import com.ittera.cometa.distributor.returntypes.RuntimeExceptionWrapper;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import org.aspectj.lang.reflect.CodeSignature;
import org.aspectj.lang.reflect.ConstructorSignature;


public class ConstructorMessage extends ArgedMessage implements ExecutableMessage, ByteSerializable {
  public static byte MAGIC = 101;
  private int distributorID=-1;
  private String senderClassName;
  private Object sender;
  private String receiverClassName;
  private Constructor constructor;

  public ConstructorMessage(CodeSignature codeSignature, Object sender, Object[] args) {
    this.senderClassName = sender==null? "" : sender.getClass().getName();
    this.sender = sender;
    this.receiverClassName =  codeSignature.getDeclaringTypeName();
    this.constructor = ((ConstructorSignature) codeSignature).getConstructor();
    setParameters(args, codeSignature.getParameterTypes());
  }

  public Object execute()
    throws MessageExecutionException {
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
    ml.NombreMetodo = "new";
    ml.Parametros = 0;
    ml.FirmaMetodo = "";

    return ml;
  }
}
