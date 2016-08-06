package com.ittera.cometa.distributor;

import com.ittera.cometa.common.ByteSerializable;
import com.ittera.cometa.common.exceptions.ErrorReconstituyendoMensaje;

import com.ittera.cometa.distributor.returntypes.ErrorWrapper;
import com.ittera.cometa.distributor.returntypes.ExceptionWrapper;
import com.ittera.cometa.distributor.returntypes.Null;
import com.ittera.cometa.distributor.returntypes.RuntimeExceptionWrapper;
import com.ittera.cometa.distributor.returntypes.Void;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

import java.net.Socket;

import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;


/**
 *
 * <p>Descripcion: </p>
 * <p>Copyright: Copyright (c) 2005</p>
 * <p>Company: Jampion.org</p>
 * <p>@author Manuel Martinez</p>
 * @todo Two-way communication with the Cell
 */
public class SocketDistributor extends AbstractDistributor {
  /* CONSTANTES */
  public static final byte bye = 50;
  private static final short Puerto = 3313;
  public static final short MAXBEFOREWARNING = 1000;
  public static final byte LOOPWARNING = 25;
  public static final byte ABORT = 37;
  public static final byte CONTINUE = 73;
  public static final byte EJECUTA = 1;

  /* VARIABLES DE CONEXION A LA RED DE MENSAJERIA*/
  private Socket conexionRed;
  private DataInputStream bytesIn;
  private DataOutputStream bytesOut;
  private MensajeLigero ultimoMensajeEnviado;
  private MensajeEjecutable ultimoMensajeAEjecutar;

  // VARIABLE QUE GUARDA EL ULTIMO VALOR HASTA QUE ES SOLICITADO LLAMANDO A getReturnedXXX()
  protected Deque<Object> valoresRecibidos = new LinkedList<Object>();

  /* HASH TABLE DE TODOS LOS OBJETOS QUE PASAN POR LOS MENSAJES */
  private ObjectTable objetos = new ObjectTable();

  //loggers
  private static Logger stLogger = LogManager.getLogger(SocketDistributor.class);

  //static variables to keep track of instances of this class
  private static final Map<Integer,IDistributor> distributorInstances = new HashMap<Integer,IDistributor>();
  private static final AtomicInteger distributorIDCounter = new AtomicInteger();

  // <editor-fold defaultstate="collapsed" desc="METODOS INTERNOS">

  /** Mensajes de gestion.
   * @param MensajeInterno Identificador de mensaje.
   */
  private void EnviarMensaje(byte MensajeInterno) {
    try {
      bytesOut.writeByte(MensajeInterno);
    } catch (Exception ex) {
      logger.error("Error escribiendo byte a socket", ex);
      System.exit(0);
    }

    // recibir respuesta
    byte respuesta = 0;

    try {
      respuesta = bytesIn.readByte();
    } catch (IOException ex) {
      logger.error("Error leyendo byte de socket", ex);
      System.exit(0);
    }

    if (respuesta == ABORT) {
      // notificar al cells que hemos acabado
      try {
        bytesOut.writeByte(bye);
      } catch (Exception ex) {
        logger.debug("Error enviando Bye byte", ex);
      }

      logger.info("El proceso se aborta por exceso de iteraciones de un bucle.");
      System.exit(0);
    }

    // deberia ver si la respuesta es CONTINUE
  }

  protected void sendExecutableMessage(MensajeEjecutable mensaje) {
    ultimoMensajeAEjecutar = mensaje;

    // Ahora creamos el mensaje ligero, con una referencia al MensajeEjecutable
    MensajeLigero mensajeLigero = mensaje.toMensajeLigero();

    // Y se envia a la red
    Enviar(mensajeLigero);

    // Lo guardamos, pero no en la tabla de objetos
    ultimoMensajeEnviado = mensajeLigero;

    Recibir();
  }

  /* Envia un mensaje ligero a la red*/
  private void Enviar(MensajeLigero mensaje) {
    try {
      bytesOut.write(mensaje.toBytes());
    } catch (Exception ex) {
      logger.error("Error writing bytes to socket", ex);
      System.exit(0);
    }
  }

