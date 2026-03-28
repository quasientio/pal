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
