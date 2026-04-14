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

import org.junit.Test;

/**
 * Unit tests for {@code DocCommandType}, the enum that classifies parsed documentation commands.
 *
 * <p>DocCommandType provides a {@code classify(String)} method that parses the first tokens of a
 * command string to determine its category (HELP, RUN, PEER_LS, LOG_PRINT, etc.). These tests
 * define the contract for classification across all supported command types, including edge cases
 * like flags, aliases, and non-PAL commands.
 */
public class DocCommandTypeTest {

  /** Verifies that help-related commands are classified as HELP. */
  @Test
  public void shouldClassifyHelpCommands() {
    // Given: commands "pal help", "pal peer --help", "pal log print --help"
    // When: classify() is called on each
    // Then: all return HELP
    assertThat(DocCommandType.classify("pal help"), is(DocCommandType.HELP));
    assertThat(DocCommandType.classify("pal peer --help"), is(DocCommandType.HELP));
    assertThat(DocCommandType.classify("pal log print --help"), is(DocCommandType.HELP));
    assertThat(DocCommandType.classify("pal --help"), is(DocCommandType.HELP));
    assertThat(DocCommandType.classify("pal -h"), is(DocCommandType.HELP));
    assertThat(DocCommandType.classify("pal help peer"), is(DocCommandType.HELP));
  }

  /** Verifies that all peer subcommands are classified to their specific types. */
  @Test
  public void shouldClassifyPeerSubcommands() {
    // Given: commands "pal peer ls ...", "pal peer call ...", "pal peer print ...",
    //        "pal peer rm ...", "pal peer stats ..."
    // When: classify() is called on each
    // Then: returns PEER_LS, PEER_CALL, PEER_PRINT, PEER_RM, PEER_STATS respectively
    assertThat(
        DocCommandType.classify("pal peer ls -d localhost:2379"), is(DocCommandType.PEER_LS));
    assertThat(
        DocCommandType.classify("pal peer call -d localhost:2379 -p uuid com.example.Main"),
        is(DocCommandType.PEER_CALL));
    assertThat(
        DocCommandType.classify("pal peer print -d localhost:2379 -p uuid"),
        is(DocCommandType.PEER_PRINT));
    assertThat(
        DocCommandType.classify("pal peer rm -d localhost:2379 -p uuid"),
        is(DocCommandType.PEER_RM));
    assertThat(
        DocCommandType.classify("pal peer stats -d localhost:2379"), is(DocCommandType.PEER_STATS));
  }

  /** Verifies that all log subcommands are classified to their specific types. */
  @Test
  public void shouldClassifyLogSubcommands() {
    // Given: commands for log ls, log print, log call, log rm, log stats, log index
    // When: classify() is called on each
    // Then: returns LOG_LS, LOG_PRINT, LOG_CALL, LOG_RM, LOG_STATS, LOG_INDEX respectively
    assertThat(DocCommandType.classify("pal log ls -d localhost:2379"), is(DocCommandType.LOG_LS));
    assertThat(
        DocCommandType.classify("pal log print -d localhost:2379 -l my-log"),
        is(DocCommandType.LOG_PRINT));
    assertThat(
        DocCommandType.classify("pal log call -d localhost:2379 -l my-log com.example.Main"),
        is(DocCommandType.LOG_CALL));
    assertThat(
        DocCommandType.classify("pal log rm -d localhost:2379 my-log"), is(DocCommandType.LOG_RM));
    assertThat(
        DocCommandType.classify("pal log stats -d localhost:2379 -l my-log"),
        is(DocCommandType.LOG_STATS));
    assertThat(
        DocCommandType.classify("pal log index -d localhost:2379 -l my-log"),
        is(DocCommandType.LOG_INDEX));
  }

  /** Verifies that all intercept subcommands are classified to their specific types. */
  @Test
  public void shouldClassifyInterceptSubcommands() {
    // Given: commands for intercept ls, intercept apply, intercept rm,
    //        intercept diff, intercept status
    // When: classify() is called on each
    // Then: returns INTERCEPT_LS, INTERCEPT_APPLY, INTERCEPT_RM,
    //       INTERCEPT_DIFF, INTERCEPT_STATUS respectively
    assertThat(
        DocCommandType.classify("pal intercept ls -d localhost:2379"),
        is(DocCommandType.INTERCEPT_LS));
    assertThat(
        DocCommandType.classify("pal intercept apply -d localhost:2379 -f intercept.yaml"),
        is(DocCommandType.INTERCEPT_APPLY));
    assertThat(
        DocCommandType.classify("pal intercept rm -d localhost:2379 intercept-uuid"),
        is(DocCommandType.INTERCEPT_RM));
    assertThat(
        DocCommandType.classify("pal intercept diff -d localhost:2379 -f intercept.yaml"),
        is(DocCommandType.INTERCEPT_DIFF));
    assertThat(
        DocCommandType.classify("pal intercept status -d localhost:2379"),
        is(DocCommandType.INTERCEPT_STATUS));
  }

