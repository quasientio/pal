/*
 * MensajeEjecutable.java
 *
 * Created on December 20, 2003, 11:24 AM
 */
package com.ittera.cometa.distributor.messages;


import com.ittera.cometa.distributor.MessageExecutionException;

/**
 *
 * @author  libre
 */
public interface ExecutableMessage {
  Object execute() throws MessageExecutionException;

  ThinMessage toMensajeLigero();
}
