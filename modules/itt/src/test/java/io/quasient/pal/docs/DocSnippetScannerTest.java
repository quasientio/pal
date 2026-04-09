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

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * Unit test specifications for {@code DocSnippetScanner}, the markdown parser that discovers all
 * PAL CLI commands from documentation files.
 *
 * <p>Tests use inline markdown strings (or temp files) to validate parsing behavior without needing
 * the actual docs directory. Each test defines expected behavior for a specific parsing edge case
 * found in the real documentation: line continuations, pipes, heredocs, env prefixes, comments,
 * non-bash blocks, and more.
 */
public class DocSnippetScannerTest {

  @Rule public TemporaryFolder tempFolder = new TemporaryFolder();

  /**
   * Verifies that a single {@code pal} command inside a {@code ```bash} block is extracted with
   * correct metadata.
   */
  @Test
  public void shouldExtractPalCommandFromSimpleBashBlock() throws IOException {
    Path file =
        writeMarkdown(
            """
            Some text

            ```bash
            pal peer ls -d localhost:2379
            ```
            """);

    List<DocCommand> commands = DocSnippetScanner.scanFile(file);

    assertThat(commands.size(), is(1));
    DocCommand cmd = commands.get(0);
    assertThat(cmd.getType(), is(DocCommandType.PEER_LS));
    assertThat(cmd.getNormalizedText(), is("pal peer ls -d localhost:2379"));
    assertThat(cmd.getSourceFile(), is(file));
    assertThat(cmd.getLineNumber(), is(4));
  }

  /** Verifies that backslash-newline continuations are joined into a single normalized command. */
  @Test
  public void shouldJoinLineContinuations() throws IOException {
    Path file =
        writeMarkdown(
            """
            ```bash
            pal run -d localhost:2379 \\
              --wal my-wal \\
              -cp app.jar com.example.Main
            ```
            """);

    List<DocCommand> commands = DocSnippetScanner.scanFile(file);

    assertThat(commands.size(), is(1));
    DocCommand cmd = commands.get(0);
    assertThat(
        cmd.getNormalizedText(),
        is("pal run -d localhost:2379 --wal my-wal -cp app.jar com.example.Main"));
    assertThat(cmd.getType(), is(DocCommandType.RUN));
    assertThat(cmd.getLineNumber(), is(2));
  }

  /**
   * Verifies that compound commands separated by {@code &&} are split into separate DocCommands.
   */
  @Test
  public void shouldSplitCompoundCommandsOnAmpersand() throws IOException {
    Path file =
        writeMarkdown(
            """
            ```bash
            pal peer ls -d localhost:2379 && pal log ls -d localhost:2379
            ```
            """);

    List<DocCommand> commands = DocSnippetScanner.scanFile(file);

    assertThat(commands.size(), is(2));
    assertThat(commands.get(0).getType(), is(DocCommandType.PEER_LS));
    assertThat(commands.get(1).getType(), is(DocCommandType.LOG_LS));
  }

  /** Verifies that compound commands separated by {@code ;} are split into separate DocCommands. */
  @Test
  public void shouldSplitCompoundCommandsOnSemicolon() throws IOException {
    Path file =
        writeMarkdown(
            """
            ```bash
            pal peer ls; pal log ls
            ```
            """);

    List<DocCommand> commands = DocSnippetScanner.scanFile(file);

    assertThat(commands.size(), is(2));
    assertThat(commands.get(0).getType(), is(DocCommandType.PEER_LS));
    assertThat(commands.get(1).getType(), is(DocCommandType.LOG_LS));
  }

