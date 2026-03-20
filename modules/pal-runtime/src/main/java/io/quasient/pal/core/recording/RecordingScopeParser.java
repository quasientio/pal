/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.core.recording;

import io.quasient.pal.core.rpc.policy.MemberCategory;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;
import org.yaml.snakeyaml.error.YAMLException;

/**
 * Assembles a {@link RecordingScope} from CLI flags ({@code --scope}, {@code --scope-exclude},
 * {@code --scope-io}, {@code --scope-default}), YAML policy files ({@code --scope-policy}), and
 * built-in presets.
 *
 * <p>Follows the same multi-source assembly pattern as {@link
 * io.quasient.pal.core.replay.ReplayPolicyParser} and {@link
 * io.quasient.pal.core.rpc.policy.RpcPolicyParser}. Rules are assembled in priority order (highest
 * first):
 *
 * <ol>
 *   <li>CLI {@code --scope-exclude} patterns &rarr; {@link RecordingScopeAction#SKIP} rules
 *   <li>CLI {@code --scope} patterns &rarr; {@link RecordingScopeAction#RECORD} rules
 *   <li>{@code --scope-io} preset rules (from {@link BuiltInScopeRules#getIoBoundaryRules()})
 *   <li>YAML file rules (from {@code --scope-policy})
 * </ol>
 *
 * <p>First-match-wins evaluation in {@link RecordingScope} means earlier rules take precedence, so
 * explicit excludes always win over includes.
 *
 * <h3>Default Action Inference</h3>
 *
 * <ul>
 *   <li>Explicit {@code --scope-default} always wins
 *   <li>Only {@code --scope} (include) patterns given &rarr; default {@link
 *       RecordingScopeAction#SKIP}
 *   <li>Only {@code --scope-exclude} patterns given &rarr; default {@link
 *       RecordingScopeAction#RECORD}
 *   <li>Both given without explicit default &rarr; default {@link RecordingScopeAction#SKIP}
 *   <li>Only {@code --scope-io} given &rarr; default {@link RecordingScopeAction#SKIP} (it is an
 *       include preset)
 * </ul>
 *
 * <h3>YAML Format</h3>
 *
 * <pre>{@code
 * defaultAction: SKIP
 * rules:
 *   - class: "com.example.**"
 *     member: "**"
 *     action: RECORD
 *     categories: [METHOD, CONSTRUCTOR]
 * }</pre>
 *
 * <p>When {@code member} is omitted, it defaults to {@code "**"}. When {@code categories} is
 * omitted, it defaults to {@code null} (match all). {@code action} is required.
 *
 * @see RecordingScope
 * @see RecordingScopeRule
 * @see BuiltInScopeRules
 */
public final class RecordingScopeParser {

  /** Logger for emitting the effective scope configuration at INFO level. */
  private static final Logger LOG = LoggerFactory.getLogger(RecordingScopeParser.class);

  /** Private constructor to prevent instantiation of this utility class. */
  private RecordingScopeParser() {}

