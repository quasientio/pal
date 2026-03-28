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
package io.quasient.pal.core.replay;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.MatcherAssert.assertThat;

import io.quasient.pal.core.replay.DivergenceDetector.DivergenceType;
import java.util.List;
import org.junit.Test;

/**
 * Unit tests for {@code DivergenceReport} — the immutable summary of divergences detected during
 * deterministic replay.
 *
 * <p>Tests cover thread-context formatting in {@code formatAsText()} for both multi-thread and
 * single-thread divergence scenarios.
 */
public class DivergenceReportTest {

  /**
   * Verifies that {@code formatAsText()} includes thread names when the report contains divergences
   * from multiple threads.
   */
  @Test
  public void formatAsText_includesThreadContext() {
    Divergence d1 =
        new Divergence(
            DivergenceType.VALUE_MISMATCH,
            42,
            "rpc-worker-1",
            "Return value mismatch for Foo.bar",
            10,
            20);
    Divergence d2 =
        new Divergence(
            DivergenceType.OPERATION_MISMATCH,
            100,
            "self-caller",
            "Expected Baz.qux but got Baz.quux",
            null,
            null);
    DivergenceReport report = new DivergenceReport(List.of(d1, d2));

    String text = report.formatAsText();

    assertThat(text, containsString("thread=rpc-worker-1"));
    assertThat(text, containsString("thread=self-caller"));
    assertThat(text, containsString("[VALUE_MISMATCH] thread=rpc-worker-1 offset=42"));
    assertThat(text, containsString("[OPERATION_MISMATCH] thread=self-caller offset=100"));
  }

  /**
   * Verifies that {@code formatAsText()} includes thread name even when the report contains
   * divergences from only a single thread.
   */
  @Test
  public void formatAsText_singleThread_includesThreadContext() {
    Divergence d =
        new Divergence(
            DivergenceType.EXTRA_OPERATION,
            -1,
            "self-caller",
            "Extra operation: Foo.bar",
            null,
            null);
    DivergenceReport report = new DivergenceReport(List.of(d));

    String text = report.formatAsText();

    assertThat(text, containsString("thread=self-caller"));
    assertThat(text, containsString("[EXTRA_OPERATION] thread=self-caller offset=-1"));
  }
}
