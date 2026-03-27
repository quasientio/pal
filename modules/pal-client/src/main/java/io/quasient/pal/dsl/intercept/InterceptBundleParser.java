/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.dsl.intercept;

import io.quasient.pal.common.lang.FieldOpType;
import io.quasient.pal.common.lang.intercept.CheckedExceptionPolicy;
import io.quasient.pal.common.lang.intercept.ExceptionPropagationPolicy;
import io.quasient.pal.common.lang.intercept.InterceptType;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/**
 * Parses YAML content into {@link InterceptBundleSpec} model objects.
 *
 * <p>This parser converts user-authored YAML files into the DSL model. It uses SnakeYAML's raw
 * {@code Map<String, Object>} parsing pattern with {@link SafeConstructor} for safe
 * deserialization.
 *
 * <h3>YAML Format</h3>
 *
 * <pre>{@code
 * bundle: "fraud-check-v1"
 * defaults:
 *   peer: "fraud-checker"
 *   priority: 0
 *   ttl: 30s
 *   forceImmediate: false
 *   exceptionPolicy: PROPAGATE_CONTROLLED_ONLY
 *   checkedExceptionPolicy: WRAP
 *
 * intercepts:
 *   - target: com.acme.payment.OrderService.placeOrder
 *     type: BEFORE
 *     callback:
 *       class: com.acme.fraud.FraudChecker
 *       method: verify
 *     params: [com.acme.payment.Order]
 *     priority: 10
 *     ttl: 15m
 * }</pre>
 *
 * @see InterceptBundleSpec
 * @see InterceptSpec
 * @see InterceptBundleDefaults
 */
public class InterceptBundleParser {

  /** Pattern for duration strings: digits followed by ms, s, m, h, or d. */
  private static final Pattern DURATION_PATTERN = Pattern.compile("(\\d+)(ms|[smhd])");

  /**
   * Parses the given YAML content into an {@link InterceptBundleSpec}.
   *
   * @param yamlContent the YAML string to parse
   * @return the parsed bundle specification
   * @throws IllegalArgumentException if the YAML is empty, malformed, or missing required fields
   */
  public InterceptBundleSpec parse(String yamlContent) {
    Yaml yaml = new Yaml(new SafeConstructor(new LoaderOptions()));
    Object raw = yaml.load(yamlContent);
    if (raw == null) {
      throw new IllegalArgumentException("Empty YAML content");
    }
    if (!(raw instanceof Map)) {
      throw new IllegalArgumentException("YAML root must be a mapping");
    }
    @SuppressWarnings("unchecked")
    Map<String, Object> root = (Map<String, Object>) raw;
    return parseRoot(root);
  }

  /**
   * Parses the root mapping into an {@link InterceptBundleSpec}.
   *
   * @param root the root YAML mapping
   * @return the parsed bundle specification
   */
  private InterceptBundleSpec parseRoot(Map<String, Object> root) {
    String bundleName = requireString(root, "bundle");
    InterceptBundleDefaults defaults = parseDefaults(root.get("defaults"));
    List<InterceptSpec> intercepts = parseIntercepts(root.get("intercepts"));

    InterceptBundleSpec.Builder builder =
        InterceptBundleSpec.builder(bundleName).defaults(defaults);
    for (InterceptSpec spec : intercepts) {
      builder.addIntercept(spec);
    }
    return builder.build();
  }

  /**
   * Parses the optional defaults section.
   *
   * @param raw the raw defaults object, or {@code null}
   * @return the parsed defaults, or {@link InterceptBundleDefaults#EMPTY} if not present
   */
  private InterceptBundleDefaults parseDefaults(Object raw) {
    if (raw == null) {
      return InterceptBundleDefaults.EMPTY;
    }
    if (!(raw instanceof Map)) {
      throw new IllegalArgumentException("'defaults' must be a mapping");
    }
    @SuppressWarnings("unchecked")
    Map<String, Object> map = (Map<String, Object>) raw;

    String peer = map.containsKey("peer") ? String.valueOf(map.get("peer")) : null;
    Integer priority = map.containsKey("priority") ? toInteger(map.get("priority")) : null;
    Duration ttl = map.containsKey("ttl") ? parseDuration(String.valueOf(map.get("ttl"))) : null;
    Boolean forceImmediate =
        map.containsKey("forceImmediate") ? toBoolean(map.get("forceImmediate")) : null;
    ExceptionPropagationPolicy exceptionPolicy =
        map.containsKey("exceptionPolicy")
            ? ExceptionPropagationPolicy.valueOf(
                String.valueOf(map.get("exceptionPolicy")).toUpperCase(Locale.ROOT))
            : null;
    CheckedExceptionPolicy checkedExceptionPolicy =
        map.containsKey("checkedExceptionPolicy")
            ? CheckedExceptionPolicy.valueOf(
                String.valueOf(map.get("checkedExceptionPolicy")).toUpperCase(Locale.ROOT))
            : null;
    Duration callbackTimeout =
        map.containsKey("callbackTimeout")
            ? parseDuration(String.valueOf(map.get("callbackTimeout")))
            : null;

    return new InterceptBundleDefaults(
        peer,
        priority,
        ttl,
        forceImmediate,
        exceptionPolicy,
        checkedExceptionPolicy,
        callbackTimeout);
  }

