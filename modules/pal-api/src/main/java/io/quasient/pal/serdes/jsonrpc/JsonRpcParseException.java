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

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Represents an exception that occurs during JSON parsing in JSON-RPC requests. This exception
 * wraps the underlying parsing exception to provide flexibility in changing the underlying JSON
 * library in the future.
 *
 * @see JsonRpcRequestException
 */
@SuppressFBWarnings(
    value = {"EI_EXPOSE_REP", "EI_EXPOSE_REP2"},
    justification = "Exception wrapper - intentionally stores and exposes the original exception")
public class JsonRpcParseException extends JsonRpcRequestException {

  /** The exception that was thrown during JSON parsing. */
  private final Exception jsonParsingException;

  /**
   * Constructs a new JsonRpcParseException with the specified underlying parsing exception.
   *
   * @param e the exception that was thrown during JSON parsing
   */
  public JsonRpcParseException(Exception e) {
    super(e);
    this.jsonParsingException = e;
  }

  /**
   * Constructs a new JsonRpcParseException with the specified underlying parsing exception and
   * request ID.
   *
   * @param e the exception that was thrown during JSON parsing
   * @param requestId the ID of the JSON-RPC request associated with this exception
   */
  public JsonRpcParseException(Exception e, String requestId) {
    this(e);
    this.requestId = requestId;
  }

  /**
   * Returns the exception that was thrown during JSON parsing.
   *
   * @return the underlying JSON parsing exception
   */
  public Exception getJsonParsingException() {
    return jsonParsingException;
  }
}
