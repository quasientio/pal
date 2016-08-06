/*
 * MensajeEjecutable.java
 *
 * Created on December 20, 2003, 11:24 AM
 */
package com.ittera.cometa.distributor.messages;


import com.ittera.cometa.distributor.ExcepcionEjecutandoMensaje;

/**
 *
 * @author  libre
 */
public interface ExecutableMessage {
  Object Ejecutar(java.lang.ClassLoader classLoader) throws ExcepcionEjecutandoMensaje;

  ThinMessage toMensajeLigero();
}
