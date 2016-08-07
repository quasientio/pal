package com.ittera.cometa.distributor;

import com.ittera.cometa.distributor.messages.ExecutableMessage;
import com.ittera.cometa.distributor.returntypes.ErrorWrapper;
import com.ittera.cometa.distributor.returntypes.ExceptionWrapper;
import com.ittera.cometa.distributor.returntypes.Null;
import com.ittera.cometa.distributor.returntypes.RuntimeExceptionWrapper;
import com.ittera.cometa.distributor.returntypes.Void;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import java.util.Deque;
import java.util.LinkedList;

/**
 * Created by libre on 8/7/16.
 */
public class MessageExecutor {
  protected static Logger logger = LogManager.getLogger("distributor");

  protected static Deque<Object> returnedValues = new LinkedList<Object>();
  protected static Deque<ExceptionWrapper> raisedExceptions = new LinkedList<>();

  public static void sendExecutableMessage(ExecutableMessage message) {
    Object valor_devuelto = null;
    try {
      valor_devuelto = message.execute();
    } catch (MessageExecutionException ex) {
      throw new DistributorError("Error ejecutando mensaje", ex);
    }

    if (valor_devuelto instanceof Void) {
      //we're done
      return;
    }

    if (valor_devuelto == null) {
      returnedValues.add(new Null());
    } else if (valor_devuelto instanceof ExceptionWrapper) {
      logger.debug("exception wrapper returned");
      raisedExceptions.add((ExceptionWrapper) valor_devuelto);
    } else if (valor_devuelto instanceof ErrorWrapper) {
      throw new RuntimeException("Can't handle RuntimeException: ", ((ErrorWrapper) valor_devuelto).getError());
    } else if (valor_devuelto instanceof RuntimeExceptionWrapper) {
      throw new RuntimeException("Can't handle error: ",
        ((RuntimeExceptionWrapper) valor_devuelto).getRuntimeException());
    } else {
      returnedValues.add(valor_devuelto);
    }
  }

  public static Object getLastReturnedObject() {
    return returnedValues.removeLast();
  }
}
