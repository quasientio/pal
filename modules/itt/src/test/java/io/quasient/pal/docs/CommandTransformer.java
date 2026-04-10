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
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Transforms raw documentation commands for safe execution in the integration test environment.
 *
 * <p>This transformer adapts CLI commands extracted from documentation markdown files by
 * substituting placeholder addresses, classpaths, main classes, and names with real test
 * environment values. It also appends required flags and classifies commands for lifecycle
 * management.
 *
 * <p>Substitution rules are applied in order:
 *
 * <ol>
 *   <li>Address substitution (etcd, Kafka)
 *   <li>Classpath substitution ({@code -cp}, {@code -jar})
 *   <li>Main class substitution (placeholder to itt-apps classes)
 *   <li>Name uniquification (peer names, WAL names)
 *   <li>Chronicle path handling
 *   <li>Flag adaptation ({@code --rpc-default-action}, {@code --dry-run}, {@code -y})
 *   <li>Stdin extraction (echo pipes, heredocs)
 *   <li>Lifecycle classification
 *   <li>Skip determination
 * </ol>
 */
public class CommandTransformer {

  private static final Logger LOG = LoggerFactory.getLogger(CommandTransformer.class);

  /** Splitter for whitespace-delimited tokens. */
  private static final Splitter WHITESPACE_SPLITTER =
      Splitter.on(Pattern.compile("\\s+")).omitEmptyStrings();

  /** Default main class for generic examples. */
  private static final String DEFAULT_MAIN_CLASS = "io.quasient.foobar.apps.quantized.rpc.Methods";

  /** Main class for calculator/service examples. */
  private static final String CALCULATOR_MAIN_CLASS =
      "io.quasient.foobar.apps.quantized.intercept.Calculator";

  /** Pattern matching {@code -d <host>:<port>} for etcd address substitution. */
  private static final Pattern ETCD_ADDRESS_PATTERN = Pattern.compile("(-d\\s+)([^\\s]+:\\d+)");

  /** Pattern matching {@code -k <host>:<port>} for Kafka address substitution. */
  private static final Pattern KAFKA_K_ADDRESS_PATTERN = Pattern.compile("(-k\\s+)([^\\s]+:\\d+)");

  /** Pattern matching {@code -b <host>:<port>} for Kafka bootstrap address substitution. */
  private static final Pattern KAFKA_B_ADDRESS_PATTERN = Pattern.compile("(-b\\s+)([^\\s]+:\\d+)");

  /** Pattern matching classpath values that look like placeholders. */
  private static final Pattern CLASSPATH_PLACEHOLDER_PATTERN =
      Pattern.compile(
          "(-cp\\s+)(app\\.jar|target/classes|target/test-classes"
              + "|build/classes/java/main|service\\.jar|target/[^\\s]*\\.jar"
              + "|[a-zA-Z][a-zA-Z0-9._-]*\\.jar)");

  /** Pattern matching {@code -jar <path>}. */
  private static final Pattern JAR_FLAG_PATTERN = Pattern.compile("-jar\\s+([^\\s]+)");

  /** Pattern matching {@code echo '...' | pal ...} for stdin extraction. */
  private static final Pattern ECHO_PIPE_PATTERN =
      Pattern.compile("^echo\\s+'([^']*)'\\s*\\|\\s*(.*)$");

  /** Pattern matching {@code echo "..." | pal ...} for stdin extraction with double quotes. */
  private static final Pattern ECHO_PIPE_DOUBLE_QUOTE_PATTERN =
      Pattern.compile("^echo\\s+\"([^\"]*)\"\\s*\\|\\s*(.*)$");

  /** Pattern matching {@code cat ... | pal ...} for stdin from cat piped into pal. */
  private static final Pattern CAT_PIPE_PATTERN =
      Pattern.compile("^cat\\s+[^|]*\\|\\s*(pal\\s+.*)$");

  /** Pattern matching {@code file:<path>} Chronicle paths (e.g., file:/tmp/x, file:./x, file:x). */
  private static final Pattern CHRONICLE_PATH_PATTERN = Pattern.compile("file:([^\\s]+)");

  /** Pattern matching {@code --wal <name>} where name doesn't start with {@code file:}. */
  private static final Pattern WAL_NAME_PATTERN = Pattern.compile("(--wal\\s+)((?!file:)[^\\s]+)");

  /**
   * Pattern matching synopsis/usage meta-syntax placeholders that are not real CLI arguments. These
   * appear in documentation code blocks as syntax descriptions, not executable commands.
   */
  private static final Pattern SYNOPSIS_PLACEHOLDER_PATTERN =
      Pattern.compile(
          "\\[OPTIONS\\]|\\[LOG\\.\\.\\.\\]|\\[PEER\\]|\\[LOG_NAME\\]|\\[DIRECTORY\\]"
              + "|\\[args\\.\\.\\.\\]|\\[FILE\\]|\\bclass\\b(?!\\w)"
              + "|<[a-z][a-z0-9_-]*>");

  /**
   * Pattern matching ellipsis-truncated UUIDs like {@code abc12345-...} or {@code
   * 550e8400-e29b...}.
   */
  private static final Pattern ELLIPSIS_UUID_PATTERN =
      Pattern.compile("[0-9a-fA-F]{4,}-[0-9a-fA-F]*\\.\\.\\.?");