  /**
   * Verifies that a pipe chain where input is piped into {@code pal} is kept intact as a single
   * command.
   */
  @Test
  public void shouldKeepPipeChainIntact() throws IOException {
    Path file =
        writeMarkdown(
            """
            ```bash
            echo '{"jsonrpc":"2.0"}' | pal peer call -d localhost:2379 -p some-uuid
            ```
            """);

    List<DocCommand> commands = DocSnippetScanner.scanFile(file);

    assertThat(commands.size(), is(1));
    DocCommand cmd = commands.get(0);
    assertThat(cmd.getType(), is(DocCommandType.PEER_CALL));
    assertTrue(
        "rawText should contain full pipe chain",
        cmd.getRawText().contains("echo") && cmd.getRawText().contains("pal peer call"));
  }

  /**
   * Verifies that when a {@code pal} command output is piped to another program, it is classified
   * based on the pal portion.
   */
  @Test
  public void shouldExtractPalFromPipeChain() throws IOException {
    Path file =
        writeMarkdown(
            """
            ```bash
            pal peer ls -d localhost:2379 | grep callback
            ```
            """);

    List<DocCommand> commands = DocSnippetScanner.scanFile(file);

    assertThat(commands.size(), is(1));
    DocCommand cmd = commands.get(0);
    assertThat(cmd.getType(), is(DocCommandType.PEER_LS));
    assertTrue("rawText should contain pipe chain", cmd.getRawText().contains("| grep"));
  }

  /** Verifies that heredoc input piped into a {@code pal} command is tracked correctly. */
  @Test
  public void shouldHandleHeredocInput() throws IOException {
    Path file =
        writeMarkdown(
            """
            ```bash
            cat <<EOF | pal peer call -d localhost:2379 -p some-uuid
            {"jsonrpc":"2.0","method":"add","params":[2,3],"id":1}
            EOF
            ```
            """);

    List<DocCommand> commands = DocSnippetScanner.scanFile(file);

    assertThat(commands.size(), is(1));
    DocCommand cmd = commands.get(0);
    assertThat(cmd.getType(), is(DocCommandType.PEER_CALL));
    assertNotNull("heredocBody should be tracked", cmd.getHeredocBody());
    assertTrue("heredocBody should contain the JSON", cmd.getHeredocBody().contains("\"jsonrpc\""));
  }

  /** Verifies that a leading {@code $} prompt marker is stripped from the normalized text. */
  @Test
  public void shouldStripLeadingDollarSign() throws IOException {
    Path file =
        writeMarkdown(
            """
            ```bash
            $ pal help
            ```
            """);

    List<DocCommand> commands = DocSnippetScanner.scanFile(file);

    assertThat(commands.size(), is(1));
    assertThat(commands.get(0).getNormalizedText(), is("pal help"));
    assertThat(commands.get(0).getType(), is(DocCommandType.HELP));
  }

  /**
   * Verifies that environment variable assignments preceding a {@code pal} command are stripped for
   * classification purposes.
   */
  @Test
  public void shouldStripEnvVarPrefix() throws IOException {
    Path file =
        writeMarkdown(
            """
            ```bash
            JAVA_TOOL_OPTIONS="-agentlib:jdwp" pal replay --wal file:/tmp/my-wal
            ```
            """);

    List<DocCommand> commands = DocSnippetScanner.scanFile(file);

    assertThat(commands.size(), is(1));
    DocCommand cmd = commands.get(0);
    assertTrue(
        "normalizedText should start with pal replay",
        cmd.getNormalizedText().startsWith("pal replay"));
    assertThat(cmd.getType(), is(DocCommandType.REPLAY));
  }

  /** Verifies that comment lines (starting with {@code #}) inside a bash block are skipped. */
  @Test
  public void shouldSkipCommentLines() throws IOException {
    Path file =
        writeMarkdown(
            """
            ```bash
            # This is a comment
            pal help
            ```
            """);

    List<DocCommand> commands = DocSnippetScanner.scanFile(file);

    assertThat(commands.size(), is(1));
    assertThat(commands.get(0).getNormalizedText(), is("pal help"));
    assertThat(commands.get(0).getType(), is(DocCommandType.HELP));
  }

