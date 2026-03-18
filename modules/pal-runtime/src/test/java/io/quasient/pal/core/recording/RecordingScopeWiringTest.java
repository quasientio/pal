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
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import org.junit.Test;

/**
 * Tests verifying that {@link RecordingScope} is correctly assembled from {@link
 * java.util.Properties} using the same property-to-parameter mapping that {@link
 * io.quasient.pal.core.service.PeerWiring#provideRecordingScope()} uses.
 *
 * <p>Each test prepares a {@link java.util.Properties} instance with specific {@code scope.*}
 * properties, invokes {@link RecordingScopeParser#fromOptions} with the same translation logic that
 * PeerWiring applies, and verifies the resulting {@link RecordingScope} (or lack thereof).
 *
 * <p>These tests ensure backward compatibility when no scope is configured (null scope) and correct
 * wiring for all supported scope configuration properties.
 *
 * @see RecordingScope
 * @see RecordingScopeParser
 * @see io.quasient.pal.core.service.PeerWiring
 */
public class RecordingScopeWiringTest {

  /**
   * Verifies that when {@code scope.patterns} is set (e.g. {@code "com.example.**"}), the wiring
   * layer produces a non-null {@link RecordingScope} whose rules match the configured patterns.
   */
  @Test
  public void scopeInjectedWhenConfigured() {
    // Given: Properties with scope.patterns=com.example.**
    Properties props = new Properties();
    props.setProperty("scope.patterns", "com.example.**");

    // When: PeerWiring's provideRecordingScope() logic is applied
    RecordingScope scope = buildScopeFromProperties(props);

    // Then: The returned RecordingScope is non-null and contains rules matching "com.example.**"
    assertThat(scope, is(notNullValue()));
    assertThat(scope.getRules().size(), is(1));
    assertThat(scope.getRules().get(0).getClassPattern(), is("com.example.**"));
    assertThat(scope.getRules().get(0).getAction(), is(RecordingScopeAction.RECORD));
  }

  /**
   * Verifies that when no {@code scope.*} properties are set, the wiring layer returns {@code
   * null}, preserving backward compatibility where all operations are recorded.
   */
  @Test
  public void scopeNullWhenNotConfigured() {
    // Given: Properties with no scope.* properties set
    Properties props = new Properties();

    // When: PeerWiring's provideRecordingScope() logic is applied
    RecordingScope scope = buildScopeFromProperties(props);

    // Then: The result is null (no filtering, everything recorded)
    assertThat(scope, is(nullValue()));
  }

  /**
   * Verifies that setting {@code scope.io=true} produces a non-null {@link RecordingScope} that
   * includes the I/O boundary rules from {@link BuiltInScopeRules#getIoBoundaryRules()}.
   */
  @Test
  public void scopeIoPropertyProducesScope() {
    // Given: Properties with scope.io=true (and no other scope.* properties)
    Properties props = new Properties();
    props.setProperty("scope.io", "true");

    // When: PeerWiring's provideRecordingScope() logic is applied
    RecordingScope scope = buildScopeFromProperties(props);

    // Then: The returned RecordingScope is non-null and contains I/O boundary rules
    assertThat(scope, is(notNullValue()));
    int expectedIoRules = BuiltInScopeRules.getIoBoundaryRules().size();
    assertThat(scope.getRules().size(), is(greaterThanOrEqualTo(expectedIoRules)));
    assertThat(scope.getDefaultAction(), is(RecordingScopeAction.SKIP));
  }

  /**
   * Verifies that setting {@code scope.exclude.patterns} (e.g. {@code "java.util.**"}) produces a
   * non-null {@link RecordingScope} with SKIP rules for the excluded patterns.
   */
  @Test
  public void scopeExcludePatternsProduceScope() {
    // Given: Properties with scope.exclude.patterns=java.util.**
    Properties props = new Properties();
    props.setProperty("scope.exclude.patterns", "java.util.**");

    // When: PeerWiring's provideRecordingScope() logic is applied
    RecordingScope scope = buildScopeFromProperties(props);

    // Then: The returned RecordingScope is non-null and has SKIP rules for java.util.**
    assertThat(scope, is(notNullValue()));
    assertThat(scope.getRules().size(), is(1));
    assertThat(scope.getRules().get(0).getClassPattern(), is("java.util.**"));
    assertThat(scope.getRules().get(0).getAction(), is(RecordingScopeAction.SKIP));
    assertThat(scope.getDefaultAction(), is(RecordingScopeAction.RECORD));
  }