  /** Verifies that "pal run" commands are classified as RUN. */
  @Test
  public void shouldClassifyRunCommand() {
    // Given: command "pal run -d localhost:2379 --wal my-log -cp app.jar com.example.Main"
    // When: classify() is called
    // Then: returns RUN
    assertThat(
        DocCommandType.classify(
            "pal run -d localhost:2379 --wal my-log -cp app.jar com.example.Main"),
        is(DocCommandType.RUN));
  }

  /** Verifies that "pal replay" commands are classified as REPLAY. */
  @Test
  public void shouldClassifyReplayCommand() {
    // Given: command "pal replay --wal file:/tmp/wal -cp app.jar com.example.Main"
    // When: classify() is called
    // Then: returns REPLAY
    assertThat(
        DocCommandType.classify("pal replay --wal file:/tmp/wal -cp app.jar com.example.Main"),
        is(DocCommandType.REPLAY));
  }

  /** Verifies that "pal init" commands are classified as INIT. */
  @Test
  public void shouldClassifyInitCommand() {
    // Given: command "pal init my-project"
    // When: classify() is called
    // Then: returns INIT
    assertThat(DocCommandType.classify("pal init my-project"), is(DocCommandType.INIT));
  }

  /** Verifies that non-PAL commands are classified as NON_PAL. */
  @Test
  public void shouldClassifyNonPalCommands() {
    // Given: commands "mvn install", "tar xzf pal.tar.gz", "docker ps", "curl http://localhost"
    // When: classify() is called on each
    // Then: all return NON_PAL
    assertThat(DocCommandType.classify("mvn install"), is(DocCommandType.NON_PAL));
    assertThat(DocCommandType.classify("./mvnw install"), is(DocCommandType.NON_PAL));
    assertThat(DocCommandType.classify("tar xzf pal.tar.gz"), is(DocCommandType.NON_PAL));
    assertThat(DocCommandType.classify("docker ps"), is(DocCommandType.NON_PAL));
    assertThat(DocCommandType.classify("curl http://localhost"), is(DocCommandType.NON_PAL));
  }

  /** Verifies that classification works correctly when commands have flags interspersed. */
  @Test
  public void shouldClassifyCommandsWithFlags() {
    // Given: command with flags before subcommand
    // When: classify() is called
    // Then: returns correct type (flags do not break classification)
    assertThat(
        DocCommandType.classify("pal peer ls -d localhost:2379 -l"), is(DocCommandType.PEER_LS));

    // With env var prefix
    assertThat(
        DocCommandType.classify("JAVA_TOOL_OPTIONS=\"-agentlib:jdwp\" pal run -cp app.jar"),
        is(DocCommandType.RUN));

    // With leading $ and whitespace
    assertThat(
        DocCommandType.classify("$ pal log ls -d localhost:2379"), is(DocCommandType.LOG_LS));
  }

  /** Verifies that documented command aliases are classified correctly. */
  @Test
  public void shouldHandleAliases() {
    // Given: commands "pal peers" and "pal logs" (if aliases are documented)
    // When: classify() is called on each
    // Then: "pal peers" maps to PEER_LS, "pal logs" maps to LOG_LS
    assertThat(DocCommandType.classify("pal peers"), is(DocCommandType.PEER_LS));
    assertThat(DocCommandType.classify("pal logs"), is(DocCommandType.LOG_LS));
    assertThat(DocCommandType.classify("pal intercepts"), is(DocCommandType.INTERCEPT_LS));

    // Aliases with flags
    assertThat(
        DocCommandType.classify("pal peers -d localhost:2379 -l"), is(DocCommandType.PEER_LS));
    assertThat(DocCommandType.classify("pal logs -d localhost:2379 -l"), is(DocCommandType.LOG_LS));
  }
}
