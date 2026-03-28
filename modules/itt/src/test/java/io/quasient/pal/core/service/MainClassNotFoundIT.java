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

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import io.quasient.pal.AbstractIntegrationTest;
import org.junit.Test;

/**
 * Integration test: when the specified main class cannot be found, `pal run` should behave like the
 * `java` command: print the error to stderr and exit with code 1.
 */
public class MainClassNotFoundIT extends AbstractIntegrationTest {

  @Test
  public void testMissingMainClass_printsToStderrAndExit1() throws Exception {
    String missingClass = "com.example.DoesNotExist";

    ProcessResult result = runPeer(missingClass);

    assertEquals("Expected exit code 1 when main class cannot be found", 1, result.exitCode());
    assertThat(
        "Expected the standard java launcher error on stderr",
        result.stderr(),
        containsString("Error: Could not find or load main class " + missingClass));
  }
}
