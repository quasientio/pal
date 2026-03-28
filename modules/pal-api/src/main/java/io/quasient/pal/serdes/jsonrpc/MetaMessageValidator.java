/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.quasient.pal.serdes.jsonrpc;

import io.quasient.pal.messages.jsonrpc.JsonRpcRequest;
import io.quasient.pal.messages.types.MessageFamily;
import io.quasient.pal.messages.types.MetaServiceType;

/**
 * Validates JSON-RPC requests representing meta messages.
 *
 * <p>Ensures that the request method is appropriate for meta operations and that the parameters are
 * correctly specified.
 */
public class MetaMessageValidator {

  /**
   * Validates the given {@link JsonRpcRequest} as a meta message.
   *
   * <p>This method checks that the request method corresponds to the 'meta' message family, ensures
   * that the {@code params.method} is non-null and supported by the {@link MetaServiceType}.
   *
   * @param request the JSON-RPC request to validate
   * @throws InvalidJsonRpcRequestException if the JSON-RPC request is invalid
   * @throws InvalidJsonRpcParamsException if the request parameters are invalid
   * @throws IllegalArgumentException if the request method does not belong to the 'meta' message
   *     family
   */
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
