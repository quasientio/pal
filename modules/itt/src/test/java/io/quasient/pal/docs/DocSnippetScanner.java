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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Markdown parser that dynamically discovers all PAL CLI commands from documentation files.
 *
 * <p>This scanner walks a directory tree of markdown files, parses {@code ```bash} and {@code
 * ```shell} code fences, and extracts individual command lines. Each command is classified by type
 * using {@link DocCommandType#classify(String)} and returned as a {@link DocCommand}.
 *
 * <p>The scanner handles common shell patterns found in documentation:
 *
 * <ul>
 *   <li>Line continuations with backslash ({@code \})
 *   <li>Compound commands separated by {@code &&} or {@code ;}
 *   <li>Pipe chains (preserved intact)
 *   <li>Heredoc patterns ({@code <<EOF})
 *   <li>Leading {@code $} prompt markers
 *   <li>Environment variable prefixes
 *   <li>Comment lines (starting with {@code #})
 * </ul>
 */
public final class DocSnippetScanner {

  private static final Logger LOG = LoggerFactory.getLogger(DocSnippetScanner.class);

  /** Matches an opening bash or shell code fence. */
  private static final Pattern BASH_FENCE_OPEN = Pattern.compile("^\\s*```(bash|shell)\\s*$");

  /** Matches an opening code fence with any language label. */
  private static final Pattern LABELED_FENCE_OPEN = Pattern.compile("^\\s*```\\w+\\s*$");

  /** Matches a closing code fence (or unlabeled opening fence). */
  private static final Pattern FENCE_CLOSE = Pattern.compile("^\\s*```\\s*$");

  /** Matches a heredoc redirect pattern like {@code <<EOF} or {@code <<'EOF'}. */
  private static final Pattern HEREDOC_PATTERN = Pattern.compile("<<'?(\\w+)'?");

  /** Splits compound commands on {@code &&} (with surrounding whitespace) or {@code ;} . */
  private static final Pattern COMPOUND_SPLIT_PATTERN = Pattern.compile("\\s+&&\\s+|\\s*;\\s+");

  /** Matches leading environment variable assignments. */
  private static final Pattern ENV_VAR_PREFIX_PATTERN =
      Pattern.compile("^([A-Z_][A-Z0-9_]*=(?:\"[^\"]*\"|'[^']*'|\\S+)\\s+)+");

  private DocSnippetScanner() {}

  /**
   * Recursively scans all markdown files under the given directory for CLI commands.
   *
   * <p>Files are sorted by path for deterministic ordering. Each file is scanned using {@link
   * #scanFile(Path)}.
   *
   * @param docsRoot the root directory to scan (must not be null)
   * @return a list of all discovered commands across all files
   * @throws NullPointerException if {@code docsRoot} is null
   * @throws UncheckedIOException if an I/O error occurs during directory walking
   */
  public static List<DocCommand> scan(Path docsRoot) {
    Objects.requireNonNull(docsRoot, "docsRoot must not be null");

    List<Path> mdFiles;
    try (Stream<Path> paths = Files.walk(docsRoot)) {
      mdFiles =
          paths.filter(p -> p.toString().endsWith(".md")).sorted().collect(Collectors.toList());
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to walk directory: " + docsRoot, e);
    }

    List<DocCommand> allCommands = new ArrayList<>();
    for (Path mdFile : mdFiles) {
      allCommands.addAll(scanFile(mdFile));
    }

    long palCount = allCommands.stream().filter(c -> c.getType() != DocCommandType.NON_PAL).count();
    LOG.info(
        "Scanned {} files under {}: {} commands total ({} pal)",
        mdFiles.size(),
        docsRoot,
        allCommands.size(),
        palCount);

    return allCommands;
  }

  /**
   * Scans a single markdown file for CLI commands in bash/shell code fences.
   *
   * @param markdownFile the markdown file to scan (must not be null)
   * @return a list of commands extracted from the file
   * @throws NullPointerException if {@code markdownFile} is null
   * @throws UncheckedIOException if an I/O error occurs reading the file
   */
  public static List<DocCommand> scanFile(Path markdownFile) {
    Objects.requireNonNull(markdownFile, "markdownFile must not be null");

    List<String> lines;
    try {
      lines = Files.readAllLines(markdownFile);
    } catch (IOException e) {
      throw new UncheckedIOException("Failed to read: " + markdownFile, e);
    }

    List<DocCommand> commands = new ArrayList<>();
    boolean insideBashBlock = false;
    boolean insideSkipBlock = false;
    List<String> blockLines = new ArrayList<>();
    List<Integer> blockLineNumbers = new ArrayList<>();
    int bashBlockCount = 0;

    for (int i = 0; i < lines.size(); i++) {
      String line = lines.get(i);
      int lineNumber = i + 1;

      if (insideBashBlock) {
        if (FENCE_CLOSE.matcher(line).matches()) {
          insideBashBlock = false;
          bashBlockCount++;
          commands.addAll(processBlock(blockLines, blockLineNumbers, markdownFile));
          blockLines.clear();
          blockLineNumbers.clear();
        } else {
          blockLines.add(line);
          blockLineNumbers.add(lineNumber);
        }
      } else if (insideSkipBlock) {
        if (FENCE_CLOSE.matcher(line).matches()) {
          insideSkipBlock = false;
        }
      } else {
        if (BASH_FENCE_OPEN.matcher(line).matches()) {
          insideBashBlock = true;
          blockLines.clear();
          blockLineNumbers.clear();
        } else if (LABELED_FENCE_OPEN.matcher(line).matches()) {
          insideSkipBlock = true;
        } else if (FENCE_CLOSE.matcher(line).matches()) {
          insideSkipBlock = true;
        }
      }
    }

    long palCount = commands.stream().filter(c -> c.getType() != DocCommandType.NON_PAL).count();
    LOG.info(
        "{}: {} bash blocks, {} commands ({} pal)",
        markdownFile.getFileName(),
        bashBlockCount,
        commands.size(),
        palCount);

    for (DocCommand cmd : commands) {
      LOG.debug(
          "  {}:{} [{}] {}",
          markdownFile.getFileName(),
          cmd.getLineNumber(),
          cmd.getType(),
          cmd.getNormalizedText());
    }

    return commands;
  }

  /**
   * Processes a collected bash block into individual commands.
   *
   * @param blockLines the lines within the bash block
   * @param lineNumbers the 1-based line numbers corresponding to each block line
   * @param sourceFile the source markdown file path
   * @return a list of parsed commands
   */
  private static List<DocCommand> processBlock(
      List<String> blockLines, List<Integer> lineNumbers, Path sourceFile) {

    if (blockLines.isEmpty()) {
      return new ArrayList<>();
    }

    List<JoinedLine> joinedLines = joinContinuations(blockLines, lineNumbers);
    List<DocCommand> commands = new ArrayList<>();
    int i = 0;

    while (i < joinedLines.size()) {
      JoinedLine jl = joinedLines.get(i);
      String text = jl.text.strip();

      if (text.isEmpty()) {
        i++;
        continue;
      }

      // Check for comments (strip $ prompt first for prompt-prefixed comments)
      if (isComment(text)) {
        i++;
        continue;
      }

      // Check for heredoc pattern
      String heredocBody = null;
      Matcher heredocMatcher = HEREDOC_PATTERN.matcher(text);
      if (heredocMatcher.find()) {
        String delimiter = heredocMatcher.group(1);
        StringBuilder body = new StringBuilder();
        i++;
        while (i < joinedLines.size()) {
          String bodyLine = joinedLines.get(i).text;
          if (bodyLine.strip().equals(delimiter)) {
            i++;
            break;
          }
          if (body.length() > 0) {
            body.append("\n");
          }
          body.append(bodyLine);
          i++;
        }
        heredocBody = body.toString();
      } else {
        i++;
      }

      // Split compound commands (skip for heredoc commands)
      List<String> segments;
      if (heredocBody != null) {
        segments = Collections.singletonList(text);
      } else {
        segments = splitCompound(text);
      }

      for (String segment : segments) {
        String trimmed = segment.strip();
        if (trimmed.isEmpty()) {
          continue;
        }
        if (isComment(trimmed)) {
          continue;
        }

        String normalized = normalize(trimmed);
        DocCommandType type = classifyCommand(normalized);

        commands.add(
            new DocCommand(sourceFile, jl.lineNumber, trimmed, normalized, type, heredocBody));
        // Only the first segment gets the heredoc body
        heredocBody = null;
      }
    }

    return commands;
  }

  /**
   * Checks whether a line is a comment (starts with {@code #}, optionally after a {@code $}
   * prompt).
   *
   * @param text the stripped line text
   * @return true if the line is a comment
   */
  private static boolean isComment(String text) {
    String check = text;
    if (check.startsWith("$ ")) {
      check = check.substring(2).stripLeading();
    }
    return check.startsWith("#");
  }

  /**
   * Joins lines ending with backslash continuations into single logical lines.
   *
   * @param lines the block lines
   * @param lineNumbers the 1-based line numbers
   * @return a list of joined logical lines
   */
  private static List<JoinedLine> joinContinuations(List<String> lines, List<Integer> lineNumbers) {

    List<JoinedLine> result = new ArrayList<>();
    int i = 0;

    while (i < lines.size()) {
      int startLine = lineNumbers.get(i);

      if (endsWithContinuation(lines.get(i))) {
        List<String> parts = new ArrayList<>();

        while (i < lines.size() && endsWithContinuation(lines.get(i))) {
          String current = lines.get(i);
          String content = stripContinuation(current);
          if (parts.isEmpty()) {
            parts.add(content);
          } else {
            parts.add(content.stripLeading());
          }
          i++;
        }

        // Add final line (no continuation)
        if (i < lines.size()) {
          parts.add(lines.get(i).strip());
          i++;
        }

        result.add(new JoinedLine(String.join(" ", parts), startLine));
      } else {
        result.add(new JoinedLine(lines.get(i), startLine));
        i++;
      }
    }

    return result;
  }

  /**
   * Checks whether a line ends with a backslash continuation (after trimming trailing whitespace).
   *
   * @param line the line to check
   * @return true if the line ends with {@code \}
   */
  private static boolean endsWithContinuation(String line) {
    int end = line.length();
    while (end > 0 && Character.isWhitespace(line.charAt(end - 1))) {
      end--;
    }
    return end > 0 && line.charAt(end - 1) == '\\';
  }

  /**
   * Strips the trailing backslash continuation and surrounding whitespace from a line.
   *
   * @param line a line ending with backslash continuation
   * @return the line content without the trailing backslash and whitespace
   */
  private static String stripContinuation(String line) {
    int end = line.length();
    while (end > 0 && Character.isWhitespace(line.charAt(end - 1))) {
      end--;
    }
    if (end > 0 && line.charAt(end - 1) == '\\') {
      end--;
    }
    while (end > 0 && Character.isWhitespace(line.charAt(end - 1))) {
      end--;
    }
    return line.substring(0, end);
  }

  /**
   * Normalizes a command by stripping the leading dollar sign prompt and environment variable
   * assignment prefixes.
   *
   * @param text the raw command text
   * @return the normalized command text
   */
  static String normalize(String text) {
    String result = text.strip();

    if (result.startsWith("$ ")) {
      result = result.substring(2).stripLeading();
    }

    Matcher envMatcher = ENV_VAR_PREFIX_PATTERN.matcher(result);
    if (envMatcher.find()) {
      String remainder = result.substring(envMatcher.end());
      if (!remainder.strip().isEmpty()) {
        result = remainder;
      }
    }

    return result;
  }

  /**
   * Classifies a normalized command, handling pipe-into-pal patterns.
   *
   * <p>First tries direct classification via {@link DocCommandType#classify(String)}. If that
   * returns {@link DocCommandType#NON_PAL}, checks for pipe-into-pal patterns like {@code echo
   * '...' | pal peer call ...}.
   *
   * @param normalizedText the normalized command text
   * @return the classified command type
   */
  private static DocCommandType classifyCommand(String normalizedText) {
    DocCommandType type = DocCommandType.classify(normalizedText);
    if (type != DocCommandType.NON_PAL) {
      return type;
    }

    // Check for pipe-into-pal pattern: ... | pal ...
    int pipeIdx = normalizedText.lastIndexOf("| pal ");
    if (pipeIdx >= 0) {
      String palPortion = normalizedText.substring(pipeIdx + 2).strip();
      return DocCommandType.classify(palPortion);
    }

    pipeIdx = normalizedText.lastIndexOf("|pal ");
    if (pipeIdx >= 0) {
      String palPortion = normalizedText.substring(pipeIdx + 1).strip();
      return DocCommandType.classify(palPortion);
    }

    return DocCommandType.NON_PAL;
  }

  /**
   * Splits a command line on compound operators ({@code &&} and {@code ;}).
   *
   * <p>Pipe chains ({@code |}) are preserved intact.
   *
   * @param text the command line to split
   * @return a list of individual command segments
   */
  private static List<String> splitCompound(String text) {
    List<String> result = new ArrayList<>();
    for (String part : COMPOUND_SPLIT_PATTERN.splitAsStream(text).collect(Collectors.toList())) {
      String trimmed = part.strip();
      if (!trimmed.isEmpty()) {
        result.add(trimmed);
      }
    }
    return result;
  }

  /** Represents a logical line after joining backslash continuations. */
  private static final class JoinedLine {

    /** The joined text (continuations resolved). */
    final String text;

    /** The 1-based line number of the first line. */
    final int lineNumber;

    JoinedLine(String text, int lineNumber) {
      this.text = text;
      this.lineNumber = lineNumber;
    }
  }
}
