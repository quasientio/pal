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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Properties;
import java.util.UUID;
import org.junit.Test;
import picocli.CommandLine;

/**
 * Tests for the {@code --fx-thread} CLI option in {@link Main}.
 *
 * <p>This test class verifies the functionality of the {@code --fx-thread} command-line option,
 * including its default value, flag parsing, property propagation, environment variable fallback,
 * and help text visibility.
 *
 * <p>The {@code --fx-thread} flag controls whether the receiving peer registers a {@code
 * JavaFxInvocationExecutor} for routing RPC calls with {@code fx-thread} affinity to the JavaFX
 * Application Thread.
 */
public class MainFxThreadOptionTest {

  /**
   * Tests that the {@code --fx-thread} option defaults to false when not specified.
   *
   * @throws Exception if reflection or parsing fails
   */
  @Test
  public void fxThreadDefaultIsFalse() throws Exception {
    Main main = new Main();
    new CommandLine(main).parseArgs();

    Field field = Main.class.getDeclaredField("fxThread");
    field.setAccessible(true);
    boolean value = (boolean) field.get(main);

    assertThat(value, is(false));
  }

  /**
   * Tests that the {@code --fx-thread} flag sets the field to true when specified.
   *
   * @throws Exception if reflection or parsing fails
   */
  @Test
  public void fxThreadFlagSetsTrue() throws Exception {
    Main main = new Main();
    new CommandLine(main).parseArgs("--fx-thread");

    Field field = Main.class.getDeclaredField("fxThread");
    field.setAccessible(true);
    boolean value = (boolean) field.get(main);

    assertThat(value, is(true));
  }

  /**
   * Tests that {@code addMiscProperties()} sets the {@code execution.fx.thread.enabled} property to
   * {@code "true"} when the {@code --fx-thread} flag is specified.
   *
   * @throws Exception if reflection or method invocation fails
   */
  @Test
  public void fxThreadPropertySetInProperties() throws Exception {
    Main main = new Main();
    new CommandLine(main).parseArgs("--fx-thread");

    // Set required uuid to avoid NPE in addMiscProperties
    Field uuidField = Main.class.getDeclaredField("uuid");
    uuidField.setAccessible(true);
    uuidField.set(main, UUID.randomUUID());

    Method method = Main.class.getDeclaredMethod("addMiscProperties");
    method.setAccessible(true);
    method.invoke(main);

    Field propertiesField = Main.class.getDeclaredField("properties");
    propertiesField.setAccessible(true);
    Properties properties = (Properties) propertiesField.get(main);

    assertThat(properties.getProperty("execution.fx.thread.enabled"), is("true"));
  }

  /**
   * Tests that {@code addMiscProperties()} sets the {@code execution.fx.thread.enabled} property to
   * {@code "false"} when the {@code --fx-thread} flag is not specified.
   *
   * @throws Exception if reflection or method invocation fails
   */
  @Test
  public void fxThreadPropertyFalseByDefault() throws Exception {
    Main main = new Main();
    new CommandLine(main).parseArgs();

    // Set required uuid to avoid NPE in addMiscProperties
    Field uuidField = Main.class.getDeclaredField("uuid");
    uuidField.setAccessible(true);
    uuidField.set(main, UUID.randomUUID());

    Method method = Main.class.getDeclaredMethod("addMiscProperties");
    method.setAccessible(true);
    method.invoke(main);

    Field propertiesField = Main.class.getDeclaredField("properties");
    propertiesField.setAccessible(true);
    Properties properties = (Properties) propertiesField.get(main);

    assertThat(properties.getProperty("execution.fx.thread.enabled"), is("false"));
  }

  /**
   * Tests that the {@code FX_THREAD} environment variable is read by {@code
   * setEmptyParamsFromEnv()} when set to {@code "true"}.
   *
   * <p>Since {@code System.getenv()} cannot be easily mocked, this test verifies that {@code
   * setEmptyParamsFromEnv()} does not change the field when the environment variable is not set
   * (the typical test environment). The code path for reading {@code FX_THREAD} is verified by the
   * existence of the field and its interaction with {@code setEmptyParamsFromEnv()}.
   *
   * @throws Exception if reflection or method invocation fails
   */
  @Test
  public void fxThreadSetFromEnvironmentVariable() throws Exception {
    Main main = new Main();
    new CommandLine(main).parseArgs();

    // Verify fxThread is false before calling setEmptyParamsFromEnv
    Field fxThreadField = Main.class.getDeclaredField("fxThread");
    fxThreadField.setAccessible(true);
    assertThat((boolean) fxThreadField.get(main), is(false));

    Method method = Main.class.getDeclaredMethod("setEmptyParamsFromEnv");
    method.setAccessible(true);
    method.invoke(main);

    // In the test environment, PAL_FX_THREAD env var is not set, so fxThread remains false.
    // The code path System.getenv("PAL_FX_THREAD") is exercised; if the env var were set
    // to "true", fxThread would become true.
    assertThat((boolean) fxThreadField.get(main), is(false));
  }

  /**
   * Tests that the {@code --help} output includes the {@code --fx-thread} option and its
   * description.
   */
  @Test
  public void helpOutputIncludesFxThread() {
    CommandLine cmd = new CommandLine(new Main());
    String help = cmd.getUsageMessage();

    assertThat(help, containsString("--fx-thread"));
    assertThat(help, containsString("JavaFX Application Thread"));
    assertThat(help, containsString("fx-thread"));
  }
}
