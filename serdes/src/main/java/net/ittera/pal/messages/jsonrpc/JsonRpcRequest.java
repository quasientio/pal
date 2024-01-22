package net.ittera.pal.messages.jsonrpc;

import com.google.gson.annotations.SerializedName;
import java.util.Arrays;
import java.util.List;

public class JsonRpcRequest {
  @SerializedName("jsonrpc")
  private String jsonrpc;

  @SerializedName("method")
  private String method; // method is in the format "ClassName.methodName"

  @SerializedName("params")
  private List<JsonRpcParameter> params;

  @SerializedName("id")
  private int id;

  public String getJsonrpc() {
    return jsonrpc;
  }

  public String getMethod() {
    return method;
  }

  public List<JsonRpcParameter> getParams() {
    return params;
  }

  public int getId() {
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

  public void setId(int id) {
    this.id = id;
  }

  // Helper methods
  public String getFullyQualifiedClassName() {
    String[] parts = method.split("\\.");

    if (isConstructorCall()) {
      // For constructor calls, the class name would be the entire string except the last part if
      // it's 'new'
      int endIndex = parts[parts.length - 1].equals("new") ? parts.length - 1 : parts.length;
      return String.join(".", Arrays.copyOfRange(parts, 0, endIndex));
    } else {
      // For method calls, check if an object reference (numeric) is present
      int endIndex = isNumeric(parts[parts.length - 2]) ? parts.length - 2 : parts.length - 1;
      return String.join(".", Arrays.copyOfRange(parts, 0, endIndex));
    }
  }

  public boolean isConstructorCall() {
    String[] parts = method.split("\\.");
    String lastPart = parts[parts.length - 1];
    return lastPart.equals("new")
        || (lastPart.length() > 0 && Character.isUpperCase(lastPart.charAt(0)));
  }

  public String getMethodName() {
    if (isConstructorCall()) {
      return "new"; // Return 'new' for constructor calls
    } else {
      String[] parts = method.split("\\.");
      return parts[parts.length - 1]; // The method name is the last part
    }
  }
  /**
   * public String getFullyQualifiedClassName() { String[] parts = method.split("\\."); int endIndex
   * = parts.length - 1; // If objectRef is present, it will be the second last part if
   * (isNumeric(parts[endIndex - 1])) { endIndex--; } return String.join(".",
   * Arrays.copyOfRange(parts, 0, endIndex)); } public String getMethodName() { String[] parts =
   * method.split("\\."); return parts[parts.length - 1]; }
   */
  public String getClassName() {
    String[] parts = getFullyQualifiedClassName().split("\\.");
    return parts[parts.length - 1];
  }

  public String getObjectRef() {
    String[] parts = method.split("\\.");
    if (parts.length > 2 && isNumeric(parts[parts.length - 2])) {
      return parts[parts.length - 2];
    }
    return null;
  }

  private boolean isNumeric(String str) {
    try {
      Double.parseDouble(str);
      return true;
    } catch (NumberFormatException e) {
      return false;
    }
  }
}