  /**
   * Verifies that only {@code ```bash} and {@code ```shell} code blocks are scanned; other
   * languages are ignored.
   */
  @Test
  public void shouldSkipNonBashCodeBlocks() throws IOException {
    Path file =
        writeMarkdown(
            """
            ```java
            calculator.add(2, 3);
            ```

            ```json
            {"key": "value"}
            ```

            ```yaml
            key: value
            ```

            ```xml
            <root/>
            ```

            ```groovy
            println 'hello'
            ```

            ```bash
            pal help
            ```
            """);

    List<DocCommand> commands = DocSnippetScanner.scanFile(file);

    assertThat(commands.size(), is(1));
    assertThat(commands.get(0).getNormalizedText(), is("pal help"));
  }

  /** Verifies that {@code ```shell} is treated the same as {@code ```bash}. */
  @Test
  public void shouldHandleShellAnnotation() throws IOException {
    Path file =
        writeMarkdown(
            """
            ```shell
            pal help
            ```
            """);

    List<DocCommand> commands = DocSnippetScanner.scanFile(file);

    assertThat(commands.size(), is(1));
    assertThat(commands.get(0).getNormalizedText(), is("pal help"));
    assertThat(commands.get(0).getType(), is(DocCommandType.HELP));
  }

  /** Verifies that unlabeled code blocks (no language annotation) are not scanned. */
  @Test
  public void shouldHandleUnlabeledCodeBlocks() throws IOException {
    Path file =
        writeMarkdown(
            """
            ```
            pal help
            ```
            """);

    List<DocCommand> commands = DocSnippetScanner.scanFile(file);

    assertTrue("unlabeled code blocks should not produce commands", commands.isEmpty());
  }

  /**
   * Verifies that line numbers in the returned DocCommands point to the actual line of the command
   * within the markdown file.
   */
  @Test
  public void shouldReturnCorrectLineNumbers() throws IOException {
    StringBuilder sb = new StringBuilder();
    for (int i = 1; i <= 9; i++) {
      sb.append("Line ").append(i).append("\n");
    }
    sb.append("```bash\n"); // line 10
    sb.append("pal help\n"); // line 11
    sb.append("pal peer ls\n"); // line 12
    sb.append("```\n"); // line 13

    Path file = writeMarkdown(sb.toString());
    List<DocCommand> commands = DocSnippetScanner.scanFile(file);

    assertThat(commands.size(), is(2));
    assertThat(commands.get(0).getLineNumber(), is(11));
    assertThat(commands.get(1).getLineNumber(), is(12));
  }

  /**
   * Verifies that scanning a directory recursively discovers commands from all nested markdown
   * files.
   */
  @Test
  public void shouldScanMultipleFilesRecursively() throws IOException {
    File root = tempFolder.getRoot();
    File subDir = new File(root, "concepts");
    assertTrue("subdir should be created", subDir.mkdirs());

    Files.writeString(
        new File(root, "guide.md").toPath(),
        """
        ```bash
        pal help
        ```
        """);
    Files.writeString(
        new File(subDir, "rpc.md").toPath(),
        """
        ```bash
        pal peer ls
        pal peer call -d localhost:2379 -p uuid com.Foo
        ```
        """);

    List<DocCommand> commands = DocSnippetScanner.scan(root.toPath());

    assertTrue(
        "should find commands from multiple files, found " + commands.size(), commands.size() >= 3);
  }

  /** Verifies that a bash block containing only whitespace and comments produces no commands. */
  @Test
  public void shouldHandleEmptyBashBlock() throws IOException {
    Path file =
        writeMarkdown(
            """
            ```bash

            # Just a comment
               \s
            ```
            """);

    List<DocCommand> commands = DocSnippetScanner.scanFile(file);

    assertTrue("empty/comment-only bash block should produce no commands", commands.isEmpty());
  }

