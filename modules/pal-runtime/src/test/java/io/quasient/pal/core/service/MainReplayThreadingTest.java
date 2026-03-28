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

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Properties;
import java.util.Set;
import org.junit.Test;
import picocli.CommandLine;

/**
 * Tests for the {@code --replay-threading} CLI option in {@link Main}.
 *
 * <p>Verifies that the CLI layer correctly translates the {@code --replay-threading} option into
 * the corresponding property in the peer's {@link Properties} and that the {@link
 * RunOptions#WITH_REPLAY} flag is set.
 */
public class MainReplayThreadingTest {

  /**
   * Tests that the default value for {@code --replay-threading} is {@code "ordered"} and is stored
   * in the properties when {@code --replay-wal} is specified.
   *
   * @throws Exception if reflection or parsing fails
   */
  @Test
  public void replayThreadingDefault_isOrdered() throws Exception {
    Main main = new Main();
    new CommandLine(main).parseArgs("--replay-wal", "file:/tmp/test-wal");

    invokeValidateInput(main);

    Properties props = getProperties(main);
    assertThat(props.getProperty("replay.threading"), is("ordered"));
  }

  /**
   * Tests that specifying {@code --replay-threading ordered} sets the property to {@code
   * "ordered"}.
   *
   * @throws Exception if reflection or parsing fails
   */
  @Test
  public void replayThreadingOrdered_setsProperty() throws Exception {
    Main main = new Main();
    new CommandLine(main)
        .parseArgs("--replay-wal", "file:/tmp/test-wal", "--replay-threading", "ordered");

    invokeValidateInput(main);

    Properties props = getProperties(main);
    assertThat(props.getProperty("replay.threading"), is("ordered"));
  }

  /**
   * Tests that specifying {@code --replay-threading unordered} sets the property to {@code
   * "unordered"}.
   *
   * @throws Exception if reflection or parsing fails
   */
  @Test
  public void replayThreadingUnordered_setsProperty() throws Exception {
    Main main = new Main();
    new CommandLine(main)
        .parseArgs("--replay-wal", "file:/tmp/test-wal", "--replay-threading", "unordered");

    invokeValidateInput(main);

    Properties props = getProperties(main);
    assertThat(props.getProperty("replay.threading"), is("unordered"));
  }

  /**
   * Tests that {@code --replay-wal} with {@code --replay-threading} correctly sets the {@code
   * WITH_REPLAY} run option.
   *
   * @throws Exception if reflection or parsing fails
   */
  @Test
  public void replayThreading_setsWithReplayRunOption() throws Exception {
    Main main = new Main();
    new CommandLine(main)
        .parseArgs("--replay-wal", "file:/tmp/test-wal", "--replay-threading", "unordered");

    invokeValidateInput(main);

    @SuppressWarnings("unchecked")
    Set<RunOptions> runOptions = (Set<RunOptions>) getFieldValue(main, "runOptions");
    assertThat(runOptions.contains(RunOptions.WITH_REPLAY), is(true));
  }

  /**
   * Tests that the {@code replay.threading} property is not set when {@code --replay-wal} is not
   * specified (i.e., not in replay mode).
   *
   * @throws Exception if reflection or parsing fails
   */
  @Test
  public void noReplayWal_noThreadingProperty() throws Exception {
    Main main = new Main();
    new CommandLine(main).parseArgs();

    invokeValidateInput(main);

    Properties props = getProperties(main);
    assertThat(props.getProperty("replay.threading") == null, is(true));
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
