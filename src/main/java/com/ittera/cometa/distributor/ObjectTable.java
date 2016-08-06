package com.ittera.cometa.distributor;

import java.util.HashMap;
import java.util.Map;


public class ObjectTable extends HashMap<Integer,Object> {
  Map<Integer,ObjectRef> HelperTable; // mapea los hashCodes en ObjectRefs,usado por putObject

  public ObjectTable() {
    HelperTable = new HashMap<Integer,ObjectRef>();
  }

  /* Mete una nueva referencia a un objeto en el Hashtable*/
  public ObjectRef putObject(Object objeto) {
    if (objeto == null) {
      return null;
    }

    Integer key = new Integer(objeto.hashCode());

    if (this.containsKey(key)) {
      return (ObjectRef) HelperTable.get(key);
    }

    put(key, objeto);
    ObjectRef nuevaref = new ObjectRef(key);

    HelperTable.put(key, nuevaref);
    return nuevaref;
  }

  public Object extractObject(ObjectRef ref) {
    if (ref == null) {
      return null;
    } else {
      Integer key = ref.getInteger();
      Object objeto = get(key);

      remove(key); // esto puede dar algunos problemas para objetos que no son mensajes
      return objeto;
    }
  }
}
