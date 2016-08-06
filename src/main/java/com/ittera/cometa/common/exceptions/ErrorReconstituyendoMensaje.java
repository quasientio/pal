package com.ittera.cometa.common.exceptions;

import java.io.IOException;


/*
 * ErrorReconstituyendoMensaje.java
 *
 * Created on December 6, 2003, 8:20 PM
 */

/**
 *
 * @author  libre
 */
public class ErrorReconstituyendoMensaje extends IOException {
  private static final long serialVersionUID = 200406221016L;

  /**
   * Creates a new instance of <code>ErrorReconstituyendoMensaje</code> without detail message.
   */
  public ErrorReconstituyendoMensaje() {
  }

  /**
   * Constructs an instance of <code>ErrorReconstituyendoMensaje</code> with the specified detail message.
   * @param msg the detail message.
   */
  public ErrorReconstituyendoMensaje(String msg) {
    super(msg);
  }
}