  /**
   * Verifies that multiple {@code pal} commands in a single bash block are each extracted as
   * separate DocCommands.
   */
  @Test
  public void shouldHandleMultiplePalCommandsInOneBlock() throws IOException {
    Path file =
        writeMarkdown(
            """
            ```bash
            pal help
            pal peer ls
            pal log ls
            ```
            """);

    List<DocCommand> commands = DocSnippetScanner.scanFile(file);

    assertThat(commands.size(), is(3));
    assertThat(commands.get(0).getType(), is(DocCommandType.HELP));
    assertThat(commands.get(1).getType(), is(DocCommandType.PEER_LS));
    assertThat(commands.get(2).getType(), is(DocCommandType.LOG_LS));
    // Verify consecutive line numbers
    assertThat(commands.get(0).getLineNumber(), is(2));
    assertThat(commands.get(1).getLineNumber(), is(3));
    assertThat(commands.get(2).getLineNumber(), is(4));
  }

  /**
   * Verifies that non-pal commands (tar, mvn, etc.) are classified as {@code NON_PAL}, not
   * discarded.
   */
  @Test
  public void shouldClassifyNonPalCommandsAsNonPal() throws IOException {
    Path file =
        writeMarkdown(
            """
            ```bash
            tar xzf pal.tar.gz
            mvn install
            pal help
            ```
            """);

    List<DocCommand> commands = DocSnippetScanner.scanFile(file);

    assertThat(commands.size(), is(3));
    assertThat(commands.get(0).getType(), is(DocCommandType.NON_PAL));
    assertThat(commands.get(1).getType(), is(DocCommandType.NON_PAL));
    assertThat(commands.get(2).getType(), is(DocCommandType.HELP));
  }

  /** Verifies that export statements are classified as {@code NON_PAL}. */
  @Test
  public void shouldHandleExportStatements() throws IOException {
    Path file =
        writeMarkdown(
            """
            ```bash
            export PAL_HOME=/opt/pal
            pal help
            ```
            """);

    List<DocCommand> commands = DocSnippetScanner.scanFile(file);

    assertThat(commands.size(), is(2));
    assertThat(commands.get(0).getType(), is(DocCommandType.NON_PAL));
    assertThat(commands.get(1).getType(), is(DocCommandType.HELP));
  }

  /**
   * Integration-flavored unit test that scans the actual docs directory and asserts a minimum
   * command count.
   */
  @Test
  public void shouldFindMinimumCommandCount() {
    Path docsRoot = findDocsRoot();
    assumeTrue("docs/user/docs/ directory must exist", Files.isDirectory(docsRoot));

    List<DocCommand> commands = DocSnippetScanner.scan(docsRoot);

    long palCount = commands.stream().filter(c -> c.getType() != DocCommandType.NON_PAL).count();
    assertTrue("Expected at least 50 pal commands, found " + palCount, palCount >= 50);
  }

  /**
   * Writes markdown content to a temporary file.
   *
   * @param content the markdown content
   * @return the path to the temporary file
   * @throws IOException if file creation fails
   */
  private Path writeMarkdown(String content) throws IOException {
    File file = tempFolder.newFile("test-" + System.nanoTime() + ".md");
    Files.writeString(file.toPath(), content);
    return file.toPath();
  }

  /**
   * Finds the docs/user/docs/ directory by checking PAL_HOME first, then relative paths.
   *
   * @return the path to the docs root
   */
  private static Path findDocsRoot() {
    String palHome = System.getenv("PAL_HOME");
    if (palHome != null) {
      Path candidate = Path.of(palHome, "docs", "user", "docs");
      if (Files.isDirectory(candidate)) {
        return candidate;
      }
    }

    // Try relative paths from current working directory
    Path cwd = Path.of(System.getProperty("user.dir"));
    Path candidate = cwd.resolve("docs/user/docs");
    if (Files.isDirectory(candidate)) {
      return candidate;
    }
    candidate = cwd.resolve("../../docs/user/docs").normalize();
    if (Files.isDirectory(candidate)) {
      return candidate;
    }

    return cwd.resolve("docs/user/docs");
  }
}
