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

/** Exception thrown to indicate that a JSON-RPC request is invalid. */
public class InvalidJsonRpcRequestException extends JsonRpcRequestException {

  /**
   * Constructs a new {@code InvalidJsonRpcRequestException} with the specified detail message.
   *
   * @param message the detail message explaining the reason for the exception
   */
  public InvalidJsonRpcRequestException(String message) {
    super(message);
  }

  /**
   * Constructs a new {@code InvalidJsonRpcRequestException} with the specified detail message and
   * request ID.
   *
   * @param message the detail message explaining the reason for the exception
   * @param requestId the identifier of the JSON-RPC request that caused the exception
   */
  public InvalidJsonRpcRequestException(String message, String requestId) {
    super(message);
    this.requestId = requestId;
  }

  /**
   * Constructs a new {@code InvalidJsonRpcRequestException} with the specified cause.
   *
   * @param cause the underlying cause of the exception
   */
  public InvalidJsonRpcRequestException(Exception cause) {
    super(cause);
  }

  /**
   * Constructs a new {@code InvalidJsonRpcRequestException} with the specified cause and request
   * ID.
   *
   * @param cause the underlying cause of the exception
   * @param requestId the identifier of the JSON-RPC request that caused the exception
   */
  public InvalidJsonRpcRequestException(Exception cause, String requestId) {
    super(cause);
    this.requestId = requestId;
  }
}
