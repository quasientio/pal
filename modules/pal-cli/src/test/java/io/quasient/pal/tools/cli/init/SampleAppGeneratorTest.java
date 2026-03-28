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
 * Unit tests for {@link SampleAppGenerator}.
 *
 * @see SampleAppGenerator
 */
public class SampleAppGeneratorTest {

  @Rule public TemporaryFolder tempDir = new TemporaryFolder();

  /**
   * Verifies that the generator creates a main class file at the correct path with the right
   * package declaration and a {@code public static void main} method.
   */
  @Test
  public void testGeneratesMainClass() throws Exception {
    // Given
    InitConfig config =
        InitConfig.builder()
            .groupId("com.example")
            .mainClass("com.example.Main")
            .packageName("com.example")
            .sampleApp(true)
            .build();
    SampleAppGenerator generator = new SampleAppGenerator(config);

    // When
    List<Path> generated = generator.generate(tempDir.getRoot().toPath());

    // Then
    Path mainFile = tempDir.getRoot().toPath().resolve("src/main/java/com/example/Main.java");
    assertTrue("Main.java should exist", Files.exists(mainFile));
    String content = Files.readString(mainFile);
    assertThat(content, containsString("package com.example;"));
    assertThat(content, containsString("public static void main(String[] args)"));
    assertTrue("Generated list should include Main.java", generated.contains(mainFile));
  }

  /**
   * Verifies that when {@code sampleApp=true}, the generator creates a {@code SampleService.java}
   * file containing some interesting operations suitable for demonstrating PAL features.
   */
  @Test
  public void testGeneratesSampleService() throws Exception {
    // Given
    InitConfig config =
        InitConfig.builder()
            .groupId("com.example")
            .mainClass("com.example.Main")
            .packageName("com.example")
            .sampleApp(true)
            .build();
    SampleAppGenerator generator = new SampleAppGenerator(config);

    // When
    generator.generate(tempDir.getRoot().toPath());

    // Then
    Path serviceFile =
        tempDir.getRoot().toPath().resolve("src/main/java/com/example/SampleService.java");
    assertTrue("SampleService.java should exist", Files.exists(serviceFile));
    String content = Files.readString(serviceFile);
    assertThat(content, containsString("processOrder"));
  }

  /**
   * Verifies that when {@code sampleApp=false}, the generator does not create any Java source
   * files.
   */
  @Test
  public void testSkipsWhenDisabled() throws Exception {
    // Given
    InitConfig config =
        InitConfig.builder()
            .groupId("com.example")
            .mainClass("com.example.Main")
            .sampleApp(false)
            .build();
    SampleAppGenerator generator = new SampleAppGenerator(config);

    // When
    List<Path> generated = generator.generate(tempDir.getRoot().toPath());

    // Then
    assertTrue("Should return empty list", generated.isEmpty());
    Path srcDir = tempDir.getRoot().toPath().resolve("src");
    assertFalse("src directory should not be created", Files.exists(srcDir));
  }

  /**
   * Verifies that all generated source files contain the correct {@code package} declaration
   * matching the configured package name.
   */
  @Test
  public void testCorrectPackageDeclaration() throws Exception {
    // Given
    InitConfig config =
        InitConfig.builder()
            .groupId("com.acme")
            .mainClass("com.acme.orders.Main")
            .packageName("com.acme.orders")
            .sampleApp(true)
            .build();
    SampleAppGenerator generator = new SampleAppGenerator(config);

    // When
    generator.generate(tempDir.getRoot().toPath());

    // Then
    Path mainFile = tempDir.getRoot().toPath().resolve("src/main/java/com/acme/orders/Main.java");
    Path serviceFile =
        tempDir.getRoot().toPath().resolve("src/main/java/com/acme/orders/SampleService.java");
    assertThat(Files.readString(mainFile), containsString("package com.acme.orders;"));
    assertThat(Files.readString(serviceFile), containsString("package com.acme.orders;"));
  }

  /**
   * Verifies that generated source files do not import any {@code io.quasient.pal.*} classes, since
   * PAL weaving is transparent and application code should not depend on PAL APIs directly.
   */
  @Test
  public void testNoDirectPalImports() throws Exception {
    // Given
    InitConfig config =
        InitConfig.builder()
            .groupId("com.example")
            .mainClass("com.example.Main")
            .packageName("com.example")
            .sampleApp(true)
            .build();
    SampleAppGenerator generator = new SampleAppGenerator(config);

    // When
    generator.generate(tempDir.getRoot().toPath());

    // Then
    Path mainFile = tempDir.getRoot().toPath().resolve("src/main/java/com/example/Main.java");
    Path serviceFile =
        tempDir.getRoot().toPath().resolve("src/main/java/com/example/SampleService.java");
    assertThat(Files.readString(mainFile), not(containsString("io.quasient.pal")));
    assertThat(Files.readString(serviceFile), not(containsString("io.quasient.pal")));
  }

  /**
   * Verifies that when {@code dryRun=true}, the generator does not write any source files to disk.
   */
  @Test
  public void testDryRunDoesNotWriteFiles() throws Exception {
    // Given
    InitConfig config =
        InitConfig.builder()
            .groupId("com.example")
            .mainClass("com.example.Main")
            .packageName("com.example")
            .sampleApp(true)
            .dryRun(true)
            .build();
    SampleAppGenerator generator = new SampleAppGenerator(config);

    // When
    List<Path> generated = generator.generate(tempDir.getRoot().toPath());

    // Then
    assertFalse("Should report files", generated.isEmpty());
    Path srcDir = tempDir.getRoot().toPath().resolve("src");
    assertFalse("src directory should not be created in dry-run", Files.exists(srcDir));
  }
}
