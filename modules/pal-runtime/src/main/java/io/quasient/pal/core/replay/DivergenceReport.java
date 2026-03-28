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

import java.util.Collections;
import java.util.List;

/**
 * Immutable summary report of all divergences detected during a deterministic replay session.
 *
 * <p>Provides access to the full list of {@link Divergence} entries and a plain-text formatter
 * suitable for printing to stderr.
 */
public class DivergenceReport {

  /** The divergences accumulated during the replay session. */
  private final List<Divergence> divergences;

  /**
   * Constructs a new report from the given list of divergences.
   *
   * @param divergences the divergences to include (defensively copied)
   */
  public DivergenceReport(List<Divergence> divergences) {
    this.divergences = Collections.unmodifiableList(List.copyOf(divergences));
  }

  /**
   * Returns the divergences in this report.
   *
   * @return an unmodifiable list of divergences
   */
  public List<Divergence> getDivergences() {
    return divergences;
  }

  /**
   * Formats this report as plain text suitable for stderr output.
   *
   * <p>Each divergence is printed on its own line with the format: {@code [TYPE] thread=threadName
   * offset=N: description (expected=E, actual=A)}
   *
   * @return the formatted report text, or an empty string if there are no divergences
   */
  public String formatAsText() {
    if (divergences.isEmpty()) {
      return "";
    }
    StringBuilder sb = new StringBuilder();
    sb.append("Replay Divergence Report (").append(divergences.size()).append(" divergence(s)):\n");
    for (int i = 0; i < divergences.size(); i++) {
      Divergence d = divergences.get(i);
      sb.append("  ")
          .append(i + 1)
          .append(". [")
          .append(d.type())
          .append("] thread=")
          .append(d.threadName())
          .append(" offset=")
          .append(d.walOffset())
          .append(": ")
          .append(d.description())
          .append(" (expected=")
          .append(d.expected())
          .append(", actual=")
          .append(d.actual())
          .append(")\n");
    }
    return sb.toString();
  }

  /**
   * Returns whether this report contains no divergences.
   *
   * @return {@code true} if the report has zero divergences
   */
  public boolean isEmpty() {
    return divergences.isEmpty();
  }

  /**
   * Returns the number of divergences in this report.
   *
   * @return the divergence count
   */
  public int size() {
    return divergences.size();
  }
}
