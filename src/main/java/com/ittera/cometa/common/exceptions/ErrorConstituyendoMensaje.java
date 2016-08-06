package com.ittera.cometa.common.exceptions;

import java.io.IOException;


/*
 * ErrorConstituyendoMensaje.java
 *
 * Created on December 6, 2003, 9:19 PM
 */

/**
 *
 * @author  libre
 */
public class ErrorConstituyendoMensaje extends IOException {
  private static final long serialVersionUID = 200406221015L;

  /**
   * Creates a new instance of <code>ErrorConstituyendoMensaje</code> without detail message.
   */
  public ErrorConstituyendoMensaje() {
  }

  /**
   * Constructs an instance of <code>ErrorConstituyendoMensaje</code> with the specified detail message.
   * @param msg the detail message.
   */
  public ErrorConstituyendoMensaje(String msg) {
    super(msg);
  }
}
