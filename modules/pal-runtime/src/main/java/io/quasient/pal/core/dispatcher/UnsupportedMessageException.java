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
package io.quasient.pal.core.dispatcher;

/**
 * Signals that a provided message type is not supported in the current invocation context.
 *
 * <p>This exception extends {@link RuntimeException} and is thrown when an attempt is made to
 * process a message that is not recognized or supported by the Pal runtime.
 */
public class UnsupportedMessageException extends RuntimeException {

  /**
   * Constructs a new UnsupportedMessageException with the specified detail message.
   *
   * <p>The message parameter should provide details about the unsupported message type or cause.
   *
   * @param message the detail message identifying the unsupported message. May include context for
   *     debugging.
   */
  public UnsupportedMessageException(String message) {
    super(message);
  }
}
