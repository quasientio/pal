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
package io.quasient.pal.docs;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;

import java.nio.file.Path;
import java.nio.file.Paths;
import org.junit.Test;

/**
 * Unit tests for {@code DocCommand}, the value class representing a parsed documentation command.
 *
 * <p>DocCommand stores the source file, line number, raw text, normalized text, and classified type
 * of a CLI command extracted from a markdown code block. These tests define the contract for
 * construction, field access, null-safety, and toString formatting.
 */
public class DocCommandTest {

  /** Verifies that all fields provided at construction time are accessible via getters. */
  @Test
  public void shouldStoreAllFieldsCorrectly() {
    // Given: valid sourceFile, lineNumber, rawText, normalizedText, and type
    Path sourceFile = Paths.get("docs/user/docs/getting-started.md");
    int lineNumber = 83;
    String rawText = "pal init pal-tutorial";
    String normalizedText = "pal init pal-tutorial";
    DocCommandType type = DocCommandType.INIT;

    // When: DocCommand is constructed with those values
    DocCommand cmd = new DocCommand(sourceFile, lineNumber, rawText, normalizedText, type);

    // Then: all getters return the provided values
    assertThat(cmd.getSourceFile(), is(sourceFile));
    assertThat(cmd.getLineNumber(), is(lineNumber));
    assertThat(cmd.getRawText(), is(rawText));
    assertThat(cmd.getNormalizedText(), is(normalizedText));
    assertThat(cmd.getType(), is(type));
  }

  /** Verifies that constructing a DocCommand with a null sourceFile throws NullPointerException. */
  @Test(expected = NullPointerException.class)
  public void shouldRejectNullSourceFile() {
    // Given: null sourceFile, with valid rawText, normalizedText, lineNumber, and type
    // When: DocCommand is constructed
    // Then: NullPointerException is thrown
    new DocCommand(null, 1, "pal help", "pal help", DocCommandType.HELP);
  }

  /** Verifies that constructing a DocCommand with a null rawText throws NullPointerException. */
  @Test(expected = NullPointerException.class)
  public void shouldRejectNullRawText() {
    // Given: null rawText, with valid sourceFile, normalizedText, lineNumber, and type
    // When: DocCommand is constructed
    // Then: NullPointerException is thrown
    new DocCommand(Paths.get("test.md"), 1, null, "pal help", DocCommandType.HELP);
  }

  /** Verifies that constructing a DocCommand with a null type throws NullPointerException. */
  @Test(expected = NullPointerException.class)
  public void shouldRejectNullType() {
    // Given: null type, with valid sourceFile, rawText, normalizedText, and lineNumber
    // When: DocCommand is constructed
    // Then: NullPointerException is thrown
    new DocCommand(Paths.get("test.md"), 1, "pal help", "pal help", null);
  }

  /** Verifies that toString() formats as "filename:line -> command". */
  @Test
  public void shouldFormatToStringWithFileAndLine() {
    // Given: DocCommand from "getting-started.md" line 83 with rawText "pal init pal-tutorial"
    DocCommand cmd =
        new DocCommand(
            Paths.get("docs/user/docs/getting-started.md"),
            83,
            "pal init pal-tutorial",
            "pal init pal-tutorial",
            DocCommandType.INIT);

    // When: toString() is called
    String result = cmd.toString();

    // Then: result matches pattern "getting-started.md:83 -> pal init pal-tutorial"
    assertThat(result, is("getting-started.md:83 -> pal init pal-tutorial"));
  }

  /** Verifies that toString() truncates commands longer than 80 characters with an ellipsis. */
  @Test
  public void shouldTruncateLongCommandsInToString() {
    // Given: DocCommand with a normalizedText longer than 80 characters
    String longCommand =
        "pal run -d localhost:2379 -k localhost:29092 --wal my-very-long-wal-name"
            + " -cp target/my-app.jar com.example.Main";
    DocCommand cmd =
        new DocCommand(Paths.get("test.md"), 10, longCommand, longCommand, DocCommandType.RUN);

    // When: toString() is called
    String result = cmd.toString();

    // Then: the command portion is truncated with ellipsis
    assertThat(result, containsString("..."));
    // The format is "test.md:10 -> <truncated>..."
    // "test.md:10 -> " is 15 chars, so total should be 15 + 80 + 3 = 98
    String prefix = "test.md:10 -> ";
    assertThat(result.startsWith(prefix), is(true));
    String commandPortion = result.substring(prefix.length());
    assertThat(commandPortion.length(), is(83)); // 80 chars + "..."
    assertThat(commandPortion.endsWith("..."), is(true));
  }
}
