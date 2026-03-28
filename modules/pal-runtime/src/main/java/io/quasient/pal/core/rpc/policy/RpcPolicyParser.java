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
package io.quasient.pal.core.rpc.policy;

import com.google.common.base.Splitter;
import io.quasient.pal.core.transport.MessageChannelType;
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
 * Parses RPC policy configuration from YAML content and CLI options into an {@link RpcPolicy}.
 *
 * <p>Supports three sources of rules, applied in priority order:
 *
 * <ol>
 *   <li><b>User rules</b> (from YAML file): highest priority, evaluated first
 *   <li><b>Preset rules</b> (from CLI {@code --rpc-policy-preset} or YAML {@code presets} section):
 *       evaluated after user rules
 *   <li><b>Default action</b>: applied when no rule matches
 * </ol>
 *
 * <h3>YAML Format</h3>
 *
 * <pre>{@code
 * version: 1
 * defaultAction: DENY
 *
 * presets:
 *   deny-unsafe: true
 *   deny-jdk-internals: true
 *
 * rules:
 *   - class: "com.example.api.**"
 *     method: "**"
 *     action: ALLOW
 *
 *   - pattern: "com.example.Calculator.add"
 *     action: ALLOW
 *     channel: ZMQ_SOCKET_RPC
 *     members: [METHOD, STATIC_METHOD]
 * }</pre>
 *
 * <p>Rules support two formats for specifying the target:
 *
 * <ul>
 *   <li>Separate {@code class} + {@code method} fields (like {@code ReplayPolicy})
 *   <li>Combined {@code pattern} field: {@code "com.example.Foo.bar"} splits at the last dot
 * </ul>
 */
public final class RpcPolicyParser {

  /** Logger for emitting warnings about potentially misconfigured policies. */
  private static final Logger LOG = LoggerFactory.getLogger(RpcPolicyParser.class);

  /** Private constructor to prevent instantiation of this utility class. */
  private RpcPolicyParser() {}

  /**
   * Parses a YAML string into an {@link RpcPolicy}.
   *
   * @param yamlContent the YAML content to parse
   * @return an RPC policy with the parsed rules and default action
   * @throws IllegalArgumentException if the YAML content is malformed or contains invalid values
   */
  @SuppressWarnings("unchecked")
  public static RpcPolicy parseYaml(String yamlContent) {
    ParsedYaml parsed = parseYamlRaw(yamlContent);
    return new RpcPolicy(parsed.rules, parsed.defaultAction);
  }

  /**
   * Internal representation of the raw parsed YAML content, before wrapping in an {@link
   * RpcPolicy}. Separating parsing from policy construction avoids double-wrapping when {@link
   * #fromOptions} combines YAML and CLI preset rules into a single policy.
   */
  static final class ParsedYaml {

    /** The parsed rules (user rules + YAML presets). */
    final List<RpcPolicyRule> rules;

    /** The default action from the YAML file. */
    final RpcPolicyAction defaultAction;

    /**
     * Creates a new parsed YAML result.
     *
     * @param rules the parsed rules
     * @param defaultAction the default action
     */
    ParsedYaml(List<RpcPolicyRule> rules, RpcPolicyAction defaultAction) {
      this.rules = rules;
      this.defaultAction = defaultAction;
    }
  }