  /**
   * Parses the intercepts list.
   *
   * @param raw the raw intercepts object
   * @return the list of parsed intercept specs
   * @throws IllegalArgumentException if the intercepts key is missing, not a list, or empty
   */
  private List<InterceptSpec> parseIntercepts(Object raw) {
    if (raw == null) {
      throw new IllegalArgumentException("Missing required field: 'intercepts'");
    }
    if (!(raw instanceof List)) {
      throw new IllegalArgumentException("'intercepts' must be a list");
    }
    @SuppressWarnings("unchecked")
    List<Object> list = (List<Object>) raw;
    if (list.isEmpty()) {
      throw new IllegalArgumentException("'intercepts' must not be empty");
    }

    List<InterceptSpec> specs = new ArrayList<>(list.size());
    for (int i = 0; i < list.size(); i++) {
      Object entry = list.get(i);
      if (!(entry instanceof Map)) {
        throw new IllegalArgumentException("Intercept entry at index " + i + " must be a mapping");
      }
      @SuppressWarnings("unchecked")
      Map<String, Object> entryMap = (Map<String, Object>) entry;
      specs.add(parseInterceptEntry(entryMap, i));
    }
    return specs;
  }

  /**
   * Parses a single intercept entry.
   *
   * @param map the intercept entry mapping
   * @param index the index of this entry in the list (for error messages)
   * @return the parsed intercept spec
   */
  private InterceptSpec parseInterceptEntry(Map<String, Object> map, int index) {
    String target = requireString(map, "target");
    String typeStr = requireString(map, "type");
    InterceptType type = InterceptType.valueOf(typeStr.toUpperCase(Locale.ROOT));

    if (!map.containsKey("callback")) {
      throw new IllegalArgumentException(
          "Intercept entry at index " + index + " missing required field: 'callback'");
    }
    Object callbackRaw = map.get("callback");
    if (!(callbackRaw instanceof Map)) {
      throw new IllegalArgumentException(
          "Intercept entry at index " + index + ": 'callback' must be a mapping");
    }
    @SuppressWarnings("unchecked")
    Map<String, Object> callbackMap = (Map<String, Object>) callbackRaw;
    String callbackClass = requireString(callbackMap, "class");
    String callbackMethod = requireString(callbackMap, "method");

    int lastDot = target.lastIndexOf('.');
    if (lastDot <= 0) {
      throw new IllegalArgumentException(
          "Invalid target '"
              + target
              + "': must contain at least one '.' separating class"
              + " and method/field name");
    }
    String targetClass = target.substring(0, lastDot);
    String targetName = target.substring(lastDot + 1);

    InterceptSpec.Builder builder =
        InterceptSpec.builder()
            .targetClass(targetClass)
            .targetName(targetName)
            .type(type)
            .callbackClass(callbackClass)
            .callbackMethod(callbackMethod);

    // Parse kind (method or field)
    if (map.containsKey("kind")) {
      String kindStr = String.valueOf(map.get("kind")).toUpperCase(Locale.ROOT);
      InterceptableKind kind = InterceptableKind.valueOf(kindStr);
      builder.kind(kind);
      if (kind == InterceptableKind.FIELD) {
        if (!map.containsKey("fieldOp")) {
          throw new IllegalArgumentException(
              "Intercept entry at index " + index + ": 'fieldOp' is required when kind is 'field'");
        }
        String fieldOpStr = String.valueOf(map.get("fieldOp")).toUpperCase(Locale.ROOT);
        builder.fieldOpType(FieldOpType.valueOf(fieldOpStr));
      }
    }

    // Parse optional parameter types
    if (map.containsKey("params")) {
      builder.parameterTypes(parseStringList(map.get("params"), "params"));
    }

    // Parse optional overrides
    if (map.containsKey("peer")) {
      builder.peerOverride(String.valueOf(map.get("peer")));
    }
    if (map.containsKey("priority")) {
      builder.priorityOverride(toInteger(map.get("priority")));
    }
    if (map.containsKey("ttl")) {
      builder.ttlOverride(parseDuration(String.valueOf(map.get("ttl"))));
    }
    if (map.containsKey("forceImmediate")) {
      builder.forceImmediateOverride(toBoolean(map.get("forceImmediate")));
    }
    if (map.containsKey("exceptionPolicy")) {
      builder.exceptionPolicyOverride(
          ExceptionPropagationPolicy.valueOf(
              String.valueOf(map.get("exceptionPolicy")).toUpperCase(Locale.ROOT)));
    }
    if (map.containsKey("checkedExceptionPolicy")) {
      builder.checkedExceptionPolicyOverride(
          CheckedExceptionPolicy.valueOf(
              String.valueOf(map.get("checkedExceptionPolicy")).toUpperCase(Locale.ROOT)));
    }
    if (map.containsKey("callbackTimeout")) {
      builder.callbackTimeoutOverride(parseDuration(String.valueOf(map.get("callbackTimeout"))));
    }

    return builder.build();
  }