  /* Recibe y procesa cualquier mensaje de la red*/
  private void Recibir() {
    // Recibir de la red el siguiente mensaje
    Object mensaje = null;

    try {
      mensaje = RecibirDeRed();
    } catch (Exception ex) {
      logger.error("Error recibiendo mensaje de red", ex);
      if ((ex instanceof MagicInvalido) || (ex instanceof ErrorReconstituyendoMensaje)) {
        System.exit(0);
      }
    }

    logger.debug("mensaje recibido");

    // si es mensaje de llamada, ejecutar
    if (mensaje instanceof MensajeLigero) {
      MensajeLigero mensajeLigero = (MensajeLigero) mensaje;

      EjecutarMensaje(mensajeLigero);
    } // si es un mensaje de valor, guardarlo
    else if (mensaje instanceof MensajeValorLigero) {
      MensajeValorLigero mensajeValor = (MensajeValorLigero) mensaje;

      if (mensajeValor.isNull()) {
        valoresRecibidos.add(new Null());
      } else {
        valoresRecibidos.add(objetos.extractObject(new ObjectRef(mensajeValor.getObjectRef())));
      }
    }
    // si es un mensaje de excepcion, guardarlo
    else if (mensaje instanceof MensajeException) {
      MensajeException mensajeException = (MensajeException) mensaje;

      raisedExceptions.add((ExceptionWrapper) objetos.extractObject(new ObjectRef(mensajeException.ObjetoValorRef)));
    }
  }

  private Object RecibirDeRed() throws IOException, MagicInvalido {
    byte magic;

    try {
      magic = (byte) bytesIn.read();
    } catch (IOException ex) {
      logger.error("IO error", ex);
      throw (ex);
    }

    if (magic == EJECUTA) {
      return ultimoMensajeEnviado;
    }

    ByteSerializable mensaje;

    if (magic == MensajeLigero.MAGIC) {
      mensaje = new MensajeLigero();
    } else if (magic == MensajeValorLigero.MAGIC) {
      mensaje = new MensajeValorLigero();
    } else if (magic == MensajeException.MAGIC) {
      mensaje = new MensajeException();
    } else {
      throw new MagicInvalido("Magic byte no es correcto. Valor=" + magic);
    }

    int bytes_available = bytesIn.available();

    if (bytes_available == 0) {
      throw (new IOException("0 bytes available."));
    }

    byte[] bytes = new byte[bytes_available];
    int read = bytesIn.read(bytes);

    if (read != bytes_available) {
      throw new IOException("Se han leido menos bytes de los que habia disponible");
    }

    byte[] allBytes = new byte[bytes.length + 1];

    allBytes[0] = magic;
    System.arraycopy(bytes, 0, allBytes, 1, bytes.length);
    try {
      mensaje.fromBytes(allBytes);
    } catch (ErrorReconstituyendoMensaje ex) {
      logger.error("Error reconstituyendo mensaje", ex);
      System.exit(0);
    }

    return mensaje;
  }

  /* Ejecuta un mensaje llamada recibido de la red, devolviendo el valor de la llamada a la red*/
  private void EjecutarMensaje(MensajeLigero llamada) {
    MensajeEjecutable mensajejecutable = ultimoMensajeAEjecutar;
    Object valor_devuelto = null;

    try {
      valor_devuelto = mensajejecutable.Ejecutar(classLoader);
    } catch (ExcepcionEjecutandoMensaje ex) {
      logger.error("Error ejecutando mensaje", ex);
      System.exit(1);
    }

    // Ahora creamos el MensajeValor con la referencia al objeto devuelto por la llamada
    Mensaje mensaje;

    if (valor_devuelto instanceof Void) {
      return;
    }

    if (valor_devuelto == null) {
      mensaje = new MensajeValorLigero();
      ((MensajeValorLigero) mensaje).setNull();
      ((MensajeValorLigero) mensaje).setType("unknown");
    } else if (valor_devuelto instanceof ExceptionWrapper) {
      logger.debug("exception wrapper returned");
      mensaje = new MensajeException(objetos.putObject(valor_devuelto).getInt());
      ((MensajeException) mensaje).message = ((ExceptionWrapper) valor_devuelto).toString();
    } else if (valor_devuelto instanceof ErrorWrapper) {
      throw new RuntimeException("Can't handle RuntimeException: ", ((ErrorWrapper) valor_devuelto).getError());
    } else if (valor_devuelto instanceof RuntimeExceptionWrapper) {
      throw new RuntimeException("Can't handle error: ",
        ((RuntimeExceptionWrapper) valor_devuelto).getRuntimeException());
    } else {
      mensaje = new MensajeValorLigero(objetos.putObject(valor_devuelto).getInt());
      ((MensajeValorLigero) mensaje).setType(valor_devuelto.getClass().getName());
    }

    // enviar mensaje aqui
    try {
      bytesOut.write(mensaje.toBytes());
    } catch (Exception ex) {
      logger.error("Error trying to send message to cells center", ex);
      System.exit(0);
    }

    Recibir();
  }

