package com.quasient.pal.messages.jsonrpc;

import com.google.gson.annotations.JsonAdapter;
import com.quasient.pal.serdes.jsonrpc.JsonRpcMessageIdAdapter;

/**
 * Represents a generic JSON-RPC message with common fields and functionalities. This abstract class
 * serves as a base for specific JSON-RPC request and response messages, handling the JSON-RPC
 * protocol version and message identification.
 */
public abstract class JsonRpcMessage {
  /** The JSON-RPC protocol version. */
  public static final String JSON_RPC_VERSION = "2.0";

  /** The JSON-RPC protocol version for this message. */
  private String jsonrpc;

  /**
   * The identifier for the JSON-RPC message. This ID is used to match responses with requests and
   * is ensured to be a string or number.
   */
  @JsonAdapter(JsonRpcMessageIdAdapter.class) // ensure id is given as a string or number
  protected String id;

  /**
   * Retrieves the JSON-RPC protocol version of this message.
   *
   * @return the JSON-RPC version as a {@code String}.
   */
  public String getJsonrpc() {
    return jsonrpc;
  }

  /**
   * Sets the JSON-RPC protocol version for this message.
   *
   * @param jsonrpc the JSON-RPC version to set, typically "2.0".
   */
  public void setJsonrpc(String jsonrpc) {
    this.jsonrpc = jsonrpc;
  }

  /**
   * Retrieves the identifier of this JSON-RPC message.
   *
   * @return the message ID as a {@code String}.
   */
  public String getId() {
    return id;
  }

  /**
   * Sets the identifier for this JSON-RPC message.
   *
   * @param id the message ID to set as a {@code String}.
   */
  public void setId(String id) {
    this.id = id;
  }

  /**
   * Sets the identifier for this JSON-RPC message using a {@code long} value.
   *
   * @param id the message ID to set as a {@code long}, which will be converted to a {@code String}.
   */
  public void setId(long id) {
    this.id = String.valueOf(id);
  }

  /**
   * Sets the identifier for this JSON-RPC message using an {@code int} value.
   *
   * @param id the message ID to set as an {@code int}, which will be converted to a {@code String}.
   */
  public void setId(int id) {
    this.id = String.valueOf(id);
  }
}
