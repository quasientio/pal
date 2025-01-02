package net.ittera.pal.serdes.jsonrpc;

import net.ittera.pal.messages.jsonrpc.JsonRpcRequest;
import net.ittera.pal.messages.types.MessageFamily;
import net.ittera.pal.messages.types.MetaServiceType;

public class MetaMessageValidator {
  public static void validate(JsonRpcRequest request)
      throws InvalidJsonRpcRequestException, InvalidJsonRpcParamsException {
    // sanity check
    if (!request.getMethod().equalsIgnoreCase(MessageFamily.META.getJsonName())) {
      throw new IllegalArgumentException(
          "Invalid method name for Meta message: " + request.getMethod());
    }

    String requestId = request.getId();

    // Validate MetaMessage method is known and supported
    String metaMethodName = request.getParams().getMethod();
    if (metaMethodName == null || metaMethodName.isBlank()) {
      throw new InvalidJsonRpcParamsException(
          "Null or blank params:method for 'meta' request", requestId);
    }
    MetaServiceType metaServiceType = MetaServiceType.fromJsonName(metaMethodName);
    if (metaServiceType == null) {
      throw new InvalidJsonRpcParamsException(
          "Invalid or unsupported params:method for 'meta' request", requestId);
    }
  }
}
