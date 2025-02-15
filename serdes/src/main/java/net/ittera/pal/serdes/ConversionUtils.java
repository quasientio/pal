package net.ittera.pal.serdes;

import net.ittera.pal.messages.colfer.Obj;
import net.ittera.pal.messages.colfer.Reflectable;
import net.ittera.pal.messages.colfer.ReturnValue;
import net.ittera.pal.messages.jsonrpc.Executable;
import net.ittera.pal.messages.jsonrpc.JsonRpcResponseReturnValue;
import net.ittera.pal.messages.jsonrpc.ResponseObject;

/**
 * Provides utility methods for converting Colfer response types into formats suitable for inclusion
 * in JSON-RPC response messages.
 */
public class ConversionUtils {

  /**
   * Converts a {@link ReturnValue} instance into a {@link JsonRpcResponseReturnValue} suitable for
   * JSON-RPC responses.
   *
   * <p>This method maps the void status, source executable, and return value from the given {@code
   * returnValue} to a new {@code JsonRpcResponseReturnValue} object.
   *
   * @param returnValue the {@code ReturnValue} to convert; must not be {@code null}
   * @return a {@code JsonRpcResponseReturnValue} representing the converted return value
   * @throws NullPointerException if {@code returnValue} is {@code null}
   */
  public static JsonRpcResponseReturnValue toResponseReturnValue(ReturnValue returnValue) {
    JsonRpcResponseReturnValue jsonRpcResponseReturnValue = new JsonRpcResponseReturnValue();

    // set isVoid
    jsonRpcResponseReturnValue.setIsVoid(returnValue.getIsVoid());
    // set from
    if (returnValue.getFrom() != null) {
      Reflectable from = returnValue.getFrom();
      jsonRpcResponseReturnValue.setFrom(toJsonRpcFromExecutable(from));
    }

    // set value
    if (!returnValue.getIsVoid()) {
      jsonRpcResponseReturnValue.setValue(toResponseObject(returnValue.getObject()));
    }

    return jsonRpcResponseReturnValue;
  }

  /**
   * Converts an {@link Obj} instance into a {@link ResponseObject} for inclusion in JSON-RPC
   * responses.
   *
   * <p>This method extracts the class name, null status, value, and reference from the provided
   * {@code object} to populate a new {@code ResponseObject}.
   *
   * @param object the {@code Obj} to convert; must not be {@code null}
   * @return a {@code ResponseObject} representing the converted object
   */
  private static ResponseObject toResponseObject(Obj object) {
    ResponseObject responseObject = new ResponseObject();
    responseObject.setType(object.getClazz().getName());
    responseObject.setIsNull(object.getIsNull());
    responseObject.setValue(object.getValue());
    if (object.getRef() != null && !object.getRef().isEmpty()) {
      responseObject.setRef(Integer.parseInt(object.getRef()));
    }
    return responseObject;
  }

  /**
   * Converts a {@link Reflectable} instance into an {@link Executable} representing the source of a
   * JSON-RPC method call.
   *
   * <p>This method examines whether the {@code Reflectable} represents a constructor, method, or
   * field, and constructs an {@code Executable} with the corresponding class name, method name,
   * field name, and modifiers.
   *
   * @param fromReflectable the {@code Reflectable} to convert; must not be {@code null}
   * @return an {@code Executable} representing the converted source executable
   */
  public static Executable toJsonRpcFromExecutable(Reflectable fromReflectable) {
    String fromClassName;
    String fromField = "";
    String fromMethod = "";
    Integer modifiers = null;
    if (fromReflectable.getConstructor() != null
        && !fromReflectable.getConstructor().getClazz().getName().isEmpty()) {
      fromClassName = fromReflectable.getConstructor().getClazz().getName();
      fromMethod = "new";
    } else if (fromReflectable.getMethod() != null
        && !fromReflectable.getMethod().getClazz().getName().isEmpty()) {
      fromClassName = fromReflectable.getMethod().getClazz().getName();
      fromMethod = fromReflectable.getMethod().getName();
      modifiers = fromReflectable.getMethod().getModifiers();
    } else if (fromReflectable.getField() != null
        && !fromReflectable.getField().getClazz().getName().isEmpty()) {
      fromClassName = fromReflectable.getField().getClazz().getName();
      fromField = fromReflectable.getField().getName();
      modifiers = fromReflectable.getField().getModifiers();
    } else {
      fromClassName = null;
    }
    return new Executable.Builder()
        .withClassName(fromClassName)
        .withMethodName(fromMethod)
        .withFieldName(fromField)
        .withModifiers(modifiers)
        .build();
  }
}