  /**
   * Pattern matching bare ellipsis ({@code ...}) as an argument. Matches standalone {@code ...}
   * surrounded by whitespace or at end of line. Catches incomplete example commands like {@code pal
   * run -n user-service ...}.
   */
  private static final Pattern BARE_ELLIPSIS_PATTERN =
      Pattern.compile("(?:^|\\s)\\.\\.\\.(?:\\s|$)");

  /** Pattern matching {@code -n <name>} or {@code --name <name>} for peer names. */
  private static final Pattern PEER_NAME_PATTERN = Pattern.compile("(-n\\s+|--name\\s+)([^\\s]+)");

  /** Specific main class mappings from placeholder to itt-apps classes. */
  private static final Map<String, String> MAIN_CLASS_MAP = new HashMap<>();

  /** Patterns that indicate a main class placeholder to replace. */
  private static final Pattern MAIN_CLASS_PLACEHOLDER_PATTERN =
      Pattern.compile(
          "\\b(com\\.example\\.[A-Za-z.]+|tutorial\\.[A-Za-z.]+|com\\.mycompany\\.[A-Za-z.]+)\\b");

  static {
    MAIN_CLASS_MAP.put("com.example.Main", DEFAULT_MAIN_CLASS);
    MAIN_CLASS_MAP.put("com.example.App", DEFAULT_MAIN_CLASS);
    MAIN_CLASS_MAP.put("com.example.Service", DEFAULT_MAIN_CLASS);
    MAIN_CLASS_MAP.put("com.example.Calculator", CALCULATOR_MAIN_CLASS);
    MAIN_CLASS_MAP.put("tutorial.CalculatorService", CALCULATOR_MAIN_CLASS);
    MAIN_CLASS_MAP.put("com.example.calculator.CalculatorService", CALCULATOR_MAIN_CLASS);
  }

  /** The etcd directory URL for the test environment. */
  private final String palDirectoryUrl;

  /** The Kafka bootstrap servers for the test environment. */
  private final String kafkaServers;

  /** The classpath to itt-apps test classes. */
  private final String ittAppsClasspath;

  /**
   * Constructs a new {@code CommandTransformer}.
   *
   * @param palDirectoryUrl the etcd directory URL for the test environment (must not be null)
   * @param kafkaServers the Kafka bootstrap servers for the test environment (must not be null)
   * @param ittAppsClasspath the classpath to itt-apps test classes (must not be null)
   * @throws NullPointerException if any argument is null
   */
  public CommandTransformer(String palDirectoryUrl, String kafkaServers, String ittAppsClasspath) {
    this.palDirectoryUrl =
        Objects.requireNonNull(palDirectoryUrl, "palDirectoryUrl must not be null");
    this.kafkaServers = Objects.requireNonNull(kafkaServers, "kafkaServers must not be null");
    this.ittAppsClasspath =
        Objects.requireNonNull(ittAppsClasspath, "ittAppsClasspath must not be null");
  }

