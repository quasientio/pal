package com.ittera.cometa.distributor;


/*
 * MensajeNoEjecutable.java
 *
 * Created on December 6, 2003, 10:55 AM
 */

/**
 *
 * @author  libre
 */
public class ExcepcionEjecutandoMensaje extends java.lang.Exception {
  /**
   * Creates a new instance of <code>MensajeNoEjecutable</code> without detail message.
   */
  public ExcepcionEjecutandoMensaje() {
  }

  /**
   * Constructs an instance of <code>MensajeNoEjecutable</code> with the specified detail message.
   * @param msg the detail message.
   */
  public ExcepcionEjecutandoMensaje(String msg) {
    super(msg);
  }
}
