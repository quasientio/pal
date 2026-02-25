/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
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
   * <p>Each divergence is printed on its own line with the format: {@code [TYPE] offset=N:
   * description (expected=E, actual=A)}
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
          .append("] offset=")
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
