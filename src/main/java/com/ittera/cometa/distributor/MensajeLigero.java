package com.ittera.cometa.distributor;

import com.ittera.cometa.common.exceptions.ErrorConstituyendoMensaje;
import com.ittera.cometa.common.exceptions.ErrorReconstituyendoMensaje;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;


public class MensajeLigero extends Mensaje {
  // FLAGS - no usado
  // static final short ES_PROTOCOLO;
  // short FLAGS;
  public final static byte MAGIC = 10;
  public int MensajeEjecutableRef;
  public int DistributorID;
  public String NombreClaseSender;
  public int Sender;
  public String NombreClaseReceiver;
  public int Receiver;
  public String NombreMetodo;
  public String FirmaMetodo;
  public int Parametros;

  public byte[] toBytes() throws ErrorConstituyendoMensaje {
    ByteArrayOutputStream tmp = new ByteArrayOutputStream();
    DataOutputStream out = new DataOutputStream(tmp);

    try {
      out.writeByte(MAGIC);
      out.writeInt(MensajeEjecutableRef);
      out.writeInt(DistributorID);
      out.writeUTF(NombreClaseSender);
      out.writeInt(Sender);
      out.writeUTF(NombreClaseReceiver);
      out.writeInt(Receiver);
      out.writeUTF(NombreMetodo);
      out.writeUTF(FirmaMetodo);
      out.writeInt(Parametros);
    } catch (IOException ex) {
      logger.error("Error en MensajeLigero::toBytes()", ex);
      throw new ErrorConstituyendoMensaje("No se ha podido constituir el mensaje. Ver IOException.");
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
      in.readByte(); // magic byte
      MensajeEjecutableRef = in.readInt();
      DistributorID = in.readInt();
      NombreClaseSender = in.readUTF();
      Sender = in.readInt();
      NombreClaseReceiver = in.readUTF();
      Receiver = in.readInt();
      NombreMetodo = in.readUTF();
      FirmaMetodo = in.readUTF();
      Parametros = in.readInt();
    } catch (Exception ex) {
      logger.error("Error en MensajeLigero::fromBytes()", ex);
      throw new ErrorReconstituyendoMensaje("No se ha podido reconstituir el mensaje: ver IOException.");
    }
  }

  @Override
  public int hashCode() {
    int result = 10;
    result = (37 * result) + MensajeEjecutableRef;
    result = (37 * result) + DistributorID;
    result = (37 * result) + NombreClaseSender.hashCode();
    result = (37 * result) + Sender;
    result = (37 * result) + NombreClaseReceiver.hashCode();
    result = (37 * result) + Receiver;
    result = (37 * result) + NombreMetodo.hashCode();
    result = (37 * result) + FirmaMetodo.hashCode();
    result = (37 * result) + Parametros;
    return result;
  }

  @Override
  public boolean equals(Object another) {
    if (another instanceof MensajeLigero) {
      MensajeLigero aMensaje = (MensajeLigero) another;

      return ((aMensaje.MensajeEjecutableRef == MensajeEjecutableRef) &&
      (aMensaje.DistributorID == DistributorID) && (aMensaje.NombreClaseSender.equals(NombreClaseSender)) &&
      (aMensaje.Sender == Sender) && (aMensaje.NombreClaseReceiver.equals(NombreClaseReceiver)) &&
      (aMensaje.Receiver == Receiver) && (aMensaje.NombreMetodo.equals(NombreMetodo)) &&
      (aMensaje.FirmaMetodo.equals(FirmaMetodo)) && (aMensaje.Parametros == Parametros));
    }

    return false;
  }

  @Override
  public String toString() {
    return ("\n" + "Mensaje Llamada Ligero\n" + "--------------------\n" + "Distributor:     " + DistributorID +
    "\n" + "ClaseSender:     " + new String(NombreClaseSender) + "\n" + "ClaseReceiver:   " +
    new String(NombreClaseReceiver) + "\n" + "Metodo:          " + new String(NombreMetodo) + "\n" +
    "Firma Metodo  :  " + new String(FirmaMetodo) + "\n");
  }
}
