package net.ittera.pal.serdes;

import java.util.ArrayList;
import java.util.List;
import net.ittera.pal.messages.colfer.Obj;
import net.ittera.pal.messages.colfer.Reflectable;
import net.ittera.pal.messages.colfer.ReturnValue;
import net.ittera.pal.messages.jsonrpc.Executable;
import net.ittera.pal.messages.jsonrpc.JsonRpcResponseReturnValue;
import net.ittera.pal.messages.jsonrpc.ResponseObject;

public class ConversionUtils {

  /* The following methods serialize colfer-response types
   * to be included in Json-Rpc response messages */
  public static JsonRpcResponseReturnValue toResponseReturnValue(ReturnValue returnValue) {
    JsonRpcResponseReturnValue jsonRpcResponseReturnValue = new JsonRpcResponseReturnValue();

    // set isVoid
    jsonRpcResponseReturnValue.setIsVoid(returnValue.getIsVoid());
    // set from
    if (returnValue.getFrom() != null) {

      Reflectable from = returnValue.getFrom();
      String fromClassName;
      String fromField = "";
      String fromMethod = "";
      Integer modifiers = null;
      if (from.getConstructor() != null && !from.getConstructor().getClazz().getName().isEmpty()) {
        fromClassName = from.getConstructor().getClazz().getName();
      } else if (from.getMethod() != null && !from.getMethod().getClazz().getName().isEmpty()) {
        fromClassName = from.getMethod().getClazz().getName();
        fromMethod = from.getMethod().getName();
        modifiers = from.getMethod().getModifiers();
      } else if (from.getField() != null && !from.getField().getClazz().getName().isEmpty()) {
        fromClassName = from.getField().getClazz().getName();
        fromField = from.getField().getName();
        modifiers = from.getField().getModifiers();
      } else {
        fromClassName = null;
      }
      jsonRpcResponseReturnValue.setFrom(
          new Executable.Builder()
              .withClassName(fromClassName)
              .withMethodName(fromMethod)
              .withFieldName(fromField)
              .withModifiers(modifiers)
              .build());
    }

    // set value
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
