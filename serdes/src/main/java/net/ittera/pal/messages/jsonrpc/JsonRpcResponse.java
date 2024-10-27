package net.ittera.pal.messages.jsonrpc;

import com.google.gson.annotations.SerializedName;

/**
 * Represents a JSON-RPC response message.
 *
 * <p>Message format:
 *
 * <pre>
 *   {
 *     "jsonrpc": "2.0",
 *     "result": "",
 *     "error": "",
 *     "id": 1
 *   }
 *   </pre>
 *
 * <p>The id field is an integer that is used to correlate the request with the response.
 *
 * <p>The result field will be filled iff there was no error executing the request.
 *
 * <p>The error field will be filled iff there was some error executing the request.
 *
 * <p>The jsonrpc field is always "2.0".
 */
public class JsonRpcResponse extends JsonRpcMessage {

  @SerializedName("jsonrpc")
  private String jsonrpc;

  @SerializedName("result")
  private JsonRpcResult result;

  @SerializedName("error")
  private JsonRpcError error;

  @SerializedName("id")
  private String id;

  public String getJsonrpc() {
    return jsonrpc;
  }

  public String getId() {
    return id;
  }

  public void setJsonrpc(String jsonrpc) {
    this.jsonrpc = jsonrpc;
  }

  public void setId(String id) {
    this.id = id;
  }

  public JsonRpcResult getResult() {
    return result;
  }

  public void setResult(JsonRpcResult result) {
    this.result = result;
  }

  public JsonRpcError getError() {
    return error;
  }

  public void setError(JsonRpcError error) {
    this.error = error;
  }

  @Override
  public String toString() {
    return toJson();
  }
}
