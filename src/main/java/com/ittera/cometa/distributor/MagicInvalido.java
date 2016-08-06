/*
 * MagicInvalido.java
 *
 * Created on December 6, 2003, 6:37 PM
 */
package com.ittera.cometa.distributor;


/**
 *
 * @author  libre
 */
public class MagicInvalido extends java.lang.Exception {
  /**
   * Creates a new instance of <code>MagicInvalido</code> without detail message.
   */
  public MagicInvalido() {
  }

  /**
   * Constructs an instance of <code>MagicInvalido</code> with the specified detail message.
   * @param msg the detail message.
   */
  public MagicInvalido(String msg) {
    super(msg);
  }
}
