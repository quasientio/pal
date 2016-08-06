package com.ittera.cometa.distributor.messages;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.ittera.cometa.util.FirmaMetodo;
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
  protected Logger logger = LogManager.getLogger(this.getClass());
  protected String firmaMetodo;
  protected Class[] clasesParametros;
  protected Object[] parametros;

  protected void setParametros(Stack args) {
    FirmaMetodo firma = new FirmaMetodo(firmaMetodo);

    clasesParametros = firma.parseTiposParametros();

    int paramCount = clasesParametros.length;

    parametros = new Object[paramCount];

    for (int i = paramCount - 1; i >= 0; i--) {
      parametros[i] = args.pop();
      if (parametros[i] instanceof Primitive) {
        parametros[i] = ((Primitive) parametros[i]).cogerJavaWrapper();
      }
    }
  }
}