  /**
   * Verifies that the {@code scope.default.action} property is passed through correctly to the
   * resulting {@link RecordingScope}. When set to {@code "record"} alongside exclude patterns, the
   * scope's default action should be {@link RecordingScopeAction#RECORD}.
   */
  @Test
  public void scopeDefaultActionPassedThrough() {
    // Given: Properties with scope.default.action=record and scope.exclude.patterns=java.**
    Properties props = new Properties();
    props.setProperty("scope.exclude.patterns", "java.**");
    props.setProperty("scope.default.action", "record");

    // When: PeerWiring's provideRecordingScope() logic is applied
    RecordingScope scope = buildScopeFromProperties(props);

    // Then: The returned RecordingScope has default action RECORD
    assertThat(scope, is(notNullValue()));
    assertThat(scope.getDefaultAction(), is(RecordingScopeAction.RECORD));
  }

  /**
   * Verifies that setting {@code scope.policy.path} to a valid YAML file produces a {@link
   * RecordingScope} matching the YAML contents (rules, default action, categories).
   */
  @Test
  public void scopePolicyPathProducesScope() throws IOException {
    // Given: Properties with scope.policy.path pointing to a temp YAML file containing scope rules
    String yamlContent =
        """
        defaultAction: SKIP
        rules:
          - class: "com.mycompany.**"
            action: RECORD
          - class: "com.mycompany.internal.**"
            action: SKIP
        """;

    Path tempFile = Files.createTempFile("scope-policy-", ".yaml");
    try {
      Files.writeString(tempFile, yamlContent);

      Properties props = new Properties();
      props.setProperty("scope.policy.path", tempFile.toString());

      // When: PeerWiring's provideRecordingScope() logic is applied
      RecordingScope scope = buildScopeFromProperties(props);

      // Then: The returned RecordingScope is non-null and its rules/defaultAction match the YAML
      assertThat(scope, is(notNullValue()));
      assertThat(scope.getDefaultAction(), is(RecordingScopeAction.SKIP));
      assertThat(scope.getRules().size(), is(2));
      assertThat(scope.getRules().get(0).getClassPattern(), is("com.mycompany.**"));
      assertThat(scope.getRules().get(0).getAction(), is(RecordingScopeAction.RECORD));
      assertThat(scope.getRules().get(1).getClassPattern(), is("com.mycompany.internal.**"));
      assertThat(scope.getRules().get(1).getAction(), is(RecordingScopeAction.SKIP));
    } finally {
      Files.deleteIfExists(tempFile);
    }
  }

  /**
   * Replicates the property-to-parameter mapping from {@link
   * io.quasient.pal.core.service.PeerWiring#provideRecordingScope()}. This helper extracts the same
   * property keys and applies the same null-guard logic, then delegates to {@link
   * RecordingScopeParser#fromOptions}.
   *
   * @param props the properties to extract scope configuration from
   * @return the recording scope, or {@code null} if no scope properties are configured
   */
  private static RecordingScope buildScopeFromProperties(Properties props) {
    String yamlPath = props.getProperty("scope.policy.path");
    boolean includeIo = Boolean.parseBoolean(props.getProperty("scope.io", "false"));
    String includePatterns = props.getProperty("scope.patterns");
    String excludePatterns = props.getProperty("scope.exclude.patterns");
    String defaultActionStr = props.getProperty("scope.default.action");

    if (yamlPath == null
        && !includeIo
        && includePatterns == null
        && excludePatterns == null
        && defaultActionStr == null) {
      return null;
    }

    return RecordingScopeParser.fromOptions(
        yamlPath,
        includeIo,
        includePatterns != null ? includePatterns.split(",") : null,
        excludePatterns != null ? excludePatterns.split(",") : null,
        defaultActionStr);
  }
}
