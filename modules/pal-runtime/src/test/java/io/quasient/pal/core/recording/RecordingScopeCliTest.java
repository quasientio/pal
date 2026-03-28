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
package io.quasient.pal.core.recording;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

import io.quasient.pal.core.service.Main;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Properties;
import org.junit.Test;
import picocli.CommandLine;

/**
 * Tests verifying that {@link Main} correctly parses {@code --scope}, {@code --scope-exclude},
 * {@code --scope-io}, {@code --scope-policy}, and {@code --scope-default} CLI flags and converts
 * them to the corresponding {@code scope.*} properties.
 *
 * <p>These tests use the reflection-based pattern from {@code MainReplayPolicyOptionsTest}: create
 * a {@link Main} instance, parse CLI args via picocli's {@code CommandLine.parseArgs()}, invoke the
 * private {@code validateInput()} method, then read the resulting {@code Properties} via
 * reflection.
 *
 * @see Main
 */
public class RecordingScopeCliTest {

  /**
   * Verifies that {@code --scope com.example.**} sets the {@code scope.patterns} property to {@code
   * "com.example.**"}.
   */
  @Test
  public void scopePatternsSetToProperty() throws Exception {
    Main main = new Main();
    new CommandLine(main).parseArgs("--scope", "com.example.**");

    invokeValidateInput(main);

    Properties props = getProperties(main);
    assertThat(props.getProperty("scope.patterns"), is("com.example.**"));
  }

  /**
   * Verifies that multiple {@code --scope} arguments are joined with comma into the {@code
   * scope.patterns} property.
   */
  @Test
  public void multipleScopePatternsJoinedWithComma() throws Exception {
    Main main = new Main();
    new CommandLine(main).parseArgs("--scope", "com.example.**,org.other.**");

    invokeValidateInput(main);

    Properties props = getProperties(main);
    assertThat(props.getProperty("scope.patterns"), is("com.example.**,org.other.**"));
  }

  /**
   * Verifies that {@code --scope-exclude java.util.**} sets the {@code scope.exclude.patterns}
   * property.
   */
  @Test
  public void scopeExcludePatternsSetToProperty() throws Exception {
    Main main = new Main();
    new CommandLine(main).parseArgs("--scope-exclude", "java.util.**");

    invokeValidateInput(main);

    Properties props = getProperties(main);
    assertThat(props.getProperty("scope.exclude.patterns"), is("java.util.**"));
  }

  /** Verifies that {@code --scope-io} sets the {@code scope.io} property to {@code "true"}. */
  @Test
  public void scopeIoSetsProperty() throws Exception {
    Main main = new Main();
    new CommandLine(main).parseArgs("--scope-io");

    invokeValidateInput(main);

    Properties props = getProperties(main);
    assertThat(props.getProperty("scope.io"), is("true"));
  }

  /**
   * Verifies that {@code --scope-policy /path/to/policy.yaml} sets the {@code scope.policy.path}
   * property.
   */
  @Test
  public void scopePolicySetsProperty() throws Exception {
    Main main = new Main();
    new CommandLine(main).parseArgs("--scope-policy", "/path/to/policy.yaml");

    invokeValidateInput(main);

    Properties props = getProperties(main);
    assertThat(props.getProperty("scope.policy.path"), is("/path/to/policy.yaml"));
  }

  /**
   * Verifies that {@code --scope-default skip} sets the {@code scope.default.action} property to
   * {@code "skip"}.
   */
  @Test
  public void scopeDefaultSetsProperty() throws Exception {
    Main main = new Main();
    new CommandLine(main).parseArgs("--scope-default", "skip");

    invokeValidateInput(main);

    Properties props = getProperties(main);
    assertThat(props.getProperty("scope.default.action"), is("skip"));
  }

  /**
   * Verifies that when no {@code --scope*} flags are specified, no {@code scope.*} properties are
   * set in the resulting properties.
   */
  @Test
  public void noScopeFlagsProducesNoProperties() throws Exception {
    Main main = new Main();
    new CommandLine(main).parseArgs();

    invokeValidateInput(main);

    Properties props = getProperties(main);
    assertThat(props.getProperty("scope.patterns"), is(nullValue()));
    assertThat(props.getProperty("scope.exclude.patterns"), is(nullValue()));
    assertThat(props.getProperty("scope.io"), is(nullValue()));
    assertThat(props.getProperty("scope.policy.path"), is(nullValue()));
    assertThat(props.getProperty("scope.default.action"), is(nullValue()));
  }

  // ===========================================================================
  // Helper methods
  // ===========================================================================

  /**
   * Invokes the private {@code validateInput()} and {@code addMiscProperties()} methods on the Main
   * instance. Recording scope properties are set in {@code addMiscProperties()}, so both methods
   * must be invoked.
   */
  private static void invokeValidateInput(Main main) throws Exception {
    Method validateInput = Main.class.getDeclaredMethod("validateInput");
    validateInput.setAccessible(true);
    validateInput.invoke(main);

    Method addMiscProperties = Main.class.getDeclaredMethod("addMiscProperties");
    addMiscProperties.setAccessible(true);
    addMiscProperties.invoke(main);
  }

  /** Retrieves the properties field from the Main instance. */
  private static Properties getProperties(Main main) throws Exception {
    Field f = Main.class.getDeclaredField("properties");
    f.setAccessible(true);
    return (Properties) f.get(main);
  }
}
