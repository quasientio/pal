/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.tools.cli.init;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * Unit tests for {@link EnvFileGenerator}.
 *
 * @see EnvFileGenerator
 */
public class EnvFileGeneratorTest {

  @Rule public TemporaryFolder tempDir = new TemporaryFolder();

  /**
   * Verifies that for LOCAL mode the generated {@code .env.pal} contains a local WAL path and
   * distributed variables commented out.
   */
  @Test
  public void testGeneratesEnvPalForLocalMode() throws Exception {
    // Given
    InitConfig config =
        InitConfig.builder().groupId("com.example").deploymentMode(DeploymentMode.LOCAL).build();
    EnvFileGenerator generator = new EnvFileGenerator(config);

    // When
    generator.generate(tempDir.getRoot().toPath());

    // Then
    Path envFile = tempDir.getRoot().toPath().resolve(".env.pal");
    assertTrue(".env.pal should exist", Files.exists(envFile));
    String content = Files.readString(envFile);
    assertThat(content, containsString("export WAL=\"file:./wal\""));
    assertThat(content, containsString("export PAL_HOME="));
    assertThat(content, containsString("# export PAL_DIRECTORY="));
    assertThat(content, containsString("# export KAFKA_SERVERS="));
  }

  /**
   * Verifies that for DISTRIBUTED mode the generated {@code .env.pal} contains distributed settings
   * uncommented.
   */
  @Test
  public void testGeneratesEnvPalForDistributedMode() throws Exception {
    // Given
    InitConfig config =
        InitConfig.builder()
            .groupId("com.example")
            .deploymentMode(DeploymentMode.DISTRIBUTED)
            .build();
    EnvFileGenerator generator = new EnvFileGenerator(config);

    // When
    generator.generate(tempDir.getRoot().toPath());

    // Then
    Path envFile = tempDir.getRoot().toPath().resolve(".env.pal");
    String content = Files.readString(envFile);
    assertThat(content, containsString("export PAL_DIRECTORY=\"localhost:2379\""));
    assertThat(content, containsString("export KAFKA_SERVERS=\"localhost:29092\""));
    assertThat(content, containsString("# export WAL=\"file:./wal\""));
  }

  /**
   * Verifies that for BOTH mode the generated {@code .env.pal} contains local defaults active with
   * distributed settings as comments.
   */
  @Test
  public void testGeneratesEnvPalForBothMode() throws Exception {
    // Given
    InitConfig config =
        InitConfig.builder().groupId("com.example").deploymentMode(DeploymentMode.BOTH).build();
    EnvFileGenerator generator = new EnvFileGenerator(config);

    // When
    generator.generate(tempDir.getRoot().toPath());

    // Then
    Path envFile = tempDir.getRoot().toPath().resolve(".env.pal");
    String content = Files.readString(envFile);
    assertThat(content, containsString("export WAL=\"file:./wal\""));
    assertThat(content, containsString("# export PAL_DIRECTORY="));
    assertThat(content, containsString("# export KAFKA_SERVERS="));
  }

  /** Verifies that all variable assignments use {@code export VAR=value} syntax. */
  @Test
  public void testEnvFileIsSourceable() throws Exception {
    // Given
    InitConfig config =
        InitConfig.builder().groupId("com.example").deploymentMode(DeploymentMode.LOCAL).build();
    EnvFileGenerator generator = new EnvFileGenerator(config);

    // When
    generator.generate(tempDir.getRoot().toPath());

    // Then
    Path envFile = tempDir.getRoot().toPath().resolve(".env.pal");
    String content = Files.readString(envFile);
    for (String line : content.lines().toList()) {
      String trimmed = line.trim();
      if (trimmed.isEmpty() || trimmed.startsWith("#")) {
        continue;
      }
      assertTrue(
          "Non-comment line should use export syntax: " + trimmed, trimmed.startsWith("export "));
    }
  }

  /**
   * Verifies that when {@code dryRun=true}, the generator does not write the {@code .env.pal} file.
   */
  @Test
  public void testDryRunDoesNotWriteFile() throws Exception {
    // Given
    InitConfig config =
        InitConfig.builder()
            .groupId("com.example")
            .deploymentMode(DeploymentMode.LOCAL)
            .dryRun(true)
            .build();
    EnvFileGenerator generator = new EnvFileGenerator(config);

    // When
    List<Path> generated = generator.generate(tempDir.getRoot().toPath());

    // Then
    assertFalse("Should report files", generated.isEmpty());
    Path envFile = tempDir.getRoot().toPath().resolve(".env.pal");
    assertFalse(".env.pal should not be created in dry-run", Files.exists(envFile));
  }
}
