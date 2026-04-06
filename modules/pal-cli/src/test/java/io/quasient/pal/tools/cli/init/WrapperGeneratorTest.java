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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * Unit tests for {@link WrapperGenerator}.
 *
 * @see WrapperGenerator
 */
public class WrapperGeneratorTest {

  /** Temporary directory for generated output. */
  @Rule public TemporaryFolder tempDir = new TemporaryFolder();

  /** Verifies that Maven wrapper files are generated for a Maven project. */
  @Test
  public void testGeneratesMavenWrapper() throws Exception {
    InitConfig config =
        InitConfig.builder()
            .groupId("com.example")
            .artifactId("my-app")
            .buildTool(BuildTool.MAVEN)
            .build();
    WrapperGenerator generator = new WrapperGenerator(config);

    List<Path> generated = generator.generate(tempDir.getRoot().toPath());

    assertTrue("Should generate 3 files", generated.size() == 3);
    assertTrue("mvnw should exist", Files.exists(tempDir.getRoot().toPath().resolve("mvnw")));
    assertTrue(
        "mvnw.cmd should exist", Files.exists(tempDir.getRoot().toPath().resolve("mvnw.cmd")));
    assertTrue(
        "maven-wrapper.properties should exist",
        Files.exists(tempDir.getRoot().toPath().resolve(".mvn/wrapper/maven-wrapper.properties")));
  }

  /** Verifies that mvnw is executable on POSIX systems. */
  @Test
  public void testMvnwIsExecutable() throws Exception {
    InitConfig config =
        InitConfig.builder()
            .groupId("com.example")
            .artifactId("my-app")
            .buildTool(BuildTool.MAVEN)
            .build();
    new WrapperGenerator(config).generate(tempDir.getRoot().toPath());

    Path mvnw = tempDir.getRoot().toPath().resolve("mvnw");
    assertTrue("mvnw should be executable", Files.isExecutable(mvnw));
  }

  /** Verifies that Gradle wrapper files are generated for a Gradle project. */
  @Test
  public void testGeneratesGradleWrapper() throws Exception {
    InitConfig config =
        InitConfig.builder()
            .groupId("com.example")
            .artifactId("my-app")
            .buildTool(BuildTool.GRADLE)
            .build();
    WrapperGenerator generator = new WrapperGenerator(config);

    List<Path> generated = generator.generate(tempDir.getRoot().toPath());

    assertTrue("Should generate 4 files", generated.size() == 4);
    assertTrue("gradlew should exist", Files.exists(tempDir.getRoot().toPath().resolve("gradlew")));
    assertTrue(
        "gradlew.bat should exist",
        Files.exists(tempDir.getRoot().toPath().resolve("gradlew.bat")));
    assertTrue(
        "gradle-wrapper.properties should exist",
        Files.exists(
            tempDir.getRoot().toPath().resolve("gradle/wrapper/gradle-wrapper.properties")));
    assertTrue(
        "gradle-wrapper.jar should exist",
        Files.exists(tempDir.getRoot().toPath().resolve("gradle/wrapper/gradle-wrapper.jar")));
  }

  /** Verifies that gradlew is executable on POSIX systems. */
  @Test
  public void testGradlewIsExecutable() throws Exception {
    InitConfig config =
        InitConfig.builder()
            .groupId("com.example")
            .artifactId("my-app")
            .buildTool(BuildTool.GRADLE)
            .build();
    new WrapperGenerator(config).generate(tempDir.getRoot().toPath());

    Path gradlew = tempDir.getRoot().toPath().resolve("gradlew");
    assertTrue("gradlew should be executable", Files.isExecutable(gradlew));
  }

  /** Verifies that dry-run returns file paths but does not write any files. */
  @Test
  public void testDryRunDoesNotWriteFiles() throws Exception {
    InitConfig config =
        InitConfig.builder()
            .groupId("com.example")
            .artifactId("my-app")
            .buildTool(BuildTool.MAVEN)
            .dryRun(true)
            .build();
    WrapperGenerator generator = new WrapperGenerator(config);

    List<Path> generated = generator.generate(tempDir.getRoot().toPath());

    assertFalse("Should report files", generated.isEmpty());
    assertFalse("mvnw should not exist in dry-run", Files.exists(generated.get(0)));
  }

  /** Verifies that dry-run works for Gradle wrapper too. */
  @Test
  public void testDryRunGradleDoesNotWriteFiles() throws Exception {
    InitConfig config =
        InitConfig.builder()
            .groupId("com.example")
            .artifactId("my-app")
            .buildTool(BuildTool.GRADLE)
            .dryRun(true)
            .build();
    WrapperGenerator generator = new WrapperGenerator(config);

    List<Path> generated = generator.generate(tempDir.getRoot().toPath());

    assertFalse("Should report files", generated.isEmpty());
    assertFalse("gradlew should not exist in dry-run", Files.exists(generated.get(0)));
  }
}
