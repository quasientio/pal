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
