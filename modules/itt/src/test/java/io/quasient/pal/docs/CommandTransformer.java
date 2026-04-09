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
              + "|build/classes/java/main|service\\.jar|target/[^\\s]*\\.jar)");

  /** Pattern matching {@code -jar <path>}. */
  private static final Pattern JAR_FLAG_PATTERN = Pattern.compile("-jar\\s+([^\\s]+)");

  /** Pattern matching {@code echo '...' | pal ...} for stdin extraction. */
  private static final Pattern ECHO_PIPE_PATTERN =
      Pattern.compile("^echo\\s+'([^']*)'\\s*\\|\\s*(.*)$");

  /** Pattern matching {@code echo "..." | pal ...} for stdin extraction with double quotes. */
  private static final Pattern ECHO_PIPE_DOUBLE_QUOTE_PATTERN =
      Pattern.compile("^echo\\s+\"([^\"]*)\"\\s*\\|\\s*(.*)$");

  /** Pattern matching {@code file:/tmp/<name>} Chronicle paths. */
  private static final Pattern CHRONICLE_PATH_PATTERN = Pattern.compile("file:/tmp/([^\\s]+)");

  /** Pattern matching {@code --wal <name>} where name doesn't start with {@code file:}. */
  private static final Pattern WAL_NAME_PATTERN = Pattern.compile("(--wal\\s+)((?!file:)[^\\s]+)");

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

    if (isRunCommand) {
      palCommand = appendRpcDefaultAction(palCommand, substitutions);
    }
    if (isInitCommand) {
      palCommand = appendDryRun(palCommand, substitutions);
      palCommand = appendNonInteractive(palCommand, substitutions);
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

    return null;
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
      if (!original.equals(kafkaServers)) {
        command = bm.replaceAll("$1" + Matcher.quoteReplacement(kafkaServers));
        substitutions.add(
            "Replaced Kafka bootstrap address (-b) " + original + " with " + kafkaServers);
      }
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

  /** Handles Chronicle {@code file:/tmp/<name>} paths by redirecting to a unique temp path. */
  private ChronicleResult handleChroniclePath(
      String command, String originalText, List<String> substitutions) {
    Matcher m = CHRONICLE_PATH_PATTERN.matcher(command);
    if (m.find()) {
      String originalName = m.group(1);
      String hash = shortHash(originalText);
      String uniqueDir = "pal-doc-test-" + hash;
      Path chroniclePath = Paths.get("/tmp", uniqueDir, originalName);
      String newPath = "file:" + chroniclePath;
      command = m.replaceFirst(Matcher.quoteReplacement(newPath));
      substitutions.add("Redirected Chronicle path file:/tmp/" + originalName + " to " + newPath);
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
