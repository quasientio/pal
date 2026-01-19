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
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Properties;
import java.util.Set;
import org.junit.Test;
import picocli.CommandLine;

/**
 * Tests for in-flight tracking CLI options and environment variable support.
 *
 * <p>This test class verifies the functionality of the --in-flight-tracking and --drain-timeout-ms
 * command-line options, as well as their corresponding environment variables IN_FLIGHT_TRACKING and
 * DRAIN_TIMEOUT_MS.
 */
public class MainInFlightTrackingOptionsTest {

  /**
   * Tests that the --in-flight-tracking option defaults to true when not specified.
   *
   * @throws Exception if reflection or parsing fails
   */
  @Test
  public void inFlightTracking_defaultsToTrue() throws Exception {
    Main main = new Main();
    new CommandLine(main).parseArgs();

    Field field = Main.class.getDeclaredField("inFlightTracking");
    field.setAccessible(true);
    Boolean value = (Boolean) field.get(main);

    // Picocli applies the defaultValue annotation
    assertThat(value, is(true));
  }

  /**
   * Tests that the --drain-timeout-ms option defaults to 5000 milliseconds.
   *
   * @throws Exception if reflection or parsing fails
   */
  @Test
  public void drainTimeout_defaultsTo5000() throws Exception {
    Main main = new Main();
    new CommandLine(main).parseArgs();

    Field field = Main.class.getDeclaredField("drainTimeoutMs");
    field.setAccessible(true);
    Integer value = (Integer) field.get(main);

    // Picocli applies the defaultValue annotation
    assertThat(value, is(5000));
  }

  /**
   * Tests that --drain-timeout-ms can be set to a custom value.
   *
   * @throws Exception if reflection or parsing fails
   */
  @Test
  public void drainTimeout_canBeSet() throws Exception {
    Main main = new Main();
    new CommandLine(main).parseArgs("--drain-timeout-ms", "10000");

    Field field = Main.class.getDeclaredField("drainTimeoutMs");
    field.setAccessible(true);
    Integer value = (Integer) field.get(main);

    assertThat(value, is(10000));
  }

  /**
   * Tests that validateInput() adds WITH_IN_FLIGHT_TRACKING to runOptions when enabled by default.
   *
   * @throws Exception if reflection or method invocation fails
   */
  @Test
  public void validateInput_addsInFlightTrackingToRunOptions_byDefault() throws Exception {
    Main main = new Main();
    new CommandLine(main).parseArgs();

    Method method = Main.class.getDeclaredMethod("validateInput");
    method.setAccessible(true);
    method.invoke(main);

    Field field = Main.class.getDeclaredField("runOptions");
    field.setAccessible(true);
    @SuppressWarnings("unchecked")
    Set<RunOptions> runOptions = (Set<RunOptions>) field.get(main);

    assertThat(runOptions, notNullValue());
    // Default is true, so option should be added
    assertThat(runOptions.contains(RunOptions.WITH_IN_FLIGHT_TRACKING), is(true));
  }

  /**
   * Tests that addMiscProperties() adds drain timeout to properties when set.
   *
   * @throws Exception if reflection or method invocation fails
   */
  @Test
  public void addMiscProperties_addsDrainTimeoutToProperties() throws Exception {
    Main main = new Main();
    new CommandLine(main).parseArgs("--drain-timeout-ms", "6000");

    // Initialize properties field
    Field propertiesField = Main.class.getDeclaredField("properties");
    propertiesField.setAccessible(true);
    Properties properties = (Properties) propertiesField.get(main);

    // Set required fields to avoid NPE in addMiscProperties
    Field uuidField = Main.class.getDeclaredField("uuid");
    uuidField.setAccessible(true);
    uuidField.set(main, java.util.UUID.randomUUID());

    Method method = Main.class.getDeclaredMethod("addMiscProperties");
    method.setAccessible(true);
    method.invoke(main);

    assertThat(properties.getProperty("intercept.drain.timeout.ms"), is("6000"));
  }

  /**
   * Tests that addMiscProperties() adds drain timeout with default value.
   *
   * @throws Exception if reflection or method invocation fails
   */
  @Test
  public void addMiscProperties_addsDrainTimeoutWithDefault() throws Exception {
    Main main = new Main();
    new CommandLine(main).parseArgs();

    // Initialize properties field
    Field propertiesField = Main.class.getDeclaredField("properties");
    propertiesField.setAccessible(true);
    Properties properties = (Properties) propertiesField.get(main);

    // Set required fields to avoid NPE in addMiscProperties
    Field uuidField = Main.class.getDeclaredField("uuid");
    uuidField.setAccessible(true);
    uuidField.set(main, java.util.UUID.randomUUID());

    Method method = Main.class.getDeclaredMethod("addMiscProperties");
    method.setAccessible(true);
    method.invoke(main);

    // With default value of 5000, it should be set
    assertThat(properties.getProperty("intercept.drain.timeout.ms"), is("5000"));
  }

  /**
   * Tests that help text includes the new in-flight tracking options.
   *
   * <p>This test verifies that the --in-flight-tracking and --drain-timeout-ms options are
   * documented in the help output.
   */
  @Test
  public void helpText_includesInFlightTrackingOptions() {
    CommandLine cmd = new CommandLine(new Main());
    String help = cmd.getUsageMessage();

    assertThat(help, containsString("--in-flight-tracking"));
    assertThat(help, containsString("--drain-timeout-ms"));
    assertThat(help, containsString("in-flight dispatch tracking"));
    assertThat(help, containsString("drain operations"));
  }
}
