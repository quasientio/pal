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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

import io.quasient.pal.core.rpc.policy.MemberCategory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * Unit tests for {@code RecordingScopeParser}, the assembler that builds a {@link RecordingScope}
 * from CLI flags ({@code --scope}, {@code --scope-exclude}, {@code --scope-io}, {@code
 * --scope-default}), YAML policy files ({@code --scope-policy}), and built-in presets.
 *
 * <p>Follows the same multi-source assembly pattern as {@link
 * io.quasient.pal.core.replay.ReplayPolicyParser}. The critical aspects under test are
 * default-action inference (SKIP when include patterns are present, RECORD when only exclude
 * patterns are present), rule priority ordering (exclude &gt; include &gt; preset &gt; YAML), and
 * correct YAML parsing including categories and member defaults.
 *
 * @see RecordingScope
 * @see RecordingScopeRule
 * @see BuiltInScopeRules
 */
public class RecordingScopeParserTest {

  @Rule public TemporaryFolder tempFolder = new TemporaryFolder();

  /**
   * Verifies that providing only {@code --scope} include patterns causes the parser to infer a
   * default action of SKIP. Operations matching the include pattern are in scope; operations not
   * matching are out of scope (default SKIP).
   */
  @Test
  public void onlyScopePatternsDefaultsToSkip() {
    RecordingScope scope =
        RecordingScopeParser.fromOptions(null, false, new String[] {"com.example.**"}, null, null);

    assertThat(scope, is(notNullValue()));
    assertThat(scope.getDefaultAction(), is(RecordingScopeAction.SKIP));
    assertThat(scope.isInScope("com.example.Foo", "bar", MemberCategory.METHOD), is(true));
    assertThat(scope.isInScope("org.other.Foo", "bar", MemberCategory.METHOD), is(false));
  }

  /**
   * Verifies that providing only {@code --scope-exclude} patterns causes the parser to infer a
   * default action of RECORD. Operations matching the exclude pattern are out of scope; all other
   * operations are in scope (default RECORD).
   */
  @Test
  public void onlyScopeExcludePatternsDefaultsToRecord() {
    RecordingScope scope =
        RecordingScopeParser.fromOptions(null, false, null, new String[] {"java.util.**"}, null);

    assertThat(scope, is(notNullValue()));
    assertThat(scope.getDefaultAction(), is(RecordingScopeAction.RECORD));
    assertThat(scope.isInScope("com.example.Foo", "bar", MemberCategory.METHOD), is(true));
    assertThat(scope.isInScope("java.util.HashMap", "put", MemberCategory.METHOD), is(false));
  }

  /**
   * Verifies that providing both {@code --scope} include and {@code --scope-exclude} exclude
   * patterns causes the parser to infer a default action of SKIP. Include patterns match as RECORD,
   * exclude patterns match as SKIP, and non-matching operations fall to default SKIP.
   */
  @Test
  public void bothScopeAndExcludeDefaultsToSkip() {
    RecordingScope scope =
        RecordingScopeParser.fromOptions(
            null,
            false,
            new String[] {"com.example.**"},
            new String[] {"com.example.internal.**"},
            null);

    assertThat(scope, is(notNullValue()));
    assertThat(scope.getDefaultAction(), is(RecordingScopeAction.SKIP));
    assertThat(scope.isInScope("com.example.Foo", "bar", MemberCategory.METHOD), is(true));
    assertThat(scope.isInScope("com.example.internal.Util", "x", MemberCategory.METHOD), is(false));
    assertThat(scope.isInScope("org.other.Foo", "bar", MemberCategory.METHOD), is(false));
  }

  /**
   * Verifies that an explicit {@code --scope-default} value overrides the inference logic. Even
   * when include patterns are present (which would normally infer SKIP), an explicit default of
   * RECORD causes non-matching operations to be recorded.
   */
  @Test
  public void explicitDefaultOverridesInference() {
    RecordingScope scope =
        RecordingScopeParser.fromOptions(
            null, false, new String[] {"com.example.**"}, null, "record");

    assertThat(scope, is(notNullValue()));
    assertThat(scope.getDefaultAction(), is(RecordingScopeAction.RECORD));
    assertThat(scope.isInScope("org.other.Foo", "bar", MemberCategory.METHOD), is(true));
  }

