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

import com.google.common.base.Splitter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Classifies parsed documentation commands by their PAL CLI category.
 *
 * <p>Each enum value represents a specific PAL CLI command or command group. The {@link
 * #classify(String)} method parses a normalized command string and returns the appropriate type.
 *
 * <p>Commands that do not start with {@code pal} are classified as {@link #NON_PAL}. Unrecognizable
 * PAL commands are classified as {@link #SKIPPED}.
 */
public enum DocCommandType {

  /** {@code pal help}, {@code pal --help}, or {@code --help}/{@code -h} on any subcommand. */
  HELP,

  /** {@code pal init ...}. */
  INIT,

  /** {@code pal run ...}. */
  RUN,

  /** {@code pal replay ...}. */
  REPLAY,

  /** {@code pal peer ls ...} or {@code pal peers ...}. */
  PEER_LS,

  /** {@code pal peer call ...}. */
  PEER_CALL,

  /** {@code pal peer print ...}. */
  PEER_PRINT,

  /** {@code pal peer rm ...}. */
  PEER_RM,

  /** {@code pal peer stats ...}. */
  PEER_STATS,

  /** {@code pal log ls ...} or {@code pal logs ...}. */
  LOG_LS,

  /** {@code pal log print ...}. */
  LOG_PRINT,

  /** {@code pal log call ...}. */
  LOG_CALL,

  /** {@code pal log rm ...}. */
  LOG_RM,

  /** {@code pal log stats ...}. */
  LOG_STATS,

  /** {@code pal log index ...}. */
  LOG_INDEX,

  /** {@code pal intercept ls ...} or {@code pal intercepts ...}. */
  INTERCEPT_LS,

  /** {@code pal intercept apply ...}. */
  INTERCEPT_APPLY,

  /** {@code pal intercept rm ...}. */
  INTERCEPT_RM,

  /** {@code pal intercept diff ...}. */
  INTERCEPT_DIFF,

  /** {@code pal intercept status ...}. */
  INTERCEPT_STATUS,

  /** Non-PAL command (e.g., {@code tar}, {@code mvn}, {@code ./mvnw}, {@code docker}). */
  NON_PAL,

  /** Unrecognizable PAL command that cannot be classified. */
  SKIPPED;

  /** Splitter for whitespace-delimited tokens. */
  private static final Splitter WHITESPACE_SPLITTER =
      Splitter.on(Pattern.compile("\\s+")).omitEmptyStrings();

  /** Maps entity names to their subcommand lookup tables. */
  private static final Map<String, Map<String, DocCommandType>> ENTITY_SUBCOMMANDS;

  /** Maps top-level command names to their types. */
  private static final Map<String, DocCommandType> TOP_LEVEL_COMMANDS;

  /** Maps entity alias shortcuts (e.g., "peers") to their list command types. */
  private static final Map<String, DocCommandType> ENTITY_ALIASES;

  static {
    Map<String, DocCommandType> peerSubs = new HashMap<>();
    peerSubs.put("ls", PEER_LS);
    peerSubs.put("call", PEER_CALL);
    peerSubs.put("print", PEER_PRINT);
    peerSubs.put("rm", PEER_RM);
    peerSubs.put("prune", PEER_RM);
    peerSubs.put("stats", PEER_STATS);

    Map<String, DocCommandType> logSubs = new HashMap<>();
    logSubs.put("ls", LOG_LS);
    logSubs.put("print", LOG_PRINT);
    logSubs.put("call", LOG_CALL);
    logSubs.put("rm", LOG_RM);
    logSubs.put("prune", LOG_RM);
    logSubs.put("stats", LOG_STATS);
    logSubs.put("index", LOG_INDEX);

    Map<String, DocCommandType> interceptSubs = new HashMap<>();
    interceptSubs.put("ls", INTERCEPT_LS);
    interceptSubs.put("apply", INTERCEPT_APPLY);
    interceptSubs.put("rm", INTERCEPT_RM);
    interceptSubs.put("diff", INTERCEPT_DIFF);
    interceptSubs.put("status", INTERCEPT_STATUS);

    ENTITY_SUBCOMMANDS = new HashMap<>();
    ENTITY_SUBCOMMANDS.put("peer", peerSubs);
    ENTITY_SUBCOMMANDS.put("log", logSubs);
    ENTITY_SUBCOMMANDS.put("intercept", interceptSubs);

    TOP_LEVEL_COMMANDS = new HashMap<>();
    TOP_LEVEL_COMMANDS.put("run", RUN);
    TOP_LEVEL_COMMANDS.put("replay", REPLAY);
    TOP_LEVEL_COMMANDS.put("init", INIT);
    TOP_LEVEL_COMMANDS.put("help", HELP);

    ENTITY_ALIASES = new HashMap<>();
    ENTITY_ALIASES.put("peers", PEER_LS);
    ENTITY_ALIASES.put("logs", LOG_LS);
    ENTITY_ALIASES.put("intercepts", INTERCEPT_LS);
  }

  /**
   * Classifies a normalized command string into its {@link DocCommandType}.
   *
   * <p>The method performs the following steps:
   *
   * <ol>
   *   <li>Strips a leading {@code $} character and surrounding whitespace
   *   <li>Strips environment variable assignments (e.g., {@code JAVA_TOOL_OPTIONS="..." pal ...})
   *   <li>Checks whether the command starts with {@code pal} — if not, returns {@link #NON_PAL}
   *   <li>Checks for {@code --help} or {@code -h} anywhere in the command — if found, returns
   *       {@link #HELP}
   *   <li>Parses the entity and subcommand tokens to determine the specific type
   *   <li>Falls back to {@link #SKIPPED} for unrecognizable PAL commands
   * </ol>
   *
   * @param normalizedCommand the command string to classify (after line-continuation joining and
   *     trimming)
   * @return the classified command type, never {@code null}
   */
  public static DocCommandType classify(String normalizedCommand) {
    String command = stripPrefix(normalizedCommand);

    if (!commandStartsWithPal(command)) {
      return NON_PAL;
    }

    if (containsHelpFlag(normalizedCommand)) {
      return HELP;
    }

    String[] tokens = tokenizeAfterPal(command);

    return classifyTokens(tokens);
  }

  /**
   * Strips leading {@code $}, whitespace, and environment variable assignments from a command.
   *
   * <p>Environment variable assignments are patterns like {@code VAR=value} or {@code VAR="quoted
   * value"} that precede the actual command.
   *
   * @param command the raw command string
   * @return the command with prefixes stripped
   */
  private static String stripPrefix(String command) {
    String stripped = command.strip();

    // Strip leading $
    if (stripped.startsWith("$")) {
      stripped = stripped.substring(1).stripLeading();
    }

    // Strip environment variable assignments (VAR=value or VAR="value" or VAR='value')
    while (looksLikeEnvAssignment(stripped)) {
      stripped = consumeEnvAssignment(stripped);
    }

    return stripped;
  }

  /**
   * Checks whether the string starts with an environment variable assignment pattern.
   *
   * @param s the string to check
   * @return true if it starts with a pattern like {@code VAR=...}
   */
  private static boolean looksLikeEnvAssignment(String s) {
    int eqIdx = s.indexOf('=');
    if (eqIdx <= 0) {
      return false;
    }
    String varName = s.substring(0, eqIdx);
    // Variable names must be uppercase letters, digits, or underscores, starting with a letter
    for (int i = 0; i < varName.length(); i++) {
      char c = varName.charAt(i);
      if (i == 0 && !Character.isLetter(c) && c != '_') {
        return false;
      }
      if (!Character.isLetterOrDigit(c) && c != '_') {
        return false;
      }
    }
    return true;
  }

  /**
   * Consumes one environment variable assignment and returns the remainder.
   *
   * @param s a string starting with an env assignment
   * @return the remainder after the assignment, stripped of leading whitespace
   */
  private static String consumeEnvAssignment(String s) {
    int eqIdx = s.indexOf('=');
    int pos = eqIdx + 1;

    if (pos < s.length() && s.charAt(pos) == '"') {
      // Quoted value — find closing quote
      pos++;
      while (pos < s.length() && s.charAt(pos) != '"') {
        if (s.charAt(pos) == '\\' && pos + 1 < s.length()) {
          pos++; // skip escaped character
        }
        pos++;
      }
      if (pos < s.length()) {
        pos++; // skip closing quote
      }
    } else if (pos < s.length() && s.charAt(pos) == '\'') {
      // Single-quoted value — find closing quote
      pos++;
      while (pos < s.length() && s.charAt(pos) != '\'') {
        pos++;
      }
      if (pos < s.length()) {
        pos++; // skip closing quote
      }
    } else {
      // Unquoted value — consume until whitespace
      while (pos < s.length() && !Character.isWhitespace(s.charAt(pos))) {
        pos++;
      }
    }

    return pos < s.length() ? s.substring(pos).stripLeading() : "";
  }

  /**
   * Checks whether the stripped command starts with {@code pal} followed by whitespace or end of
   * string.
   *
   * @param command the prefix-stripped command
   * @return true if this is a PAL command
   */
  private static boolean commandStartsWithPal(String command) {
    if (!command.startsWith("pal")) {
      return false;
    }
    return command.length() == 3 || Character.isWhitespace(command.charAt(3));
  }

  /**
   * Checks whether the command contains {@code --help} or {@code -h} as a standalone flag.
   *
   * @param command the full command string
   * @return true if a help flag is present
   */
  private static boolean containsHelpFlag(String command) {
    for (String part : WHITESPACE_SPLITTER.splitToList(command)) {
      if ("--help".equals(part) || "-h".equals(part)) {
        return true;
      }
    }
    return false;
  }

  /**
   * Extracts the tokens after the initial {@code pal} keyword, skipping flags (tokens starting with
   * {@code -}) and their values.
   *
   * @param command the command starting with {@code pal}
   * @return array of non-flag tokens after {@code pal}
   */
  private static String[] tokenizeAfterPal(String command) {
    String afterPal = command.substring(3).stripLeading();
    if (afterPal.isEmpty()) {
      return new String[0];
    }
    List<String> allTokens = WHITESPACE_SPLITTER.splitToList(afterPal);

    // Collect non-flag tokens, skipping flags and their arguments
    List<String> result = new ArrayList<>();
    for (int i = 0; i < allTokens.size(); i++) {
      String token = allTokens.get(i);
      if (token.startsWith("-")) {
        // Skip flag and its value if it looks like a flag with a separate argument
        if (isFlagWithArgument(token) && i + 1 < allTokens.size()) {
          i++; // skip the argument
        }
        continue;
      }
      result.add(token);
    }
    return result.toArray(new String[0]);
  }

  /**
   * Determines whether a flag token expects a separate argument value.
   *
   * <p>Boolean flags (like {@code --help}, {@code -V}, {@code -l}, {@code --all}) do not consume
   * the next token. Flags with values (like {@code -d}, {@code -k}, {@code -cp}) do.
   *
   * @param flag the flag token
   * @return true if the flag expects a separate argument
   */
  private static boolean isFlagWithArgument(String flag) {
    // Flags known to be boolean (no argument)
    if ("--help".equals(flag)
        || "-h".equals(flag)
        || "--version".equals(flag)
        || "-V".equals(flag)
        || "-v".equals(flag)
        || "-l".equals(flag)
        || "--all".equals(flag)
        || "--long".equals(flag)
        || "--force".equals(flag)
        || "--dry-run".equals(flag)
        || "--json-rpc".equals(flag)
        || "--interceptable".equals(flag)
        || "-P".equals(flag)
        || "-L".equals(flag)
        || "-y".equals(flag)) {
      return false;
    }
    // If the flag contains '=' it's self-contained (e.g. --foo=bar)
    if (flag.contains("=")) {
      return false;
    }
    // All other flags are assumed to take an argument
    return true;
  }

  /**
   * Classifies the non-flag tokens extracted from after {@code pal}.
   *
   * @param tokens non-flag tokens after the {@code pal} keyword
   * @return the classified command type
   */
  private static DocCommandType classifyTokens(String[] tokens) {
    if (tokens.length == 0) {
      return SKIPPED;
    }

    String first = tokens[0];

    // Check top-level commands (run, replay, init, help)
    DocCommandType topLevel = TOP_LEVEL_COMMANDS.get(first);
    if (topLevel != null) {
      // "pal help <entity>" is still HELP
      return topLevel;
    }

    // Check entity aliases (peers, logs, intercepts)
    DocCommandType alias = ENTITY_ALIASES.get(first);
    if (alias != null) {
      return alias;
    }

    // Check entity + subcommand patterns
    Map<String, DocCommandType> subcommands = ENTITY_SUBCOMMANDS.get(first);
    if (subcommands != null) {
      if (tokens.length < 2) {
        return SKIPPED;
      }
      DocCommandType subType = subcommands.get(tokens[1]);
      if (subType != null) {
        return subType;
      }
      return SKIPPED;
    }

    return SKIPPED;
  }
}
