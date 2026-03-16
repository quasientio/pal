/*
 * Copyright (C) 2026 Quasient Inc. <https://www.quasient.com>
 *
 * Use of this software is governed by the Business Source License 1.1
 * included in the file LICENSE and at https://mariadb.com/bsl11
 *
 * Change Date: 2030-10-01
 * Change License: Apache 2.0
 */
package io.quasient.pal.dsl.intercept;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Immutable value class for a named bundle of intercept specifications.
 *
 * <p>A bundle groups related {@link InterceptSpec} instances under a single name and provides
 * shared {@link InterceptBundleDefaults} that individual specs can override.
 *
 * <p>Use the {@link Builder} to construct instances:
 *
 * <pre>{@code
 * InterceptBundleSpec bundle = InterceptBundleSpec.builder("fraud-check-v1")
 *     .defaults(defaults)
 *     .addIntercept(spec1)
 *     .addIntercept(spec2)
 *     .build();
 * }</pre>
 *
 * @see InterceptSpec
 * @see InterceptBundleDefaults
 */
public final class InterceptBundleSpec {

  /** The name of this bundle. */
  private final String bundleName;

  /** The bundle-level defaults applied to intercepts when no override is set. */
  private final InterceptBundleDefaults defaults;

  /** The list of intercept specifications in this bundle. */
  private final List<InterceptSpec> intercepts;

  /**
   * Constructs an InterceptBundleSpec from a builder.
   *
   * @param builder the builder containing validated field values
   */
  private InterceptBundleSpec(Builder builder) {
    this.bundleName = builder.bundleName;
    this.defaults = builder.defaults;
    this.intercepts = Collections.unmodifiableList(new ArrayList<>(builder.intercepts));
  }

  /**
   * Parses a YAML string into an {@code InterceptBundleSpec}.
   *
   * @param yamlContent the YAML content to parse
   * @return the parsed bundle specification
   * @throws IllegalArgumentException if the YAML is empty, malformed, or missing required fields
   */
  public static InterceptBundleSpec fromYaml(String yamlContent) {
    return new InterceptBundleParser().parse(yamlContent);
  }

  /**
   * Reads a YAML file and parses it into an {@code InterceptBundleSpec}.
   *
   * @param path the path to the YAML file
   * @return the parsed bundle specification
   * @throws IOException if the file cannot be read
   * @throws IllegalArgumentException if the YAML is empty, malformed, or missing required fields
   */
  public static InterceptBundleSpec fromYamlFile(Path path) throws IOException {
    return fromYaml(Files.readString(path));
  }

  /**
   * Creates a new builder for constructing {@code InterceptBundleSpec} instances.
   *
   * @param bundleName the name of the bundle; must not be {@code null}
   * @return a new builder
   */
  public static Builder builder(String bundleName) {
    return new Builder(bundleName);
  }

  /**
   * Returns the bundle name.
   *
   * @return the bundle name
   */
  public String getBundleName() {
    return bundleName;
  }

  /**
   * Returns the bundle-level defaults.
   *
   * @return the defaults; never {@code null}
   */
  public InterceptBundleDefaults getDefaults() {
    return defaults;
  }

  /**
   * Returns the list of intercept specifications in this bundle.
   *
   * @return an unmodifiable list of intercept specs
   */
  public List<InterceptSpec> getIntercepts() {
    return intercepts;
  }

  /**
   * Builder for constructing {@link InterceptBundleSpec} instances.
   *
   * <p>Requires a bundle name and at least one intercept spec.
   */
  public static final class Builder {

    /** The name of the bundle being built. */
    private final String bundleName;

    /** The bundle-level defaults; defaults to EMPTY if not set. */
    private InterceptBundleDefaults defaults = InterceptBundleDefaults.EMPTY;

    /** The accumulated intercept specifications. */
    private final List<InterceptSpec> intercepts = new ArrayList<>();

    /**
     * Constructs a new builder with the given bundle name.
     *
     * @param bundleName the bundle name
     */
    private Builder(String bundleName) {
      this.bundleName = bundleName;
    }

    /**
     * Sets the bundle-level defaults.
     *
     * @param defaults the defaults to apply to intercepts in this bundle
     * @return this builder
     */
    public Builder defaults(InterceptBundleDefaults defaults) {
      this.defaults = defaults != null ? defaults : InterceptBundleDefaults.EMPTY;
      return this;
    }

    /**
     * Adds an intercept specification to this bundle.
     *
     * @param spec the intercept spec to add; must not be {@code null}
     * @return this builder
     * @throws NullPointerException if {@code spec} is {@code null}
     */
    public Builder addIntercept(InterceptSpec spec) {
      Objects.requireNonNull(spec, "intercept spec must not be null");
      this.intercepts.add(spec);
      return this;
    }

    /**
     * Builds and validates the {@link InterceptBundleSpec}.
     *
     * @return a new immutable {@code InterceptBundleSpec}
     * @throws NullPointerException if bundleName is {@code null}
     * @throws IllegalStateException if no intercepts have been added
     */
    public InterceptBundleSpec build() {
      Objects.requireNonNull(bundleName, "bundleName must not be null");
      if (intercepts.isEmpty()) {
        throw new IllegalStateException("at least one intercept must be added");
      }
      return new InterceptBundleSpec(this);
    }
  }
}
