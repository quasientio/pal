/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.tools.cli.init;

import java.util.List;

/**
 * Abstraction for terminal I/O used by {@link InitWizard}.
 *
 * <p>This interface decouples the wizard prompt logic from the actual terminal implementation,
 * enabling unit testing with a pre-configured answer provider and graceful fallback when a real
 * terminal is not available.
 *
 * @since 1.0.0
 * @see JLinePromptProvider
 */
public interface PromptProvider {

  /**
   * Prompts the user for text input with an optional default value.
   *
   * <p>If the user presses Enter without typing anything, the default value is returned.
   *
   * @param prompt the prompt message to display
   * @param defaultValue the default value (may be {@code null})
   * @return the user's input, or the default value if the user entered nothing
   */
  String promptText(String prompt, String defaultValue);

  /**
   * Prompts the user for a yes/no answer with a default value.
   *
   * @param prompt the prompt message to display
   * @param defaultValue the default answer
   * @return {@code true} for yes, {@code false} for no
   */
  boolean promptYesNo(String prompt, boolean defaultValue);

  /**
   * Prompts the user to select one option from a list, with a default selection.
   *
   * <p>Implementations may use arrow-key navigation or simple numbered selection depending on
   * terminal capabilities.
   *
   * @param <T> the type of the options
   * @param prompt the prompt message to display
   * @param options the list of available options
   * @param defaultValue the default selection (must be in the options list)
   * @return the selected option
   */
  <T> T promptSelect(String prompt, List<T> options, T defaultValue);

  /**
   * Prints a message to the terminal output.
   *
   * @param message the message to print
   */
  void println(String message);
}