  /**
   * Parses a YAML string into raw rules and a default action, without wrapping in an {@link
   * RpcPolicy}. Used internally by {@link #fromOptions} to avoid double-prepending mandatory rules
   * (which are added by the {@link RpcPolicy} constructor).
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
        return new ParsedYaml(List.of(), RpcPolicyAction.DENY);
      }
      if (!(loaded instanceof Map)) {
        throw new IllegalArgumentException(
            "RPC policy YAML must be a mapping, got: " + loaded.getClass().getSimpleName());
      }
      doc = (Map<String, Object>) loaded;
    } catch (YAMLException e) {
      throw new IllegalArgumentException("Malformed RPC policy YAML: " + e.getMessage(), e);
    }

    RpcPolicyAction defaultAction = RpcPolicyAction.DENY;
    if (doc.containsKey("defaultAction")) {
      defaultAction = parseAction(String.valueOf(doc.get("defaultAction")));
    }

    List<RpcPolicyRule> rules = new ArrayList<>();

    // Parse user-defined rules first (highest priority)
    if (doc.containsKey("rules")) {
      Object rulesObj = doc.get("rules");
      if (!(rulesObj instanceof List)) {
        throw new IllegalArgumentException(
            "RPC policy 'rules' must be a list, got: " + rulesObj.getClass().getSimpleName());
      }
      for (Object entry : (List<Object>) rulesObj) {
        if (!(entry instanceof Map)) {
          throw new IllegalArgumentException(
              "Each rule must be a mapping, got: " + entry.getClass().getSimpleName());
        }
        rules.add(parseRule((Map<String, Object>) entry));
      }
    }

    // Parse preset rules (lower priority than user rules)
    if (doc.containsKey("presets")) {
      Object presetsObj = doc.get("presets");
      if (presetsObj instanceof Map) {
        Map<String, Object> presets = (Map<String, Object>) presetsObj;
        for (Map.Entry<String, Object> preset : presets.entrySet()) {
          if ("deny-pal-internals".equals(preset.getKey())
              && Boolean.FALSE.equals(preset.getValue())) {
            LOG.warn(
                "Ignoring 'deny-pal-internals: false' in YAML policy — this preset is always"
                    + " enforced and cannot be disabled");
          }
          if (Boolean.TRUE.equals(preset.getValue())) {
            rules.addAll(RpcPolicyPresets.resolvePreset(preset.getKey()));
          }
        }
      }
    }

    return new ParsedYaml(rules, defaultAction);
  }

  /**
   * Builds an {@link RpcPolicy} from CLI options and an optional YAML file.
   *
   * <p>Priority order: user rules from YAML first, then preset rules (from both YAML and CLI), then
   * the default action.
   *
   * @param yamlPath path to a YAML RPC policy file, or {@code null} if not provided
   * @param presetNames comma-separated preset names to enable, or {@code null}
   * @param defaultAction the default action when no rule matches (defaults to {@code "DENY"})
   * @return an RPC policy combining all sources
   * @throws IllegalArgumentException if the YAML file is malformed
   * @throws UncheckedIOException if the YAML file cannot be read
   */
  public static RpcPolicy fromOptions(String yamlPath, String presetNames, String defaultAction) {
    List<RpcPolicyRule> allRules = new ArrayList<>();
    RpcPolicyAction action =
        defaultAction != null
            ? parseAction(defaultAction.toUpperCase(Locale.ROOT))
            : RpcPolicyAction.DENY;

    // 1. YAML file rules (highest priority — user rules come first)
    if (yamlPath != null) {
      String yamlContent;
      try {
        yamlContent = Files.readString(Path.of(yamlPath));
      } catch (IOException e) {
        throw new UncheckedIOException("Cannot read RPC policy file: " + yamlPath, e);
      }
      ParsedYaml parsed = parseYamlRaw(yamlContent);
      allRules.addAll(parsed.rules);
      action = parsed.defaultAction;
    }

    // 2. CLI preset rules (after YAML rules, including YAML presets already added)
    if (presetNames != null) {
      for (String preset : Splitter.on(',').trimResults().omitEmptyStrings().split(presetNames)) {
        allRules.addAll(RpcPolicyPresets.resolvePreset(preset));
      }
    }

    // 3. CLI default action overrides YAML default if explicitly provided
    if (defaultAction != null) {
      action = parseAction(defaultAction.toUpperCase(Locale.ROOT));
    }

    RpcPolicy policy = new RpcPolicy(allRules, action);

    // Warn if DENY default with no ALLOW rules
    if (action == RpcPolicyAction.DENY || action == RpcPolicyAction.LOG_AND_DENY) {
      boolean hasAllowRule =
          allRules.stream()
              .anyMatch(
                  r ->
                      r.getAction() == RpcPolicyAction.ALLOW
                          || r.getAction() == RpcPolicyAction.LOG_AND_ALLOW);
      if (!hasAllowRule) {
        LOG.warn(
            "RPC policy has default action {} with no ALLOW rules — all RPC operations will be"
                + " denied",
            action);
      }
    }

    return policy;
  }

