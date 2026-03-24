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

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.IOException;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Scanner;
import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.UserInterruptException;
import org.jline.terminal.Attributes;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.utils.NonBlockingReader;

/**
 * JLine 3 implementation of {@link PromptProvider} for rich interactive prompts.
 *
 * <p>Uses JLine's {@link LineReader} for text input with readline-style editing, and raw terminal
 * key reading for arrow-key selection on list prompts. Falls back to a simple {@link Scanner}-based
 * implementation when JLine cannot acquire a terminal (e.g., piped stdin or non-interactive
 * environments).
 *
 * @since 1.0.0
 */
public final class JLinePromptProvider implements PromptProvider {

  /** ANSI escape: move cursor up one line. */
  private static final String CURSOR_UP = "\033[A";

  /** ANSI escape: clear from cursor to end of line. */
  private static final String CLEAR_LINE = "\033[2K";

  /** ANSI escape: move cursor to beginning of line. */
  private static final String CARRIAGE_RETURN = "\r";

  /** Escape character (0x1B). */
  private static final int ESC = 27;

  /** Enter / carriage return. */
  private static final int ENTER_CR = '\r';

  /** Newline / linefeed. */
  private static final int ENTER_LF = '\n';

  /** The JLine terminal, or {@code null} if in fallback mode. */
  private final Terminal terminal;

  /** The JLine line reader, or {@code null} if in fallback mode. */
  private final LineReader lineReader;

  /** The output stream for prompts and messages. */
  private final PrintStream out;

  /** Scanner for fallback mode when JLine is unavailable. */
  private final Scanner fallbackScanner;

  /** Whether this provider is in fallback (non-JLine) mode. */
  private final boolean fallbackMode;

