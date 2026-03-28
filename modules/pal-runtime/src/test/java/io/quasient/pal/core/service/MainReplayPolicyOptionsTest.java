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
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Properties;
import java.util.Set;
import org.junit.Test;
import picocli.CommandLine;

/**
 * Tests for the replay policy CLI options in {@link Main}.
 *
 * <p>Verifies that {@code --replay-policy}, {@code --replay-shield-io}, {@code
 * --replay-re-execute}, {@code --replay-stub}, {@code --replay-stub-all-else}, and {@code
 * --replay-force-stub} are correctly translated into properties.
 */
public class MainReplayPolicyOptionsTest {

  /** Tests that --replay-policy sets the replay.policy.path property. */
  @Test
  public void replayPolicy_setsProperty() throws Exception {
    Main main = new Main();
    new CommandLine(main)
        .parseArgs("--replay-wal", "file:/tmp/wal", "--replay-policy", "/tmp/policy.yaml");

    invokeValidateInput(main);

    Properties props = getProperties(main);
    assertThat(props.getProperty("replay.policy.path"), is("/tmp/policy.yaml"));
  }

  /** Tests that --replay-shield-io sets the replay.shield.io property to true. */
  @Test
  public void replayShieldIo_setsProperty() throws Exception {
    Main main = new Main();
    new CommandLine(main).parseArgs("--replay-wal", "file:/tmp/wal", "--replay-shield-io");

    invokeValidateInput(main);

    Properties props = getProperties(main);
    assertThat(props.getProperty("replay.shield.io"), is("true"));
  }

  /** Tests that --replay-shield-io defaults to false when not specified. */
  @Test
  public void replayShieldIo_defaultsFalse() throws Exception {
    Main main = new Main();
    new CommandLine(main).parseArgs("--replay-wal", "file:/tmp/wal");

    invokeValidateInput(main);

    Properties props = getProperties(main);
    assertThat(props.getProperty("replay.shield.io"), is("false"));
  }

  /** Tests that --replay-re-execute sets the replay.re-execute.patterns property. */
  @Test
  public void replayReExecute_setsProperty() throws Exception {
    Main main = new Main();
    new CommandLine(main)
        .parseArgs(
            "--replay-wal", "file:/tmp/wal", "--replay-re-execute", "com.example.**,com.other.**");

    invokeValidateInput(main);

    Properties props = getProperties(main);
    assertThat(props.getProperty("replay.re-execute.patterns"), is("com.example.**,com.other.**"));
  }

  /** Tests that --replay-stub sets the replay.stub.patterns property. */
  @Test
  public void replayStub_setsProperty() throws Exception {
    Main main = new Main();
    new CommandLine(main)
        .parseArgs("--replay-wal", "file:/tmp/wal", "--replay-stub", "java.io.**,java.net.**");

    invokeValidateInput(main);

    Properties props = getProperties(main);
    assertThat(props.getProperty("replay.stub.patterns"), is("java.io.**,java.net.**"));
  }

  /** Tests that --replay-stub-all-else sets the replay.stub.all.else property to true. */
  @Test
  public void replayStubAllElse_setsProperty() throws Exception {
    Main main = new Main();
    new CommandLine(main).parseArgs("--replay-wal", "file:/tmp/wal", "--replay-stub-all-else");

    invokeValidateInput(main);

    Properties props = getProperties(main);
    assertThat(props.getProperty("replay.stub.all.else"), is("true"));
  }

  /** Tests that --replay-stub-all-else defaults to false when not specified. */
  @Test
  public void replayStubAllElse_defaultsFalse() throws Exception {
    Main main = new Main();
    new CommandLine(main).parseArgs("--replay-wal", "file:/tmp/wal");

    invokeValidateInput(main);

    Properties props = getProperties(main);
    assertThat(props.getProperty("replay.stub.all.else"), is("false"));
  }

