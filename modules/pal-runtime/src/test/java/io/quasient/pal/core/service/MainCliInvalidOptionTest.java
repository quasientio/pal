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
import static org.junit.Assert.assertThrows;

import org.junit.Test;
import picocli.CommandLine;

public class MainCliInvalidOptionTest {

  @Test
  public void unknownOption_throwsParameterException() {
    CommandLine.ParameterException pe =
        assertThrows(
            CommandLine.ParameterException.class,
            () -> new CommandLine(new Main()).parseArgs("--unknown-option"));
    assertThat(pe.getMessage(), containsString("--unknown-option"));
  }
}