  /**
   * Creates a JLine prompt provider, attempting to acquire a system terminal. If the terminal
   * cannot be opened, falls back to Scanner-based prompts.
   *
   * @param out the output stream for messages
   */
  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "PrintStream is intentionally shared")
  public JLinePromptProvider(PrintStream out) {
    this.out = out;
    Terminal term = null;
    LineReader reader = null;
    boolean useFallback = false;
    try {
      term = TerminalBuilder.builder().system(true).dumb(false).build();
      reader = LineReaderBuilder.builder().terminal(term).build();
    } catch (IOException | IllegalStateException e) {
      useFallback = true;
    }
    this.terminal = term;
    this.lineReader = reader;
    this.fallbackMode = useFallback;
    this.fallbackScanner = useFallback ? new Scanner(System.in, StandardCharsets.UTF_8) : null;
  }

  /**
   * Package-private constructor for testing with an explicit line reader and output.
   *
   * @param lineReader the JLine line reader (may be {@code null} for fallback mode)
   * @param out the output stream
   */
  JLinePromptProvider(LineReader lineReader, PrintStream out) {
    this.lineReader = lineReader;
    this.terminal = lineReader != null ? lineReader.getTerminal() : null;
    this.out = out;
    this.fallbackMode = lineReader == null;
    this.fallbackScanner = fallbackMode ? new Scanner(System.in, StandardCharsets.UTF_8) : null;
  }

  /**
   * Returns whether this provider is running in fallback mode (no JLine terminal).
   *
   * @return {@code true} if using Scanner-based fallback
   */
  public boolean isFallbackMode() {
    return fallbackMode;
  }

  /** {@inheritDoc} */
  @Override
  public String promptText(String prompt, String defaultValue) {
    String displayPrompt = formatTextPrompt(prompt, defaultValue);
    String input = readLine(displayPrompt);
    if (input == null || input.isEmpty()) {
      return defaultValue;
    }
    return input;
  }

  /** {@inheritDoc} */
  @Override
  public boolean promptYesNo(String prompt, boolean defaultValue) {
    String suffix = defaultValue ? " [Y/n]" : " [y/N]";
    String input = readLine(prompt + suffix + " ");
    if (input == null || input.isEmpty()) {
      return defaultValue;
    }
    String lower = input.trim().toLowerCase(Locale.ROOT);
    return lower.startsWith("y");
  }

  /** {@inheritDoc} */
  @Override
  public <T> T promptSelect(String prompt, List<T> options, T defaultValue) {
    if (options.isEmpty()) {
      throw new IllegalArgumentException("Options list must not be empty");
    }
    if (fallbackMode) {
      return promptSelectFallback(prompt, options, defaultValue);
    }
    return promptSelectArrowKey(prompt, options, defaultValue);
  }

  /** {@inheritDoc} */
  @Override
  public void println(String message) {
    out.println(message);
  }

  /**
   * Reads a line of input using JLine or the fallback scanner.
   *
   * @param prompt the prompt to display
   * @return the user's input, or {@code null} on EOF
   */
  private String readLine(String prompt) {
    if (fallbackMode) {
      out.print(prompt);
      out.flush();
      if (fallbackScanner.hasNextLine()) {
        return fallbackScanner.nextLine();
      }
      return null;
    }
    try {
      return lineReader.readLine(prompt);
    } catch (UserInterruptException e) {
      return null;
    } catch (EndOfFileException e) {
      return null;
    }
  }

  /**
   * Arrow-key-based select prompt using JLine's raw terminal mode.
   *
   * <p>Renders the option list with a {@code ❯} marker on the currently highlighted option. The
   * user navigates with up/down arrow keys and confirms with Enter. The terminal is put into raw
   * mode for unbuffered key reading and restored afterward.
   *
   * @param <T> option type
   * @param prompt the prompt message
   * @param options the available options
   * @param defaultValue the default option
   * @return the selected option
   */
  private <T> T promptSelectArrowKey(String prompt, List<T> options, T defaultValue) {
    int selectedIndex = options.indexOf(defaultValue);
    if (selectedIndex < 0) {
      selectedIndex = 0;
    }

    PrintWriter writer = terminal.writer();
    writer.println(prompt + " (use arrow keys, Enter to confirm)");
    renderOptions(writer, options, selectedIndex);
    writer.flush();

    Attributes savedAttributes = terminal.enterRawMode();
    try {
      NonBlockingReader reader = terminal.reader();
      while (true) {
        int ch = reader.read(200);
        if (ch == NonBlockingReader.READ_EXPIRED) {
          continue;
        }
        if (ch == NonBlockingReader.EOF) {
          break;
        }

        if (ch == ENTER_CR || ch == ENTER_LF) {
          break;
        }

        if (ch == ESC) {
          int next = reader.read(100);
          if (next == '[') {
            int arrow = reader.read(100);
            if (arrow == 'A' && selectedIndex > 0) {
              selectedIndex--;
            } else if (arrow == 'B' && selectedIndex < options.size() - 1) {
              selectedIndex++;
            }
            moveUpAndRedraw(writer, options, selectedIndex);
          }
        }
      }
    } catch (IOException e) {
      // On I/O error, return current selection
    } finally {
      terminal.setAttributes(savedAttributes);
    }

    // Print final selection on a new line
    writer.println();
    writer.flush();
    return options.get(selectedIndex);
  }

  /**
   * Renders the option list with the selected option highlighted.
   *
   * @param <T> option type
   * @param writer the terminal writer
   * @param options the option list
   * @param selectedIndex the currently selected index
   */
  private <T> void renderOptions(PrintWriter writer, List<T> options, int selectedIndex) {
    for (int i = 0; i < options.size(); i++) {
      String marker = (i == selectedIndex) ? "  \u276f " : "    ";
      writer.println(marker + options.get(i));
    }
  }

  /**
   * Moves the cursor back up over the previously rendered options and redraws them with the updated
   * selection.
   *
   * @param <T> option type
   * @param writer the terminal writer
   * @param options the option list
   * @param selectedIndex the currently selected index
   */
  private <T> void moveUpAndRedraw(PrintWriter writer, List<T> options, int selectedIndex) {
    // Move cursor up by the number of option lines
    for (int i = 0; i < options.size(); i++) {
      writer.print(CURSOR_UP);
    }
    writer.print(CARRIAGE_RETURN);

    // Redraw each option line, clearing old content
    for (int i = 0; i < options.size(); i++) {
      writer.print(CLEAR_LINE);
      String marker = (i == selectedIndex) ? "  \u276f " : "    ";
      writer.println(marker + options.get(i));
    }
    writer.flush();
  }

  /**
   * Scanner-based fallback for select prompts when JLine is unavailable. Uses numbered text input.
   *
   * @param <T> option type
   * @param prompt the prompt message
   * @param options the available options
   * @param defaultValue the default option
   * @return the selected option
   */
  private <T> T promptSelectFallback(String prompt, List<T> options, T defaultValue) {
    int defaultIndex = options.indexOf(defaultValue);
    if (defaultIndex < 0) {
      defaultIndex = 0;
    }

    out.println(prompt);
    for (int i = 0; i < options.size(); i++) {
      out.println("  " + (i + 1) + ") " + options.get(i));
    }

    String input = readLine("Enter selection [" + (defaultIndex + 1) + "]: ");
    return parseSelection(input, options, defaultIndex);
  }

  /**
   * Parses a numbered selection input, returning the selected option or the default.
   *
   * @param <T> option type
   * @param input the user's raw input
   * @param options the available options
   * @param defaultIndex the default option index
   * @return the selected option
   */
  private <T> T parseSelection(String input, List<T> options, int defaultIndex) {
    if (input == null || input.isEmpty()) {
      return options.get(defaultIndex);
    }
    try {
      int selected = Integer.parseInt(input.trim()) - 1;
      if (selected >= 0 && selected < options.size()) {
        return options.get(selected);
      }
    } catch (NumberFormatException e) {
      for (T option : options) {
        if (option.toString().equalsIgnoreCase(input.trim())) {
          return option;
        }
      }
    }
    return options.get(defaultIndex);
  }

  /**
   * Formats a text prompt with an optional default value hint.
   *
   * @param prompt the prompt message
   * @param defaultValue the default value (may be {@code null})
   * @return the formatted prompt string
   */
  private static String formatTextPrompt(String prompt, String defaultValue) {
    if (defaultValue != null && !defaultValue.isEmpty()) {
      return prompt + " [" + defaultValue + "]: ";
    }
    return prompt + ": ";
  }
}
