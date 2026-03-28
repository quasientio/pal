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

/**
 * Represents an exception that is thrown when the parameters of a JSON-RPC request are invalid.
 * This exception indicates that the provided parameters are either malformed or do not meet the
 * expected criteria.
 */
public class InvalidJsonRpcParamsException extends JsonRpcRequestException {

  /**
   * Constructs a new InvalidJsonRpcParamsException with the specified detail message and request
   * identifier.
   *
   * @param message the detail message explaining the reason for the exception
   * @param requestId the identifier of the JSON-RPC request that caused the exception
   */
  public InvalidJsonRpcParamsException(String message, String requestId) {
    super(message);
    this.requestId = requestId;
  }
}
