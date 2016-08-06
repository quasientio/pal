package com.ittera.cometa.distributor;


/*
 * Esta clase sustituye la referencia a un objeto.
 * Resulta que para serializar un objeto Mensaje, no basta con que la clase Mensaje
 * implemente Serializable, tambien cada uno de sus campos ha de implementarlo.
 *
 * Como la Class a las que Sender y Receiver pertenecen son impredecibles, quedan dos soluciones:
 * 1 - al realizar la transformacion, se hace que cada clase implemente Serializable, recursivamente.
 *     Coste: lo que represente hacer cada clase de una aplicacion serializable, en terminos de eficiencia.
 * 2 - se guarda en el mensaje una referencia a la referencia del objeto. Usamos el hashCode
 *     de cada objeto. Asi, el mensaje incluye dos ints, en vez de dos objetos.
 *     Coste: mantener un HashTable en el Distributor que permita regenerar la referencia a partir del
 *     hashCode.
 *
 *     Como de momento hacemos la transformacion a mano, es mas facil la solucion 2.
 *     Tambien es mas eficiente serializar int's que Object's.
 *     Por otro lado, puede que mas adelante necesitamos hacer que todas las clases implementen
 *     Serializable, si queremos transmitir objetos entre Distributores que se encuentren en
 *     maquinas distintas.
 *
 */
public class ObjectRef implements java.io.Serializable {
  private Integer ObjectHashCode;

  ObjectRef(Integer hashCode) {
    ObjectHashCode = hashCode;
  }

  ObjectRef(int hashCode) {
    ObjectHashCode = new Integer(hashCode);
  }

  public int getInt() {
    return ObjectHashCode.intValue();
  }

  public Integer getInteger() {
    return ObjectHashCode;
  }
}