  /**
   * Builds a {@link RecordingScope} from CLI options, presets, and an optional YAML policy file.
   *
   * <p>When no scope configuration is provided (all arguments are null, false, or empty), returns a
   * permit-all scope with no rules and a default action of {@link RecordingScopeAction#RECORD}.
   *
   * @param yamlPath path to a YAML scope policy file, or {@code null} if not provided
   * @param includeIo whether to include built-in I/O boundary rules ({@code --scope-io})
   * @param scopePatterns Ant-style patterns for operations to record ({@code --scope}), or {@code
   *     null}
   * @param scopeExcludePatterns Ant-style patterns for operations to exclude ({@code
   *     --scope-exclude}), or {@code null}
   * @param defaultActionStr explicit default action ({@code "record"} or {@code "skip"}), or {@code
   *     null} to infer
   * @return a recording scope combining all sources, or a permit-all scope if no configuration is
   *     provided
   * @throws IllegalArgumentException if the YAML file is malformed or contains invalid values
   * @throws UncheckedIOException if the YAML file cannot be read
   */
  public static RecordingScope fromOptions(
      String yamlPath,
      boolean includeIo,
      String[] scopePatterns,
      String[] scopeExcludePatterns,
      String defaultActionStr) {

    boolean hasPatterns = scopePatterns != null && scopePatterns.length > 0;
    boolean hasExcludePatterns = scopeExcludePatterns != null && scopeExcludePatterns.length > 0;

    // Return a permit-all scope when no scope configuration is provided
    if (yamlPath == null
        && !includeIo
        && !hasPatterns
        && !hasExcludePatterns
        && defaultActionStr == null) {
      return new RecordingScope(List.of(), RecordingScopeAction.RECORD);
    }

    List<RecordingScopeRule> allRules = new ArrayList<>();
    RecordingScopeAction yamlDefaultAction = null;

    // 1. CLI --scope-exclude patterns → SKIP rules (highest priority)
    if (hasExcludePatterns) {
      for (String pattern : scopeExcludePatterns) {
        allRules.add(patternToRule(pattern, RecordingScopeAction.SKIP));
      }
    }

    // 2. CLI --scope patterns → RECORD rules
    if (hasPatterns) {
      for (String pattern : scopePatterns) {
        allRules.add(patternToRule(pattern, RecordingScopeAction.RECORD));
      }
    }

    // 3. --scope-io preset rules
    if (includeIo) {
      allRules.addAll(BuiltInScopeRules.getIoBoundaryRules());
    }

    // 4. YAML file rules (lowest priority)
    if (yamlPath != null) {
      String yamlContent;
      try {
        yamlContent = Files.readString(Path.of(yamlPath));
      } catch (IOException e) {
        throw new UncheckedIOException("Cannot read scope policy file: " + yamlPath, e);
      }
      ParsedYaml parsed = parseYamlRaw(yamlContent);
      allRules.addAll(parsed.rules);
      yamlDefaultAction = parsed.defaultAction;
    }

    // Determine effective default action:
    // explicit --scope-default > YAML defaultAction > inference from flags
    RecordingScopeAction defaultAction;
    if (defaultActionStr != null) {
      defaultAction = parseAction(defaultActionStr.toUpperCase(Locale.ROOT));
    } else if (yamlDefaultAction != null) {
      defaultAction = yamlDefaultAction;
    } else {
      defaultAction = inferDefaultAction(hasPatterns, hasExcludePatterns, includeIo);
    }

    RecordingScope scope = new RecordingScope(allRules, defaultAction);

    long recordCount =
        allRules.stream().filter(r -> r.getAction() == RecordingScopeAction.RECORD).count();
    long skipCount =
        allRules.stream().filter(r -> r.getAction() == RecordingScopeAction.SKIP).count();
    LOG.info(
        "Recording scope active: default action = {}, {} rules ({} RECORD, {} SKIP)",
        defaultAction,
        allRules.size(),
        recordCount,
        skipCount);

    return scope;
  }

  /**
   * Parses a YAML string into a {@link RecordingScope}.
   *
   * @param yamlContent the YAML content to parse
   * @return a recording scope with the parsed rules and default action
   * @throws IllegalArgumentException if the YAML content is malformed or contains invalid values
   */
  @SuppressWarnings("unchecked")
  public static RecordingScope parseYaml(String yamlContent) {
    ParsedYaml parsed = parseYamlRaw(yamlContent);
    return new RecordingScope(parsed.rules, parsed.defaultAction);
  }

  /**
   * Converts a CLI pattern string into a {@link RecordingScopeRule} with {@code memberPattern="**"}
   * and {@code null} categories (match all).
   *
   * @param pattern the Ant-style class pattern (e.g. {@code "com.example.**"})
   * @param action the action to assign
   * @return a new rule matching all members of classes that match the pattern
   */
  static RecordingScopeRule patternToRule(String pattern, RecordingScopeAction action) {
    return new RecordingScopeRule(pattern, "**", action, null);
  }

  /**
   * Infers the default action based on which CLI flags are present.
   *
   * @param hasPatterns whether {@code --scope} include patterns are present
   * @param hasExcludePatterns whether {@code --scope-exclude} patterns are present
   * @param includeIo whether {@code --scope-io} is enabled
   * @return the inferred default action
   */
  private static RecordingScopeAction inferDefaultAction(
      boolean hasPatterns, boolean hasExcludePatterns, boolean includeIo) {
    if (hasPatterns) {
      // Include patterns present (with or without exclude) → default SKIP
      return RecordingScopeAction.SKIP;
    }
    if (hasExcludePatterns) {
      // Only exclude patterns → default RECORD
      return RecordingScopeAction.RECORD;
    }
    if (includeIo) {
      // --scope-io is an include preset → default SKIP
      return RecordingScopeAction.SKIP;
    }
    // Fallback (shouldn't reach here given null check in fromOptions)
    return RecordingScopeAction.RECORD;
  }