  /**
   * Transforms a raw documentation command for safe execution in the test environment.
   *
   * <p>The transformation applies substitution rules in order: address substitution, classpath
   * substitution, main class substitution, name uniquification, Chronicle path handling, flag
   * adaptation, stdin extraction, lifecycle classification, and skip determination.
   *
   * @param command the documentation command to transform (must not be null)
   * @return the transformed command with all substitutions applied
   * @throws NullPointerException if {@code command} is null
   */
  public TransformedCommand transform(DocCommand command) {
    Objects.requireNonNull(command, "command must not be null");

    String text = command.getNormalizedText();
    if (text == null) {
      text = command.getRawText();
    }

    List<String> substitutions = new ArrayList<>();

    // Step 7: Stdin extraction (must happen before skip/NON_PAL checks, since echo|pal is NON_PAL)
    String stdinData = null;
    String palCommand = text;
    DocCommandType effectiveType = command.getType();

    Matcher echoMatcher = ECHO_PIPE_PATTERN.matcher(text.strip());
    if (echoMatcher.matches()) {
      stdinData = echoMatcher.group(1);
      palCommand = echoMatcher.group(2).strip();
      substitutions.add("Extracted stdin from echo pipe");
      effectiveType = DocCommandType.classify(palCommand);
    } else {
      Matcher echoDoubleMatcher = ECHO_PIPE_DOUBLE_QUOTE_PATTERN.matcher(text.strip());
      if (echoDoubleMatcher.matches()) {
        stdinData = echoDoubleMatcher.group(1);
        palCommand = echoDoubleMatcher.group(2).strip();
        substitutions.add("Extracted stdin from echo pipe (double-quoted)");
        effectiveType = DocCommandType.classify(palCommand);
      } else {
        // Handle "cat ... | pal ..." patterns (heredoc body comes from DocCommand)
        Matcher catPipeMatcher = CAT_PIPE_PATTERN.matcher(text.strip());
        if (catPipeMatcher.matches()) {
          palCommand = catPipeMatcher.group(1).strip();
          substitutions.add("Extracted pal command from cat pipe");
          effectiveType = DocCommandType.classify(palCommand);
          if (command.getHeredocBody() != null) {
            stdinData = command.getHeredocBody();
            substitutions.add("Using heredoc body as stdin data");
          }
        }
      }
    }

    // Check for skip conditions
    String skipReason = determineSkipReason(palCommand, effectiveType);
    if (skipReason != null) {
      return TransformedCommand.skipped(skipReason, substitutions);
    }

    // Handle NON_PAL commands (after stdin extraction reclassification)
    if (effectiveType == DocCommandType.NON_PAL) {
      return TransformedCommand.skipped("Non-PAL command", substitutions);
    }

    // Strip leading $ and env var assignments for processing
    palCommand = stripShellPrefix(palCommand);

    // Strip inline comments (e.g., "pal init my-project  # Create new project")
    palCommand = stripInlineComment(palCommand, substitutions);

    // Strip trailing pipe-to-non-pal commands (e.g., "pal peer ls ... | grep ...")
    palCommand = stripTrailingPipe(palCommand, substitutions);

    // Strip shell redirects (e.g., "> output.json", ">> file.txt")
    palCommand = stripShellRedirect(palCommand, substitutions);

    // Fix negated boolean flags (e.g., "--in-flight-tracking=false" -> skip the =false)
    palCommand = fixNegatedBooleanFlags(palCommand, substitutions);

    // Fix obsolete/incorrect short flags from docs (e.g., -t -> --types for print commands)
    palCommand = fixObsoleteFlags(palCommand, effectiveType, substitutions);

    // Step 1: Address substitution
    palCommand = substituteEtcdAddress(palCommand, substitutions);
    palCommand = substituteKafkaAddress(palCommand, substitutions);

    // Step 2: Classpath substitution
    palCommand = substituteClasspath(palCommand, substitutions);
    palCommand = substituteJarFlag(palCommand, substitutions);

    // Step 3: Main class substitution
    palCommand = substituteMainClass(palCommand, substitutions);

    // Step 4: Name uniquification
    WalResult walResult = uniquifyWalName(palCommand, text, substitutions);
    palCommand = walResult.command;

    PeerNameResult peerResult = uniquifyPeerName(palCommand, text, substitutions);
    palCommand = peerResult.command;

    // Step 5: Chronicle path handling
    ChronicleResult chronicleResult = handleChroniclePath(palCommand, text, substitutions);
    palCommand = chronicleResult.command;

    // Step 6: Flag adaptation
    boolean isRunCommand = effectiveType == DocCommandType.RUN;
    boolean isInitCommand = effectiveType == DocCommandType.INIT;

    boolean isReplayCommand = effectiveType == DocCommandType.REPLAY;

    if (isRunCommand) {
      palCommand = appendRpcDefaultAction(palCommand, substitutions);
      palCommand = handleRpcPolicy(palCommand, substitutions);
      palCommand = ensureDirectoryFlag(palCommand, substitutions);
      palCommand = ensureKafkaFlag(palCommand, walResult, chronicleResult, substitutions);
    }
    if (isReplayCommand) {
      palCommand = handleRpcPolicy(palCommand, substitutions);
    }
    if (isInitCommand) {
      palCommand = appendDryRun(palCommand, substitutions);
      palCommand = appendNonInteractive(palCommand, substitutions);
      palCommand = appendGroupId(palCommand, substitutions);
    }

    // Step 8: Lifecycle classification
    boolean needsPeerLifecycle = isRunCommand;
    boolean longRunning = isRunCommand && isLongRunning(palCommand);

    // Parse into subcommand parts and args
    SubcommandParseResult parseResult = parseCommand(palCommand);

    for (String sub : substitutions) {
      LOG.info("{}: {}", command, sub);
    }

    return new TransformedCommand(
        parseResult.subcommandParts,
        parseResult.args,
        stdinData,
        false,
        null,
        substitutions,
        walResult.walName,
        chronicleResult.path,
        peerResult.peerName,
        needsPeerLifecycle,
        longRunning);
  }

  /**
   * Determines whether a command should be skipped and returns the reason, or {@code null} if it
   * can be tested.
   */
  private String determineSkipReason(String text, DocCommandType type) {
    if (type == DocCommandType.SKIPPED) {
      return "Unrecognizable PAL command";
    }

    // Commands requiring JavaFX runtime
    if (text.contains("--fx-thread")) {
      return "Requires JavaFX runtime (--fx-thread)";
    }

    // Synopsis/usage lines contain meta-syntax placeholders that are not real arguments
    if (isSynopsisLine(text)) {
      return "Synopsis/usage line with meta-syntax placeholders";
    }

    // Ellipsis-truncated UUIDs (e.g., "abc12345-..." or "550e8400-e29b...") are placeholder
    // examples, not real identifiers that can be looked up.
    if (ELLIPSIS_UUID_PATTERN.matcher(text).find()) {
      return "Contains ellipsis-truncated UUID placeholder";
    }

    // Direct WebSocket/HTTP connection URLs (ws://, http://) connect to specific endpoints
    // that don't exist in the test environment.
    if (text.contains("ws://") || text.contains("wss://")) {
      return "Direct WebSocket connection URL (no test WS endpoint)";
    }

    // Commands with ellipsis as arguments (e.g., "pal run -n user-service ...") are
    // incomplete examples that cannot be executed.
    if (BARE_ELLIPSIS_PATTERN.matcher(text).find()) {
      return "Contains bare ellipsis placeholder";
    }

    return null;
  }

  /**
   * Checks whether a command line is a synopsis/usage line containing meta-syntax placeholders.
   * These lines document CLI syntax but are not executable commands.
   */
  private static boolean isSynopsisLine(String text) {
    String stripped = stripShellPrefix(text);
    return SYNOPSIS_PLACEHOLDER_PATTERN.matcher(stripped).find();
  }