  /**
   * Parses a single rule from its YAML map representation.
   *
   * <p>Supports two formats:
   *
   * <ul>
   *   <li>Separate {@code class} + {@code method} fields
   *   <li>Combined {@code pattern} field split at the last dot
   * </ul>
   *
   * @param ruleMap the YAML map for one rule
   * @return the parsed rule
   * @throws IllegalArgumentException if the rule is missing required fields
   */
  @SuppressWarnings("unchecked")
  private static RpcPolicyRule parseRule(Map<String, Object> ruleMap) {
    String classPattern;
    String memberPattern;

    if (ruleMap.containsKey("pattern")) {
      String pattern = String.valueOf(ruleMap.get("pattern"));
      int lastDot = pattern.lastIndexOf('.');
      if (lastDot < 0) {
        classPattern = pattern;
        memberPattern = "**";
      } else {
        classPattern = pattern.substring(0, lastDot);
        memberPattern = pattern.substring(lastDot + 1);
      }
    } else if (ruleMap.containsKey("class")) {
      classPattern = requireString(ruleMap, "class");
      memberPattern = ruleMap.containsKey("method") ? String.valueOf(ruleMap.get("method")) : null;
    } else {
      throw new IllegalArgumentException("Rule must have either 'pattern' or 'class' field");
    }

    RpcPolicyAction ruleAction = parseAction(requireString(ruleMap, "action"));

    Set<MessageChannelType> channels = parseChannels(ruleMap.get("channel"));
    Set<MemberCategory> members = parseMembers(ruleMap.get("members"));
    Set<MemberVisibility> visibilities = parseVisibilities(ruleMap.get("visibility"));

    return new RpcPolicyRule(
        classPattern, memberPattern, ruleAction, channels, members, visibilities);
  }

  /**
   * Parses a channel constraint from a YAML value, which may be a single string or a list.
   *
   * @param value the YAML value for the {@code channel} field, or {@code null}
   * @return the set of channels, or {@code null} if no constraint
   */
  @SuppressWarnings("unchecked")
  private static Set<MessageChannelType> parseChannels(Object value) {
    if (value == null) {
      return null;
    }
    EnumSet<MessageChannelType> result = EnumSet.noneOf(MessageChannelType.class);
    if (value instanceof List) {
      for (Object item : (List<Object>) value) {
        result.add(MessageChannelType.valueOf(String.valueOf(item)));
      }
    } else {
      result.add(MessageChannelType.valueOf(String.valueOf(value)));
    }
    return result;
  }

  /**
   * Parses a members constraint from a YAML value, which should be a list of member category names.
   *
   * @param value the YAML value for the {@code members} field, or {@code null}
   * @return the set of member categories, or {@code null} if no constraint
   */
  @SuppressWarnings("unchecked")
  private static Set<MemberCategory> parseMembers(Object value) {
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
   * Parses a visibility constraint from a YAML value, which may be a single string or a list.
   *
   * <p>Follows the same pattern as {@link #parseChannels(Object)} and {@link
   * #parseMembers(Object)}:
   *
   * <ul>
   *   <li>{@code null} input returns {@code null} (no restriction, match all visibilities)
   *   <li>Single {@code String} is parsed via {@link MemberVisibility#fromString(String)}; returns
   *       {@code null} if the value is {@code "ALL"}
   *   <li>{@code List<?>} iterates each element; returns {@code null} if any element is {@code
   *       "ALL"}
   *   <li>Other types throw {@link IllegalArgumentException}
   * </ul>
   *
   * @param value the YAML value for the {@code visibility} field, or {@code null}
   * @return the set of visibilities, or {@code null} if no constraint (match all)
   * @throws IllegalArgumentException if the value is not a valid visibility
   */
  @SuppressWarnings("unchecked")
  private static Set<MemberVisibility> parseVisibilities(Object value) {
    if (value == null) {
      return null;
    }
    if (value instanceof String s) {
      MemberVisibility vis = MemberVisibility.fromString(s);
      return vis != null ? EnumSet.of(vis) : null;
    }
    if (value instanceof List<?> list) {
      EnumSet<MemberVisibility> result = EnumSet.noneOf(MemberVisibility.class);
      for (Object item : list) {
        MemberVisibility vis = MemberVisibility.fromString(String.valueOf(item).trim());
        if (vis == null) {
          return null;
        }
        result.add(vis);
      }
      return result;
    }
    throw new IllegalArgumentException("Invalid visibility value: " + value);
  }

  /**
   * Parses an RPC policy action from its string name.
   *
   * @param value the action name
   * @return the parsed action
   * @throws IllegalArgumentException if the value is not a valid action name
   */
  private static RpcPolicyAction parseAction(String value) {
    try {
      return RpcPolicyAction.valueOf(value);
    } catch (IllegalArgumentException e) {
      throw new IllegalArgumentException("Unknown RPC policy action: " + value, e);
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