  /**
   * Internal representation of the raw parsed YAML content, before wrapping in a {@link
   * RecordingScope}.
   */
  static final class ParsedYaml {

    /** The parsed rules. */
    final List<RecordingScopeRule> rules;

    /** The default action from the YAML file. */
    final RecordingScopeAction defaultAction;

    /**
     * Creates a new parsed YAML result.
     *
     * @param rules the parsed rules
     * @param defaultAction the default action
     */
    ParsedYaml(List<RecordingScopeRule> rules, RecordingScopeAction defaultAction) {
      this.rules = rules;
      this.defaultAction = defaultAction;
    }
  }

  /**
   * Parses a YAML string into raw rules and a default action.
   *
   * @param yamlContent the YAML content to parse
   * @return the parsed rules and default action
   * @throws IllegalArgumentException if the YAML content is malformed or contains invalid values
   */
  @SuppressWarnings("unchecked")
  static ParsedYaml parseYamlRaw(String yamlContent) {
    Map<String, Object> doc;
    try {
      Yaml yaml = new Yaml(new SafeConstructor(new LoaderOptions()));
      Object loaded = yaml.load(yamlContent);
      if (loaded == null) {
        return new ParsedYaml(List.of(), RecordingScopeAction.RECORD);
      }
      if (!(loaded instanceof Map)) {
        throw new IllegalArgumentException(
            "Scope policy YAML must be a mapping, got: " + loaded.getClass().getSimpleName());
      }
      doc = (Map<String, Object>) loaded;
    } catch (YAMLException e) {
      throw new IllegalArgumentException("Malformed scope policy YAML: " + e.getMessage(), e);
    }

    RecordingScopeAction defaultAction = RecordingScopeAction.RECORD;
    if (doc.containsKey("defaultAction")) {
      defaultAction = parseAction(String.valueOf(doc.get("defaultAction")));
    }

    List<RecordingScopeRule> rules = new ArrayList<>();
    if (doc.containsKey("rules")) {
      Object rulesObj = doc.get("rules");
      if (!(rulesObj instanceof List)) {
        throw new IllegalArgumentException(
            "Scope policy 'rules' must be a list, got: " + rulesObj.getClass().getSimpleName());
      }
      for (Object entry : (List<Object>) rulesObj) {
        if (!(entry instanceof Map)) {
          throw new IllegalArgumentException(
              "Each rule must be a mapping, got: " + entry.getClass().getSimpleName());
        }
        rules.add(parseRule((Map<String, Object>) entry));
      }
    }

    return new ParsedYaml(rules, defaultAction);
  }

  /**
   * Parses a single rule from its YAML map representation.
   *
   * @param ruleMap the YAML map for one rule
   * @return the parsed rule
   * @throws IllegalArgumentException if the rule is missing required fields
   */
  @SuppressWarnings("unchecked")
  private static RecordingScopeRule parseRule(Map<String, Object> ruleMap) {
    String classPattern = requireString(ruleMap, "class");
    String memberPattern =
        ruleMap.containsKey("member") ? String.valueOf(ruleMap.get("member")) : null;
    RecordingScopeAction action = parseAction(requireString(ruleMap, "action"));
    Set<MemberCategory> categories = parseCategories(ruleMap.get("categories"));

    return new RecordingScopeRule(classPattern, memberPattern, action, categories);
  }

  /**
   * Parses a categories constraint from a YAML value, which should be a list of {@link
   * MemberCategory} names.
   *
   * @param value the YAML value for the {@code categories} field, or {@code null}
   * @return the set of member categories, or {@code null} if no constraint (match all)
   */
  @SuppressWarnings("unchecked")
  private static Set<MemberCategory> parseCategories(Object value) {
    if (value == null) {
      return null;
    }
    EnumSet<MemberCategory> result = EnumSet.noneOf(MemberCategory.class);
    if (value instanceof List) {
      for (Object item : (List<Object>) value) {
        result.add(MemberCategory.valueOf(String.valueOf(item)));
      }
    } else {
      result.add(MemberCategory.valueOf(String.valueOf(value)));
    }
    return result;
  }

  /**
   * Parses a recording scope action from its string name.
   *
   * @param value the action name
   * @return the parsed action
   * @throws IllegalArgumentException if the value is not a valid action name
   */
  private static RecordingScopeAction parseAction(String value) {
    try {
      return RecordingScopeAction.valueOf(value);
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("Unknown recording scope action: " + value, e);
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
