package net.ittera.pal.messages.jsonrpc;

import com.google.gson.annotations.SerializedName;
import net.ittera.pal.messages.types.ExecMessageType;
import net.ittera.pal.serdes.jsonrpc.JsonRpcError;

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
public class JsonRpcResponse {
  private ExecMessageType execMessageType;

  @SerializedName("jsonrpc")
  private String jsonrpc;

  @SerializedName("result")
  private String result;

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

  public String getResult() {
    return result;
  }

  public void setResult(String result) {
    this.result = result;
  }

  public JsonRpcError getError() {
    return error;
  }

  public void setError(JsonRpcError error) {
    this.error = error;
  }
}
