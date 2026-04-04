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
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * Unit tests for {@link ReadmeGenerator}.
 *
 * @see ReadmeGenerator
 */
public class ReadmeGeneratorTest {

  /** Temporary directory for generated output. */
  @Rule public TemporaryFolder tempDir = new TemporaryFolder();

  /** Verifies that the generator creates a README.md with Maven build commands. */
  @Test
  public void testGeneratesMavenReadme() throws Exception {
    // Given
    InitConfig config =
        InitConfig.builder()
            .groupId("com.example")
            .artifactId("my-app")
            .buildTool(BuildTool.MAVEN)
            .mainClass("com.example.Main")
            .build();
    ReadmeGenerator generator = new ReadmeGenerator(config);

    // When
    List<Path> generated = generator.generate(tempDir.getRoot().toPath());

    // Then
    assertFalse(generated.isEmpty());
    String content = Files.readString(generated.get(0), StandardCharsets.UTF_8);
    assertThat(content, containsString("# my-app"));
    assertThat(content, containsString("mvn test"));
    assertThat(content, containsString("mvn package"));
    assertThat(content, containsString("prepare-package"));
    assertThat(content, containsString("unwoven"));
    assertThat(content, containsString("target/classes"));
    assertThat(content, containsString("com.example.Main"));
  }

  /** Verifies that the generator creates a README.md with Gradle build commands. */
  @Test
  public void testGeneratesGradleReadme() throws Exception {
    // Given
    InitConfig config =
        InitConfig.builder()
            .groupId("com.example")
            .artifactId("my-app")
            .buildTool(BuildTool.GRADLE)
            .mainClass("com.example.Main")
            .build();
    ReadmeGenerator generator = new ReadmeGenerator(config);

    // When
    List<Path> generated = generator.generate(tempDir.getRoot().toPath());

    // Then
    String content = Files.readString(generated.get(0), StandardCharsets.UTF_8);
    assertThat(content, containsString("gradle test"));
    assertThat(content, containsString("gradle build"));
    assertThat(content, containsString("weaveClasses"));
    assertThat(content, containsString("unwoven"));
    assertThat(content, containsString("build/classes/java/main"));
    assertThat(content, not(containsString("mvn")));
  }

  /** Verifies that the main class falls back to groupId + ".Main" when not specified. */
  @Test
  public void testFallsBackToGroupIdMain() throws Exception {
    // Given
    InitConfig config =
        InitConfig.builder()
            .groupId("com.example")
            .artifactId("my-app")
            .buildTool(BuildTool.MAVEN)
            .build();
    ReadmeGenerator generator = new ReadmeGenerator(config);

    // When
    generator.generate(tempDir.getRoot().toPath());

    // Then
    String content =
        Files.readString(tempDir.getRoot().toPath().resolve("README.md"), StandardCharsets.UTF_8);
    assertThat(content, containsString("com.example.Main"));
  }

  /** Verifies that the generator does not overwrite an existing README.md without force. */
  @Test
  public void testDoesNotOverwriteWithoutForce() throws Exception {
    // Given
    Path readme = tempDir.getRoot().toPath().resolve("README.md");
    String original = "# Existing README\n";
    Files.writeString(readme, original, StandardCharsets.UTF_8);

    InitConfig config =
        InitConfig.builder()
            .groupId("com.example")
            .artifactId("my-app")
            .buildTool(BuildTool.MAVEN)
            .force(false)
            .build();
    ReadmeGenerator generator = new ReadmeGenerator(config);

    // When
    List<Path> generated = generator.generate(tempDir.getRoot().toPath());

    // Then
    assertTrue("Should return empty list when skipped", generated.isEmpty());
    assertEquals(original, Files.readString(readme, StandardCharsets.UTF_8));
  }

  /** Verifies that dry-run returns the file path but does not write it. */
  @Test
  public void testDryRunDoesNotWriteFile() throws Exception {
    // Given
    InitConfig config =
        InitConfig.builder()
            .groupId("com.example")
            .artifactId("my-app")
            .buildTool(BuildTool.MAVEN)
            .dryRun(true)
            .build();
    ReadmeGenerator generator = new ReadmeGenerator(config);

    // When
    List<Path> generated = generator.generate(tempDir.getRoot().toPath());

    // Then
    assertFalse("Should report the file", generated.isEmpty());
    assertFalse("File should not exist", Files.exists(generated.get(0)));
  }
}
