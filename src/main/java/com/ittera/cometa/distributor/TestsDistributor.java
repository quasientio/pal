package com.ittera.cometa.distributor;

import com.ittera.cometa.distributor.returntypes.ErrorWrapper;
import com.ittera.cometa.distributor.returntypes.ExceptionWrapper;
import com.ittera.cometa.distributor.returntypes.Null;
import com.ittera.cometa.distributor.returntypes.RuntimeExceptionWrapper;
import com.ittera.cometa.distributor.returntypes.Void;

import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;


/**
 *
 * @author libre
 */
public class TestsDistributor extends AbstractDistributor {
  protected Deque<Object> valoresRecibidos = new LinkedList<Object>();

  //static variables to keep track of instances of this class
  private static final Map<Integer,IDistributor> distributorInstances = new HashMap<Integer,IDistributor>();
  private static final AtomicInteger distributorIDCounter = new AtomicInteger();
  private ObjectTable objetos = new ObjectTable();

  @Override
  protected void sendExecutableMessage(MensajeEjecutable mensaje) {
    Object valor_devuelto = null;
    try {
      valor_devuelto = mensaje.Ejecutar(classLoader);
    } catch (ExcepcionEjecutandoMensaje ex) {
      logger.error("Error ejecutando mensaje", ex);
      System.exit(1);
    }

    if (valor_devuelto instanceof Void) {
      //we're done
      return;
    }

    if (valor_devuelto == null) {
      valoresRecibidos.add(new Null());
    } else if (valor_devuelto instanceof ExceptionWrapper) {
      logger.debug("exception wrapper returned");
      raisedExceptions.add((ExceptionWrapper) valor_devuelto);
    } else if (valor_devuelto instanceof ErrorWrapper) {
      throw new RuntimeException("Can't handle RuntimeException: ", ((ErrorWrapper) valor_devuelto).getError());
    } else if (valor_devuelto instanceof RuntimeExceptionWrapper) {
      throw new RuntimeException("Can't handle error: ",
        ((RuntimeExceptionWrapper) valor_devuelto).getRuntimeException());
    } else {
      valoresRecibidos.add(valor_devuelto);
    }
  }

  @Override
  protected Object getLastReturnedObject() {
    return valoresRecibidos.removeLast();
  }

  public static TestsDistributor getInstanceByID(int ID) {
    return (TestsDistributor) distributorInstances.get(ID);
  }

  public static TestsDistributor newInstance() {
    TestsDistributor newDistributor = new TestsDistributor();
    distributorInstances.put(distributorIDCounter.incrementAndGet(), newDistributor);
    newDistributor.setId(distributorIDCounter.get());
    return newDistributor;
  }
}
