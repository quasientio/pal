package net.ittera.pal.serdes.colfer;

import net.ittera.pal.messages.colfer.Obj;
import net.ittera.pal.serdes.Unwrappable;

public class ObjUnwrappableAdapter implements Unwrappable {

  private final Obj obj;

  public ObjUnwrappableAdapter(Obj obj) {
    this.obj = obj;
  }

  @Override
  public boolean isNull() {
    return obj.getIsNull();
  }

  @Override
  public String getValue() {
    return obj.getValue();
  }

  @Override
  public String getType() {
    return obj.getClazz() != null ? obj.getClazz().getName() : null;
  }

  @Override
  public Integer getRef() {
    if (obj.getRef() == null || obj.getRef().isEmpty()) {
      return null;
    }
    return Integer.parseInt(obj.getRef());
  }
}