  @Override
  protected Object getLastReturnedObject() {
    return valoresRecibidos.removeLast();
  }

  public static SocketDistributor getInstanceByID(int ID) {
    return (SocketDistributor) distributorInstances.get(ID);
  }

  public static SocketDistributor newInstance() {
    SocketDistributor newDistributor = new SocketDistributor();
    distributorInstances.put(distributorIDCounter.incrementAndGet(), newDistributor);
    newDistributor.setId(distributorIDCounter.get());
    return newDistributor;
  }

  /******************************* MAIN ******************************/
  /*
   * @param args String[0] : nombre de la clase con el main a ejecutar.
   * String[1] a String[args.length-1] : parametros al main de la clase a ejecutar.
   */
  public static void main(String[] args) {
    if (args.length == 0) {
      return;
    }

    SocketDistributor distributor = newInstance();
    int distributorID = distributor.getId();

    String nombreAplicacion = args[0];

    String[] argv = new String[args.length - 1];

    System.arraycopy(args, 1, argv, 0, argv.length);

    String[] packagesToIgnore = { "distributor.", "util.", "org.apache.bcel." };

    /**
    TransformationOptions options = new TransformationOptions();
    options.setDebug(false);
    options.setWrite(false);
    options.setOnlyLevel1(true);
    options.setDistributorClassname("distributor.SocketDistributor");
    options.setDistributorId(distributor.getId());
    distributor.setClassLoader(new ClassLoader(packagesToIgnore, options));
    */

    Class<? > clase = null;

    try {
      clase = distributor.classLoader.loadClass(nombreAplicacion);
    } catch (ClassNotFoundException ex) {
      stLogger.error(ex.getStackTrace());
      System.exit(0);
    }

    Method method = null;

    try {
      method = clase.getDeclaredMethod("main", new Class[] { argv.getClass() });
    } catch (NoSuchMethodException no) {
      stLogger.error("In class " + nombreAplicacion + ": public static void main(String[] argv) is not defined");
      return;
    } catch (SecurityException se) {
      stLogger.error("Security Exception raised accessing 'main' method: " + se);
      return;
    }

    /* Method main is sane ? */
    int m = method.getModifiers();
    Class r = method.getReturnType();

    if (!Modifier.isStatic(m) || Modifier.isAbstract(m) || (r != java.lang.Void.TYPE)) {
      throw new RuntimeException("Method 'main' is either non-static, abstract or not void");
    }

    try {
      distributor.conexionRed = new Socket("localhost", Puerto);
      distributor.bytesIn = new DataInputStream(distributor.conexionRed.getInputStream());
      distributor.bytesOut = new DataOutputStream(distributor.conexionRed.getOutputStream());
    } catch (IOException E) {
      stLogger.error(E);
      return;
    }

    try {
      method.setAccessible(true);
      method.invoke(null, new Object[] { argv });
    } catch (Throwable ex) {
      stLogger.error("Error/Exception raised in Distributor.main:");
      stLogger.error(ex.getStackTrace());
      stLogger.error("Cause:" + ex.getCause());
      stLogger.error(ex.getCause().getStackTrace());
    }

    // notificar al cells que hemos acabado
    try {
      distributor.bytesOut.writeByte(bye);
    } catch (Exception E) {
      stLogger.error(E + " enviando Bye byte");
    }

    // cerrar conexion al Tubo de mensajes
    try { // bytesIn.close();
          // bytesOut.close();
          // ConexionRed.close();
    } catch (Exception E) {
      stLogger.error(E);
    }
  }
}
