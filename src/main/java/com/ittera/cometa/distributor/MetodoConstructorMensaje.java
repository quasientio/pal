package com.ittera.cometa.distributor;

import com.ittera.cometa.common.ByteSerializable;
import com.ittera.cometa.common.exceptions.ErrorConstituyendoMensaje;
import com.ittera.cometa.common.exceptions.ErrorReconstituyendoMensaje;

import com.ittera.cometa.distributor.returntypes.ErrorWrapper;
import com.ittera.cometa.distributor.returntypes.ExceptionWrapper;
import com.ittera.cometa.distributor.returntypes.RuntimeExceptionWrapper;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import java.util.Stack;


class MetodoConstructorMensaje extends ArgedMensaje implements MensajeEjecutable, ByteSerializable {
  public static byte MAGIC = 101;
  private int distributorID;
  private String nombreClaseSender;
  private Object sender;
  private String nombreClaseReceiver;

  MetodoConstructorMensaje(int distributor, String nombreClaseSender, Object sender, String nombreClaseReceiver,
    String firmaMetodo, Stack args) throws ExcepcionCreandoMensajeEjecutable {
    this.distributorID = distributor;
    this.nombreClaseSender = nombreClaseSender;
    this.sender = sender;
    this.firmaMetodo = firmaMetodo;

    if ((nombreClaseReceiver == null) || nombreClaseReceiver.isEmpty()) {
      throw new ExcepcionCreandoMensajeEjecutable("Nombre de la ClaseReceiver es null o <empty string>.");
    } else {
      this.nombreClaseReceiver = nombreClaseReceiver;
    }

    if (args == null) {
      throw new ExcepcionCreandoMensajeEjecutable("Par�metros = null.");
    } else {
      setParametros(args);
    }
  }

  public Object Ejecutar(java.lang.ClassLoader classLoader)
    throws ExcepcionEjecutandoMensaje {
    Object valor_devuelto = null;

    try {
      Constructor _Constructor = null;

      try {
        _Constructor = Class.forName(nombreClaseReceiver, true, classLoader).getConstructor(clasesParametros);
      } catch (NoSuchMethodException E) {
        _Constructor = Class.forName(nombreClaseReceiver, true, classLoader)
                            .getDeclaredConstructor(clasesParametros);
      }

      _Constructor.setAccessible(true);
      valor_devuelto = _Constructor.newInstance(parametros);
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

  public MensajeLigero toMensajeLigero() {
    MensajeLigero ml = new MensajeLigero();

    ml.DistributorID = this.distributorID;
    ml.MensajeEjecutableRef = 0;
    ml.NombreClaseSender = this.nombreClaseSender;
    ml.Sender = 0;
    ml.NombreClaseReceiver = this.nombreClaseReceiver;
    ml.Receiver = 0;
    ml.NombreMetodo = "new";
    ml.Parametros = 0;
    ml.FirmaMetodo = "";

    return ml;
  }
}
