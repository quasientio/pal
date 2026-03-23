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

import java.nio.file.Path;

/**
 * Immutable configuration POJO holding all wizard and flag choices for the {@code pal init}
 * command.
 *
 * <p>Use the {@link Builder} to construct instances:
 *
 * <pre>{@code
 * InitConfig config = InitConfig.builder()
 *     .groupId("com.example")
 *     .artifactId("my-app")
 *     .mainClass("com.example.Main")
 *     .build();
 * }</pre>
 *
 * @since 1.0.0
 */
public final class InitConfig {

  /** Maven/Gradle group ID (e.g., {@code "com.example"}). */
  private final String groupId;

  /** Maven/Gradle artifact ID (e.g., {@code "my-app"}). */
  private final String artifactId;

  /** Project version string (e.g., {@code "1.0-SNAPSHOT"}). */
  private final String projectVersion;

  /** Fully-qualified main class name. */
  private final String mainClass;

  /** Explicit Java package name, or {@code null} to infer from groupId. */
  private final String packageName;

  /** Selected build tool (Maven or Gradle). */
  private final BuildTool buildTool;

  /** Deployment mode (local, distributed, or both). */
  private final DeploymentMode deploymentMode;

  /** PAL version string for generated build files. */
  private final String palVersion;

  /** AspectJ version string for generated build files. */
  private final String aspectjVersion;

  /** Target directory for project generation, or {@code null} for current directory. */
  private final Path targetDir;

  /** Path to an existing build file, or {@code null} for a new project. */
  private final Path existingBuildFile;

  /** Whether to generate sample application code. */
  private final boolean sampleApp;

  /** Whether to generate an RPC policy config file. */
  private final boolean rpcPolicy;

  /** Whether to generate a recording scope config file. */
  private final boolean scopePolicy;

  /** Whether to generate a logging configuration file. */
  private final boolean loggingConfig;

  /** Whether to generate an intercept bundle example. */
  private final boolean interceptBundle;

  /** Whether to generate Docker infrastructure files. */
  private final boolean infra;

  /** Whether to overwrite existing files without prompting. */
  private final boolean force;

  /** Whether this is a dry-run preview (no files written). */
  private final boolean dryRun;

