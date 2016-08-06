package com.ittera.cometa.common;

import com.ittera.cometa.common.exceptions.*;


/*
 * ByteSerializableMensaje.java
 *
 * Created on December 6, 2003, 9:02 PM
 */

/**
 *
 * @author  libre
 */
public interface ByteSerializable {
  public byte[] toBytes() throws ErrorConstituyendoMensaje;

  public void fromBytes(byte[] bytes) throws ErrorReconstituyendoMensaje;
}
