/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
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
