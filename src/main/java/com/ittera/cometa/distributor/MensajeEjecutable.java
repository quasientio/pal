/*
 * MensajeEjecutable.java
 *
 * Created on December 20, 2003, 11:24 AM
 */
package com.ittera.cometa.distributor;


/**
 *
 * @author  libre
 */
public interface MensajeEjecutable {
  Object Ejecutar(java.lang.ClassLoader classLoader) throws ExcepcionEjecutandoMensaje;

  MensajeLigero toMensajeLigero();
}
