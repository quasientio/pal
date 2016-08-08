package com.ittera.cometa.distributor.messages;

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
abstract class ArgedMessage {
  protected static Logger logger = LogManager.getLogger("distributor");
  protected Class[] parameterClasses;
  protected Object[] parameters;

  protected void setParameters(Object[] args, Class[] types) {
    parameterClasses=types;
    parameters=args;
  }
}
