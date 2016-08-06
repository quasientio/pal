package com.ittera.cometa.distributor.messages;


/**
 * <p>Title: </p>
 * <p>Description: </p>
 * <p>Copyright: Copyright (c) 2004</p>
 * <p>Company: </p>
 * @author not attributable
 * @version 1.0
 */
import com.ittera.cometa.common.exceptions.*;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import java.io.*;


public class ExceptionMessage extends Message {
  private Logger logger = LogManager.getLogger(this.getClass());
  public String message;
  public int ObjetoValorRef;
  public final static byte MAGIC = 70;

  public ExceptionMessage() {
  }

  public ExceptionMessage(int ref) {
    ObjetoValorRef = ref;
  }

  /**
   * fromBytes
   *
   * @param bytes byte[]
   */
  public void fromBytes(byte[] bytes) throws ErrorReconstituyendoMensaje {
    if (bytes == null) {
      throw new ErrorReconstituyendoMensaje("Byte source null");
    }

    ByteArrayInputStream tmp = new ByteArrayInputStream(bytes);
    DataInputStream in = new DataInputStream(tmp);

    try {
      in.readByte();
      message = in.readUTF();
      ObjetoValorRef = in.readInt();
    } catch (Exception E) {
      System.err.println("Error en ExceptionMessage::fromBytes().");
      System.err.println(E.getMessage());
      throw new ErrorReconstituyendoMensaje("No se ha podido reconstituir el mensaje: ver IOException.");
    }
  }

  /**
   * toBytes
   *
   * @return byte[]
   */
  public byte[] toBytes() throws ErrorConstituyendoMensaje {
    ByteArrayOutputStream tmp = new ByteArrayOutputStream();
    DataOutputStream out = new DataOutputStream(tmp);

    try {
      // constant-length
      out.writeByte(MAGIC);
      out.writeUTF(message);
      out.writeInt(ObjetoValorRef);
    } catch (IOException E) {
      System.err.println("Error en ExceptionMessage::toBytes().");
      System.err.println(E.getMessage());
    }

    logger.debug("bytestream size:" + tmp.size());
    return tmp.toByteArray();
  }

  @Override
  public int hashCode() {
    int result = 10;
    result = (result * 37) + ObjetoValorRef;
    result = (result * 37) + message.hashCode();
    return result;
  }

  @Override
  public boolean equals(Object another) {
    if (another instanceof ExceptionMessage) {
      ExceptionMessage anotherMensajeException = (ExceptionMessage) another;

      return ((anotherMensajeException.ObjetoValorRef == this.ObjetoValorRef) &&
      (anotherMensajeException.message.equals(this.message)));
    }

    return false;
  }
}
