/*
 * FalloCreandoMensajeEjecutable.java
 *
 * Created on December 6, 2003, 11:02 AM
 */
package com.ittera.cometa.distributor;


/**
 *
 * @author  libre
 */
public class ExecutableMessageCreationException extends java.lang.Exception {
  /**
   * Creates a new instance of <code>FalloCreandoMensajeEjecutable</code> without detail message.
   */
  public ExecutableMessageCreationException() {
  }

  /**
   * Constructs an instance of <code>FalloCreandoMensajeEjecutable</code> with the specified detail message.
   * @param msg the detail message.
   */
  public ExecutableMessageCreationException(String msg) {
    super(msg);
  }
}
