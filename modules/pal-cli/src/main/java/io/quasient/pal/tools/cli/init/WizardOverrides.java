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
package io.quasient.pal.tools.cli.init;

/**
 * CLI flag overrides for the interactive wizard.
 *
 * <p>When a field is non-null, the wizard uses it directly instead of prompting the user. When
 * {@code null}, the wizard prompts as usual. This allows CLI flags like {@code --group-id} to
 * pre-fill values in interactive mode.
 *
 * @since 1.0.0
 */
public final class WizardOverrides {

  /** Group ID override, or {@code null} to prompt. */
  private final String groupId;

  /** Artifact ID override, or {@code null} to prompt. */
  private final String artifactId;

  /** Project version override, or {@code null} to prompt. */
  private final String projectVersion;

  /** Build tool override, or {@code null} to prompt. */
  private final BuildTool buildTool;

  /** Main class override, or {@code null} to prompt. */
  private final String mainClass;

  /** Whether the app is interceptable, or {@code null} to prompt. */
  private final Boolean interceptable;

  /** Whether the app intercepts other peers, or {@code null} to prompt. */
  private final Boolean intercepting;

  /** Whether to expose methods via JSON-RPC, or {@code null} to prompt. */
  private final Boolean jsonRpc;

  /** Whether to use Kafka for WAL, or {@code null} to prompt. */
  private final Boolean kafka;

  /** Whether the app runs in as-service mode (no main class). */
  private final boolean asService;

  /**
   * Constructs overrides from the given builder.
   *
   * @param builder the builder containing override values
   */
  private WizardOverrides(Builder builder) {
    this.groupId = builder.groupId;
    this.artifactId = builder.artifactId;
    this.projectVersion = builder.projectVersion;
    this.buildTool = builder.buildTool;
    this.mainClass = builder.mainClass;
    this.interceptable = builder.interceptable;
    this.intercepting = builder.intercepting;
    this.jsonRpc = builder.jsonRpc;
    this.kafka = builder.kafka;
    this.asService = builder.asService;
  }

  /**
   * Returns an empty overrides instance where all fields are {@code null} (prompt for everything).
   *
   * @return empty overrides
   */
  public static WizardOverrides none() {
    return new Builder().build();
  }

  /**
   * Creates a new builder.
   *
   * @return a fresh builder
   */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * Returns the group ID override.
   *
   * @return the group ID, or {@code null} to prompt
   */
  public String getGroupId() {
    return groupId;
  }

  /**
   * Returns the artifact ID override.
   *
   * @return the artifact ID, or {@code null} to prompt
   */
  public String getArtifactId() {
    return artifactId;
  }

  /**
   * Returns the project version override.
   *
   * @return the version, or {@code null} to prompt
   */
  public String getProjectVersion() {
    return projectVersion;
  }

  /**
   * Returns the build tool override.
   *
   * @return the build tool, or {@code null} to prompt
   */
  public BuildTool getBuildTool() {
    return buildTool;
  }

  /**
   * Returns the main class override.
   *
   * @return the main class, or {@code null} to prompt
   */
  public String getMainClass() {
    return mainClass;
  }

  /**
   * Returns the interceptable override.
   *
   * @return {@code true}/{@code false} if explicitly set, or {@code null} to prompt
   */
  public Boolean getInterceptable() {
    return interceptable;
  }

  /**
   * Returns the intercepting override.
   *
   * @return {@code true}/{@code false} if explicitly set, or {@code null} to prompt
   */
  public Boolean getIntercepting() {
    return intercepting;
  }

  /**
   * Returns the JSON-RPC override.
   *
   * @return {@code true}/{@code false} if explicitly set, or {@code null} to prompt
   */
  public Boolean getJsonRpc() {
    return jsonRpc;
  }

  /**
   * Returns the Kafka override.
   *
   * @return {@code true}/{@code false} if explicitly set, or {@code null} to prompt
   */
  public Boolean getKafka() {
    return kafka;
  }

  /**
   * Returns whether as-service mode was explicitly requested.
   *
   * @return {@code true} if as-service was requested
   */
  public boolean isAsService() {
    return asService;
  }

  /**
   * Builder for {@link WizardOverrides}.
   *
   * @since 1.0.0
   */
  public static final class Builder {

    /** Group ID override. */
    private String groupId;

    /** Artifact ID override. */
    private String artifactId;

    /** Project version override. */
    private String projectVersion;

    /** Build tool override. */
    private BuildTool buildTool;

    /** Main class override. */
    private String mainClass;

    /** Interceptable override. */
    private Boolean interceptable;

    /** Intercepting override. */
    private Boolean intercepting;

    /** JSON-RPC override. */
    private Boolean jsonRpc;

    /** Kafka override. */
    private Boolean kafka;

    /** As-service flag. */
    private boolean asService;

    /** Creates a new builder with all defaults null. */
    private Builder() {}

    /**
     * Sets the group ID override.
     *
     * @param groupId the group ID
     * @return this builder
     */
    public Builder groupId(String groupId) {
      this.groupId = groupId;
      return this;
    }

    /**
     * Sets the artifact ID override.
     *
     * @param artifactId the artifact ID
     * @return this builder
     */
    public Builder artifactId(String artifactId) {
      this.artifactId = artifactId;
      return this;
    }

    /**
     * Sets the project version override.
     *
     * @param projectVersion the version
     * @return this builder
     */
    public Builder projectVersion(String projectVersion) {
      this.projectVersion = projectVersion;
      return this;
    }

    /**
     * Sets the build tool override.
     *
     * @param buildTool the build tool
     * @return this builder
     */
    public Builder buildTool(BuildTool buildTool) {
      this.buildTool = buildTool;
      return this;
    }

    /**
     * Sets the main class override.
     *
     * @param mainClass the main class
     * @return this builder
     */
    public Builder mainClass(String mainClass) {
      this.mainClass = mainClass;
      return this;
    }

    /**
     * Sets the interceptable override.
     *
     * @param interceptable true or false
     * @return this builder
     */
    public Builder interceptable(boolean interceptable) {
      this.interceptable = interceptable;
      return this;
    }

    /**
     * Sets the intercepting override.
     *
     * @param intercepting true or false
     * @return this builder
     */
    public Builder intercepting(boolean intercepting) {
      this.intercepting = intercepting;
      return this;
    }

    /**
     * Sets the JSON-RPC override.
     *
     * @param jsonRpc true or false
     * @return this builder
     */
    public Builder jsonRpc(boolean jsonRpc) {
      this.jsonRpc = jsonRpc;
      return this;
    }

    /**
     * Sets the Kafka override.
     *
     * @param kafka true or false
     * @return this builder
     */
    public Builder kafka(boolean kafka) {
      this.kafka = kafka;
      return this;
    }

    /**
     * Sets the as-service flag.
     *
     * @param asService true if as-service mode
     * @return this builder
     */
    public Builder asService(boolean asService) {
      this.asService = asService;
      return this;
    }

    /**
     * Builds the overrides.
     *
     * @return a new {@code WizardOverrides}
     */
    public WizardOverrides build() {
      return new WizardOverrides(this);
    }
  }
}
