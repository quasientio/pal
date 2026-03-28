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
package io.quasient.pal.core.service;

import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;

import java.util.EnumSet;
import org.junit.Test;

/**
 * Unit tests for the {@link RunOptions} enum.
 *
 * <p>These tests verify that RunOptions enum constants are properly defined and can be used
 * alongside other RunOptions in EnumSet collections. Includes specifications for
 * WITH_WAL_INCOMING_RPC and WITH_WAL_ALL_INCOMING_RPC options.
 */
public class RunOptionsTest {

  /**
   * Verifies that the WITH_IN_FLIGHT_TRACKING enum constant exists in the RunOptions enum.
   *
   * <p>Acceptance Criterion: [TEST:RunOptionsTest.withInFlightTracking_existsInEnum]
   */
  @Test
  public void withInFlightTracking_existsInEnum() {
    // Given: The RunOptions enum
    // When: Attempting to access WITH_IN_FLIGHT_TRACKING constant
    RunOptions option = RunOptions.WITH_IN_FLIGHT_TRACKING;

    // Then: The constant exists and is not null
    assertThat(option, is(notNullValue()));
    assertThat(option.name(), is("WITH_IN_FLIGHT_TRACKING"));
  }

  /**
   * Verifies that WITH_IN_FLIGHT_TRACKING can be added to an EnumSet alongside other RunOptions.
   *
   * <p>This test ensures that the new option integrates properly with the existing RunOptions
   * system and can be combined with other options using EnumSet, which is the standard way to
   * handle combinations of enum flags in Java.
   *
   * <p>Acceptance Criterion: [TEST:RunOptionsTest.withInFlightTracking_canBeAddedToSet]
   */
  @Test
  public void withInFlightTracking_canBeAddedToSet() {
    // Given: An EnumSet containing other RunOptions
    EnumSet<RunOptions> options = EnumSet.of(RunOptions.WITH_PALDIR, RunOptions.WITH_SESSIONS);

    // When: WITH_IN_FLIGHT_TRACKING is added to the set
    options.add(RunOptions.WITH_IN_FLIGHT_TRACKING);

    // Then: The set contains WITH_IN_FLIGHT_TRACKING along with other options
    assertThat(options, hasItem(RunOptions.WITH_IN_FLIGHT_TRACKING));
    assertThat(options, hasItem(RunOptions.WITH_PALDIR));
    assertThat(options, hasItem(RunOptions.WITH_SESSIONS));
    assertThat(options.size(), is(3));
  }

  /**
   * Verifies that the WITH_WAL_INCOMING_RPC enum constant exists in the RunOptions enum and is
   * distinct from other values.
   *
   * <p>Acceptance Criterion: [TEST:RunOptionsTest.withWalIncomingRpc_isDistinctEnumValue]
   */
  @Test
  public void withWalIncomingRpc_isDistinctEnumValue() {
    // Given: The RunOptions enum
    // When: WITH_WAL_INCOMING_RPC is accessed
    RunOptions option = RunOptions.WITH_WAL_INCOMING_RPC;

    // Then: Enum value exists, is distinct from other values, and can be added to EnumSet
    assertThat(option, is(notNullValue()));
    assertThat(option.name(), is("WITH_WAL_INCOMING_RPC"));

    EnumSet<RunOptions> options = EnumSet.of(RunOptions.WITH_WAL);
    options.add(option);
    assertThat(options, hasItem(RunOptions.WITH_WAL_INCOMING_RPC));
    assertThat(options, hasItem(RunOptions.WITH_WAL));
    assertThat(options.size(), is(2));
  }

  /**
   * Verifies that the WITH_WAL_ALL_INCOMING_RPC enum constant exists in the RunOptions enum and is
   * distinct from other values.
   *
   * <p>Acceptance Criterion: [TEST:RunOptionsTest.withWalAllIncomingRpc_isDistinctEnumValue]
   */
  @Test
  public void withWalAllIncomingRpc_isDistinctEnumValue() {
    // Given: The RunOptions enum
    // When: WITH_WAL_ALL_INCOMING_RPC is accessed
    RunOptions option = RunOptions.WITH_WAL_ALL_INCOMING_RPC;

    // Then: Enum value exists, is distinct from other values, and can be added to EnumSet
    assertThat(option, is(notNullValue()));
    assertThat(option.name(), is("WITH_WAL_ALL_INCOMING_RPC"));

    EnumSet<RunOptions> options = EnumSet.of(RunOptions.WITH_WAL);
    options.add(option);
    assertThat(options, hasItem(RunOptions.WITH_WAL_ALL_INCOMING_RPC));
    assertThat(options, hasItem(RunOptions.WITH_WAL));
    assertThat(options.size(), is(2));
  }

