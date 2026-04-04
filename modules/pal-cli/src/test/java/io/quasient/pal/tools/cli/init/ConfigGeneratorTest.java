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
package io.quasient.pal.tools.cli.init;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * Unit tests for {@link ConfigGenerator}.
 *
 * @see ConfigGenerator
 */
public class ConfigGeneratorTest {

  @Rule public TemporaryFolder tempDir = new TemporaryFolder();

  /**
   * Verifies that when {@code loggingConfig=true}, the generator creates peer and CLI logging
   * config files with PAL runtime loggers (not application loggers).
   */
  @Test
  public void testGeneratesLoggingConfigs() throws Exception {
    // Given
    InitConfig config =
        InitConfig.builder()
            .groupId("com.example")
            .packageName("com.example")
            .loggingConfig(true)
            .build();
    ConfigGenerator generator = new ConfigGenerator(config);

    // When
    generator.generate(tempDir.getRoot().toPath());

    // Then — peer-logging.xml
    Path peerLoggingFile = tempDir.getRoot().toPath().resolve("config/peer-logging.xml");
    assertTrue("peer-logging.xml should exist", Files.exists(peerLoggingFile));
    String peerContent = Files.readString(peerLoggingFile);
    assertThat(peerContent, containsString("<configuration"));
    assertThat(peerContent, containsString("io.quasient.pal"));

    // Then — cli-logging.xml
    Path cliLoggingFile = tempDir.getRoot().toPath().resolve("config/cli-logging.xml");
    assertTrue("cli-logging.xml should exist", Files.exists(cliLoggingFile));
    String cliContent = Files.readString(cliLoggingFile);
    assertThat(cliContent, containsString("<configuration"));
    assertThat(cliContent, containsString("io.quasient.pal"));
  }

  /**
   * Verifies that when {@code rpcPolicy=true}, the generator creates a YAML file at {@code
   * config/rpc-policy.yaml} with an ALLOW default and deny-unsafe preset.
   */
  @Test
  public void testGeneratesRpcPolicy() throws Exception {
    // Given
    InitConfig config =
        InitConfig.builder()
            .groupId("com.example")
            .packageName("com.example")
            .rpcPolicy(true)
            .loggingConfig(false)
            .build();
    ConfigGenerator generator = new ConfigGenerator(config);

    // When
    generator.generate(tempDir.getRoot().toPath());

    // Then
    Path rpcFile = tempDir.getRoot().toPath().resolve("config/rpc-policy.yaml");
    assertTrue("rpc-policy.yaml should exist", Files.exists(rpcFile));
    String content = Files.readString(rpcFile);
    assertThat(content, containsString("defaultAction: ALLOW"));
    assertThat(content, containsString("deny-unsafe: true"));
    assertThat(content, containsString("deny-jdk-internals: true"));
    assertThat(content, containsString("deny-nonpublic: true"));
  }

  /**
   * Verifies that when {@code scopePolicy=true}, the generator creates a YAML file at {@code
   * config/recording-scope.yaml} containing include patterns derived from the configured package.
   */
  @Test
  public void testGeneratesRecordingScope() throws Exception {
    // Given
    InitConfig config =
        InitConfig.builder()
            .groupId("com.example")
            .packageName("com.example")
            .scopePolicy(true)
            .loggingConfig(false)
            .build();
    ConfigGenerator generator = new ConfigGenerator(config);

    // When
    generator.generate(tempDir.getRoot().toPath());

    // Then
    Path scopeFile = tempDir.getRoot().toPath().resolve("config/recording-scope.yaml");
    assertTrue("recording-scope.yaml should exist", Files.exists(scopeFile));
    String content = Files.readString(scopeFile);
    assertThat(content, containsString("defaultAction: SKIP"));
    assertThat(content, containsString("com.example.**"));
    assertThat(content, containsString("categories: [FIELD_GET]"));
    assertThat(content, containsString("action: RECORD"));
  }

  /**
   * Verifies that when {@code interceptBundle=true}, the generator creates a YAML file at {@code
   * config/intercept-bundle.yaml}.
   */
  @Test
  public void testGeneratesInterceptBundle() throws Exception {
    // Given
    InitConfig config =
        InitConfig.builder()
            .groupId("com.example")
            .packageName("com.example")
            .mainClass("com.example.Main")
            .interceptBundle(true)
            .loggingConfig(false)
            .build();
    ConfigGenerator generator = new ConfigGenerator(config);

    // When
    generator.generate(tempDir.getRoot().toPath());

    // Then
    Path bundleFile = tempDir.getRoot().toPath().resolve("config/intercept-bundle.yaml");
    assertTrue("intercept-bundle.yaml should exist", Files.exists(bundleFile));
    String content = Files.readString(bundleFile);
    assertThat(content, containsString("com.example.Main"));
  }

  /**
   * Verifies that when all config flags are {@code false}, the generator does not create any config
   * files.
   */
  @Test
  public void testSkipsDisabledConfigs() throws Exception {
    // Given
    InitConfig config =
        InitConfig.builder()
            .groupId("com.example")
            .loggingConfig(false)
            .rpcPolicy(false)
            .scopePolicy(false)
            .interceptBundle(false)
            .build();
    ConfigGenerator generator = new ConfigGenerator(config);

    // When
    List<Path> generated = generator.generate(tempDir.getRoot().toPath());

    // Then
    assertTrue("Should return empty list", generated.isEmpty());
    Path configDir = tempDir.getRoot().toPath().resolve("config");
    assertFalse("config directory should not be created", Files.exists(configDir));
  }

  /**
   * Verifies that the generator does not overwrite an existing {@code config/peer-logging.xml} file
   * when {@code force=false}.
   */
  @Test
  public void testDoesNotOverwriteExistingWithoutForce() throws Exception {
    // Given
    Path configDir = tempDir.getRoot().toPath().resolve("config");
    Files.createDirectories(configDir);
    Path existingFile = configDir.resolve("peer-logging.xml");
    String originalContent = "<!-- existing content -->";
    Files.writeString(existingFile, originalContent);

    InitConfig config =
        InitConfig.builder()
            .groupId("com.example")
            .packageName("com.example")
            .loggingConfig(true)
            .force(false)
            .build();
    ConfigGenerator generator = new ConfigGenerator(config);

    // When
    generator.generate(tempDir.getRoot().toPath());

    // Then
    assertEquals(
        "Existing file should not be overwritten", originalContent, Files.readString(existingFile));
  }

  /**
   * Verifies that when {@code dryRun=true}, the generator does not write any config files to disk.
   */
  @Test
  public void testDryRunDoesNotWriteFiles() throws Exception {
    // Given
    InitConfig config =
        InitConfig.builder()
            .groupId("com.example")
            .packageName("com.example")
            .loggingConfig(true)
            .rpcPolicy(true)
            .scopePolicy(true)
            .interceptBundle(true)
            .mainClass("com.example.Main")
            .dryRun(true)
            .build();
    ConfigGenerator generator = new ConfigGenerator(config);

    // When
    List<Path> generated = generator.generate(tempDir.getRoot().toPath());

    // Then
    assertFalse("Should report files", generated.isEmpty());
    Path configDir = tempDir.getRoot().toPath().resolve("config");
    assertFalse("config directory should not be created in dry-run", Files.exists(configDir));
  }
}
