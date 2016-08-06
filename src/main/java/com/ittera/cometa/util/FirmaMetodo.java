package com.ittera.cometa.util;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import java.lang.reflect.Array;

import java.util.*;


public class FirmaMetodo {
  private static Logger logger = LogManager.getLogger("util.FirmaMetodo");
  private int pos = 0;
  private String firma;
  private String args;

  public FirmaMetodo(String firma) {
    this.firma = firma;
  }

  public Class[] parseTiposParametros() {
    int p1 = firma.indexOf('(');
    int p2 = firma.indexOf(')');

    firma = firma.replace('/', '.');

    args = firma.substring(p1 + 1, p2);

    ArrayList<Class> tipos = new ArrayList<Class>();
    int len = args.length();

    while (pos < len) {
      switch (args.charAt(pos)) {
        case ('Z'):
          tipos.add(boolean.class);
          pos++;
          break;
        case ('I'):
          tipos.add(int.class);
          pos++;
          break;
        case ('B'):
          tipos.add(byte.class);
          pos++;
          break;
        case ('C'):
          tipos.add(char.class);
          pos++;
          break;
        case ('J'):
          tipos.add(long.class);
          pos++;
          break;
        case ('D'):
          tipos.add(double.class);
          pos++;
          break;
        case ('F'):
          tipos.add(float.class);
          pos++;
          break;
        case ('S'):
          tipos.add(short.class);
          pos++;
          break;
        case ('L'): // object
          int newpos = args.indexOf(';', pos);
          try {
            tipos.add(Class.forName(args.substring(pos + 1, newpos)));
          } catch (Exception E) {
            E.printStackTrace(System.err);
            System.exit(1);
          }

          pos = newpos + 1;
          break;
        case ('['): // array
          int dim = 1;
          pos++;
          while (args.charAt(pos) == '[') {
            dim++;
            pos++;
          }

          int[] dimensiones = new int[dim];

          tipos.add(Array.newInstance(getArrayType(), dimensiones).getClass());
          pos++;
          break;
        default:
          break;
      }
    }

    // return (Class[]) tipos.toArray();

    // hay que hacerlo uno a uno
    int size = tipos.size();
    Class[] clases_parametros = new Class[size];

    for (int i = 0; i < size; i++) {
      clases_parametros[i] = tipos.get(i);
    }

    return clases_parametros;
  }

  private Class getArrayType() {
    switch (args.charAt(pos)) {
      case ('Z'):
        return (boolean.class);
      case ('I'):
        return (int.class);
      case ('B'):
        return (byte.class);
      case ('C'):
        return (char.class);
      case ('J'):
        return (long.class);
      case ('D'):
        return (double.class);
      case ('F'):
        return (float.class);
      case ('S'):
        return (short.class);
      case ('L'): // object
        int newpos = args.indexOf(';', pos);
        Class tipo = null;

        try {
          tipo = Class.forName(args.substring(pos + 1, newpos));
        } catch (Exception E) {
          E.printStackTrace(System.err);
          System.exit(1);
        }

        pos = newpos;
        return tipo;
      default:
        return null;
    }
  }

  public static void main(String[] args) {
    String unafirma = "(Ljava/lang/String;II[[Ljava/lang/Float;)V";

    FirmaMetodo m = new FirmaMetodo(unafirma);

    logger.debug("Parseando " + unafirma);
    Class[] tipos_clases = m.parseTiposParametros();

    for (int i = 0; i < tipos_clases.length; i++) {
      logger.debug(tipos_clases[i].getName());
    }
  }
}