  /** Strips leading {@code $}, whitespace, and environment variable assignments. */
  private static String stripShellPrefix(String command) {
    String stripped = command.strip();
    if (stripped.startsWith("$")) {
      stripped = stripped.substring(1).stripLeading();
    }
    // Strip env var assignments like JAVA_TOOL_OPTIONS="..." or VAR=value
    while (true) {
      Matcher m =
          Pattern.compile("^[A-Z_][A-Z0-9_]*=(?:\"[^\"]*\"|'[^']*'|\\S+)\\s+").matcher(stripped);
      if (m.find()) {
        stripped = stripped.substring(m.end());
      } else {
        break;
      }
    }
    return stripped;
  }

  /** Substitutes etcd {@code -d} address with the test palDirectoryUrl. */
  private String substituteEtcdAddress(String command, List<String> substitutions) {
    Matcher m = ETCD_ADDRESS_PATTERN.matcher(command);
    if (m.find()) {
      String original = m.group(2);
      if (!original.equals(palDirectoryUrl)) {
        String result = m.replaceAll("$1" + Matcher.quoteReplacement(palDirectoryUrl));
        substitutions.add("Replaced etcd address " + original + " with " + palDirectoryUrl);
        return result;
      }
    }
    return command;
  }

  /** Substitutes Kafka {@code -k} and {@code -b} addresses with the test kafkaServers. */
  private String substituteKafkaAddress(String command, List<String> substitutions) {
    Matcher km = KAFKA_K_ADDRESS_PATTERN.matcher(command);
    if (km.find()) {
      String original = km.group(2);
      if (!original.equals(kafkaServers)) {
        command = km.replaceAll("$1" + Matcher.quoteReplacement(kafkaServers));
        substitutions.add("Replaced Kafka address (-k) " + original + " with " + kafkaServers);
      }
    }
    Matcher bm = KAFKA_B_ADDRESS_PATTERN.matcher(command);
    if (bm.find()) {
      String original = bm.group(2);
      // Replace -b with -k since -b is not a valid flag for most subcommands
      command = bm.replaceAll("-k " + Matcher.quoteReplacement(kafkaServers));
      substitutions.add(
          "Replaced -b " + original + " with -k " + kafkaServers + " (-b is not a valid flag)");
    }
    return command;
  }

  /** Substitutes placeholder classpath values with the itt-apps classpath. */
  private String substituteClasspath(String command, List<String> substitutions) {
    Matcher m = CLASSPATH_PLACEHOLDER_PATTERN.matcher(command);
    if (m.find()) {
      String original = m.group(2);
      String result = m.replaceAll("$1" + Matcher.quoteReplacement(ittAppsClasspath));
      substitutions.add("Replaced classpath " + original + " with itt-apps classpath");
      return result;
    }
    return command;
  }

  /** Converts {@code -jar <path>} to {@code -cp <ittAppsClasspath>}. */
  private String substituteJarFlag(String command, List<String> substitutions) {
    Matcher m = JAR_FLAG_PATTERN.matcher(command);
    if (m.find()) {
      String original = m.group(1);
      command = m.replaceFirst("-cp " + Matcher.quoteReplacement(ittAppsClasspath));
      substitutions.add("Replaced -jar " + original + " with -cp itt-apps classpath");
      // If no main class is present after substitution, append default
      if (!containsMainClass(command)) {
        command = command + " " + DEFAULT_MAIN_CLASS;
        substitutions.add("Appended default main class " + DEFAULT_MAIN_CLASS);
      }
    }
    return command;
  }

  /** Substitutes placeholder main classes with itt-apps classes. */
  private String substituteMainClass(String command, List<String> substitutions) {
    Matcher m = MAIN_CLASS_PLACEHOLDER_PATTERN.matcher(command);
    StringBuffer sb = new StringBuffer();
    boolean found = false;
    while (m.find()) {
      String original = m.group(1);
      String replacement = resolveMainClass(original);
      if (!original.equals(replacement)) {
        m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        if (!found) {
          substitutions.add("Replaced main class " + original + " with " + replacement);
          found = true;
        }
      }
    }
    if (found) {
      m.appendTail(sb);
      return sb.toString();
    }
    return command;
  }

  /** Resolves a placeholder main class to its itt-apps equivalent. */
  private static String resolveMainClass(String placeholder) {
    String mapped = MAIN_CLASS_MAP.get(placeholder);
    if (mapped != null) {
      return mapped;
    }
    // Any unrecognized com.example.*, tutorial.*, com.mycompany.* -> default
    if (placeholder.startsWith("com.example.")
        || placeholder.startsWith("tutorial.")
        || placeholder.startsWith("com.mycompany.")) {
      return DEFAULT_MAIN_CLASS;
    }
    return placeholder;
  }

  /** Checks whether the command already contains a recognized main class token. */
  private boolean containsMainClass(String command) {
    return MAIN_CLASS_PLACEHOLDER_PATTERN.matcher(command).find()
        || command.contains("io.quasient.");
  }