  /**
   * Parses a duration string in the format {@code (\d+)(ms|[smhd])}.
   *
   * <p>Supported suffixes: {@code ms} (milliseconds), {@code s} (seconds), {@code m} (minutes),
   * {@code h} (hours), {@code d} (days). The special value {@code 0} or {@code 0s} yields {@link
   * Duration#ZERO}.
   *
   * @param value the duration string
   * @return the parsed duration
   * @throws IllegalArgumentException if the format is invalid
   */
  static Duration parseDuration(String value) {
    if ("0".equals(value)) {
      return Duration.ZERO;
    }
    Matcher matcher = DURATION_PATTERN.matcher(value);
    if (!matcher.matches()) {
      throw new IllegalArgumentException(
          "Invalid duration format: '"
              + value
              + "'"
              + " (expected format: <digits><ms|s|m|h|d>, e.g. '500ms', '30s', '5m', '1h', '1d')");
    }
    long amount = Long.parseLong(matcher.group(1));
    String unit = matcher.group(2);
    return switch (unit) {
      case "ms" -> Duration.ofMillis(amount);
      case "s" -> Duration.ofSeconds(amount);
      case "m" -> Duration.ofMinutes(amount);
      case "h" -> Duration.ofHours(amount);
      case "d" -> Duration.ofDays(amount);
      default -> throw new IllegalArgumentException("Unknown duration unit: " + unit);
    };
  }

  /**
   * Parses a list of strings from a raw YAML value.
   *
   * @param raw the raw YAML value (expected to be a List)
   * @param fieldName the field name (for error messages)
   * @return the list of strings
   * @throws IllegalArgumentException if the value is not a list
   */
  private List<String> parseStringList(Object raw, String fieldName) {
    if (!(raw instanceof List)) {
      throw new IllegalArgumentException("'" + fieldName + "' must be a list");
    }
    @SuppressWarnings("unchecked")
    List<Object> list = (List<Object>) raw;
    List<String> result = new ArrayList<>(list.size());
    for (Object item : list) {
      result.add(String.valueOf(item));
    }
    return result;
  }

  /**
   * Extracts a required string value from a map.
   *
   * @param map the map to extract from
   * @param key the required key
   * @return the string value
   * @throws IllegalArgumentException if the key is missing
   */
  private static String requireString(Map<String, Object> map, String key) {
    if (!map.containsKey(key)) {
      throw new IllegalArgumentException("Missing required field: '" + key + "'");
    }
    return String.valueOf(map.get(key));
  }

  /**
   * Converts a raw YAML value to an integer.
   *
   * @param value the raw value
   * @return the integer value
   * @throws IllegalArgumentException if the value cannot be converted
   */
  private static Integer toInteger(Object value) {
    if (value instanceof Number number) {
      return number.intValue();
    }
    return Integer.parseInt(String.valueOf(value));
  }

  /**
   * Converts a raw YAML value to a boolean.
   *
   * @param value the raw value
   * @return the boolean value
   */
  private static Boolean toBoolean(Object value) {
    if (value instanceof Boolean bool) {
      return bool;
    }
    return Boolean.parseBoolean(String.valueOf(value));
  }
}
