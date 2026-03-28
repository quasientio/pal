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
package io.quasient.pal.core.runtime.session;

/**
 * Exception indicating that an operation referenced a session which does not exist.
 *
 * <p>This exception is intended for use in contexts where session management is performed,
 * signaling that a requested session (identified by an ID or other reference) could not be found.
 */
public class NoSuchSessionException extends Exception {

  /**
   * Constructs a new NoSuchSessionException without a detail message.
   *
   * <p>Use this constructor when no additional context is required regarding the missing session.
   */
  public NoSuchSessionException() {}

  /**
   * Constructs a new NoSuchSessionException with the specified detail message.
   *
   * @param message a descriptive message providing context for the exception
   */
  public NoSuchSessionException(String message) {
    super(message);
  }
}