  /**
   * Constructs an {@code InitConfig} from the given builder.
   *
   * @param builder the builder containing all configuration values
   */
  private InitConfig(Builder builder) {
    this.groupId = builder.groupId;
    this.artifactId = builder.artifactId;
    this.projectVersion = builder.projectVersion;
    this.mainClass = builder.mainClass;
    this.packageName = builder.packageName;
    this.buildTool = builder.buildTool;
    this.deploymentMode = builder.deploymentMode;
    this.palVersion = builder.palVersion;
    this.aspectjVersion = builder.aspectjVersion;
    this.targetDir = builder.targetDir;
    this.existingBuildFile = builder.existingBuildFile;
    this.sampleApp = builder.sampleApp;
    this.rpcPolicy = builder.rpcPolicy;
    this.scopePolicy = builder.scopePolicy;
    this.loggingConfig = builder.loggingConfig;
    this.interceptBundle = builder.interceptBundle;
    this.infra = builder.infra;
    this.force = builder.force;
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
   * Returns the Maven/Gradle group ID.
   *
   * @return the group ID
   */
  public String getGroupId() {
    return groupId;
  }

  /**
   * Returns the Maven/Gradle artifact ID.
   *
   * @return the artifact ID
   */
  public String getArtifactId() {
    return artifactId;
  }

  /**
   * Returns the project version.
   *
   * @return the project version
   */
  public String getProjectVersion() {
    return projectVersion;
  }

  /**
   * Returns the fully-qualified main class name.
   *
   * @return the main class
   */
  public String getMainClass() {
    return mainClass;
  }

  /**
   * Returns the Java package name. If no explicit package name was set, this returns the group ID
   * as the inferred package name.
   *
   * @return the package name (explicit or inferred from groupId)
   */
  public String getPackageName() {
    if (packageName != null) {
      return packageName;
    }
    return groupId;
  }

  /**
   * Returns the selected build tool.
   *
   * @return the build tool
   */
  public BuildTool getBuildTool() {
    return buildTool;
  }

  /**
   * Returns the deployment mode.
   *
   * @return the deployment mode
   */
  public DeploymentMode getDeploymentMode() {
    return deploymentMode;
  }

  /**
   * Returns the PAL version to use in generated build files.
   *
   * @return the PAL version string
   */
  public String getPalVersion() {
    return palVersion;
  }

  /**
   * Returns the AspectJ version to use in generated build files.
   *
   * @return the AspectJ version string
   */
  public String getAspectjVersion() {
    return aspectjVersion;
  }

  /**
   * Returns the target directory for project generation.
   *
   * @return the target directory, or {@code null} for the current directory
   */
  public Path getTargetDir() {
    return targetDir;
  }

  /**
   * Returns the path to an existing build file, if this is an existing project.
   *
   * @return the existing build file path, or {@code null} for a new project
   */
  public Path getExistingBuildFile() {
    return existingBuildFile;
  }

  /**
   * Returns whether to generate sample application code.
   *
   * @return {@code true} to generate sample code
   */
  public boolean isSampleApp() {
    return sampleApp;
  }

  /**
   * Returns whether to generate an RPC policy config file.
   *
   * @return {@code true} to generate RPC policy
   */
  public boolean isRpcPolicy() {
    return rpcPolicy;
  }

  /**
   * Returns whether to generate a recording scope config file.
   *
   * @return {@code true} to generate recording scope
   */
  public boolean isScopePolicy() {
    return scopePolicy;
  }

  /**
   * Returns whether to generate a logging configuration file.
   *
   * @return {@code true} to generate logging config
   */
  public boolean isLoggingConfig() {
    return loggingConfig;
  }

  /**
   * Returns whether to generate an intercept bundle example.
   *
   * @return {@code true} to generate intercept bundle
   */
  public boolean isInterceptBundle() {
    return interceptBundle;
  }

  /**
   * Returns whether to generate Docker infrastructure files.
   *
   * @return {@code true} to generate infrastructure files
   */
  public boolean isInfra() {
    return infra;
  }

  /**
   * Returns whether to overwrite existing files without prompting.
   *
   * @return {@code true} to force overwrite
   */
  public boolean isForce() {
    return force;
  }

  /**
   * Returns whether this is a dry-run preview (no files written).
   *
   * @return {@code true} for dry-run mode
   */
  public boolean isDryRun() {
    return dryRun;
  }

  /**
   * Returns {@code true} if this config represents a new project (no existing build file).
   *
   * @return whether this is a new project
   */
  public boolean isNewProject() {
    return existingBuildFile == null;
  }

  /**
   * Returns {@code true} if the deployment mode includes local configuration.
   *
   * @return whether the deployment mode is local
   */
  public boolean isLocal() {
    return deploymentMode.isLocal();
  }

  /**
   * Returns {@code true} if the deployment mode includes distributed configuration.
   *
   * @return whether the deployment mode is distributed
   */
  public boolean isDistributed() {
    return deploymentMode.isDistributed();
  }

  /**
   * Returns the source directory path derived from the package name.
   *
   * <p>Converts the package name to a path under {@code src/main/java/}. For example, package
   * {@code com.example.app} yields {@code src/main/java/com/example/app}.
   *
   * @return the source directory path string
   */
  public String getSourceDirectory() {
    return "src/main/java/" + getPackageName().replace('.', '/');
  }

  /**
   * Builder for {@link InitConfig}. All optional fields have sensible defaults.
   *
   * @since 1.0.0
   */
  public static final class Builder {

    /** Default project version. */
    private static final String DEFAULT_VERSION = "1.0-SNAPSHOT";

    /** Default AspectJ version. */
    private static final String DEFAULT_ASPECTJ_VERSION = "1.9.24";

    /** Maven/Gradle group ID. */
    private String groupId;

    /** Maven/Gradle artifact ID. */
    private String artifactId;

    /** Project version string. */
    private String projectVersion = DEFAULT_VERSION;

    /** Fully-qualified main class name. */
    private String mainClass;

    /** Explicit Java package name. */
    private String packageName;

    /** Selected build tool. */
    private BuildTool buildTool = BuildTool.MAVEN;

    /** Deployment mode. */
    private DeploymentMode deploymentMode = DeploymentMode.LOCAL;

    /** PAL version string. */
    private String palVersion;

    /** AspectJ version string. */
    private String aspectjVersion = DEFAULT_ASPECTJ_VERSION;

    /** Target directory for project generation. */
    private Path targetDir;

    /** Path to an existing build file. */
    private Path existingBuildFile;

    /** Whether to generate sample application code. */
    private boolean sampleApp = true;

    /** Whether to generate an RPC policy config. */
    private boolean rpcPolicy;

    /** Whether to generate a recording scope config. */
    private boolean scopePolicy;

    /** Whether to generate a logging config. */
    private boolean loggingConfig = true;

    /** Whether to generate an intercept bundle example. */
    private boolean interceptBundle;

    /** Whether to generate Docker infrastructure files. */
    private boolean infra;

    /** Whether to force overwrite existing files. */
    private boolean force;

    /** Whether this is a dry-run preview. */
    private boolean dryRun;

    /** Creates a new builder with default values. */
    private Builder() {}

    /**
     * Sets the Maven/Gradle group ID.
     *
     * @param groupId the group ID
     * @return this builder
     */
    public Builder groupId(String groupId) {
      this.groupId = groupId;
      return this;
    }

    /**
     * Sets the Maven/Gradle artifact ID.
     *
     * @param artifactId the artifact ID
     * @return this builder
     */
    public Builder artifactId(String artifactId) {
      this.artifactId = artifactId;
      return this;
    }

    /**
     * Sets the project version.
     *
     * @param projectVersion the project version
     * @return this builder
     */
    public Builder projectVersion(String projectVersion) {
      this.projectVersion = projectVersion;
      return this;
    }

    /**
     * Sets the fully-qualified main class name.
     *
     * @param mainClass the main class
     * @return this builder
     */
    public Builder mainClass(String mainClass) {
      this.mainClass = mainClass;
      return this;
    }

    /**
     * Sets the Java package name explicitly, overriding group-ID-based inference.
     *
     * @param packageName the package name
     * @return this builder
     */
    public Builder packageName(String packageName) {
      this.packageName = packageName;
      return this;
    }

    /**
     * Sets the build tool.
     *
     * @param buildTool the build tool
     * @return this builder
     */
    public Builder buildTool(BuildTool buildTool) {
      this.buildTool = buildTool;
      return this;
    }

    /**
     * Sets the deployment mode.
     *
     * @param deploymentMode the deployment mode
     * @return this builder
     */
    public Builder deploymentMode(DeploymentMode deploymentMode) {
      this.deploymentMode = deploymentMode;
      return this;
    }

    /**
     * Sets the PAL version string for generated build files.
     *
     * @param palVersion the PAL version
     * @return this builder
     */
    public Builder palVersion(String palVersion) {
      this.palVersion = palVersion;
      return this;
    }

    /**
     * Sets the AspectJ version string for generated build files.
     *
     * @param aspectjVersion the AspectJ version
     * @return this builder
     */
    public Builder aspectjVersion(String aspectjVersion) {
      this.aspectjVersion = aspectjVersion;
      return this;
    }

    /**
     * Sets the target directory for project generation.
     *
     * @param targetDir the target directory
     * @return this builder
     */
    public Builder targetDir(Path targetDir) {
      this.targetDir = targetDir;
      return this;
    }

    /**
     * Sets the path to an existing build file, indicating this is an existing project.
     *
     * @param existingBuildFile the existing build file path
     * @return this builder
     */
    public Builder existingBuildFile(Path existingBuildFile) {
      this.existingBuildFile = existingBuildFile;
      return this;
    }

    /**
     * Sets whether to generate sample application code.
     *
     * @param sampleApp {@code true} to generate sample code
     * @return this builder
     */
    public Builder sampleApp(boolean sampleApp) {
      this.sampleApp = sampleApp;
      return this;
    }

    /**
     * Sets whether to generate an RPC policy config.
     *
     * @param rpcPolicy {@code true} to generate RPC policy
     * @return this builder
     */
    public Builder rpcPolicy(boolean rpcPolicy) {
      this.rpcPolicy = rpcPolicy;
      return this;
    }

    /**
     * Sets whether to generate a recording scope config.
     *
     * @param scopePolicy {@code true} to generate recording scope
     * @return this builder
     */
    public Builder scopePolicy(boolean scopePolicy) {
      this.scopePolicy = scopePolicy;
      return this;
    }

    /**
     * Sets whether to generate a logging configuration.
     *
     * @param loggingConfig {@code true} to generate logging config
     * @return this builder
     */
    public Builder loggingConfig(boolean loggingConfig) {
      this.loggingConfig = loggingConfig;
      return this;
    }

    /**
     * Sets whether to generate an intercept bundle example.
     *
     * @param interceptBundle {@code true} to generate intercept bundle
     * @return this builder
     */
    public Builder interceptBundle(boolean interceptBundle) {
      this.interceptBundle = interceptBundle;
      return this;
    }

    /**
     * Sets whether to generate Docker infrastructure files.
     *
     * @param infra {@code true} to generate infrastructure
     * @return this builder
     */
    public Builder infra(boolean infra) {
      this.infra = infra;
      return this;
    }

    /**
     * Sets whether to overwrite existing files without prompting.
     *
     * @param force {@code true} to force overwrite
     * @return this builder
     */
    public Builder force(boolean force) {
      this.force = force;
      return this;
    }

    /**
     * Sets whether this is a dry-run preview.
     *
     * @param dryRun {@code true} for dry-run mode
     * @return this builder
     */
    public Builder dryRun(boolean dryRun) {
      this.dryRun = dryRun;
      return this;
    }

    /**
     * Builds an immutable {@link InitConfig} from this builder's state.
     *
     * @return a new {@code InitConfig}
     */
    public InitConfig build() {
      return new InitConfig(this);
    }
  }
}