  /** Uniquifies WAL names with a {@code doc-test-wal-} prefix and a deterministic hash suffix. */
  private WalResult uniquifyWalName(
      String command, String originalText, List<String> substitutions) {
    Matcher m = WAL_NAME_PATTERN.matcher(command);
    if (m.find()) {
      String original = m.group(2);
      String hash = shortHash(originalText);
      String uniqueName = "doc-test-wal-" + original + "-" + hash;
      String result = m.replaceFirst("$1" + Matcher.quoteReplacement(uniqueName));
      substitutions.add("Uniquified WAL name " + original + " to " + uniqueName);
      return new WalResult(result, uniqueName);
    }
    return new WalResult(command, null);
  }

  /** Uniquifies peer names with a {@code doc-test-} prefix and a deterministic hash suffix. */
  private PeerNameResult uniquifyPeerName(
      String command, String originalText, List<String> substitutions) {
    Matcher m = PEER_NAME_PATTERN.matcher(command);
    if (m.find()) {
      String original = m.group(2);
      String hash = shortHash(originalText);
      String uniqueName = "doc-test-" + original + "-" + hash;
      String result = m.replaceFirst("$1" + Matcher.quoteReplacement(uniqueName));
      substitutions.add("Uniquified peer name " + original + " to " + uniqueName);
      return new PeerNameResult(result, uniqueName);
    }
    return new PeerNameResult(command, null);
  }

  /** Handles {@code file:<path>} Chronicle paths by redirecting to a unique temp path. */
  private ChronicleResult handleChroniclePath(
      String command, String originalText, List<String> substitutions) {
    Matcher m = CHRONICLE_PATH_PATTERN.matcher(command);
    if (m.find()) {
      String originalPath = m.group(1);
      // Extract the leaf name from the path for the redirected location
      Path parsed = Paths.get(originalPath);
      String leafName = parsed.getFileName().toString();
      String hash = shortHash(originalText);
      String uniqueDir = "pal-doc-test-" + hash;
      Path chroniclePath = Paths.get("/tmp", uniqueDir, leafName);
      String newPath = "file:" + chroniclePath;
      command = m.replaceFirst(Matcher.quoteReplacement(newPath));
      substitutions.add("Redirected Chronicle path file:" + originalPath + " to " + newPath);
      return new ChronicleResult(command, chroniclePath);
    }
    return new ChronicleResult(command, null);
  }

  /** Appends {@code --rpc-default-action ALLOW} to run commands if not already present. */
  private String appendRpcDefaultAction(String command, List<String> substitutions) {
    if (!command.contains("--rpc-default-action")) {
      substitutions.add("Appended --rpc-default-action ALLOW");
      return command + " --rpc-default-action ALLOW";
    }
    return command;
  }

  /** Appends {@code --dry-run} to init commands if not already present. */
  private String appendDryRun(String command, List<String> substitutions) {
    if (!command.contains("--dry-run")) {
      substitutions.add("Appended --dry-run");
      return command + " --dry-run";
    }
    return command;
  }

  /** Appends {@code -y} (non-interactive) to init commands if not already present. */
  private String appendNonInteractive(String command, List<String> substitutions) {
    if (!command.contains("-y") && !command.contains("--non-interactive")) {
      substitutions.add("Appended -y (non-interactive)");
      return command + " -y";
    }
    return command;
  }

  /**
   * Appends required init flags ({@code --group-id}, {@code --artifact-id}, {@code --main-class})
   * to init commands if not already present and not running as a service.
   */
  private String appendGroupId(String command, List<String> substitutions) {
    if (!command.contains("--group-id")) {
      command = command + " --group-id com.example";
      substitutions.add("Appended --group-id com.example");
    }
    if (!command.contains("--artifact-id")) {
      command = command + " --artifact-id doc-test";
      substitutions.add("Appended --artifact-id doc-test");
    }
    if (!command.contains("--main-class") && !command.contains("--as-service")) {
      command = command + " --main-class com.example.Main";
      substitutions.add("Appended --main-class com.example.Main");
    }
    return command;
  }

  /**
   * Strips inline comments from a command. For example, {@code "pal init my-project # Create new
   * project"} becomes {@code "pal init my-project"}.
   */
  private static String stripInlineComment(String command, List<String> substitutions) {
    // Only strip # that appears after whitespace (not inside flag values)
    int hashIdx = command.indexOf(" #");
    if (hashIdx > 0) {
      String stripped = command.substring(0, hashIdx).stripTrailing();
      substitutions.add("Stripped inline comment");
      return stripped;
    }
    return command;
  }

  /**
   * Strips trailing pipe-to-non-pal commands. For example, {@code "pal peer ls -d localhost:2379 |
   * grep callback-peer"} becomes {@code "pal peer ls -d localhost:2379"}.
   */
  private static String stripTrailingPipe(String command, List<String> substitutions) {
    // Only strip pipe that goes to a non-pal command
    int pipeIdx = command.lastIndexOf(" | ");
    if (pipeIdx > 0) {
      String afterPipe = command.substring(pipeIdx + 3).strip();
      if (!afterPipe.startsWith("pal ")) {
        String stripped = command.substring(0, pipeIdx).stripTrailing();
        substitutions.add("Stripped trailing pipe to non-pal command: | " + afterPipe);
        return stripped;
      }
    }
    return command;
  }

  /**
   * Strips shell output redirects from a command. For example, {@code "pal log print my-log --json
   * > output.json"} becomes {@code "pal log print my-log --json"}.
   */
  private static String stripShellRedirect(String command, List<String> substitutions) {
    // Match > or >> followed by a filename
    int redirectIdx = command.indexOf(" > ");
    if (redirectIdx < 0) {
      redirectIdx = command.indexOf(" >> ");
    }
    if (redirectIdx > 0) {
      String stripped = command.substring(0, redirectIdx).stripTrailing();
      substitutions.add("Stripped shell redirect");
      return stripped;
    }
    return command;
  }

