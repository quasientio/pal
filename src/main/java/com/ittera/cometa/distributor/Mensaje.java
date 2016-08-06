package com.ittera.cometa.distributor;

import com.ittera.cometa.common.ByteSerializable;
import com.ittera.cometa.common.exceptions.ErrorConstituyendoMensaje;
import com.ittera.cometa.common.exceptions.ErrorReconstituyendoMensaje;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;


/**
 * <p>Title: </p>
 * <p>Description: </p>
 * <p>Copyright: Copyright (c) 2004</p>
 * <p>Company: </p>
 * @author not attributable
 * @version 1.0
 */
abstract public class Mensaje implements ByteSerializable {
  protected Logger logger = LogManager.getLogger(this.getClass());

  /**
   * fromBytes
   *
   * @param bytes byte[]
   */
  abstract public void fromBytes(byte[] bytes) throws ErrorReconstituyendoMensaje;

  /**
   * toBytes
   *
   * @return byte[]
   */
  abstract public byte[] toBytes() throws ErrorConstituyendoMensaje;
}