  /**
   * Verifies that {@code --scope-io} adds the built-in I/O boundary RECORD rules from {@link
   * BuiltInScopeRules#getIoBoundaryRules()} to the scope. Operations matching I/O boundary patterns
   * are in scope even without explicit CLI include patterns.
   */
  @Test
  public void scopeIoAddsIoBoundaryRules() {
    RecordingScope scope = RecordingScopeParser.fromOptions(null, true, null, null, null);

    assertThat(scope, is(notNullValue()));
    assertThat(
        scope.isInScope("java.sql.DriverManager", "getConnection", MemberCategory.METHOD),
        is(true));
  }

  /**
   * Verifies that exclude rules (from {@code --scope-exclude}) are ordered before include rules
   * (from {@code --scope}) in the resulting rule list, so that first-match-wins evaluation causes
   * an exclude to win when both patterns match the same operation.
   */
  @Test
  public void excludeRulesHaveHigherPriorityThanInclude() {
    RecordingScope scope =
        RecordingScopeParser.fromOptions(
            null, false, new String[] {"com.example.**"}, new String[] {"com.example.**"}, null);

    assertThat(scope, is(notNullValue()));
    assertThat(scope.isInScope("com.example.Foo", "bar", MemberCategory.METHOD), is(false));
  }

  /**
   * Verifies that a YAML string with {@code defaultAction}, and rules containing {@code class},
   * {@code member}, {@code categories}, and {@code action} fields is correctly parsed into a {@link
   * RecordingScope} with the expected rules and default action.
   */
  @Test
  public void yamlParsesCorrectly() throws IOException {
    String yaml =
        """
        defaultAction: SKIP
        rules:
          - class: "com.example.**"
            member: "get*"
            action: RECORD
            categories: [METHOD]
        """;

    Path yamlFile = tempFolder.newFile("scope-policy.yaml").toPath();
    Files.writeString(yamlFile, yaml);

    RecordingScope scope =
        RecordingScopeParser.fromOptions(yamlFile.toString(), false, null, null, null);

    assertThat(scope, is(notNullValue()));
    assertThat(scope.getDefaultAction(), is(RecordingScopeAction.SKIP));
    assertThat(scope.isInScope("com.example.Foo", "getValue", MemberCategory.METHOD), is(true));
    assertThat(scope.isInScope("com.example.Foo", "setValue", MemberCategory.METHOD), is(false));
    // Category mismatch: rule only applies to METHOD, not FIELD_GET
    assertThat(scope.isInScope("com.example.Foo", "getValue", MemberCategory.FIELD_GET), is(false));
  }

  /**
   * Verifies that CLI rules (from {@code --scope} patterns) take priority over YAML rules when both
   * are provided. The parser adds CLI rules before YAML rules in the rule list, so first-match-wins
   * gives CLI rules precedence.
   */
  @Test
  public void yamlRulesHaveLowerPriorityThanCliRules() throws IOException {
    String yaml =
        """
        defaultAction: RECORD
        rules:
          - class: "com.example.**"
            action: SKIP
        """;

    Path yamlFile = tempFolder.newFile("scope-policy.yaml").toPath();
    Files.writeString(yamlFile, yaml);

    RecordingScope scope =
        RecordingScopeParser.fromOptions(
            yamlFile.toString(), false, new String[] {"com.example.**"}, null, null);

    assertThat(scope, is(notNullValue()));
    // CLI RECORD rule takes priority over YAML SKIP rule
    assertThat(scope.isInScope("com.example.Foo", "bar", MemberCategory.METHOD), is(true));
  }

  /**
   * Verifies that when all inputs are null, false, or empty, {@code fromOptions} returns a
   * permit-all scope with no rules and a default action of {@link RecordingScopeAction#RECORD}.
   */
  @Test
  public void noInputsProducePermitAllScope() {
    RecordingScope scope = RecordingScopeParser.fromOptions(null, false, null, null, null);

    assertThat(scope, is(notNullValue()));
    assertThat(scope.getRules().isEmpty(), is(true));
    assertThat(scope.getDefaultAction(), is(RecordingScopeAction.RECORD));
  }

