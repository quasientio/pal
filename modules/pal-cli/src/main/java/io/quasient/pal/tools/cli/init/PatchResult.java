/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.tools.cli.init;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Immutable result of a build file patch operation, reporting additions, skips, and warnings.
 *
 * <p>Use the {@link Builder} to construct instances:
 *
 * <pre>{@code
 * PatchResult result = PatchResult.builder()
 *     .addition("Added pal-weave dependency")
 *     .skip("AspectJ plugin already present")
 *     .build();
 * }</pre>
 *
 * @since 1.0.0
 */
public final class PatchResult {

  /** Descriptions of items that were added to the build file. */
  private final List<String> additions;

  /** Descriptions of items that were skipped (already present). */
  private final List<String> skips;

  /** Warning messages about potential issues encountered during patching. */
  private final List<String> warnings;

  /** Whether this result was produced during a dry-run (no files written). */
  private final boolean dryRun;

  /**
   * Constructs a {@code PatchResult} from the given builder.
   *
   * @param builder the builder containing all result data
   */
  private PatchResult(Builder builder) {
    this.additions = Collections.unmodifiableList(new ArrayList<>(builder.additions));
    this.skips = Collections.unmodifiableList(new ArrayList<>(builder.skips));
    this.warnings = Collections.unmodifiableList(new ArrayList<>(builder.warnings));
    this.dryRun = builder.dryRun;
  }

  /**
   * Creates a new {@link Builder}.
   *
   * @return a fresh builder instance
   */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * Returns descriptions of items that were added to the build file.
   *
   * @return an unmodifiable list of addition descriptions
   */
  public List<String> getAdditions() {
    return additions;
  }

  /**
   * Returns descriptions of items that were skipped because they were already present.
   *
   * @return an unmodifiable list of skip descriptions
   */
  public List<String> getSkips() {
    return skips;
  }

  /**
   * Returns warning messages about potential issues encountered during patching.
   *
   * @return an unmodifiable list of warning messages
   */
  public List<String> getWarnings() {
    return warnings;
  }

  /**
   * Returns {@code true} if the build file was already fully configured (no additions were made but
   * at least one item was skipped).
   *
   * @return whether the file was already configured
   */
  public boolean isAlreadyConfigured() {
    return additions.isEmpty() && !skips.isEmpty();
  }

  /**
   * Returns {@code true} if this result was produced during a dry-run operation where no files were
   * actually written or modified.
   *
   * @return whether this is a dry-run result
   */
  public boolean isDryRun() {
    return dryRun;
  }

  /**
   * Builder for {@link PatchResult}.
   *
   * @since 1.0.0
   */
  public static final class Builder {

    /** Accumulated addition descriptions. */
    private final List<String> additions = new ArrayList<>();

    /** Accumulated skip descriptions. */
    private final List<String> skips = new ArrayList<>();

    /** Accumulated warning messages. */
    private final List<String> warnings = new ArrayList<>();

    /** Whether this result is from a dry-run operation. */
    private boolean dryRun;

    /** Creates a new builder. */
    private Builder() {}

    /**
     * Records that an item was added to the build file.
     *
     * @param description a human-readable description of the addition
     * @return this builder
     */
    public Builder addition(String description) {
      additions.add(description);
      return this;
    }

    /**
     * Records that an item was skipped because it was already present.
     *
     * @param description a human-readable description of the skipped item
     * @return this builder
     */
    public Builder skip(String description) {
      skips.add(description);
      return this;
    }

    /**
     * Records a warning about a potential issue encountered during patching.
     *
     * @param message the warning message
     * @return this builder
     */
    public Builder warning(String message) {
      warnings.add(message);
      return this;
    }

    /**
     * Sets whether this result is from a dry-run operation.
     *
     * @param dryRun {@code true} if this is a dry-run result
     * @return this builder
     */
    public Builder dryRun(boolean dryRun) {
      this.dryRun = dryRun;
      return this;
    }

    /**
     * Builds an immutable {@link PatchResult} from this builder's state.
     *
     * @return a new {@code PatchResult}
     */
    public PatchResult build() {
      return new PatchResult(this);
    }
  }
}
