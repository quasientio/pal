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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.fail;

import java.util.EnumSet;
import org.junit.Ignore;
import org.junit.Test;

/**
 * Unit tests for the {@link RunOptions} enum, focusing on the WITH_IN_FLIGHT_TRACKING option.
 *
 * <p>These tests verify that the new WITH_IN_FLIGHT_TRACKING enum constant is properly defined and
 * can be used alongside other RunOptions in EnumSet collections.
 */
public class RunOptionsTest {

  /**
   * Verifies that the WITH_IN_FLIGHT_TRACKING enum constant exists in the RunOptions enum.
   *
   * <p>Acceptance Criterion: [TEST:RunOptionsTest.withInFlightTracking_existsInEnum]
   */
  @Test
  @Ignore("Awaiting implementation in #238")
  public void withInFlightTracking_existsInEnum() {
    // Given: The RunOptions enum
    // When: Attempting to access WITH_IN_FLIGHT_TRACKING constant
    // Then: The constant exists and is not null

    // TODO: Implement after #238 adds WITH_IN_FLIGHT_TRACKING to RunOptions enum
    // Expected implementation:
    // RunOptions option = RunOptions.WITH_IN_FLIGHT_TRACKING;
    // assertThat(option, is(notNullValue()));
    // assertThat(option.name(), is("WITH_IN_FLIGHT_TRACKING"));

    fail("Not yet implemented");
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
  @Ignore("Awaiting implementation in #238")
  public void withInFlightTracking_canBeAddedToSet() {
    // Given: An EnumSet containing other RunOptions
    // When: WITH_IN_FLIGHT_TRACKING is added to the set
    // Then: The set contains WITH_IN_FLIGHT_TRACKING along with other options

    // TODO: Implement after #238 adds WITH_IN_FLIGHT_TRACKING to RunOptions enum
    // Expected implementation:
    // EnumSet<RunOptions> options = EnumSet.of(RunOptions.WITH_PALDIR, RunOptions.WITH_SESSIONS);
    // options.add(RunOptions.WITH_IN_FLIGHT_TRACKING);
    //
    // assertThat(options, hasItem(RunOptions.WITH_IN_FLIGHT_TRACKING));
    // assertThat(options, hasItem(RunOptions.WITH_PALDIR));
    // assertThat(options, hasItem(RunOptions.WITH_SESSIONS));
    // assertThat(options.size(), is(3));

    fail("Not yet implemented");
  }
}
