package com.ittera.cometa.distributor.messages;

import com.ittera.cometa.common.ByteSerializable;
import com.ittera.cometa.common.exceptions.ErrorConstituyendoMensaje;
import com.ittera.cometa.common.exceptions.ErrorReconstituyendoMensaje;

import com.ittera.cometa.distributor.ExcepcionCreandoMensajeEjecutable;
import com.ittera.cometa.distributor.ExcepcionEjecutandoMensaje;
import com.ittera.cometa.distributor.returntypes.ErrorWrapper;
import com.ittera.cometa.distributor.returntypes.ExceptionWrapper;
import com.ittera.cometa.distributor.returntypes.RuntimeExceptionWrapper;
import com.ittera.cometa.distributor.returntypes.Void;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;


public class NoArgsInstanceMethodMessage implements ExecutableMessage, ByteSerializable {
  protected Logger logger = LogManager.getLogger(this.getClass());
  public static byte MAGIC = 106;
  private int distributorID;
  private String nombreClaseSender;
  private Object sender;
  private String nombreClaseReceiver;
  private Object receiver;
  private String firmaMetodo;
  private String nombreMetodo;

  public NoArgsInstanceMethodMessage(int distributor, Object sender, String nombreClaseSender, Object receiver,
                                     String nombreClaseReceiver, String nombreMetodo, String firmaMetodo)
    throws ExcepcionCreandoMensajeEjecutable {
    this.distributorID = distributor;
    this.nombreClaseSender = nombreClaseSender;
    this.sender = sender;
    this.nombreClaseReceiver = nombreClaseReceiver;
    this.receiver = receiver;

    if ((nombreMetodo == null) || nombreMetodo.isEmpty()) {
      throw new ExcepcionCreandoMensajeEjecutable("Nombre del Metodo es null o <empty string>.");
    } else {
      this.nombreMetodo = nombreMetodo;
    }

    this.firmaMetodo = firmaMetodo;
  }

  public Object Ejecutar(java.lang.ClassLoader classLoader)
    throws ExcepcionEjecutandoMensaje {
    Object valor_devuelto = null;

    try {
      Method Metodo = null;

      try {
        Metodo = Class.forName(nombreClaseReceiver, true, classLoader).getMethod(nombreMetodo, (Class[]) null);
      } catch (NoSuchMethodException E) {
        Metodo = Class.forName(nombreClaseReceiver, true, classLoader)
                      .getDeclaredMethod(nombreMetodo, (Class[]) null);
      }

      Metodo.setAccessible(true);
      if (Metodo.getReturnType() == void.class) {
        valor_devuelto = new Void();
        Metodo.invoke(receiver, (Object[]) null);
      } else {
        valor_devuelto = Metodo.invoke(receiver, (Object[]) null);
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
          return new ExcepcionEjecutandoMensaje("Throwable type is not wrappable");
        }
      } else {
        logger.error("Error caught:", ex);
        throw new ExcepcionEjecutandoMensaje(ex.getMessage());
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
    ml.NombreClaseSender = this.nombreClaseSender;
    ml.Sender = 0;
    ml.NombreClaseReceiver = this.nombreClaseReceiver;
    ml.Receiver = 0;
    ml.NombreMetodo = this.nombreMetodo;
    ml.Parametros = 0;
    ml.FirmaMetodo = firmaMetodo;

    return ml;
  }
}