  /**
   * Fixes negated boolean flags that use {@code =false} or {@code =true} syntax, which PicoCLI does
   * not accept for boolean flags. For example, {@code --in-flight-tracking=false} becomes {@code
   * --no-in-flight-tracking} (if the flag supports negation) or is simply removed.
   */
  private static String fixNegatedBooleanFlags(String command, List<String> substitutions) {
    // Handle --in-flight-tracking=false -> remove the flag entirely (disables tracking)
    if (command.contains("--in-flight-tracking=false")) {
      command = command.replace("--in-flight-tracking=false", "").replaceAll("\\s+", " ").strip();
      substitutions.add("Removed --in-flight-tracking=false (default is disabled)");
    }
    return command;
  }

  /**
   * Fixes obsolete or incorrect short flags that appear in documentation but are not valid for the
   * actual CLI subcommand.
   *
   * <ul>
   *   <li>{@code peer print -t TYPE} → {@code peer print --types TYPE} (no {@code -t} alias)
   *   <li>{@code log print -t TYPE} → {@code log print --types TYPE} (no {@code -t} alias)
   *   <li>{@code peer print -f} → {@code peer print --full} ({@code -f} not valid for peer print)
   * </ul>
   */
  private static String fixObsoleteFlags(
      String command, DocCommandType type, List<String> substitutions) {
    boolean isPeerPrint = type == DocCommandType.PEER_PRINT;
    boolean isLogPrint = type == DocCommandType.LOG_PRINT;

    // -t is only a valid short alias for log stats and peer call/log call (--num-threads).
    // For peer print and log print, -t should be --types.
    if ((isPeerPrint || isLogPrint) && command.contains(" -t ")) {
      command = command.replace(" -t ", " --types ");
      substitutions.add("Replaced -t with --types (no -t alias for this subcommand)");
    }

    // peer print has no -f flag; in docs it means --full
    if (isPeerPrint && command.contains(" -f")) {
      // Only replace standalone -f (not -fp, -ft, etc.)
      command = command.replaceAll("\\s+-f\\b", " --full");
      substitutions.add("Replaced -f with --full (peer print has no -f alias)");
    }

    return command;
  }

  /**
   * Handles {@code --rpc-policy <path>} flags by creating a temporary RPC policy YAML file if the
   * referenced file does not exist as a real file.
   */
  private String handleRpcPolicy(String command, List<String> substitutions) {
    // Replace non-existent policy file references with temp file paths.
    // Handles --rpc-policy (run), --policy (replay), and --scope-policy (run).
    command = replacePolicyFlag(command, "--rpc-policy", substitutions);
    command = replacePolicyFlag(command, "--policy", substitutions);
    command = replacePolicyFlag(command, "--scope-policy", substitutions);
    return command;
  }

  /**
   * Replaces a policy flag's file path argument with a temporary YAML file if the referenced file
   * does not exist.
   */
  private String replacePolicyFlag(String command, String flag, List<String> substitutions) {
    Pattern policyPattern = Pattern.compile(Pattern.quote(flag) + "\\s+([^\\s]+)");
    Matcher m = policyPattern.matcher(command);
    if (m.find()) {
      String path = m.group(1);
      if (!Paths.get(path).toFile().exists()) {
        String tempPath = createTempRpcPolicyPath();
        if (tempPath != null) {
          command = command.replace(flag + " " + path, flag + " " + tempPath);
          substitutions.add("Replaced " + flag + " " + path + " with temp policy " + tempPath);
        }
      }
    }
    return command;
  }

  /**
   * Creates a temporary RPC policy YAML file path. Returns the path string, or {@code null} if
   * creation fails.
   */
  private String createTempRpcPolicyPath() {
    try {
      Path tempFile = Files.createTempFile("doc-test-rpc-policy-", ".yaml");
      tempFile.toFile().deleteOnExit();
      Files.writeString(
          tempFile,
          "# Temporary RPC policy for doc snippet testing\nrules: []\n",
          StandardCharsets.UTF_8);
      return tempFile.toAbsolutePath().toString();
    } catch (IOException e) {
      LOG.warn("Failed to create temp RPC policy file: {}", e.getMessage());
      return null;
    }
  }

  /**
   * Ensures a run command has a {@code -d} flag pointing to the test etcd directory. Commands
   * without {@code -d} rely on the {@code PAL_DIRECTORY} environment variable, which is cleared in
   * the test environment.
   */
  private String ensureDirectoryFlag(String command, List<String> substitutions) {
    if (!command.contains(" -d ") && !command.contains(" --directory ")) {
      command = command + " -d " + palDirectoryUrl;
      substitutions.add("Appended -d " + palDirectoryUrl + " (required for test environment)");
    }
    return command;
  }