  /** Tests that --replay-force-stub sets the replay.force.stub property to true. */
  @Test
  public void replayForceStub_setsProperty() throws Exception {
    Main main = new Main();
    new CommandLine(main).parseArgs("--replay-wal", "file:/tmp/wal", "--replay-force-stub");

    invokeValidateInput(main);

    Properties props = getProperties(main);
    assertThat(props.getProperty("replay.force.stub"), is("true"));
  }

  /** Tests that --replay-force-stub defaults to false when not specified. */
  @Test
  public void replayForceStub_defaultsFalse() throws Exception {
    Main main = new Main();
    new CommandLine(main).parseArgs("--replay-wal", "file:/tmp/wal");

    invokeValidateInput(main);

    Properties props = getProperties(main);
    assertThat(props.getProperty("replay.force.stub"), is("false"));
  }

  /** Tests that no replay policy properties are set when --replay-wal is not specified. */
  @Test
  public void noReplayWal_noPolicyProperties() throws Exception {
    Main main = new Main();
    new CommandLine(main).parseArgs();

    invokeValidateInput(main);

    Properties props = getProperties(main);
    assertThat(props.getProperty("replay.policy.path"), is(nullValue()));
    assertThat(props.getProperty("replay.shield.io"), is(nullValue()));
    assertThat(props.getProperty("replay.re-execute.patterns"), is(nullValue()));
    assertThat(props.getProperty("replay.stub.patterns"), is(nullValue()));
    assertThat(props.getProperty("replay.stub.all.else"), is(nullValue()));
    assertThat(props.getProperty("replay.force.stub"), is(nullValue()));
  }

  /**
   * Tests that all replay policy options can be specified together and produce the expected
   * properties.
   */
  @Test
  public void allReplayPolicyOptions_setProperties() throws Exception {
    Main main = new Main();
    new CommandLine(main)
        .parseArgs(
            "--replay-wal",
            "file:/tmp/wal",
            "--replay-policy",
            "/tmp/p.yaml",
            "--replay-shield-io",
            "--replay-re-execute",
            "com.example.**",
            "--replay-stub",
            "java.io.**",
            "--replay-stub-all-else",
            "--replay-force-stub");

    invokeValidateInput(main);

    Properties props = getProperties(main);
    assertThat(props.getProperty("replay.policy.path"), is("/tmp/p.yaml"));
    assertThat(props.getProperty("replay.shield.io"), is("true"));
    assertThat(props.getProperty("replay.re-execute.patterns"), is("com.example.**"));
    assertThat(props.getProperty("replay.stub.patterns"), is("java.io.**"));
    assertThat(props.getProperty("replay.stub.all.else"), is("true"));
    assertThat(props.getProperty("replay.force.stub"), is("true"));
  }

  /** Tests that WITH_REPLAY run option is set when replay policy options are used. */
  @Test
  public void replayPolicyOptions_setWithReplayRunOption() throws Exception {
    Main main = new Main();
    new CommandLine(main)
        .parseArgs("--replay-wal", "file:/tmp/wal", "--replay-shield-io", "--replay-force-stub");

    invokeValidateInput(main);

    @SuppressWarnings("unchecked")
    Set<RunOptions> runOptions = (Set<RunOptions>) getFieldValue(main, "runOptions");
    assertThat(runOptions.contains(RunOptions.WITH_REPLAY), is(true));
  }

  // ===========================================================================
  // Helper methods
  // ===========================================================================

  /** Invokes the private {@code validateInput()} method on the Main instance. */
  private static void invokeValidateInput(Main main) throws Exception {
    Method validateInput = Main.class.getDeclaredMethod("validateInput");
    validateInput.setAccessible(true);
    validateInput.invoke(main);
  }

  /** Retrieves the properties field from the Main instance. */
  private static Properties getProperties(Main main) throws Exception {
    return (Properties) getFieldValue(main, "properties");
  }

  /** Gets a field value from the Main instance via reflection. */
  private static Object getFieldValue(Main main, String fieldName) throws Exception {
    Field f = Main.class.getDeclaredField(fieldName);
    f.setAccessible(true);
    return f.get(main);
  }
}
