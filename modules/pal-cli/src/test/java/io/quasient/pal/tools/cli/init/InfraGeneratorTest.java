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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * Unit tests for {@link InfraGenerator}.
 *
 * @see InfraGenerator
 */
public class InfraGeneratorTest {

  @Rule public TemporaryFolder tempDir = new TemporaryFolder();

  /**
   * Verifies that when {@code infra=true}, the generator creates a {@code infra/docker-compose.yml}
   * file containing service definitions for etcd and Kafka.
   */
  @Test
  public void testGeneratesDockerCompose() throws Exception {
    // Given
    InitConfig config = InitConfig.builder().groupId("com.example").infra(true).build();
    InfraGenerator generator = new InfraGenerator(config);

    // When
    generator.generate(tempDir.getRoot().toPath());

    // Then
    Path composeFile = tempDir.getRoot().toPath().resolve("infra/docker-compose.yml");
    assertTrue("docker-compose.yml should exist", Files.exists(composeFile));
    String content = Files.readString(composeFile);
    assertThat(content, containsString("etcd"));
    assertThat(content, containsString("kafka"));
  }

  /**
   * Verifies that when {@code infra=true}, the generator creates an {@code infra/.env} file
   * containing port configurations.
   */
  @Test
  public void testGeneratesDockerEnv() throws Exception {
    // Given
    InitConfig config = InitConfig.builder().groupId("com.example").infra(true).build();
    InfraGenerator generator = new InfraGenerator(config);

    // When
    generator.generate(tempDir.getRoot().toPath());

    // Then
    Path envFile = tempDir.getRoot().toPath().resolve("infra/.env");
    assertTrue("infra/.env should exist", Files.exists(envFile));
    String content = Files.readString(envFile);
    assertThat(content, containsString("KAFKA_PORT"));
    assertThat(content, containsString("ETCD_CLIENT_PORT"));
  }

  /** Verifies that when {@code infra=true}, the generator creates start.sh and stop.sh scripts. */
  @Test
  public void testGeneratesStartStopScripts() throws Exception {
    // Given
    InitConfig config = InitConfig.builder().groupId("com.example").infra(true).build();
    InfraGenerator generator = new InfraGenerator(config);

    // When
    generator.generate(tempDir.getRoot().toPath());

    // Then
    Path startScript = tempDir.getRoot().toPath().resolve("infra/start.sh");
    Path stopScript = tempDir.getRoot().toPath().resolve("infra/stop.sh");
    assertTrue("start.sh should exist", Files.exists(startScript));
    assertTrue("stop.sh should exist", Files.exists(stopScript));
    assertThat(Files.readString(startScript), containsString("docker compose"));
    assertThat(Files.readString(stopScript), containsString("docker compose"));
  }

  /**
   * Verifies that when {@code infra=false}, the generator does not create any infrastructure files.
   */
  @Test
  public void testSkipsWhenDisabled() throws Exception {
    // Given
    InitConfig config = InitConfig.builder().groupId("com.example").infra(false).build();
    InfraGenerator generator = new InfraGenerator(config);

    // When
    List<Path> generated = generator.generate(tempDir.getRoot().toPath());

    // Then
    assertTrue("Should return empty list", generated.isEmpty());
    Path infraDir = tempDir.getRoot().toPath().resolve("infra");
    assertFalse("infra directory should not be created", Files.exists(infraDir));
  }

  /**
   * Verifies that when {@code dryRun=true}, the generator does not write any files even when {@code
   * infra=true}.
   */
  @Test
  public void testDryRunDoesNotWriteFiles() throws Exception {
    // Given
    InitConfig config =
        InitConfig.builder().groupId("com.example").infra(true).dryRun(true).build();
    InfraGenerator generator = new InfraGenerator(config);

    // When
    List<Path> generated = generator.generate(tempDir.getRoot().toPath());

    // Then
    assertFalse("Should report files", generated.isEmpty());
    Path infraDir = tempDir.getRoot().toPath().resolve("infra");
    assertFalse("infra directory should not be created in dry-run", Files.exists(infraDir));
  }
}
