package net.ittera.pal.serdes.jsonrpc;

/** Represents an exception that occurs during JSON serialization or deserialization processes. */
public class JsonSerializationException extends Exception {

  /**
   * Constructs a new JsonSerializationException with the specified detail message and cause.
   *
   * @param message the detail message explaining the reason for the exception
   * @param cause the underlying cause of the exception
   */
  public JsonSerializationException(String message, Throwable cause) {
    super(message, cause);
  }
}
