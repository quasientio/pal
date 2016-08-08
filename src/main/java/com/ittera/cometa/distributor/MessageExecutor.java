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
    Object returnedVal = null;
    try {
      returnedVal = message.execute();
    } catch (MessageExecutionException ex) {
      throw new DistributorError("Error executing mensaje", ex);
    }

    if (returnedVal instanceof Void) {
      //we're done
      return;
    }

    if (returnedVal == null) {
      returnedValues.add(new Null());
    } else if (returnedVal instanceof ExceptionWrapper) {
      logger.debug("exception wrapper returned");
      raisedExceptions.add((ExceptionWrapper) returnedVal);
    } else if (returnedVal instanceof ErrorWrapper) {
      throw new RuntimeException("Can't handle RuntimeException: ", ((ErrorWrapper) returnedVal).getError());
    } else if (returnedVal instanceof RuntimeExceptionWrapper) {
      throw new RuntimeException("Can't handle error: ",
        ((RuntimeExceptionWrapper) returnedVal).getRuntimeException());
    } else {
      returnedValues.add(returnedVal);
    }
  }

  public static Object getLastReturnedObject() {
    Object value=returnedValues.removeLast();
    if (value instanceof Null) {
      return null;
    }
    return value;
  }
}