  /**
   * Ensures a non-Chronicle run command has a {@code -k} flag pointing to the test Kafka servers.
   * Chronicle-based commands (WAL starting with {@code file:}) do not need Kafka.
   */
  private String ensureKafkaFlag(
      String command,
      WalResult walResult,
      ChronicleResult chronicleResult,
      List<String> substitutions) {
    boolean usesChronicle =
        chronicleResult.path != null
            || command.contains("file:")
            || (walResult.walName != null && walResult.walName.startsWith("file:"));
    if (!usesChronicle && !command.contains(" -k ") && !command.contains(" --kafka-servers ")) {
      command = command + " -k " + kafkaServers;
      substitutions.add("Appended -k " + kafkaServers + " (required for non-Chronicle WAL)");
    }
    return command;
  }

  /** Determines whether a run command is long-running (needs launchPeer/stopPeer). */
  private static boolean isLongRunning(String command) {
    return command.contains("--json-rpc")
        || command.contains("--zmq-rpc")
        || command.contains("-j ")
        || command.contains("-r ")
        || command.contains("--as-service");
  }

  /**
   * Generates a short deterministic hash from the original command text for uniquification.
   *
   * @param text the original command text
   * @return a short hex hash string
   */
  static String shortHash(String text) {
    int hash = text.hashCode();
    return String.format("%08x", hash);
  }

  /**
   * Parses a PAL command into subcommand parts and remaining arguments.
   *
   * <p>For example, {@code "pal peer ls -d localhost:2379 -l"} yields subcommand parts {@code
   * ["peer", "ls"]} and args {@code ["-d", "localhost:2379", "-l"]}.
   */
  private SubcommandParseResult parseCommand(String command) {
    // Strip "pal " prefix
    String afterPal = command;
    if (afterPal.startsWith("pal ")) {
      afterPal = afterPal.substring(4).strip();
    } else if (afterPal.equals("pal")) {
      return new SubcommandParseResult(new String[] {"help"}, new String[0]);
    }

    List<String> tokenList = WHITESPACE_SPLITTER.splitToList(afterPal);
    String[] tokens = tokenList.toArray(new String[0]);
    if (tokens.length == 0) {
      return new SubcommandParseResult(new String[] {"help"}, new String[0]);
    }

    String first = tokens[0];

    // Top-level commands: run, replay, init, help
    if (isTopLevelCommand(first)) {
      String[] args =
          tokens.length > 1 ? Arrays.copyOfRange(tokens, 1, tokens.length) : new String[0];
      return new SubcommandParseResult(new String[] {first}, args);
    }

    // Entity + subcommand: peer ls, log print, intercept apply, etc.
    if (isEntity(first) && tokens.length > 1 && isSubcommand(tokens[1])) {
      String[] args =
          tokens.length > 2 ? Arrays.copyOfRange(tokens, 2, tokens.length) : new String[0];
      return new SubcommandParseResult(new String[] {first, tokens[1]}, args);
    }

    // Entity aliases: peers, logs, intercepts
    if (isEntityAlias(first)) {
      String[] args =
          tokens.length > 1 ? Arrays.copyOfRange(tokens, 1, tokens.length) : new String[0];
      return new SubcommandParseResult(new String[] {first}, args);
    }

    // Fallback: treat everything as args with first token as subcommand
    String[] args =
        tokens.length > 1 ? Arrays.copyOfRange(tokens, 1, tokens.length) : new String[0];
    return new SubcommandParseResult(new String[] {first}, args);
  }

  /** Checks whether a token is a top-level command. */
  private static boolean isTopLevelCommand(String token) {
    return "run".equals(token)
        || "replay".equals(token)
        || "init".equals(token)
        || "help".equals(token);
  }

  /** Checks whether a token is an entity name. */
  private static boolean isEntity(String token) {
    return "peer".equals(token) || "log".equals(token) || "intercept".equals(token);
  }

  /** Checks whether a token is an entity alias. */
  private static boolean isEntityAlias(String token) {
    return "peers".equals(token) || "logs".equals(token) || "intercepts".equals(token);
  }

  /** Checks whether a token is a valid subcommand for an entity. */
  private static boolean isSubcommand(String token) {
    return "ls".equals(token)
        || "call".equals(token)
        || "print".equals(token)
        || "rm".equals(token)
        || "prune".equals(token)
        || "stats".equals(token)
        || "index".equals(token)
        || "apply".equals(token)
        || "diff".equals(token)
        || "status".equals(token);
  }

  /** Intermediate result for WAL name uniquification. */
  private static class WalResult {

    /** The command with uniquified WAL name. */
    final String command;

    /** The uniquified WAL name, or {@code null} if no WAL was present. */
    final String walName;

    WalResult(String command, String walName) {
      this.command = command;
      this.walName = walName;
    }
  }

  /** Intermediate result for peer name uniquification. */
  private static class PeerNameResult {

    /** The command with uniquified peer name. */
    final String command;

    /** The uniquified peer name, or {@code null} if no peer name was present. */
    final String peerName;

    PeerNameResult(String command, String peerName) {
      this.command = command;
      this.peerName = peerName;
    }
  }

  /** Intermediate result for Chronicle path handling. */
  private static class ChronicleResult {

    /** The command with redirected Chronicle path. */
    final String command;

    /** The Chronicle temp path, or {@code null} if no Chronicle path was present. */
    final Path path;

    ChronicleResult(String command, Path path) {
      this.command = command;
      this.path = path;
    }
  }

  /** Result of parsing a command into subcommand parts and args. */
  private static class SubcommandParseResult {

    /** The subcommand parts (e.g., {@code ["peer", "ls"]}). */
    final String[] subcommandParts;

    /** The remaining arguments after the subcommand. */
    final String[] args;

