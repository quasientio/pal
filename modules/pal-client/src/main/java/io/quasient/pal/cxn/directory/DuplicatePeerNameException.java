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

/**
 * Thrown when attempting to register a peer with a name that is already in use by another peer in
 * the directory.
 *
 * <p>Peer names must be unique within a PAL directory namespace. This exception is thrown by {@link
 * PalDirectory#createPeer} when the given peer's name collides with an existing peer that has a
 * different UUID.
 */
public class DuplicatePeerNameException extends Exception {

  /**
   * Constructs a new {@code DuplicatePeerNameException} with the specified detail message.
   *
   * @param message the detail message explaining which name is duplicated
   */
  public DuplicatePeerNameException(String message) {
    super(message);
  }
}
