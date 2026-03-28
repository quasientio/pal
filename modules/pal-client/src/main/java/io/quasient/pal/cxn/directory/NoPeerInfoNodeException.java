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
package io.quasient.pal.cxn.directory;

/** Represents an exception that is thrown when no peer information node is available. */
public class NoPeerInfoNodeException extends Exception {

  /**
   * Constructs a new NoPeerInfoNodeException with the specified detail message.
   *
   * @param message the detail message explaining the reason for the exception
   */
  public NoPeerInfoNodeException(String message) {
    super(message);
  }
}
