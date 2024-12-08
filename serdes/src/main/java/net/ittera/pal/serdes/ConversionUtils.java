package net.ittera.pal.serdes;

import java.util.ArrayList;
import java.util.List;
import net.ittera.pal.messages.colfer.Obj;
import net.ittera.pal.messages.colfer.ReturnValue;
import net.ittera.pal.messages.jsonrpc.JsonRpcResponseReturnValue;
import net.ittera.pal.messages.jsonrpc.ResponseObject;

public class ConversionUtils {

  /* The following methods serialize colfer-response types
   * to be included in Json-Rpc response messages */
  public static JsonRpcResponseReturnValue toResponseReturnValue(ReturnValue returnValue) {
    JsonRpcResponseReturnValue jsonRpcResponseReturnValue = new JsonRpcResponseReturnValue();
    jsonRpcResponseReturnValue.setIsVoid(returnValue.getIsVoid());
    if (!returnValue.getIsVoid()) {
      jsonRpcResponseReturnValue.setValue(toResponseObject(returnValue.getObject()));
    }
    return jsonRpcResponseReturnValue;
  }

  private static ResponseObject toResponseObject(Obj object) {
    ResponseObject responseObject = new ResponseObject();
    responseObject.setType(object.getClazz().getName());
    responseObject.setIsNull(object.getIsNull());
    responseObject.setValue(object.getValue());
    if (object.getRef() != null && !object.getRef().isEmpty()) {
      responseObject.setRef(Integer.parseInt(object.getRef()));
    }
    if (object.getArrayValues() != null && object.getArrayValues().length > 0) {
      List<ResponseObject> arrayValues = new ArrayList<>();
      for (Obj arrayValue : object.getArrayValues()) {
        arrayValues.add(toResponseObject(arrayValue));
      }
      responseObject.setArrayValues(arrayValues.toArray(new ResponseObject[0]));
    }
    return responseObject;
  }
}
