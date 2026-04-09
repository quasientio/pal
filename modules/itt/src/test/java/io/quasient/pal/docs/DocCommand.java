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
package io.quasient.pal.docs;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Immutable value class representing a parsed CLI command extracted from a documentation markdown
 * file.
 *
 * <p>Each {@code DocCommand} captures the source location (file and line number), the original and
 * normalized command text, and the classified command type. This class is the foundation data model
 * for the documentation snippet testing infrastructure.
 */
public final class DocCommand {

  /** Maximum length of the command portion in {@link #toString()} output before truncation. */
  private static final int MAX_COMMAND_LENGTH = 80;

  /** Relative path to the markdown file containing this command. */
  private final Path sourceFile;

  /** Line number in the source file where the command starts. */
  private final int lineNumber;

  /** Original text from the markdown code block. */
  private final String rawText;

  /** Normalized text after joining line continuations and trimming. */
  private final String normalizedText;

  /** Classified command type. */
  private final DocCommandType type;

  /** Heredoc body content, or {@code null} if not a heredoc command. */
  private final String heredocBody;

  /**
   * Constructs a new {@code DocCommand} without heredoc body.
   *
   * @param sourceFile relative path to the markdown file (must not be null)
   * @param lineNumber line number in the source file where the command starts
   * @param rawText original text from the markdown code block (must not be null)
   * @param normalizedText normalized text after joining line continuations and trimming
   * @param type classified command type (must not be null)
   * @throws NullPointerException if {@code sourceFile}, {@code rawText}, or {@code type} is null
   */
  public DocCommand(
      Path sourceFile, int lineNumber, String rawText, String normalizedText, DocCommandType type) {
    this(sourceFile, lineNumber, rawText, normalizedText, type, null);
  }

  /**
   * Constructs a new {@code DocCommand} with an optional heredoc body.
   *
   * @param sourceFile relative path to the markdown file (must not be null)
   * @param lineNumber line number in the source file where the command starts
   * @param rawText original text from the markdown code block (must not be null)
   * @param normalizedText normalized text after joining line continuations and trimming
   * @param type classified command type (must not be null)
   * @param heredocBody the heredoc body content, or {@code null} if not a heredoc command
   * @throws NullPointerException if {@code sourceFile}, {@code rawText}, or {@code type} is null
   */
  public DocCommand(
      Path sourceFile,
      int lineNumber,
      String rawText,
      String normalizedText,
      DocCommandType type,
      String heredocBody) {
    this.sourceFile = Objects.requireNonNull(sourceFile, "sourceFile must not be null");
    this.lineNumber = lineNumber;
    this.rawText = Objects.requireNonNull(rawText, "rawText must not be null");
    this.normalizedText = normalizedText;
    this.type = Objects.requireNonNull(type, "type must not be null");
    this.heredocBody = heredocBody;
  }

  /**
   * Returns the relative path to the markdown file containing this command.
   *
   * @return the source file path, never null
   */
  public Path getSourceFile() {
    return sourceFile;
  }

  /**
   * Returns the line number in the source file where the command starts.
   *
   * @return the line number (1-based)
   */
  public int getLineNumber() {
    return lineNumber;
  }

  /**
   * Returns the original text from the markdown code block.
   *
   * @return the raw command text, never null
   */
  public String getRawText() {
    return rawText;
  }

  /**
   * Returns the normalized text after joining line continuations and trimming.
   *
   * @return the normalized command text
   */
  public String getNormalizedText() {
    return normalizedText;
  }

  /**
   * Returns the classified command type.
   *
   * @return the command type, never null
   */
  public DocCommandType getType() {
    return type;
  }

  /**
   * Returns the heredoc body content, or {@code null} if this is not a heredoc command.
   *
   * @return the heredoc body, or null
   */
  public String getHeredocBody() {
    return heredocBody;
  }

  /**
   * Returns a string representation formatted as {@code filename:line -> command}.
   *
   * <p>The file name is extracted from the source path (not the full path) for readability. The
   * command portion (from {@code normalizedText}) is truncated at 80 characters with an ellipsis if
   * it exceeds that length.
   *
   * @return a formatted string representation
   */
  @Override
  public String toString() {
    String fileName = sourceFile.getFileName().toString();
    String commandPortion = normalizedText != null ? normalizedText : rawText;

    if (commandPortion.length() > MAX_COMMAND_LENGTH) {
      commandPortion = commandPortion.substring(0, MAX_COMMAND_LENGTH) + "...";
    }

    return fileName + ":" + lineNumber + " -> " + commandPortion;
  }
}
