package com.ittera.cometa.distributor.messages;

import com.ittera.cometa.util.MethodSignature;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.ittera.cometa.util.Primitive;

import java.util.Stack;


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
  protected String methodSignatureStr;
  protected Class[] parameterClasses;
  protected Object[] parameters;

  //keeping now for backwards compatibility with bcel-impl
  protected void setParameters(Stack args) {
    MethodSignature methodSignature = new MethodSignature(methodSignatureStr);

    parameterClasses = methodSignature.parseTiposParametros();

    int paramCount = parameterClasses.length;

    parameters = new Object[paramCount];

    for (int i = paramCount - 1; i >= 0; i--) {
      parameters[i] = args.pop();
      if (parameters[i] instanceof Primitive) {
        parameters[i] = ((Primitive) parameters[i]).cogerJavaWrapper();
      }
    }
  }

  protected void setParameters(Object[] args, Class[] types) {
    parameterClasses=types;
    parameters=args;
  }
}
