package net.ittera.pal.messages.jsonrpc;

import com.google.gson.annotations.SerializedName;
import java.util.Arrays;
import java.util.List;
import net.ittera.pal.messages.types.ExecMessageType;

/**
 * Represents a JSON-RPC request message.
 *
 * <p>Message format:
 *
 * <pre>
 *   {
 *     "jsonrpc": "2.0",
 *     "method": "net.ittera.pal.core.exec.PeerMessageInvoker.1234.getPeerUuid",
 *     "params": [],
 *     "id": 1
 *   }
 *   </pre>
 *
 * <p>For the method field, the following formats are supported:
 *
 * <ul>
 *   <li>The format "ClassName.methodName" indicates a static method call.
 *   <li>The format "ClassName.1234.methodName" indicates an instance method call.
 *   <li>The format "new:ClassName" indicates a constructor call.
 *   <li>The format "get:ClassName.fieldName" indicates a static field get.
 *   <li>The format "put:ClassName.fieldName" indicates a static field put.
 *   <li>The format "get:ClassName.1234.fieldName" indicates an instance field get.
 *   <li>The format "put:ClassName.1234.fieldName" indicates an instance field put.
 * </ul>
 *
 * <p>The params field is an array of parameters.
 *
 * <p>The id field is an integer that is used to correlate the request with the response.
 *
 * <p>The jsonrpc field is always "2.0".
 *
 * <p>The following table shows the mapping between the method field and the ExecMessageType enum:
 *
 * <pre>
 * <p>
 * <table>
 * <tr>
 * <th>Method field</th>
 * <th>ExecMessageType</th>
 * </tr>
 *
 * <tr>
 * <td>new:ClassName</td>
 * <td>CONSTRUCTOR</td>
 * </tr>
 * <tr>
 * <td>ClassName.methodName</td>
 * <td>CLASS_METHOD</td>
 * </tr>
 * <tr>
 * <td>ClassName.1234.methodName</td>
 * <td>INSTANCE_METHOD</td>
 * </tr>
 * <tr>
 * <td>get:ClassName.fieldName</td>
 * <td>GET_STATIC</td>
 * </tr>
 * <tr>
 * <td>get:ClassName.1234.fieldName</td>
 * <td>GET_INSTANCE_FIELD</td>
 * </tr>
 *
 * <tr>
 * <td>put:ClassName.fieldName</td>
 * <td>PUT_STATIC</td>
 * </tr>
 * <tr>
 * <td>put:ClassName.1234.fieldName</td>
 * <td>PUT_INSTANCE_FIELD</td>
 * </tr>
 * </table>
 * <p>
 * </pre>
 */
public class JsonRpcRequest {
  private ExecMessageType execMessageType;
  private String objectRef;
  private String className;
  private String fullyQualifiedClassName;
  private String methodName;
  private String fieldName;

  @SerializedName("jsonrpc")
  private String jsonrpc;

  @SerializedName("method")
  private String method;

  @SerializedName("params")
  private List<JsonRpcParameter> params;

  @SerializedName("id")
  private String id;

  public String getJsonrpc() {
    return jsonrpc;
  }

  public String getMethod() {
    return method;
  }

  public List<JsonRpcParameter> getParams() {
    return params;
  }

  public String getId() {
    return id;
  }

  public void setJsonrpc(String jsonrpc) {
    this.jsonrpc = jsonrpc;
  }

  public void setMethod(String method) {
    this.method = method;
  }

  public void setParams(List<JsonRpcParameter> params) {
    this.params = params;
  }

  public void setId(String id) {
    this.id = id;
  }

  // Helper methods
  public String getFullyQualifiedClassName() {
    return fullyQualifiedClassName;
  }

  public String getMethodName() {
    return methodName;
  }

  public String getFieldName() {
    return fieldName;
  }

  public String getClassName() {
    return className;
  }

  public String getObjectRef() {
    return objectRef;
  }

  private static boolean isNumeric(String str) {
    try {
      Integer.parseInt(str);
      return true;
    } catch (NumberFormatException e) {
      return false;
    }
  }

  public ExecMessageType getExecMessageType() {
    return execMessageType;
  }

  public void processMethodParts() {
    final String[] parts = method.split("\\.");

    // set objectRef
    this.objectRef =
        parts.length > 2 && isNumeric(parts[parts.length - 2]) ? parts[parts.length - 2] : null;

    // set execMessageType and other fields
    if (parts[0].startsWith("new:")) {
      execMessageType = ExecMessageType.CONSTRUCTOR;
      parts[0] = parts[0].split(":")[1]; // Remove 'new'
      className = parts[parts.length - 1];
      fullyQualifiedClassName = String.join(".", Arrays.copyOfRange(parts, 0, parts.length));
    } else if (parts[0].startsWith("get:")) {
      parts[0] = parts[0].split(":")[1]; // Remove 'get'
      fieldName = parts[parts.length - 1];
      if (objectRef != null) {
        execMessageType = ExecMessageType.GET_FIELD;
        className = parts[parts.length - 3];
        fullyQualifiedClassName = String.join(".", Arrays.copyOfRange(parts, 0, parts.length - 2));
      } else {
        execMessageType = ExecMessageType.GET_STATIC;
        className = parts[parts.length - 2];
        fullyQualifiedClassName = String.join(".", Arrays.copyOfRange(parts, 0, parts.length - 1));
      }
    } else if (parts[0].startsWith("put:")) {
      parts[0] = parts[0].split(":")[1]; // Remove 'put'
      fieldName = parts[parts.length - 1];
      if (objectRef != null) {
        execMessageType = ExecMessageType.PUT_FIELD;
        className = parts[parts.length - 3];
        fullyQualifiedClassName = String.join(".", Arrays.copyOfRange(parts, 0, parts.length - 2));
      } else {
        execMessageType = ExecMessageType.PUT_STATIC;
        className = parts[parts.length - 2];
        fullyQualifiedClassName = String.join(".", Arrays.copyOfRange(parts, 0, parts.length - 1));
      }
    } else {
      methodName = parts[parts.length - 1];
      if (objectRef != null) {
        execMessageType = ExecMessageType.INSTANCE_METHOD;
        className = parts[parts.length - 3];
        fullyQualifiedClassName = String.join(".", Arrays.copyOfRange(parts, 0, parts.length - 2));
      } else {
        execMessageType = ExecMessageType.CLASS_METHOD;
        className = parts[parts.length - 2];
        fullyQualifiedClassName = String.join(".", Arrays.copyOfRange(parts, 0, parts.length - 1));
      }
    }
  }
}