    SubcommandParseResult(String[] subcommandParts, String[] args) {
      this.subcommandParts = subcommandParts;
      this.args = args;
    }
  }

  /**
   * Result of transforming a documentation command for test execution.
   *
   * <p>Contains the adapted argument list, metadata about what was changed, and lifecycle
   * information for the test harness.
   */
  public static class TransformedCommand {

    /** Subcommand parts for {@code runCliSubcommand()}, e.g., {@code ["peer", "ls"]}. */
    private final String[] subcommandParts;

    /** Remaining arguments after the subcommand. */
    private final String[] args;

    /** Extracted stdin data from echo/heredoc pipes, or {@code null}. */
    private final String stdinData;

    /** Whether this command was skipped. */
    private final boolean skipped;

    /** Reason for skipping, or {@code null} if not skipped. */
    private final String skipReason;

    /** Human-readable log of substitutions performed. */
    private final List<String> substitutions;

    /** Uniquified WAL name for cleanup tracking, or {@code null}. */
    private final String uniqueWalName;

    /** Chronicle temp path for cleanup tracking, or {@code null}. */
    private final Path chroniclePath;

    /** Uniquified peer name for cleanup tracking, or {@code null}. */
    private final String peerName;

    /** Whether this command needs peer lifecycle (pal run commands). */
    private final boolean needsPeerLifecycle;

    /** Whether this is a long-running command (needs launchPeer/stopPeer). */
    private final boolean longRunning;

    /**
     * Constructs a new {@code TransformedCommand}.
     *
     * @param subcommandParts subcommand parts for CLI execution
     * @param args remaining arguments
     * @param stdinData extracted stdin data, or null
     * @param skipped whether this command was skipped
     * @param skipReason reason for skipping, or null
     * @param substitutions log of substitutions performed
     * @param uniqueWalName uniquified WAL name, or null
     * @param chroniclePath Chronicle temp path, or null
     * @param peerName uniquified peer name, or null
     * @param needsPeerLifecycle whether this needs peer lifecycle management
     * @param longRunning whether this is a long-running command
     */
    TransformedCommand(
        String[] subcommandParts,
        String[] args,
        String stdinData,
        boolean skipped,
        String skipReason,
        List<String> substitutions,
        String uniqueWalName,
        Path chroniclePath,
        String peerName,
        boolean needsPeerLifecycle,
        boolean longRunning) {
      this.subcommandParts = subcommandParts;
      this.args = args;
      this.stdinData = stdinData;
      this.skipped = skipped;
      this.skipReason = skipReason;
      this.substitutions = substitutions;
      this.uniqueWalName = uniqueWalName;
      this.chroniclePath = chroniclePath;
      this.peerName = peerName;
      this.needsPeerLifecycle = needsPeerLifecycle;
      this.longRunning = longRunning;
    }

    /**
     * Creates a skipped result with the given reason.
     *
     * @param reason the skip reason
     * @param substitutions any substitutions logged before skip determination
     * @return a skipped {@code TransformedCommand}
     */
    static TransformedCommand skipped(String reason, List<String> substitutions) {
      return new TransformedCommand(
          new String[0],
          new String[0],
          null,
          true,
          reason,
          substitutions,
          null,
          null,
          null,
          false,
          false);
    }

    /**
     * Returns the subcommand parts for CLI execution.
     *
     * @return subcommand parts array, e.g., {@code ["peer", "ls"]}
     */
    public String[] getSubcommandParts() {
      return subcommandParts;
    }

    /**
     * Returns the remaining arguments after the subcommand.
     *
     * @return argument array
     */
    public String[] getArgs() {
      return args;
    }

    /**
     * Returns the extracted stdin data, or {@code null}.
     *
     * @return stdin data string, or null
     */
    public String getStdinData() {
      return stdinData;
    }

    /**
     * Returns whether this command was skipped.
     *
     * @return true if skipped
     */
    public boolean isSkipped() {
      return skipped;
    }

    /**
     * Returns the skip reason, or {@code null} if not skipped.
     *
     * @return skip reason string, or null
     */
    public String getSkipReason() {
      return skipReason;
    }

    /**
     * Returns the human-readable log of substitutions performed.
     *
     * @return list of substitution descriptions
     */
    public List<String> getSubstitutions() {
      return substitutions;
    }

    /**
     * Returns the uniquified WAL name for cleanup tracking, or {@code null}.
     *
     * @return WAL name, or null
     */
    public String getUniqueWalName() {
      return uniqueWalName;
    }

    /**
     * Returns the Chronicle temp path for cleanup tracking, or {@code null}.
     *
     * @return Chronicle path, or null
     */
    public Path getChroniclePath() {
      return chroniclePath;
    }

    /**
     * Returns the uniquified peer name for cleanup tracking, or {@code null}.
     *
     * @return peer name, or null
     */
    public String getPeerName() {
      return peerName;
    }

    /**
     * Returns whether this command needs peer lifecycle management.
     *
     * @return true for {@code pal run} commands
     */
    public boolean isNeedsPeerLifecycle() {
      return needsPeerLifecycle;
    }

    /**
     * Returns whether this is a long-running command.
     *
     * @return true if the command uses {@code --json-rpc}, {@code --zmq-rpc}, or {@code
     *     --as-service}
     */
    public boolean isLongRunning() {
      return longRunning;
    }
  }
}
