package com.ittera.cometa.distributor.messages;

import com.ittera.cometa.common.exceptions.ErrorConstituyendoMensaje;
import com.ittera.cometa.common.exceptions.ErrorReconstituyendoMensaje;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;


public class ThinValueMessage extends Message {
  // FLAGS - no usado
  // static final short ES_PROTOCOLO;
  // short FLAGS;
  public final static byte MAGIC = 5;
  private int objetoValorRef;
  private boolean isNull;
  private String type = "not determined";

  public ThinValueMessage() {
  }

  public ThinValueMessage(int objetoValorRef) {
    this.objetoValorRef = objetoValorRef;
  }

  public void setNull() {
    isNull = true;
  }

  public boolean isNull() {
    return isNull;
  }

  public int getObjectRef() {
    return objetoValorRef;
  }

  public void setType(String type) {
    this.type = type;
  }

  public String getType() {
    return type;
  }

  public byte[] toBytes() throws ErrorConstituyendoMensaje {
    ByteArrayOutputStream tmp = new ByteArrayOutputStream();
    DataOutputStream out = new DataOutputStream(tmp);

    try {
      // constant-length
      out.writeByte(MAGIC);
      out.writeBoolean(isNull);
      out.writeInt(objetoValorRef);
      out.writeUTF(type);
    } catch (IOException ex) {
      logger.error("Error en ThinValueMessage::toBytes()", ex);
      throw new ErrorConstituyendoMensaje("No se ha podido reconstituir el mensaje: ver IOException.");
    }

    return tmp.toByteArray();
  }

  public void fromBytes(byte[] bytes) throws ErrorReconstituyendoMensaje {
    if (bytes == null) {
      throw new ErrorReconstituyendoMensaje("Byte source null");
    }

    ByteArrayInputStream tmp = new ByteArrayInputStream(bytes);
    DataInputStream in = new DataInputStream(tmp);

    try {
      in.readByte(); // read magic byte
      isNull = in.readBoolean();
      objetoValorRef = in.readInt();
      type = in.readUTF();
    } catch (Exception ex) {
      logger.error("Error en ThinValueMessage::fromBytes()", ex);
      throw new ErrorReconstituyendoMensaje("No se ha podido reconstituir el mensaje: ver IOException.");
    }
  }

  @Override
  public int hashCode() {
    int result = 10;
    result = (37 * result) + getObjectRef();
    result = (37 * result) + (isNull() ? 0 : 1);
    return result;
  }

  @Override
  public boolean equals(Object another) {
    if (another instanceof ThinValueMessage) {
      ThinValueMessage aMensaje = (ThinValueMessage) another;

      return ((aMensaje.getObjectRef() == getObjectRef()) && (aMensaje.isNull() == isNull()));
    }

    return false;
  }

  @Override
  public String toString() {
    String s;

    if (isNull) {
      s = "Valor es Null";
    } else {
      s = "Valor no es Null";
    }

    return ("\n" + "Message Valor Ligero\n" + "-----------------\n" + s + "\n" + "Type: " + type);
  }
}
