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
import static org.hamcrest.CoreMatchers.not;
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
   * Verifies that the generated {@code .env.pal} always contains the WAL section and includes etcd
   * and kafka sections based on intent flags.
   */
  @Test
  public void testGeneratesEnvPalWithAllSections() throws Exception {
    // Given: interceptable + kafka → all sections present
    InitConfig config =
        InitConfig.builder()
            .groupId("com.example")
            .interceptable(true)
            .kafka(true)
            .loggingConfig(true)
            .build();
    EnvFileGenerator generator = new EnvFileGenerator(config);

    // When
    generator.generate(tempDir.getRoot().toPath());

    // Then
    Path envFile = tempDir.getRoot().toPath().resolve(".env.pal");
    assertTrue(".env.pal should exist", Files.exists(envFile));
    String content = Files.readString(envFile);
    assertThat(content, containsString("# export PAL_WAL=\"file:./wal\""));
    assertThat(content, containsString("export PAL_DIRECTORY=\"localhost:2379\""));
    assertThat(content, containsString("export PAL_KAFKA_SERVERS=\"localhost:29092\""));
    assertThat(content, containsString("# export PAL_PEER_LOGGING_CONFIG="));
    assertThat(content, containsString("# export PAL_CLI_LOGGING_CONFIG="));
  }

  /**
   * Verifies that a plain local config (no intercepts, no kafka) only includes WAL and logging
   * sections.
   */
  @Test
  public void testPlainLocalOmitsEtcdAndKafka() throws Exception {
    // Given: no intercepts, no kafka
    InitConfig config = InitConfig.builder().groupId("com.example").loggingConfig(true).build();
    EnvFileGenerator generator = new EnvFileGenerator(config);

    // When
    generator.generate(tempDir.getRoot().toPath());

    // Then
    Path envFile = tempDir.getRoot().toPath().resolve(".env.pal");
    String content = Files.readString(envFile);
    assertThat(content, containsString("# export PAL_WAL=\"file:./wal\""));
    assertThat(content, not(containsString("PAL_DIRECTORY")));
    assertThat(content, not(containsString("PAL_KAFKA_SERVERS")));
    assertThat(content, containsString("# export PAL_PEER_LOGGING_CONFIG="));
  }

  /** Verifies that all variable assignments use {@code export VAR=value} syntax. */
  @Test
  public void testEnvFileIsSourceable() throws Exception {
    // Given
    InitConfig config =
        InitConfig.builder()
            .groupId("com.example")
            .interceptable(true)
            .kafka(true)
            .loggingConfig(true)
            .build();
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
    InitConfig config = InitConfig.builder().groupId("com.example").dryRun(true).build();
    EnvFileGenerator generator = new EnvFileGenerator(config);

    // When
    List<Path> generated = generator.generate(tempDir.getRoot().toPath());

    // Then
    assertFalse("Should report files", generated.isEmpty());
    Path envFile = tempDir.getRoot().toPath().resolve(".env.pal");
    assertFalse(".env.pal should not be created in dry-run", Files.exists(envFile));
  }
}
