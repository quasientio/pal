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
 * <p>Several accessors are derived from intent flags rather than stored directly:
 *
 * <ul>
 *   <li>{@link #isRpcPolicy()} is derived from {@link #isIntercepting()} or {@link #isJsonRpc()}
 *   <li>{@link #isInterceptBundle()} is derived from {@link #isIntercepting()}
 *   <li>{@link #isInfra()} is derived from {@link #needsEtcd()} or {@link #needsKafka()}
 *   <li>{@link #isAsService()} is derived from {@link #isIntercepting()} or {@link #isJsonRpc()}
 *       and absence of {@link #getMainClass()}
 *   <li>{@link #needsWeaving()} returns the {@code weaving} flag (default {@code true}, {@code
 *       false} for RPC-gateway-only mode)
 * </ul>
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

  /** Fully-qualified main class name, or {@code null} for as-service mode. */
  private final String mainClass;

  /** Explicit Java package name, or {@code null} to infer from groupId. */
  private final String packageName;

  /** Selected build tool (Maven or Gradle). */
  private final BuildTool buildTool;

  /** PAL version string for generated build files. */
  private final String palVersion;

  /** AspectJ version string for generated build files. */
  private final String aspectjVersion;

  /** Target directory for project generation, or {@code null} for current directory. */
  private final Path targetDir;

  /** Path to an existing build file, or {@code null} for a new project. */
  private final Path existingBuildFile;

  /** Whether this app can be intercepted by other peers. */
  private final boolean interceptable;

  /** Whether this app intercepts other peers via callbacks. */
  private final boolean intercepting;

  /** Whether to expose methods via JSON-RPC. */
  private final boolean jsonRpc;

  /** Whether AspectJ weaving is needed (false for RPC-gateway-only). */
  private final boolean weaving;

  /** Whether this app uses Kafka for WAL. */
  private final boolean kafka;

  /** Whether to generate sample application code. */
  private final boolean sampleApp;

  /** Whether to generate a recording scope config file. */
  private final boolean scopePolicy;

  /** Whether to generate a logging configuration file. */
  private final boolean loggingConfig;

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
    this.palVersion = builder.palVersion;
    this.aspectjVersion = builder.aspectjVersion;
    this.targetDir = builder.targetDir;
    this.existingBuildFile = builder.existingBuildFile;
    this.interceptable = builder.interceptable;
    this.intercepting = builder.intercepting;
    this.jsonRpc = builder.jsonRpc;
    this.weaving = builder.weaving;
    this.kafka = builder.kafka;
    this.sampleApp = builder.sampleApp;
    this.scopePolicy = builder.scopePolicy;
    this.loggingConfig = builder.loggingConfig;
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
   * Returns the fully-qualified main class name, or {@code null} for as-service mode.
   *
   * @return the main class, or {@code null}
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
   * Returns whether this app can be intercepted by other peers.
   *
   * @return {@code true} if the app is interceptable
   */
  public boolean isInterceptable() {
    return interceptable;
  }

  /**
   * Returns whether this app intercepts other peers via callbacks.
   *
   * @return {@code true} if the app is intercepting
   */
  public boolean isIntercepting() {
    return intercepting;
  }

  /**
   * Returns whether to expose methods via JSON-RPC.
   *
   * @return {@code true} if JSON-RPC is enabled
   */
  public boolean isJsonRpc() {
    return jsonRpc;
  }

  /**
   * Returns whether AspectJ weaving is needed. Defaults to {@code true}; only {@code false} for
   * RPC-gateway-only mode where PAL's message pipeline is not used.
   *
   * @return {@code true} if weaving is needed
   */
  public boolean needsWeaving() {
    return weaving;
  }

  /**
   * Returns whether this app uses Kafka for WAL.
   *
   * @return {@code true} if Kafka is used
   */
  public boolean isKafka() {
    return kafka;
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
   * Returns whether to generate an RPC policy config file. Derived from {@link #isIntercepting()}
   * or {@link #isJsonRpc()}.
   *
   * @return {@code true} to generate RPC policy
   */
  public boolean isRpcPolicy() {
    return intercepting || jsonRpc;
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
   * Returns whether to generate an intercept bundle example. Derived from {@link
   * #isIntercepting()}.
   *
   * @return {@code true} to generate intercept bundle
   */
  public boolean isInterceptBundle() {
    return intercepting;
  }

  /**
   * Returns whether to generate Docker infrastructure files. Derived: {@code true} when either etcd
   * or Kafka is needed.
   *
   * @return {@code true} to generate infrastructure files
   */
  public boolean isInfra() {
    return needsEtcd() || needsKafka();
  }

  /**
   * Returns whether etcd infrastructure is needed. Etcd is required for intercept registration
   * (both interceptable and intercepting roles).
   *
   * @return {@code true} if etcd is needed
   */
  public boolean needsEtcd() {
    return interceptable || intercepting;
  }

  /**
   * Returns whether Kafka infrastructure is needed.
   *
   * @return {@code true} if Kafka is needed
   */
  public boolean needsKafka() {
    return kafka;
  }

  /**
   * Returns whether the {@code pal-client} dependency should be added to the build file. Required
   * for intercepting apps to implement callback handlers.
   *
   * @return {@code true} if pal-client is needed
   */
  public boolean isPalClient() {
    return intercepting;
  }

  /**
   * Returns whether this app runs in as-service mode (no main class). Derived from intercepting or
   * JSON-RPC intent with no main class specified.
   *
   * @return {@code true} for as-service mode
   */
  public boolean isAsService() {
    return (intercepting || jsonRpc) && (mainClass == null || mainClass.isEmpty());
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
   * Returns {@code true} if the project uses Kafka for distributed WAL.
   *
   * @return whether the project uses Kafka
   */
  public boolean isDistributed() {
    return kafka;
  }

  /**
   * Returns {@code true} always. Every project can work in local mode.
   *
   * @return {@code true}
   */
  public boolean isLocal() {
    return true;
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
   * Builds the basic {@code pal run} command: just the classpath and main class.
   *
   * @param cpDir the classpath directory (e.g., {@code "target/classes"})
   * @param mainClassFallback the main class to use when {@link #getMainClass()} is {@code null}
   * @return the formatted run command
   */
  public String buildRunCommand(String cpDir, String mainClassFallback) {
    StringBuilder cmd = new StringBuilder("pal run");
    cmd.append(" -cp ").append(cpDir);
    if (!isAsService()) {
      String mc = mainClass != null ? mainClass : mainClassFallback;
      cmd.append(' ').append(mc);
    }
    return cmd.toString();
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

    /** PAL version string. */
    private String palVersion;

    /** AspectJ version string. */
    private String aspectjVersion = DEFAULT_ASPECTJ_VERSION;

    /** Target directory for project generation. */
    private Path targetDir;

    /** Path to an existing build file. */
    private Path existingBuildFile;

    /** Whether this app can be intercepted by other peers. */
    private boolean interceptable;

    /** Whether this app intercepts other peers via callbacks. */
    private boolean intercepting;

    /** Whether to expose methods via JSON-RPC. */
    private boolean jsonRpc;

    /** Whether AspectJ weaving is needed. */
    private boolean weaving = true;

    /** Whether this app uses Kafka for WAL. */
    private boolean kafka;

    /** Whether to generate sample application code. */
    private boolean sampleApp = true;

    /** Whether to generate a recording scope config. */
    private boolean scopePolicy;

    /** Whether to generate a logging config. */
    private boolean loggingConfig = true;

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
     * Sets whether this app can be intercepted by other peers.
     *
     * @param interceptable {@code true} if the app is interceptable
     * @return this builder
     */
    public Builder interceptable(boolean interceptable) {
      this.interceptable = interceptable;
      return this;
    }

    /**
     * Sets whether this app intercepts other peers via callbacks.
     *
     * @param intercepting {@code true} if the app is intercepting
     * @return this builder
     */
    public Builder intercepting(boolean intercepting) {
      this.intercepting = intercepting;
      return this;
    }

    /**
     * Sets whether to expose methods via JSON-RPC.
     *
     * @param jsonRpc {@code true} to enable JSON-RPC
     * @return this builder
     */
    public Builder jsonRpc(boolean jsonRpc) {
      this.jsonRpc = jsonRpc;
      return this;
    }

    /**
     * Sets whether AspectJ weaving is needed.
     *
     * @param weaving {@code true} if weaving is needed (default), {@code false} for
     *     RPC-gateway-only
     * @return this builder
     */
    public Builder weaving(boolean weaving) {
      this.weaving = weaving;
      return this;
    }

    /**
     * Sets whether this app uses Kafka for WAL.
     *
     * @param kafka {@code true} to use Kafka
     * @return this builder
     */
    public Builder kafka(boolean kafka) {
      this.kafka = kafka;
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
