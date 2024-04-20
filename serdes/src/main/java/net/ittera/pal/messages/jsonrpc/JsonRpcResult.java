package net.ittera.pal.messages.jsonrpc;

import com.google.gson.annotations.SerializedName;
import net.ittera.pal.messages.colfer.InstanceFieldPutDone;
import net.ittera.pal.messages.colfer.ReturnValue;
import net.ittera.pal.messages.colfer.StaticFieldPutDone;
import net.ittera.pal.messages.types.JsonRpcResultType;

public class JsonRpcResult {

  @SerializedName("object")
  private final Object object;

  @SerializedName("type")
  private final JsonRpcResultType resultType;

  public JsonRpcResult(Object object) {
    this.object = object;
    if (object instanceof InstanceFieldPutDone) {
      this.resultType = JsonRpcResultType.INSTANCE_FIELDPUT_DONE;
    } else if (object instanceof StaticFieldPutDone) {
      this.resultType = JsonRpcResultType.STATIC_FIELDPUT_DONE;
    } else if (object instanceof ReturnValue) {
      this.resultType = JsonRpcResultType.RETURN_VALUE;
    } else {
      throw new IllegalArgumentException("Unsupported result type: " + object.getClass());
    }
  }

  public Object getObject() {
    return object;
  }

  public JsonRpcResultType getResultType() {
    return resultType;
  }
}
