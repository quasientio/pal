/*
 * Copyright (C) 2025 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2029-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.core.dispatcher.thread;

/**
 * A no-operation {@link Runnable} implementation that performs no actions when run.
 *
 * <p>This class is useful in contexts where a {@code Runnable} is required to fulfill an API
 * contract or as a default placeholder, but no execution logic is necessary.
 */
public class NoOpRunnable implements Runnable {

  /**
   * {@inheritDoc}
   *
   * <p>This implementation intentionally does nothing. It is safe to invoke in any execution
   * context without side effects.
   */
  @Override
  public void run() {
    // This method intentionally left blank
  }
}
