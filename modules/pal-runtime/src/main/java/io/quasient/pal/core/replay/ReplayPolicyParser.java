/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.core.replay;

import io.quasient.pal.core.replay.ReplayPolicy.ReplayAction;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.YAMLException;

/**
 * Parses replay policy configuration from YAML content and CLI options into a {@link ReplayPolicy}.
 *
 * <p>Supports four sources of rules, applied in priority order:
 *
 * <ol>
 *   <li><b>Built-in I/O rules</b> ({@code --shield-io}): I/O and non-deterministic operation stubs
 *   <li><b>Built-in JavaFX rules</b> ({@code --shield-fx}): JavaFX animation and timing stubs
 *   <li><b>CLI patterns</b> ({@code --re-execute}, {@code --stub}): override defaults and YAML
 *       rules but not shields
 *   <li><b>YAML file</b> ({@code --replay-policy}): user-defined rules with a default action
 * </ol>
 *
 * <p>The {@code --stub-all-else} flag sets the default action to {@link ReplayAction#STUB_FROM_WAL}
 * so that any operation not matching an explicit rule is stubbed.
 *
 * <h3>YAML Format</h3>
 *
 * <pre>{@code
 * defaultAction: RE_EXECUTE
 *
 * rules:
 *   - class: "java.lang.System"
 *     method: "currentTimeMillis"
 *     action: STUB_FROM_WAL
 *
 *   - class: "java.io.**"
 *     method: "**"
 *     action: STUB_FROM_WAL
 * }</pre>
 */
public final class ReplayPolicyParser {

  /** Private constructor to prevent instantiation of this utility class. */
  private ReplayPolicyParser() {}

  /**
   * Parses a YAML string into a {@link ReplayPolicy}.
   *
   * @param yamlContent the YAML content to parse
   * @return a replay policy with the parsed rules and default action
   * @throws IllegalArgumentException if the YAML content is malformed or contains invalid values
   */
  @SuppressWarnings("unchecked")
  public static ReplayPolicy parseYaml(String yamlContent) {
    Map<String, Object> doc;
    try {
      Yaml yaml = new Yaml(new SafeConstructor(new LoaderOptions()));
      Object loaded = yaml.load(yamlContent);
      if (loaded == null) {
        return new ReplayPolicy(Collections.emptyList(), ReplayAction.RE_EXECUTE);
      }
      if (!(loaded instanceof Map)) {
        throw new IllegalArgumentException(
            "Replay policy YAML must be a mapping, got: " + loaded.getClass().getSimpleName());
      }
      doc = (Map<String, Object>) loaded;
    } catch (YAMLException e) {
      throw new IllegalArgumentException("Malformed replay policy YAML: " + e.getMessage(), e);
    }

    ReplayAction defaultAction = ReplayAction.RE_EXECUTE;
    if (doc.containsKey("defaultAction")) {
      defaultAction = parseAction(String.valueOf(doc.get("defaultAction")));
    }

    List<ReplayPolicyRule> rules = new ArrayList<>();
    if (doc.containsKey("rules")) {
      Object rulesObj = doc.get("rules");
      if (!(rulesObj instanceof List)) {
        throw new IllegalArgumentException(
            "Replay policy 'rules' must be a list, got: " + rulesObj.getClass().getSimpleName());
      }
      for (Object entry : (List<Object>) rulesObj) {
        if (!(entry instanceof Map)) {
          throw new IllegalArgumentException(
              "Each rule must be a mapping, got: " + entry.getClass().getSimpleName());
        }
        Map<String, Object> ruleMap = (Map<String, Object>) entry;
        String classPattern = requireString(ruleMap, "class");
        String methodPattern =
            ruleMap.containsKey("method") ? String.valueOf(ruleMap.get("method")) : null;
        ReplayAction action = parseAction(requireString(ruleMap, "action"));
        rules.add(new ReplayPolicyRule(classPattern, methodPattern, action));
      }
    }

    return new ReplayPolicy(rules, defaultAction);
  }