  /**
   * Verifies that all three configuration sources (CLI patterns, {@code --scope-io} preset, and
   * YAML file) contribute rules in the correct priority order: exclude rules first, then include
   * rules, then preset rules, then YAML rules.
   */
  @Test
  public void combinedCliAndPresetAndYaml() throws IOException {
    String yaml =
        """
        defaultAction: SKIP
        rules:
          - class: "org.yaml.only.**"
            action: RECORD
        """;

    Path yamlFile = tempFolder.newFile("scope-policy.yaml").toPath();
    Files.writeString(yamlFile, yaml);

    RecordingScope scope =
        RecordingScopeParser.fromOptions(
            yamlFile.toString(),
            true,
            new String[] {"com.app.**"},
            new String[] {"com.app.internal.**"},
            null);

    assertThat(scope, is(notNullValue()));
    // Exclude wins for com.app.internal (exclude rule is first)
    assertThat(scope.isInScope("com.app.internal.Util", "x", MemberCategory.METHOD), is(false));
    // Include wins for com.app (include rule is second)
    assertThat(scope.isInScope("com.app.Service", "run", MemberCategory.METHOD), is(true));
    // I/O preset rule matches
    assertThat(
        scope.isInScope("java.sql.DriverManager", "getConnection", MemberCategory.METHOD),
        is(true));
    // YAML rule matches
    assertThat(scope.isInScope("org.yaml.only.Config", "get", MemberCategory.METHOD), is(true));
    // Nothing else matches → default SKIP
    assertThat(scope.isInScope("org.other.Foo", "bar", MemberCategory.METHOD), is(false));
  }

  /**
   * Verifies that a YAML rule with only {@code class} and {@code action} (no {@code member} field)
   * defaults the member pattern to {@code "**"}, matching all members of the specified class.
   */
  @Test
  public void yamlMemberDefaultsToWildcard() throws IOException {
    String yaml =
        """
        defaultAction: SKIP
        rules:
          - class: "com.example.Foo"
            action: RECORD
        """;

    Path yamlFile = tempFolder.newFile("scope-policy.yaml").toPath();
    Files.writeString(yamlFile, yaml);

    RecordingScope scope =
        RecordingScopeParser.fromOptions(yamlFile.toString(), false, null, null, null);

    assertThat(scope, is(notNullValue()));
    // Member defaults to "**" — all members match
    assertThat(scope.isInScope("com.example.Foo", "bar", MemberCategory.METHOD), is(true));
    assertThat(scope.isInScope("com.example.Foo", "baz", MemberCategory.CONSTRUCTOR), is(true));
    assertThat(scope.isInScope("com.example.Bar", "bar", MemberCategory.METHOD), is(false));
  }

  /**
   * Verifies that a YAML rule with a {@code categories} list (e.g. {@code [FIELD_GET, FIELD_SET]})
   * is parsed into a {@link RecordingScopeRule} with the correct {@link
   * io.quasient.pal.core.rpc.policy.MemberCategory} set.
   */
  @Test
  public void yamlCategoriesParsedCorrectly() throws IOException {
    String yaml =
        """
        defaultAction: SKIP
        rules:
          - class: "com.example.**"
            action: RECORD
            categories: [FIELD_GET, FIELD_SET]
        """;

    Path yamlFile = tempFolder.newFile("scope-policy.yaml").toPath();
    Files.writeString(yamlFile, yaml);

    RecordingScope scope =
        RecordingScopeParser.fromOptions(yamlFile.toString(), false, null, null, null);

    assertThat(scope, is(notNullValue()));
    // FIELD_GET matches
    assertThat(scope.isInScope("com.example.Order", "total", MemberCategory.FIELD_GET), is(true));
    // FIELD_SET matches
    assertThat(scope.isInScope("com.example.Order", "total", MemberCategory.FIELD_SET), is(true));
    // METHOD does not match the category filter
    assertThat(scope.isInScope("com.example.Order", "total", MemberCategory.METHOD), is(false));
  }
}
