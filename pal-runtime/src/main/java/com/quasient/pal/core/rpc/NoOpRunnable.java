package com.quasient.pal.core.rpc;

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
