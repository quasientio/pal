/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.cxn.directory;

/**
 * Indicates that the PAL Directory (etcd) is unreachable or unhealthy.
 *
 * <p>This exception is thrown by {@link PalDirectory} when the preflight connectivity check or a
 * subsequent status check fails within the configured timeout.
 */
public class EtcdUnavailableException extends RuntimeException {
  /**
   * Creates an exception indicating that etcd is unreachable or unhealthy.
   *
   * @param message a human-readable description of the failure
   */
  public EtcdUnavailableException(String message) {
    super(message);
  }

  /**
   * Creates an exception indicating that etcd is unreachable or unhealthy.
   *
   * @param message a human-readable description of the failure
   * @param cause the underlying cause that triggered this condition
   */
  public EtcdUnavailableException(String message, Throwable cause) {
    super(message, cause);
  }
}
