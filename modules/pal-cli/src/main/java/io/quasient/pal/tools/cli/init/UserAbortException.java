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
package io.quasient.pal.tools.cli.init;

/**
 * Thrown when the user aborts the interactive wizard (e.g., via Ctrl+C).
 *
 * <p>This is an unchecked exception used to unwind the wizard call stack without calling {@code
 * System.exit()}, which SpotBugs flags as {@code DM_EXIT}.
 *
 * @since 1.0.0
 */
public final class UserAbortException extends RuntimeException {

  /** Serial version UID. */
  private static final long serialVersionUID = 1L;

  /** Creates a new user abort exception with a default message. */
  public UserAbortException() {
    super("User aborted the wizard");
  }
}
