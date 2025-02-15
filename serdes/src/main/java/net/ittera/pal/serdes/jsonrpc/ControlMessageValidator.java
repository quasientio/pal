package net.ittera.pal.serdes.jsonrpc;

import net.ittera.pal.messages.jsonrpc.JsonRpcRequest;
import net.ittera.pal.messages.jsonrpc.Params;
import net.ittera.pal.messages.types.ControlCommandType;
import net.ittera.pal.messages.types.MessageFamily;

/**
 * Validates JSON-RPC control messages to ensure they adhere to the expected format and contain
 * supported commands.
 */
public class ControlMessageValidator {
  /**
   * Validates the provided JSON-RPC control message request.
   *
   * <p>Ensures that the request method corresponds to the control message family, verifies the
   * presence and validity of the command type, and checks for necessary arguments based on the
   * command type.
   *
   * @param request the JSON-RPC request to validate
   * @throws InvalidJsonRpcRequestException if the JSON-RPC request is invalid
   * @throws InvalidJsonRpcParamsException if the JSON-RPC request parameters are invalid
   * @throws IllegalArgumentException if the request method name does not correspond to the control
   *     message family
   */
  public static void validate(JsonRpcRequest request)
      throws InvalidJsonRpcRequestException, InvalidJsonRpcParamsException {
    // sanity check
    if (!request.getMethod().equalsIgnoreCase(MessageFamily.CONTROL.getJsonName())) {
      throw new IllegalArgumentException(
          "Invalid method name for Control message: " + request.getMethod());
    }

    String requestId = request.getId();

    // Validate ControlMessage method is known and supported
    Params params = request.getParams();
    String commandName = params.getMethod();
    if (commandName == null || commandName.isBlank()) {
      throw new InvalidJsonRpcParamsException(
          "Null or blank params:method for 'control' request", requestId);
    }
    ControlCommandType commandType = ControlCommandType.fromJsonName(commandName);
    if (commandType == null) {
      throw new InvalidJsonRpcParamsException(
          "Invalid or unsupported params:method for 'control' request", requestId);
    }
    // Validate args are given when required
    if (commandType.equals(ControlCommandType.DELETE_OBJECT)) {
      if (params.getArgs().isEmpty() || params.getArgs().get(0).getRef() == null) {
        throw new InvalidJsonRpcParamsException(
            String.format(
                "Missing object ref for %s request",
                ControlCommandType.DELETE_OBJECT.getJsonName()),
            requestId);
      }
    }
  }
}