  /**
   * Builds a {@link ReplayPolicy} from CLI options and an optional YAML file.
   *
   * <p>Built-in shield rules ({@code --shield-io}, {@code --shield-fx}) have highest priority and
   * cannot be overridden by CLI patterns. CLI patterns override YAML rules and the default action.
   * The {@code --stub-all-else} flag overrides the default action to {@link
   * ReplayAction#STUB_FROM_WAL}.
   *
   * @param yamlPath path to a YAML replay policy file, or {@code null} if not provided
   * @param shieldIo whether to include built-in I/O shield rules
   * @param shieldFx whether to include built-in JavaFX shield rules
   * @param reExecutePatterns Ant-style patterns for operations to re-execute, or {@code null}
   * @param stubPatterns Ant-style patterns for operations to stub, or {@code null}
   * @param stubAllElse whether to set the default action to {@link ReplayAction#STUB_FROM_WAL}
   * @return a replay policy combining all sources
   * @throws IllegalArgumentException if the YAML file is malformed
   * @throws java.io.UncheckedIOException if the YAML file cannot be read
   */
  public static ReplayPolicy fromOptions(
      String yamlPath,
      boolean shieldIo,
      boolean shieldFx,
      String[] reExecutePatterns,
      String[] stubPatterns,
      boolean stubAllElse) {

    List<ReplayPolicyRule> allRules = new ArrayList<>();
    ReplayAction defaultAction = ReplayAction.RE_EXECUTE;

    // 1. Built-in shield rules have highest priority — they are curated safety
    //    rules that should not be defeated by blanket CLI patterns like '**'.
    if (shieldIo) {
      allRules.addAll(BuiltInStubRules.getIoShieldRules());
    }
    if (shieldFx) {
      allRules.addAll(BuiltInStubRules.getFxShieldRules());
    }

    // 2. CLI patterns override defaults and YAML rules but not shields
    if (reExecutePatterns != null) {
      for (String pattern : reExecutePatterns) {
        allRules.add(patternToRule(pattern, ReplayAction.RE_EXECUTE));
      }
    }
    if (stubPatterns != null) {
      for (String pattern : stubPatterns) {
        allRules.add(patternToRule(pattern, ReplayAction.STUB_FROM_WAL));
      }
    }

    // 3. YAML file rules (lowest priority)
    if (yamlPath != null) {
      String yamlContent;
      try {
        yamlContent = Files.readString(Path.of(yamlPath));
      } catch (IOException e) {
        throw new UncheckedIOException("Cannot read replay policy file: " + yamlPath, e);
      }
      ReplayPolicy yamlPolicy = parseYaml(yamlContent);
      allRules.addAll(yamlPolicy.getRules());
      if (!stubAllElse) {
        defaultAction = yamlPolicy.getDefaultAction();
      }
    }

    // 4. --stub-all-else overrides default
    if (stubAllElse) {
      defaultAction = ReplayAction.STUB_FROM_WAL;
    }

    return new ReplayPolicy(allRules, defaultAction);
  }

  /**
   * Converts a CLI pattern string into a {@link ReplayPolicyRule}.
   *
   * <p>If the pattern contains no method separator (the last dot-separated segment is treated as
   * the method pattern), the method pattern defaults to {@code "**"}.
   *
   * @param pattern the Ant-style pattern (e.g. {@code "com.example.**"})
   * @param action the action to assign
   * @return a new rule
   */
  private static ReplayPolicyRule patternToRule(String pattern, ReplayAction action) {
    return new ReplayPolicyRule(pattern, "**", action);
  }

  /**
   * Parses a replay action from its string name.
   *
   * @param value the action name
   * @return the parsed action
   * @throws IllegalArgumentException if the value is not a valid action name
   */
  private static ReplayAction parseAction(String value) {
    try {
      return ReplayAction.valueOf(value);
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("Unknown replay action: " + value, e);
    }
  }

  /**
   * Extracts a required string value from a rule map.
   *
   * @param map the rule map
   * @param key the required key
   * @return the string value
   * @throws IllegalArgumentException if the key is missing
   */
  private static String requireString(Map<String, Object> map, String key) {
    if (!map.containsKey(key)) {
      throw new IllegalArgumentException("Rule missing required field: " + key);
    }
    return String.valueOf(map.get(key));
  }
}