  /**
   * Verifies that both new WAL-incoming options can coexist in an EnumSet alongside WITH_WAL.
   *
   * <p>This test ensures all three WAL-related options are independently present and properly
   * combinable, which is required for the flexible configuration model where WITH_WAL controls
   * locally-initiated writes, WITH_WAL_INCOMING_RPC controls incoming RPC writes, and
   * WITH_WAL_ALL_INCOMING_RPC additionally includes LOG_RPC writes.
   *
   * <p>Acceptance Criterion: [TEST:RunOptionsTest.enumSet_canContainBothNewOptions]
   */
  @Test
  public void enumSet_canContainBothNewOptions() {
    // Given: An EnumSet with WITH_WAL, WITH_WAL_INCOMING_RPC, and WITH_WAL_ALL_INCOMING_RPC
    EnumSet<RunOptions> options =
        EnumSet.of(
            RunOptions.WITH_WAL,
            RunOptions.WITH_WAL_INCOMING_RPC,
            RunOptions.WITH_WAL_ALL_INCOMING_RPC);

    // When: Checked for containment
    // Then: All three are independently present
    assertThat(options, hasItem(RunOptions.WITH_WAL));
    assertThat(options, hasItem(RunOptions.WITH_WAL_INCOMING_RPC));
    assertThat(options, hasItem(RunOptions.WITH_WAL_ALL_INCOMING_RPC));
    assertThat(options.size(), is(3));
  }

  /**
   * Verifies that the WITH_REPLAY enum constant exists in the RunOptions enum and is distinct from
   * other values.
   *
   * <p>Acceptance Criterion: [TEST:RunOptionsTest.withReplay_isDistinctEnumValue]
   */
  @Test
  public void withReplay_isDistinctEnumValue() {
    // Given: The RunOptions enum
    // When: WITH_REPLAY is accessed
    RunOptions option = RunOptions.WITH_REPLAY;

    // Then: Enum value exists, is distinct from other values, and can be added to EnumSet
    assertThat(option, is(notNullValue()));
    assertThat(option.name(), is("WITH_REPLAY"));

    EnumSet<RunOptions> options = EnumSet.of(RunOptions.WITH_PALDIR);
    options.add(option);
    assertThat(options, hasItem(RunOptions.WITH_REPLAY));
    assertThat(options, hasItem(RunOptions.WITH_PALDIR));
    assertThat(options.size(), is(2));
  }

  /**
   * Verifies that WITH_REPLAY is mutually exclusive with WAL and source log options at the EnumSet
   * level (both can be added to a set but represent conflicting configurations).
   *
   * <p>Acceptance Criterion: [TEST:RunOptionsTest.withReplay_isMutuallyExclusiveByConvention]
   */
  @Test
  public void withReplay_isMutuallyExclusiveByConvention() {
    // Given: An EnumSet containing WITH_REPLAY
    EnumSet<RunOptions> replayOptions = EnumSet.of(RunOptions.WITH_REPLAY);

    // When/Then: WITH_REPLAY does not overlap with WAL or SOURCE_LOG
    assertThat(replayOptions.contains(RunOptions.WITH_WAL), is(false));
    assertThat(replayOptions.contains(RunOptions.WITH_SOURCE_LOG), is(false));
  }

  /**
   * Verifies that the RunOptions enum contains all expected values after the addition of the
   * WAL-incoming options and the WITH_REPLAY option.
   *
   * <p>The expected total is 13 values: the 9 original options (WITH_PALDIR, WITH_ZMQ_RPC,
   * WITH_JSON_RPC, WITH_TCP_PUB, WITH_INTERCEPTS, WITH_SOURCE_LOG, WITH_WAL, WITH_SESSIONS,
   * WITH_IN_FLIGHT_TRACKING) plus WITH_WAL_INCOMING_RPC, WITH_WAL_INCOMING_CLI,
   * WITH_WAL_ALL_INCOMING_RPC, and WITH_REPLAY.
   *
   * <p>Acceptance Criterion: [TEST:RunOptionsTest.enumValues_containsAllExpectedOptions]
   */
  @Test
  public void enumValues_containsAllExpectedOptions() {
    // Given: RunOptions.values()
    RunOptions[] values = RunOptions.values();

    // When: Length is checked
    // Then: Contains all 13 expected values
    assertThat(values.length, is(13));

    EnumSet<RunOptions> allOptions = EnumSet.allOf(RunOptions.class);
    assertThat(allOptions, hasItem(RunOptions.WITH_PALDIR));
    assertThat(allOptions, hasItem(RunOptions.WITH_ZMQ_RPC));
    assertThat(allOptions, hasItem(RunOptions.WITH_JSON_RPC));
    assertThat(allOptions, hasItem(RunOptions.WITH_TCP_PUB));
    assertThat(allOptions, hasItem(RunOptions.WITH_INTERCEPTS));
    assertThat(allOptions, hasItem(RunOptions.WITH_SOURCE_LOG));
    assertThat(allOptions, hasItem(RunOptions.WITH_WAL));
    assertThat(allOptions, hasItem(RunOptions.WITH_WAL_INCOMING_RPC));
    assertThat(allOptions, hasItem(RunOptions.WITH_WAL_INCOMING_CLI));
    assertThat(allOptions, hasItem(RunOptions.WITH_WAL_ALL_INCOMING_RPC));
    assertThat(allOptions, hasItem(RunOptions.WITH_SESSIONS));
    assertThat(allOptions, hasItem(RunOptions.WITH_IN_FLIGHT_TRACKING));
    assertThat(allOptions, hasItem(RunOptions.WITH_REPLAY));
  }
}
