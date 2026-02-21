/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.core.service;

import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.fail;

import java.util.EnumSet;
import org.junit.Ignore;
import org.junit.Test;

/**
 * Unit tests for the {@link RunOptions} enum.
 *
 * <p>These tests verify that RunOptions enum constants are properly defined and can be used
 * alongside other RunOptions in EnumSet collections. Includes specifications for
 * WITH_WAL_INCOMING_RPC and WITH_WAL_ALL_INCOMING_RPC options (awaiting implementation in #772).
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
  @Ignore("Awaiting implementation in #772")
  public void withWalIncomingRpc_isDistinctEnumValue() {
    // Given: The RunOptions enum
    // When: WITH_WAL_INCOMING_RPC is accessed
    // Then: Enum value exists, is distinct from other values, and can be added to EnumSet

    // TODO(#772): Implement test logic
    fail("Not yet implemented");
  }

  /**
   * Verifies that the WITH_WAL_ALL_INCOMING_RPC enum constant exists in the RunOptions enum and is
   * distinct from other values.
   *
   * <p>Acceptance Criterion: [TEST:RunOptionsTest.withWalAllIncomingRpc_isDistinctEnumValue]
   */
  @Test
  @Ignore("Awaiting implementation in #772")
  public void withWalAllIncomingRpc_isDistinctEnumValue() {
    // Given: The RunOptions enum
    // When: WITH_WAL_ALL_INCOMING_RPC is accessed
    // Then: Enum value exists, is distinct from other values, and can be added to EnumSet

    // TODO(#772): Implement test logic
    fail("Not yet implemented");
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
  @Ignore("Awaiting implementation in #772")
  public void enumSet_canContainBothNewOptions() {
    // Given: An EnumSet with WITH_WAL, WITH_WAL_INCOMING_RPC, and WITH_WAL_ALL_INCOMING_RPC
    // When: Checked for containment
    // Then: All three are independently present

    // TODO(#772): Implement test logic
    fail("Not yet implemented");
  }

  /**
   * Verifies that the RunOptions enum contains all expected values after the addition of the two
   * new WAL-incoming options.
   *
   * <p>The expected total is 11 values: the 9 existing options (WITH_PALDIR, WITH_ZMQ_RPC,
   * WITH_JSON_RPC, WITH_TCP_PUB, WITH_INTERCEPTS, WITH_SOURCE_LOG, WITH_WAL, WITH_SESSIONS,
   * WITH_IN_FLIGHT_TRACKING) plus the 2 new options (WITH_WAL_INCOMING_RPC,
   * WITH_WAL_ALL_INCOMING_RPC).
   *
   * <p>Acceptance Criterion: [TEST:RunOptionsTest.enumValues_containsAllExpectedOptions]
   */
  @Test
  @Ignore("Awaiting implementation in #772")
  public void enumValues_containsAllExpectedOptions() {
    // Given: RunOptions.values()
    // When: Length is checked
    // Then: Contains all 11 expected values (9 existing + 2 new)

    // TODO(#772): Implement test logic
    fail("Not yet implemented");
  }
}
